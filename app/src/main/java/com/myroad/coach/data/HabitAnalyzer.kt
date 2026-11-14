package com.myroad.coach.data

import kotlin.math.roundToInt

/** 주행 한 번의 기록 */
data class TripRecord(
    val id: Long,
    /** 주행 시작 시각 (epoch millis) */
    val startedAt: Long,
    val startLabel: String,
    val endLabel: String,
    val distanceKm: Float,
    /** 와이퍼 작동으로 추정한 우천 */
    val rainy: Boolean,
    /** 위치·시각으로 계산한 일몰 후 주행 */
    val night: Boolean,
    /** 도로 유형별 통과 횟수 (참고용) */
    val passes: Map<RoadType, Int>,
    val moments: List<RiskMoment>,
    /** 시연용 샘플 기록이면 true (화면에 '시연용 샘플' 표시) */
    val isSample: Boolean = false,
    /** 주행 경로 (위치 정보가 있을 때, 최대 수백 개로 줄여 저장) */
    val route: List<GeoPoint> = emptyList(),
    /** 판정 기회 목록 (정상·위험·판정 불가). 습관 비율의 분모 */
    val checks: List<Check> = emptyList(),
)

data class HabitReport(
    /** 행동별 지표 (습관 / 기준 이하 / 데이터 부족) — 습관인 것부터 */
    val habits: List<Habit>,
    /** 약점 프로필 (행동 × 도로 유형), 비율 높은 순 */
    val weakRoads: List<WeakRoad>,
    val weakTypes: Set<RoadType>,
    val conditions: List<String>,
    val weekly: List<WeeklyPoint>,
    /** 집중 코칭 대상 칸 */
    val focus: WeakRoad?,
    val focusChangePercent: Int?,
    /** "지난주 3/8회 → 이번 주 1/8회" (표본 부족이면 null) */
    val focusCompareText: String?,
    val signalUseRate: Int?,
    val signalUseDelta: Int?,
    val signalUseCountText: String?,
    val nextTarget: String?,
    val summary: String,
    val excludedCount: Int,
    val undeterminedCount: Int,
    val totalKm: Float,
    val tripCount: Int,
    val sampleTripCount: Int,
    val isEnough: Boolean,
    val avgMomentsPerTrip: Float,
) {
    val focusType: RoadType? get() = focus?.type
}

/**
 * 쌓인 주행 기록으로 습관과 약점 구간을 계산합니다.
 *  - 모든 지표는 '위험 판정 수 / 판정한 기회 수'로 계산하고 건수를 함께 보여 줍니다.
 *  - 판정 불가(신호를 못 받음, 통과 근거 없음)와 운전자가 '돌발 상황'으로 제외한 기록은 분모·분자 모두에서 뺍니다.
 *  - 표본이 기준보다 적으면 값을 만들지 않고 '데이터 부족'으로 둡니다. 0으로 채우지 않습니다.
 *  - 기준값(최소 표본, 1.5배 등)은 초기값이며 실주행 데이터로 조정할 예정입니다.
 */
object HabitAnalyzer {

    const val MIN_KM_FOR_HABIT = 200f
    /** 행동별 지표를 내기 위한 최소 판정 수 */
    const val MIN_SAMPLES = 10
    /** 약점 프로필 한 칸(행동 × 도로 유형)의 최소 판정 수 (초기값) */
    const val MIN_PASSES_FOR_WEAK = 5
    /** 같은 행동의 평균 대비 이 배수 이상이면 약점 (초기값) */
    const val WEAK_RATIO = 1.5f
    /** 주간 비교에 필요한 주당 최소 판정 수 */
    const val MIN_WEEKLY_SAMPLES = 5
    /** 조건별 비교에 필요한 조건당 최소 판정 수 */
    const val MIN_CONDITION_SAMPLES = 20

