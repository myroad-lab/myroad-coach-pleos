package com.myroad.coach.data

/**
 * 시연용 4주치 출퇴근 주행 기록 (주 8회).
 * 처음 설치했을 때 습관 리포트가 비어 보이지 않도록 넣는 예시 데이터이며, 화면에 '시연용 샘플'로 표시합니다.
 * 실제 주행이 쌓이면 그 기록과 함께 분석됩니다.
 *
 * 판정 기회(분모)를 함께 만들어, 화면의 'n/N회'가 실제 계산과 맞도록 합니다.
 * 흐름: 합류 직전 제동이 잦던 운전자가 진입 전 코칭을 받으며 매주 줄어드는 모습.
 */
object SeedTrips {

    private const val DAY = 24L * 60 * 60 * 1000
    private const val TRIPS_PER_WEEK = 8
    private const val KM_PER_TRIP = 19.5f

    // 주차별(4주 전 → 이번 주) 위험 판정 수
    private val mergeLate = intArrayOf(5, 4, 3, 1)        // 합류 직전 제동 / 주 8회
    private val roundaboutLate = intArrayOf(2, 2, 1, 1)   // 회전교차로 직전 제동 / 주 4회
    private val icLate = intArrayOf(1, 0, 0, 0)
    private val icNoSignal = intArrayOf(4, 3, 3, 2)       // 우측 IC 진출 / 주 8회
    private val rightNoSignal = intArrayOf(3, 2, 1, 1)
    private val leftNoSignal = intArrayOf(2, 2, 1, 1)
    private val mergeNoSignal = intArrayOf(1, 1, 0, 0)
    private val speeding = intArrayOf(3, 2, 2, 1)         // 어린이보호구역 카메라 / 주 8회

    private class Slot(val behavior: Behavior, val type: RoadType, val dir: Direction, var outcome: Outcome = Outcome.OK, var momentId: Int? = null)

