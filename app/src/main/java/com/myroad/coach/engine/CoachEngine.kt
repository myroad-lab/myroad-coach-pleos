package com.myroad.coach.engine

import com.myroad.coach.data.CoachingCard
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RiskMoment
import com.myroad.coach.data.RoadType
import com.myroad.coach.data.SampleData
import com.myroad.coach.sdk.RoadTypeMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DriveState { PARKED, DRIVING }

enum class SignalSide { OFF, LEFT, RIGHT }

data class LiveSignals(
    val brake: Boolean = false,
    val turnSignal: SignalSide = SignalSide.OFF,
    val steeringAngle: Float = 0f,
    val rainy: Boolean = false,
    val night: Boolean = false,
)

data class NextManeuver(val type: RoadType, val remainMeters: Int, val description: String)

data class CoachUiState(
    val driveState: DriveState = DriveState.PARKED,
    val source: String = "대기 중",
    val signals: LiveSignals = LiveSignals(),
    val next: NextManeuver? = null,
    val coaching: CoachingCard? = null,
    val moments: List<RiskMoment> = SampleData.moments,
    val tripSummary: String = SampleData.tripSummary,
    val tripStart: String = SampleData.tripStart,
    val tripEnd: String = SampleData.tripEnd,
    val coachedCount: Int = 0,
    val assistantAnswer: String? = null,
    val requestHighlightTab: Long = 0L,
)

/**
 * 마이로드 코치의 판단 로직.
 * 실차 신호(Vehicle/NaviHelper SDK)와 시나리오 재생 모두 같은 함수(onTbt, onBrake …)로 들어오므로
 * 에뮬레이터 시연과 실제 동작이 동일한 규칙으로 판단됩니다.
 */