    /** 행동별 '습관'으로 보는 위험 비율 기준 (초기값) */
    val HABIT_RATE = mapOf(
        Behavior.PRE_ENTRY_BRAKE to 0.15f,
        Behavior.SIGNAL to 0.15f,
        Behavior.ZONE_SPEED to 0.15f,
    )
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    private class Judged(val check: Check, val trip: TripRecord)

    private data class Rate(val risk: Int, val judged: Int) {
        val value get() = if (judged == 0) 0f else risk.toFloat() / judged
        val text get() = "$risk/${judged}회"
    }

    private fun List<Judged>.rate() = Rate(count { it.check.outcome == Outcome.RISK }, size)

    fun analyze(trips: List<TripRecord>, now: Long = trips.maxOfOrNull { it.startedAt } ?: 0L): HabitReport {
        val excludedIds = trips.flatMap { t -> t.moments.filter { it.judgement == Judgement.EXCLUDED_BY_DRIVER }.map { it.id } }.toSet()
        val all = trips.flatMap { t -> t.checks.map { Judged(it, t) } }
            .filter { it.check.momentId == null || it.check.momentId !in excludedIds }
        val judged = all.filter { it.check.outcome != Outcome.UNDETERMINED }
        val undetermined = all.size - judged.size
        val totalKm = trips.sumOf { it.distanceKm.toDouble() }.toFloat()
        val counted = trips.flatMap { t -> t.moments.filter { it.judgement != Judgement.EXCLUDED_BY_DRIVER } }

        // ── 약점 프로필: 행동 × 도로 유형, 같은 행동의 평균 대비 ──
        val byBehavior = judged.groupBy { it.check.behavior }
        val weakRoads = byBehavior.flatMap { (behavior, js) ->
            val avg = js.rate().value
            js.groupBy { it.check.roadType }
                .filter { (type, cell) -> type != RoadType.OTHER && cell.size >= MIN_PASSES_FOR_WEAK }
                .map { (type, cell) ->
                    val r = cell.rate()
                    WeakRoad(
                        type = type,
                        ratio = if (avg > 0f) round1(r.value / avg) else 0f,
                        behavior = behavior,
                        risk = r.risk,
                        judged = r.judged,
                        direction = if (behavior == Behavior.SIGNAL) dominantDirection(cell) else Direction.NONE,
                    )
                }
        }.filter { it.risk > 0 }.sortedByDescending { it.ratio }
        val weakCells = weakRoads.filter { it.ratio >= WEAK_RATIO && it.risk >= 2 }
        val weakTypes = weakCells.map { it.type }.toSet()

        // ── 행동별 지표 ──
        val habits = Behavior.entries.map { b ->
            val js = byBehavior[b].orEmpty()
            val r = js.rate()
            val threshold = HABIT_RATE.getValue(b)
            val status = when {
                r.judged < MIN_SAMPLES -> HabitStatus.INSUFFICIENT
                r.value >= threshold -> HabitStatus.HABIT
                else -> HabitStatus.OK
            }
            val top = weakRoads.firstOrNull { it.behavior == b }
            val rainyShare = js.filter { it.check.outcome == Outcome.RISK }.let { rs ->
                if (rs.isEmpty()) 0f else rs.count { it.trip.rainy }.toFloat() / rs.size
            }
            Habit(
                behavior = b,
                title = habitTitle(b, top, status),
                where = when (status) {
                    HabitStatus.INSUFFICIENT -> "판정 ${r.judged}회 · ${MIN_SAMPLES}회 이상 모이면 알려 드릴게요"
                    else -> (if (rainyShare >= 0.6f) "비 오는 날(추정) · " else "") +
                        (top?.let { "주로 ${it.place} (${it.risk}/${it.judged}회)" } ?: "특정 구간에 몰리지 않았어요")
                },
                value = if (status == HabitStatus.INSUFFICIENT) "—" else "${(r.value * 100).roundToInt()}",
                unit = if (status == HabitStatus.INSUFFICIENT) "" else "%",
                metricLabel = if (status == HabitStatus.INSUFFICIENT) "데이터 부족" else "${b.unitLabel} 중",
                countText = r.text,
                status = status,
            ) to (if (status == HabitStatus.HABIT) r.value / threshold else -1f)
        }.sortedByDescending { it.second }.map { it.first }

        // ── 조건별 (우천 추정 / 야간 / 맑은 낮) 위험 비율, 표본이 충분할 때만 ──
        val clear = judged.filter { !it.trip.rainy && !it.trip.night }.rate()
        val conditions = buildList {
            if (clear.judged >= MIN_CONDITION_SAMPLES && clear.value > 0f) {
                val rain = judged.filter { it.trip.rainy }.rate()
                val night = judged.filter { it.trip.night }.rate()
                if (rain.judged >= MIN_CONDITION_SAMPLES) add("우천 추정 ${round1(rain.value / clear.value)}×")
                if (night.judged >= MIN_CONDITION_SAMPLES) add("야간 ${round1(night.value / clear.value)}×")
                if (isNotEmpty()) add("맑은 낮 1.0×")
            }
        }

        // ── 집중 약점: 약점 칸 중 위험 판정이 가장 많은 칸 ──
        val focus = weakCells.maxWithOrNull(compareBy<WeakRoad> { it.risk }.thenBy { it.ratio })
        fun inWeek(w: Int, j: Judged): Boolean {
            val from = now - (w + 1) * WEEK_MS
            val to = now - w * WEEK_MS
            return j.trip.startedAt in (from + 1)..to
        }
        fun weekRate(w: Int, filter: (Check) -> Boolean): Rate? =
            judged.filter { inWeek(w, it) && filter(it.check) }.rate().takeIf { it.judged >= MIN_WEEKLY_SAMPLES }

        val focusFilter: (Check) -> Boolean = { c -> focus != null && c.behavior == focus.behavior && c.roadType == focus.type }
        val behaviorFilter: (Check) -> Boolean = { c -> c.behavior == (focus?.behavior ?: Behavior.PRE_ENTRY_BRAKE) }
        val weekly = (3 downTo 0).map { w ->
            val f = weekRate(w, focusFilter)
            val o = weekRate(w, behaviorFilter)
            WeeklyPoint(
                label = when (w) { 0 -> "이번 주"; 1 -> "지난주"; else -> "${w}주 전" },
                merge = f?.let { pct(it.value) },
                overall = o?.let { pct(it.value) },
                countText = f?.text,
            )
        }
        val thisWeek = weekRate(0, focusFilter)
        val lastWeek = weekRate(1, focusFilter)
        val focusChange = if (thisWeek != null && lastWeek != null && lastWeek.value > 0f) {
            ((lastWeek.value - thisWeek.value) / lastWeek.value * 100f).roundToInt()
        } else null
        val focusCompare = if (thisWeek != null && lastWeek != null) "지난주 ${lastWeek.text} → 이번 주 ${thisWeek.text}" else null

        // ── 방향지시등 사용률 = 정상 / 판정한 회전·분기 ──
        val isSignal: (Check) -> Boolean = { it.behavior == Behavior.SIGNAL }
        val signalNow = weekRate(0, isSignal)
        val signalPrev = weekRate(1, isSignal)
        val useNow = signalNow?.let { ((1f - it.value) * 100f).roundToInt() }
        val usePrev = signalPrev?.let { ((1f - it.value) * 100f).roundToInt() }

        // ── 다음 코칭 목표: 집중 약점이 30% 이상 좋아지면 다음 약점 칸으로 ──
        val improved = (focusChange ?: 0) >= 30
        val next = if (improved) weakCells.filter { it != focus }.maxByOrNull { it.ratio } else null
        val nextTarget = next?.let { "${it.place} ${practice(it.behavior)}" }

        val summary = buildString {
            val name = focus?.let { "${it.place} ${behaviorShort(it.behavior)}" }
            when {
                focus == null -> append("아직 뚜렷한 약점 구간이 없거나, 판정한 기록이 부족해요.")
                focusCompare != null && focusChange != null && focusChange > 0 ->
                    append("$name${subjectJosa(name!!)} ${focusCompare.replace(" → ", "에서 ")}로 줄었어요.")
                focusCompare != null -> append("$name${subjectJosa(name!!)} ${focusCompare.replace(" → ", ", ")}이었어요.")
                else -> append("$name${subjectJosa(name!!)} 가장 잦았어요. 주간 비교는 판정 기록이 더 모이면 알려 드릴게요.")
            }
            if (nextTarget != null) append(" 다음 주에는 ${nextTarget}${objectJosa(nextTarget)} 함께 연습해 볼게요.")
        }

        return HabitReport(
            habits = habits,
            weakRoads = weakRoads.take(4),
            weakTypes = weakTypes,
            conditions = conditions,
            weekly = weekly,
            focus = focus,
            focusChangePercent = focusChange,
            focusCompareText = focusCompare,
            signalUseRate = useNow,
            signalUseDelta = if (useNow != null && usePrev != null) useNow - usePrev else null,
            signalUseCountText = signalNow?.let { "${it.judged - it.risk}/${it.judged}회" },
            nextTarget = nextTarget,
            summary = summary,
            excludedCount = excludedIds.size,
            undeterminedCount = undetermined,
            totalKm = totalKm,
            tripCount = trips.size,
            sampleTripCount = trips.count { it.isSample },
            isEnough = totalKm >= MIN_KM_FOR_HABIT,
            avgMomentsPerTrip = if (trips.isEmpty()) 0f else round1(counted.size.toFloat() / trips.size),
        )
    }