    fun generate(now: Long): List<TripRecord> {
        val trips = mutableListOf<TripRecord>()
        var momentId = 1000
        for (w in 0 until 4) {
            val weekStart = now - (4 - w) * 7 * DAY + DAY / 2
            val rainy = BooleanArray(TRIPS_PER_WEEK) { it % 3 == 0 }      // 주 3회 우천 추정
            val night = BooleanArray(TRIPS_PER_WEEK) { it % 4 == 1 }      // 주 2회 야간 (퇴근길)
            val slots = List(TRIPS_PER_WEEK) { t ->
                buildList {
                    add(Slot(Behavior.PRE_ENTRY_BRAKE, RoadType.MERGE, Direction.LEFT))
                    add(Slot(Behavior.PRE_ENTRY_BRAKE, RoadType.IC_EXIT, Direction.RIGHT))
                    if (t % 2 == 0) add(Slot(Behavior.PRE_ENTRY_BRAKE, RoadType.ROUNDABOUT, Direction.RIGHT))
                    if (t % 3 == 0) add(Slot(Behavior.PRE_ENTRY_BRAKE, RoadType.SHARP_TURN, Direction.NONE))
                    add(Slot(Behavior.SIGNAL, RoadType.MERGE, Direction.LEFT))
                    add(Slot(Behavior.SIGNAL, RoadType.IC_EXIT, Direction.RIGHT))
                    add(Slot(Behavior.SIGNAL, RoadType.IC_ENTER, Direction.RIGHT))
                    add(Slot(Behavior.SIGNAL, RoadType.RIGHT_TURN, Direction.RIGHT))
                    add(Slot(Behavior.SIGNAL, RoadType.LEFT_TURN, Direction.LEFT))
                    if (t % 2 == 0) add(Slot(Behavior.SIGNAL, RoadType.ROUNDABOUT, Direction.RIGHT))
                    // 조향 근거가 없어 판정하지 못한 좌회전 (분모에서 제외)
                    if (t % 2 == 1) add(Slot(Behavior.SIGNAL, RoadType.LEFT_TURN, Direction.LEFT, Outcome.UNDETERMINED))
                    add(Slot(Behavior.ZONE_SPEED, RoadType.SCHOOL_ZONE, Direction.NONE))
                }
            }
            val moments = List(TRIPS_PER_WEEK) { mutableListOf<RiskMoment>() }
            val order = (0 until TRIPS_PER_WEEK).sortedBy { if (rainy[it]) 0 else 1 } // 비 오는 날부터 채움

            fun flip(count: Int, b: Behavior, type: RoadType, preferRainy: Boolean, kind: MomentKind, cause: (Int) -> String) {
                var left = count
                val tripsOrder = if (preferRainy) order else (0 until TRIPS_PER_WEEK).map { (it * 3 + w) % TRIPS_PER_WEEK }
                for (t in tripsOrder) {
                    if (left == 0) break
                    val s = slots[t].firstOrNull { it.behavior == b && it.type == type && it.outcome == Outcome.OK } ?: continue
                    s.outcome = Outcome.RISK
                    s.momentId = momentId
                    val remain = if (b == Behavior.PRE_ENTRY_BRAKE) 25 + (left * 7) % 25 else null
                    moments[t] += RiskMoment(
                        id = momentId++, time = if (t % 2 == 0) "08:4${t % 10}" else "18:2${t % 10}",
                        kind = kind, roadType = type, rainy = rainy[t], night = night[t],
                        cause = cause(remain ?: 0), mapX = 0.3f, mapY = 0.6f,
                        remainMeters = remain, direction = s.dir,
                    )
                    left--
                }
            }
            flip(mergeLate[w], Behavior.PRE_ENTRY_BRAKE, RoadType.MERGE, true, MomentKind.LATE_BRAKE) { "합류 지점 ${it}m 전에서야 브레이크를 밟았어요" }
            flip(roundaboutLate[w], Behavior.PRE_ENTRY_BRAKE, RoadType.ROUNDABOUT, false, MomentKind.LATE_BRAKE) { "회전교차로 ${it}m 전에서야 브레이크를 밟았어요" }
            flip(icLate[w], Behavior.PRE_ENTRY_BRAKE, RoadType.IC_EXIT, false, MomentKind.LATE_BRAKE) { "IC 진출 ${it}m 전에서야 브레이크를 밟았어요" }
            flip(icNoSignal[w], Behavior.SIGNAL, RoadType.IC_EXIT, false, MomentKind.NO_SIGNAL) { "우측 IC 진출 중 방향지시등이 꺼져 있었어요" }
            flip(rightNoSignal[w], Behavior.SIGNAL, RoadType.RIGHT_TURN, false, MomentKind.NO_SIGNAL) { "우회전할 때 방향지시등이 꺼져 있었어요" }
            flip(leftNoSignal[w], Behavior.SIGNAL, RoadType.LEFT_TURN, false, MomentKind.NO_SIGNAL) { "좌회전할 때 방향지시등이 꺼져 있었어요" }
            flip(mergeNoSignal[w], Behavior.SIGNAL, RoadType.MERGE, false, MomentKind.NO_SIGNAL) { "좌측 합류 중 방향지시등이 꺼져 있었어요" }
            flip(speeding[w], Behavior.ZONE_SPEED, RoadType.SCHOOL_ZONE, false, MomentKind.SPEEDING) { "제한속도 30km/h 단속 구간이었어요" }

            for (t in 0 until TRIPS_PER_WEEK) {
                val checks = slots[t].map { Check(it.behavior, it.type, it.dir, it.outcome, it.momentId) }
                val passes = checks.filter { it.behavior != Behavior.SIGNAL }.groupingBy { it.roadType }.eachCount() +
                    mapOf(RoadType.TUNNEL to 1)
                trips += TripRecord(
                    id = (w * 100 + t).toLong(),
                    startedAt = weekStart + (t / 2) * DAY + (if (t % 2 == 0) 0 else DAY / 3),
                    startLabel = if (t % 2 == 0) "08:31" else "18:05",
                    endLabel = if (t % 2 == 0) "09:07" else "18:44",
                    distanceKm = KM_PER_TRIP,
                    rainy = rainy[t], night = night[t],
                    passes = passes,
                    moments = moments[t],
                    isSample = true,
                    checks = checks,
                )
            }
        }
        return trips
    }
}