class CoachEngine(
    private val speak: (String) -> Unit,
    private val clock: () -> String = { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date()) },
) {
    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    /** 습관 분석에서 약점으로 판정된 도로 유형 */
    var weakTypes: Set<RoadType> = SampleData.weakTypes

    // 현재 접근 중인 구간(회전 안내 1건)의 상태
    private var activeType: RoadType? = null
    private var lastDistance = Int.MAX_VALUE
    private var coachedThisManeuver = false
    private var signalUsedThisManeuver = false
    private var lateBrakeRecorded = false
    private var steeredThisManeuver = false

    private var tripMoments = mutableListOf<RiskMoment>()
    private var tripStartedAt: String? = null
    private var nextId = 100

    var clockOverride: (() -> String)? = null
    private fun now() = clockOverride?.invoke() ?: clock()

    fun setSource(label: String) = _state.update { it.copy(source = label) }

    // ───────────────────────── 차량 상태 ─────────────────────────

    fun onDriveState(driving: Boolean) {
        val prev = _state.value.driveState
        if (driving && prev == DriveState.PARKED) {
            tripMoments = mutableListOf()
            tripStartedAt = now()
            _state.update { it.copy(driveState = DriveState.DRIVING, coaching = null, coachedCount = 0) }
        } else if (!driving && prev == DriveState.DRIVING) {
            finishTrip()
        }
    }

    fun onBrake(pressed: Boolean) {
        val wasPressed = _state.value.signals.brake
        _state.update { it.copy(signals = it.signals.copy(brake = pressed)) }
        if (!pressed || wasPressed) return
        // 늦은 감속: 감속이 필요한 구간 50m 이내에서 처음 제동
        val type = activeType ?: return
        if (!lateBrakeRecorded && lastDistance in 1..LATE_BRAKE_METERS && type in DECELERATION_TYPES) {
            lateBrakeRecorded = true
            record(
                MomentKind.HARD_BRAKE, type,
                "${type.label.substringBefore('·')} 지점 ${lastDistance}m 전에서야 브레이크를 밟았어요",
            )
        }
    }

    fun onTurnSignal(side: SignalSide) {
        _state.update { it.copy(signals = it.signals.copy(turnSignal = side)) }
        if (side != SignalSide.OFF && lastDistance <= SIGNAL_WINDOW_METERS) signalUsedThisManeuver = true
    }

    fun onSteering(angle: Float) {
        _state.update { it.copy(signals = it.signals.copy(steeringAngle = angle)) }
        if (kotlin.math.abs(angle) >= TURN_STEER_DEGREES && lastDistance <= SIGNAL_WINDOW_METERS) {
            steeredThisManeuver = true
        }
    }

    fun onConditions(rainy: Boolean, night: Boolean) =
        _state.update { it.copy(signals = it.signals.copy(rainy = rainy, night = night)) }

    // ───────────────────────── 내비게이션 ─────────────────────────

    /** NaviHelper onTBTInfo: 다음 회전 안내의 유형과 남은 거리 */
    fun onTbt(type: RoadType, remainMeters: Int, description: String) {
        val newManeuver = type != activeType || remainMeters.toLong() > lastDistance.toLong() + 150
        if (newManeuver) {
            closeManeuver()
            activeType = type
            coachedThisManeuver = false
            signalUsedThisManeuver = _state.value.signals.turnSignal != SignalSide.OFF
            lateBrakeRecorded = false
            steeredThisManeuver = false
        }
        lastDistance = remainMeters
        if (remainMeters <= SIGNAL_WINDOW_METERS && _state.value.signals.turnSignal != SignalSide.OFF) {
            signalUsedThisManeuver = true
        }
        _state.update { it.copy(next = NextManeuver(type, remainMeters, description)) }

        // 진입 전 코칭: 약점 유형 + 기준 거리 이내 + 이번 구간에서 아직 안내 안 함
        if (!coachedThisManeuver && type in weakTypes &&
            remainMeters in 1..RoadTypeMapper.coachingDistance(type)
        ) {
            coachedThisManeuver = true
            val card = coachingFor(type, remainMeters)
            _state.update { it.copy(coaching = card, coachedCount = it.coachedCount + 1) }
            speak("${card.headline}이에요. ${card.action}")
        }

        if (remainMeters <= 0) closeManeuver()
    }

    /** NaviHelper onCautionPointInfo */
    fun onCautionPoint(type: RoadType, remainMeters: Int) {
        if (type == RoadType.OTHER) return
        if (activeType == null || remainMeters < lastDistance) {
            _state.update { it.copy(next = NextManeuver(type, remainMeters, type.label)) }
        }
    }

    /** NaviHelper onCameraAlert */
    fun onCameraAlert(overSpeed: Boolean, speedLimit: Int, inSchoolZone: Boolean) {
        if (!overSpeed) return
        val type = if (inSchoolZone) RoadType.SCHOOL_ZONE else RoadType.OTHER
        record(MomentKind.SPEEDING, type, "제한속도 ${speedLimit}km/h 단속 구간이었어요")
    }

    fun onDestinationArrived() = onDriveState(false)

    private fun closeManeuver() {
        val type = activeType
        if (type != null && RoadTypeMapper.needsSignal(type) && steeredThisManeuver && !signalUsedThisManeuver) {
            record(MomentKind.NO_SIGNAL, type, "${type.label}할 때 방향지시등이 꺼져 있었어요")
            steeredThisManeuver = false
        }
        if (_state.value.coaching?.roadType == type) _state.update { it.copy(coaching = null) }
        activeType = null
        lastDistance = Int.MAX_VALUE
    }

    private fun record(kind: MomentKind, type: RoadType, cause: String) {
        val pos = MAP_SLOTS[tripMoments.size % MAP_SLOTS.size]
        val s = _state.value.signals
        tripMoments += RiskMoment(
            id = nextId++, time = now(), kind = kind, roadType = type,
            rainy = s.rainy, night = s.night, cause = cause, mapX = pos.first, mapY = pos.second,
            judgement = if (type in DECELERATION_TYPES || kind != MomentKind.HARD_BRAKE) Judgement.HABIT_SUSPECTED
            else Judgement.PENDING,
        )
    }

    private fun finishTrip() {
        closeManeuver()
        val end = now()
        _state.update {
            it.copy(
                driveState = DriveState.PARKED,
                coaching = null,
                next = null,
                moments = tripMoments.toList(),
                tripStart = tripStartedAt ?: it.tripStart,
                tripEnd = end,
                requestHighlightTab = System.currentTimeMillis(),
            )
        }
    }

    // ───────────────────────── 정차 중 조작 ─────────────────────────

    /** 하이라이트에서 '앞에 돌발 상황이 있었어요' / '맞아요' */
    fun setJudgement(id: Int, external: Boolean) = _state.update { st ->
        st.copy(moments = st.moments.map {
            if (it.id == id) it.copy(
                judgement = if (external) Judgement.EXCLUDED_BY_DRIVER else Judgement.HABIT_SUSPECTED,
            ) else it
        })
    }

    fun answerWeekly() {
        val answer = "합류 구간 급제동이 40% 줄었어요. 다음 주에는 비 오는 날 회전교차로를 함께 연습해 볼게요."
        _state.update { it.copy(assistantAnswer = answer) }
        speak(answer)
    }

    private fun coachingFor(type: RoadType, remain: Int): CoachingCard {
        val rounded = (remain / 100.0).let { kotlin.math.round(it) * 100 }.toInt().coerceAtLeast(100)
        return when (type) {
            RoadType.MERGE -> CoachingCard(
                type, rounded, "${rounded}m 앞 합류 구간", "지금 미리 속도를 맞춰 보세요",
                "합류 구간에서 급제동이 평소보다 3.1배 많았어요",
            )
            RoadType.IC_EXIT -> CoachingCard(
                type, rounded, "${rounded}m 앞 IC 진출", "방향지시등을 미리 켜 주세요",
                "IC 진출 때 방향지시등을 자주 놓쳤어요",
            )
            RoadType.ROUNDABOUT -> CoachingCard(
                type, rounded, "${rounded}m 앞 회전교차로", "진입 전에 충분히 속도를 줄여 주세요",
                "비 오는 날 회전교차로에서 급제동이 잦았어요",
            )
            else -> CoachingCard(
                type, rounded, "${rounded}m 앞 ${type.label}", "미리 준비해 주세요",
                "이 유형에서 위험 순간이 평소보다 많았어요",
            )
        }
    }

    companion object {
        const val LATE_BRAKE_METERS = 50
        const val SIGNAL_WINDOW_METERS = 100
        const val TURN_STEER_DEGREES = 30f
        val DECELERATION_TYPES = setOf(
            RoadType.MERGE, RoadType.IC_EXIT, RoadType.ROUNDABOUT, RoadType.SHARP_TURN,
            RoadType.SCHOOL_ZONE,
        )
        private val MAP_SLOTS = listOf(0.24f to 0.66f, 0.58f to 0.42f, 0.80f to 0.18f, 0.40f to 0.55f)
    }
}
