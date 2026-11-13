package com.myroad.coach.data

/**
 * 시연용 4주치 출퇴근 주행 기록.
 * 처음 설치했을 때 습관 리포트가 비어 보이지 않도록 넣는 예시 데이터이며,
 * 실제 주행이 쌓이면 그 기록과 함께 분석됩니다.
 *
 * 흐름: 합류 구간 급제동이 많던 운전자가 진입 전 코칭을 받으며 매주 줄어드는 모습.
 */
object SeedTrips {

    private const val DAY = 24L * 60 * 60 * 1000
    private const val TRIPS_PER_WEEK = 8
    private const val KM_PER_TRIP = 19.5f

    // 주차별(4주 전 → 이번 주) 위험 순간 수
    private val mergeBrakes = intArrayOf(11, 9, 7, 4)
    private val otherBrakes = intArrayOf(3, 3, 2, 2)
    private val noSignal = intArrayOf(12, 10, 8, 6)
    private val speeding = intArrayOf(4, 3, 3, 2)

    private val passesPerTrip = mapOf(
        RoadType.MERGE to 2, RoadType.IC_EXIT to 1, RoadType.IC_ENTER to 1,
        RoadType.RIGHT_TURN to 1, RoadType.LEFT_TURN to 1, RoadType.TUNNEL to 1,
        RoadType.SCHOOL_ZONE to 1,
    )

    fun generate(now: Long): List<TripRecord> {
        val trips = mutableListOf<TripRecord>()
        var momentId = 1000
        for (w in 0 until 4) {
            val weekStart = now - (4 - w) * 7 * DAY + DAY / 2
            val buckets = List(TRIPS_PER_WEEK) { mutableListOf<RiskMoment>() }
            val rainy = BooleanArray(TRIPS_PER_WEEK) { it % 3 == 0 }      // 주 3회 우천
            val night = BooleanArray(TRIPS_PER_WEEK) { it % 4 == 1 }      // 주 2회 야간 (퇴근길)
            val rainyIdx = (0 until TRIPS_PER_WEEK).filter { rainy[it] }

            fun add(trip: Int, kind: MomentKind, type: RoadType, cause: String) {
                buckets[trip] += RiskMoment(
                    id = momentId++, time = if (trip % 2 == 0) "08:4${trip % 10}" else "18:2${trip % 10}",
                    kind = kind, roadType = type, rainy = rainy[trip], night = night[trip],
                    cause = cause, mapX = 0.3f, mapY = 0.6f,
                )
            }
            // 합류 급제동은 비 오는 날에 몰리도록 배치
            repeat(mergeBrakes[w]) { i ->
                val t = if (i < rainyIdx.size * 2) rainyIdx[i % rainyIdx.size] else i % TRIPS_PER_WEEK
                add(t, MomentKind.HARD_BRAKE, RoadType.MERGE, "합류 지점 ${30 + i * 5}m 전에서야 브레이크를 밟았어요")
            }
            repeat(otherBrakes[w]) { i ->
                add((i * 3 + 1) % TRIPS_PER_WEEK, MomentKind.HARD_BRAKE, RoadType.ROUNDABOUT, "회전교차로 진입 직전에 제동했어요")
            }
            repeat(noSignal[w]) { i ->
                add((i * 5 + 2) % TRIPS_PER_WEEK, MomentKind.NO_SIGNAL, when (i % 3) { 0 -> RoadType.IC_EXIT; 1 -> RoadType.RIGHT_TURN; else -> RoadType.LEFT_TURN }, "방향지시등이 꺼져 있었어요")
            }
            repeat(speeding[w]) { i ->
                add((i * 2 + 3) % TRIPS_PER_WEEK, MomentKind.SPEEDING, RoadType.SCHOOL_ZONE, "제한속도 30km/h 단속 구간이었어요")
            }
            for (t in 0 until TRIPS_PER_WEEK) {
                val roundabout = if (t % 2 == 0) mapOf(RoadType.ROUNDABOUT to 1) else emptyMap()
                val sharp = if (t % 3 == 0) mapOf(RoadType.SHARP_TURN to 1) else emptyMap()
                trips += TripRecord(
                    id = (w * 100 + t).toLong(),
                    startedAt = weekStart + (t / 2) * DAY + (if (t % 2 == 0) 0 else DAY / 3),
                    startLabel = if (t % 2 == 0) "08:31" else "18:05",
                    endLabel = if (t % 2 == 0) "09:07" else "18:44",
                    distanceKm = KM_PER_TRIP,
                    rainy = rainy[t], night = night[t],
                    passes = passesPerTrip + roundabout + sharp,
                    moments = buckets[t],
                    isSample = true,
                )
            }
        }
        return trips
    }
}
