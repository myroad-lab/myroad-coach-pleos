package com.myroad.coach.data

import java.util.Locale
import kotlin.math.roundToInt

/** 주행 한 번의 기록 */
data class TripRecord(
    val id: Long,
    /** 주행 시작 시각 (epoch millis) */
    val startedAt: Long,
    val startLabel: String,
    val endLabel: String,
    val distanceKm: Float,
    val rainy: Boolean,
    val night: Boolean,
    /** 도로 유형별 통과 횟수 (위험 비율의 분모) */
    val passes: Map<RoadType, Int>,
    val moments: List<RiskMoment>,
    /** 시연용 샘플 기록이면 true (화면에 '시연용 샘플' 표시) */
    val isSample: Boolean = false,
)

data class HabitReport(
    val habits: List<Habit>,
    val weakRoads: List<WeakRoad>,
    val weakTypes: Set<RoadType>,
    /** 방향지시등 미사용이 가장 많이 나온 도로 유형 (깜빡이 코칭 대상) */
    val signalFocusType: RoadType?,
    val conditions: List<String>,
    val weekly: List<WeeklyPoint>,
    val focusType: RoadType?,
    val focusChangePercent: Int?,
    val signalUseRate: Int?,
    val signalUseDelta: Int?,
    val nextTarget: String?,
    val summary: String,
    val excludedCount: Int,
    val totalKm: Float,
    val tripCount: Int,
    val sampleTripCount: Int,
    val isEnough: Boolean,
    val avgMomentsPerTrip: Float,
)

/**
 * 쌓인 주행 기록으로 습관과 약점 도로 유형을 계산합니다.
 * 기준값은 기획서 '판단 기준'과 같고, 운전자가 '돌발 상황'으로 표시한 기록은 제외합니다.
 */
object HabitAnalyzer {

    const val MIN_KM_FOR_HABIT = 200f
    const val MIN_PASSES_FOR_WEAK = 5
    const val WEAK_RATIO = 1.5f
    const val HARD_BRAKE_PER_100KM = 2f
    const val SIGNAL_USE_MIN = 0.85f
    const val SPEEDING_PER_WEEK = 2f
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    private val SIGNAL_TYPES = setOf(
        RoadType.MERGE, RoadType.IC_EXIT, RoadType.IC_ENTER, RoadType.ROUNDABOUT,
        RoadType.LEFT_TURN, RoadType.RIGHT_TURN, RoadType.U_TURN,
    )

    private fun RiskMoment.counts() = judgement != Judgement.EXCLUDED_BY_DRIVER