    fun behaviorShort(b: Behavior) = when (b) {
        Behavior.PRE_ENTRY_BRAKE -> "직전 제동"
        Behavior.SIGNAL -> "방향지시등 미사용"
        Behavior.ZONE_SPEED -> "과속"
    }

    /** 연습 목표로 말할 때의 표현 */
    fun practice(b: Behavior) = when (b) {
        Behavior.PRE_ENTRY_BRAKE -> "미리 감속하기"
        Behavior.SIGNAL -> "방향지시등 켜기"
        Behavior.ZONE_SPEED -> "속도 미리 줄이기"
    }

    private fun habitTitle(b: Behavior, top: WeakRoad?, status: HabitStatus): String = when (b) {
        Behavior.PRE_ENTRY_BRAKE ->
            if (status == HabitStatus.HABIT) "${top?.type?.label?.substringBefore('·') ?: "감속 구간"} 직전에서야\n브레이크를 밟아요"
            else "감속 구간 직전 제동"
        Behavior.SIGNAL ->
            if (status == HabitStatus.HABIT) "회전·분기할 때 방향지시등을\n자주 놓쳐요" else "방향지시등 사용"
        Behavior.ZONE_SPEED ->
            if (status == HabitStatus.HABIT) "${top?.type?.label ?: "단속 구간"}에서\n속도를 늦게 줄여요" else "단속 구간 속도"
    }

    private fun dominantDirection(cell: List<Judged>): Direction {
        val risks = cell.filter { it.check.outcome == Outcome.RISK }.map { it.check.direction }
        if (risks.isEmpty()) return Direction.NONE
        val top = risks.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return Direction.NONE
        return if (top.value.toFloat() / risks.size >= 0.6f) top.key else Direction.NONE
    }

    /** 받침이 있으면 '을', 없으면 '를' */
    fun objectJosa(word: String): String = if (hasBatchim(word)) "을" else "를"

    /** 받침이 있으면 '이', 없으면 '가' */
    fun subjectJosa(word: String): String = if (hasBatchim(word)) "이" else "가"

    private fun hasBatchim(word: String): Boolean {
        val c = word.lastOrNull() ?: return false
        if (c !in '가'..'힣') return false
        return (c - '가') % 28 != 0
    }

    private fun round1(v: Float) = (v * 10f).roundToInt() / 10f
    private fun pct(v: Float) = (v * 100f).roundToInt().toFloat()
}