    fun analyze(trips: List<TripRecord>, now: Long = trips.maxOfOrNull { it.startedAt } ?: 0L): HabitReport {
        val moments = trips.flatMap { t -> t.moments.filter { it.counts() } }
        val excluded = trips.sumOf { t -> t.moments.count { !it.counts() } }
        val totalKm = trips.sumOf { it.distanceKm.toDouble() }.toFloat()
        val passes = mutableMapOf<RoadType, Int>()
        trips.forEach { t -> t.passes.forEach { (k, v) -> passes[k] = (passes[k] ?: 0) + v } }

        // ── 도로 유형별 위험 비율 (나의 평균 대비) ──
        val riskyPasses = moments.groupingBy { it.roadType }.eachCount()
        val totalPasses = passes.values.sum().coerceAtLeast(1)
        val avgRate = riskyPasses.filterKeys { it in passes }.values.sum().toFloat() / totalPasses
        val weakRoads = passes
            .filter { (type, n) -> n >= MIN_PASSES_FOR_WEAK && type != RoadType.OTHER }
            .map { (type, n) ->
                val rate = (riskyPasses[type] ?: 0).toFloat() / n
                WeakRoad(type, if (avgRate > 0f) round1(rate / avgRate) else 0f)
            }
            .sortedByDescending { it.ratio }
            .take(4)
        val weakTypes = weakRoads.filter { it.ratio >= WEAK_RATIO }.map { it.type }.toSet()

        // ── 조건별 (우천 / 야간 / 맑은 낮) 100km당 위험 순간 ──
        fun per100(ts: List<TripRecord>): Float {
            val km = ts.sumOf { it.distanceKm.toDouble() }.toFloat()
            return if (km <= 0f) 0f else ts.sumOf { t -> t.moments.count { it.counts() } } / km * 100f
        }
        val base = per100(trips.filter { !it.rainy && !it.night }).takeIf { it > 0f } ?: per100(trips)
        val conditions = buildList {
            val rain = per100(trips.filter { it.rainy })
            val night = per100(trips.filter { it.night })
            if (base > 0f) {
                if (rain > 0f) add("비 오는 날 ${round1(rain / base)}×")
                if (night > 0f) add("야간 ${round1(night / base)}×")
                add("맑은 낮 1.0×")
            }
        }

        // ── 습관 ──
        val weeks = ((now - (trips.minOfOrNull { it.startedAt } ?: now)) / WEEK_MS + 1).coerceAtLeast(1)
        val habits = mutableListOf<Pair<Float, Habit>>()
        val brakes = moments.filter { it.kind == MomentKind.HARD_BRAKE || it.kind == MomentKind.LATE_BRAKE }
        if (totalKm > 0f) {
            val per100km = brakes.size / totalKm * 100f
            if (per100km >= HARD_BRAKE_PER_100KM) {
                val where = topPlace(brakes)
                habits += per100km / HARD_BRAKE_PER_100KM to Habit(
                    "${where?.label?.substringBefore('·') ?: "감속 구간"} 직전에 늦게 감속해\n급제동해요",
                    concentration(brakes, where), fmt1(per100km), "회", "100km당 급제동",
                )
            }
        }
        val signalPasses = passes.filterKeys { it in SIGNAL_TYPES }.values.sum()
        val noSignal = moments.filter { it.kind == MomentKind.NO_SIGNAL }
        var signalFocus: RoadType? = null
        if (signalPasses > 0) {
            val miss = noSignal.size.toFloat() / signalPasses
            if (1f - miss < SIGNAL_USE_MIN) {
                signalFocus = topPlace(noSignal)
                val top2 = noSignal.groupingBy { it.roadType }.eachCount().entries.sortedByDescending { it.value }.take(2)
                habits += (miss / (1f - SIGNAL_USE_MIN)) to Habit(
                    "회전·분기할 때 방향지시등을\n자주 놓쳐요",
                    "주로 " + top2.joinToString("과 ") { it.key.label } + "에서 나와요",
                    "${(miss * 100).roundToInt()}", "%", "미사용 비율",
                )
            }
        }
        val speeding = moments.filter { it.kind == MomentKind.SPEEDING }
        val speedPerWeek = speeding.size.toFloat() / weeks
        if (speedPerWeek >= SPEEDING_PER_WEEK) {
            val where = topPlace(speeding)
            habits += speedPerWeek / SPEEDING_PER_WEEK to Habit(
                "${where?.label ?: "단속 구간"}에서\n속도를 늦게 줄여요",
                "단속 카메라 과속 안내 기준", fmt0(speedPerWeek), "회", "주당 과속 안내",
            )
        }
        val topHabits = habits.sortedByDescending { it.first }.map { it.second }.take(3)

        // ── 주간 추이 (최근 4주) ──
        // 집중 약점: 약점 유형 중 위험 순간이 가장 많은 유형
        val focus = weakTypes.maxByOrNull { riskyPasses[it] ?: 0 }
        val weekly = (3 downTo 0).map { w ->
            val from = now - (w + 1) * WEEK_MS
            val to = now - w * WEEK_MS
            val ts = trips.filter { it.startedAt in (from + 1)..to }
            val km = ts.sumOf { it.distanceKm.toDouble() }.toFloat()
            val ms = ts.flatMap { t -> t.moments.filter { it.counts() } }
            val br = ms.filter { it.kind == MomentKind.HARD_BRAKE || it.kind == MomentKind.LATE_BRAKE }
            val label = when (w) { 0 -> "이번 주"; 1 -> "지난주"; else -> "${w}주 전" }
            WeeklyPoint(
                label,
                merge = if (km > 0f) round1(br.count { it.roadType == focus } / km * 100f) else 0f,
                overall = if (km > 0f) round1(br.size / km * 100f) else 0f,
            )
        }
        val thisWeek = weekly.last()
        val lastWeek = weekly[weekly.size - 2]
        val focusChange = if (lastWeek.merge > 0f) ((lastWeek.merge - thisWeek.merge) / lastWeek.merge * 100f).roundToInt() else null

        // ── 방향지시등 사용률 (이번 주 vs 지난주) ──
        fun signalUse(fromW: Int): Int? {
            val from = now - (fromW + 1) * WEEK_MS
            val to = now - fromW * WEEK_MS
            val ts = trips.filter { it.startedAt in (from + 1)..to }
            val sp = ts.sumOf { t -> t.passes.filterKeys { it in SIGNAL_TYPES }.values.sum() }
            if (sp == 0) return null
            val miss = ts.sumOf { t -> t.moments.count { it.counts() && it.kind == MomentKind.NO_SIGNAL } }
            return ((1f - miss.toFloat() / sp) * 100f).roundToInt()
        }
        val useNow = signalUse(0)
        val usePrev = signalUse(1)

        // ── 다음 코칭 목표: 집중 약점이 30% 이상 좋아지면 다음 약점으로 ──
        val improved = (focusChange ?: 0) >= 30
        val next = if (improved) weakRoads.firstOrNull { it.type in weakTypes && it.type != focus } else null
        val nextTarget = next?.let { wr ->
            val ms = moments.filter { it.roadType == wr.type }
            val rainyShare = ms.count { it.rainy }.toFloat() / ms.size.coerceAtLeast(1)
            (if (rainyShare >= 0.5f) "비 오는 날 " else "") + wr.type.label
        }

        val summary = buildString {
            if (focus != null && focusChange != null && focusChange > 0) {
                append("${focus.label.substringBefore('·')} 구간 급제동이 ${focusChange}% 줄었어요.")
            } else if (focus != null) {
                append("${focus.label.substringBefore('·')} 구간에서 위험 순간이 가장 많았어요.")
            } else {
                append("이번 주는 눈에 띄는 약점 구간이 없었어요.")
            }
            if (nextTarget != null) append(" 다음 주에는 ${nextTarget}${objectJosa(nextTarget)} 함께 연습해 볼게요.")
        }

        return HabitReport(
            habits = topHabits,
            weakRoads = weakRoads,
            weakTypes = weakTypes,
            signalFocusType = signalFocus,
            conditions = conditions,
            weekly = weekly,
            focusType = focus,
            focusChangePercent = focusChange,
            signalUseRate = useNow,
            signalUseDelta = if (useNow != null && usePrev != null) useNow - usePrev else null,
            nextTarget = nextTarget,
            summary = summary,
            excludedCount = excluded,
            totalKm = totalKm,
            tripCount = trips.size,
            sampleTripCount = trips.count { it.isSample },
            isEnough = totalKm >= MIN_KM_FOR_HABIT,
            avgMomentsPerTrip = if (trips.isEmpty()) 0f else round1(moments.size.toFloat() / trips.size),
        )
    }

    private fun topPlace(ms: List<RiskMoment>): RoadType? =
        ms.filter { it.roadType != RoadType.OTHER }.groupingBy { it.roadType }.eachCount().maxByOrNull { it.value }?.key

    private fun concentration(ms: List<RiskMoment>, where: RoadType?): String {
        if (where == null || ms.isEmpty()) return "여러 구간에서 고르게 나와요"
        val inPlace = ms.filter { it.roadType == where }
        val rainyShare = inPlace.count { it.rainy }.toFloat() / inPlace.size.coerceAtLeast(1)
        val prefix = if (rainyShare >= 0.6f) "비 오는 날 " else ""
        return "$prefix${where.label} 구간에 집중돼요"
    }

    /** 받침이 있으면 '을', 없으면 '를' */
    fun objectJosa(word: String): String {
        val c = word.lastOrNull() ?: return "를"
        if (c !in '가'..'힣') return "를"
        return if ((c - '가') % 28 != 0) "을" else "를"
    }

    private fun round1(v: Float) = (v * 10f).roundToInt() / 10f
    private fun fmt1(v: Float) = String.format(Locale.US, "%.1f", v)
    private fun fmt0(v: Float) = v.roundToInt().toString()
}
