package com.myroad.coach.engine

import com.myroad.coach.data.Behavior
import com.myroad.coach.data.Check
import com.myroad.coach.data.CoachingCard
import com.myroad.coach.data.Direction
import com.myroad.coach.data.GeoPoint
import com.myroad.coach.data.HabitAnalyzer
import com.myroad.coach.data.HabitReport
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.Outcome
import com.myroad.coach.data.RiskMoment
import com.myroad.coach.data.RoadType
import com.myroad.coach.data.SampleData
import com.myroad.coach.data.TripRecord
import com.myroad.coach.data.placeLabel
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
    /** 와이퍼로 추정한 우천 */
    val rainy: Boolean = false,
    /** 일몰 후 */
    val night: Boolean = false,
)

data class NextManeuver(val type: RoadType, val remainMeters: Int, val description: String, val direction: Direction = Direction.NONE)

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
    /** 첫 주행이 끝나기 전 하이라이트에 보여 주는 예시 데이터인지 */
    val highlightIsSample: Boolean = true,
    /** 하이라이트 지도에 그릴 최근 주행 경로 */
    val route: List<GeoPoint> = SampleData.route,
    /** 최근 주행의 판정 기회 (정상·위험·판정 불가 건수 표시용) */
    val tripChecks: List<Check> = emptyList(),
    val report: HabitReport = HabitAnalyzer.analyze(emptyList()),
)

/**
 * 마이로드 코치의 판단 로직.
 * 실차 신호(Vehicle/NaviHelper/FusedLocation SDK)와 시나리오 재생 모두 같은 함수(onTbt, onBrake …)로 들어오므로
 * 에뮬레이터 시연과 실제 동작이 동일한 규칙으로 판단됩니다.
 *
 * 판정 원칙
 *  - 구간을 실제로 지나간 경우(남은 거리 30m 이내까지 도달)만 판정 기회로 셉니다. 재탐색·경로 취소는 세지 않습니다.
 *  - 필요한 신호를 한 번도 받지 못했거나 통과 근거(조향)가 없으면 '판정 불가'로 두고 분모에서 뺍니다.
 *  - 방향지시등은 안내 방향과 같은 쪽이 켜졌을 때만 정상입니다.
 *  - 단속 카메라 과속은 카메라 1대당 한 번만 셉니다.
 */
class CoachEngine(
    /** 음성 안내. 오디오 포커스를 받지 못해 말하지 못하면 false (→ 화면 카드만) */
    private val speak: (String) -> Boolean,
    private val clock: () -> String = { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date()) },
    private val onHistoryChanged: (List<TripRecord>) -> Unit = {},
    private val stopSpeaking: () -> Unit = {},
    private val millis: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    /** 습관 분석에서 약점으로 판정된 도로 유형 */
    var weakTypes: Set<RoadType> = SampleData.weakTypes

    // 현재 접근 중인 구간(회전 안내 1건)의 상태
    private var activeType: RoadType? = null
    private var activeDir = Direction.NONE
    private var lastDistance = Int.MAX_VALUE
    private var coachedThisManeuver = false
    private var signalUsedThisManeuver = false
    private var lateBrakeMomentId: Int? = null
    private var brakeSeenThisManeuver = false
    private var steeredThisManeuver = false
    private var steerAngleThisManeuver = 0f

    // 신호 수신 여부 (한 번도 못 받았으면 해당 판정은 '판정 불가')
    private var brakeDataSeen = false
    private var signalDataSeen = false
    private var steeringDataSeen = false

    private var tripMoments = mutableListOf<RiskMoment>()
    private var tripChecks = mutableListOf<Check>()
    private var tripStartedAt: String? = null
    private var tripStartMillis = 0L
    private var tripPasses = mutableMapOf<RoadType, Int>()
    private var tripDistanceKm = 0f
    private var tripRoute = mutableListOf<GeoPoint>()
    private var lastLocation: GeoPoint? = null
    private var lastLocationMillis = 0L
    private var lastLocationConfidence: Float? = null
    private var locationDistanceM = 0.0
    private var tripRainy = false
    private var tripNight = false
    private var wiperOnSince: Long? = null
    private var wiperDataSeen = false
    /** 방금 통과 처리한 구간 (같은 0m 안내가 반복돼도 두 번 세지 않음) */
    private var recentlyClosed: RoadType? = null
    private var lastCautionCounted: RoadType? = null
    /** 이번 주행에서 만난 단속 카메라: id → (도로 유형, 과속 여부, 위험 순간 id) */
    private val cameras = linkedMapOf<String, Triple<RoadType, Boolean, Int?>>()
    private var nextId = 100

    /** 저장된 전체 주행 기록 */
    private var history: List<TripRecord> = emptyList()

    /** 저장소에서 읽은 기록으로 습관 통계와 약점 유형을 계산합니다. */
    fun setHistory(trips: List<TripRecord>) {
        history = trips
        nextId = (trips.flatMap { t -> t.moments.map { it.id } }.maxOrNull() ?: 99) + 1
        recompute(notify = false)
    }

    private fun recompute(notify: Boolean) {
        val report = HabitAnalyzer.analyze(history)
        weakTypes = if (report.isEnough && report.weakTypes.isNotEmpty()) report.weakTypes else SampleData.weakTypes
        _state.update { it.copy(report = report) }
        if (notify) onHistoryChanged(history)
    }

    /** 이번 주행 거리를 직접 지정 (주행거리계 등). 지정하지 않으면 위치 이동 거리를 씁니다. */
    fun onTripDistance(km: Float) {
        tripDistanceKm = km
    }

    /** FusedLocation SDK (또는 시나리오)에서 받은 위치 */
    fun onLocation(lat: Double, lng: Double, timeMillis: Long = millis(), confidence: Float? = null) {
        val p = GeoPoint(lat, lng)
        val prev = lastLocation
        lastLocation = p
        lastLocationMillis = timeMillis
        lastLocationConfidence = confidence
        if (_state.value.driveState != DriveState.DRIVING) return
        if (prev != null) locationDistanceM += distanceMeters(prev, p)
        val lastKept = tripRoute.lastOrNull()
        // 경로는 약 20m 간격으로만 저장 (저장 용량과 지도 그리기 부담을 줄임)
        if (lastKept == null || distanceMeters(lastKept, p) >= ROUTE_STEP_METERS) tripRoute += p
    }

    /** 위험 순간에 붙일 수 있을 만큼 위치가 최신이고 정확한지 */
    private fun reliableLocation(): GeoPoint? {
        val p = lastLocation ?: return null
        if (millis() - lastLocationMillis > LOCATION_STALE_MS) return null
        val c = lastLocationConfidence
        if (c != null && c > 0f && c > LOCATION_MAX_ERROR_M) return null
        return p
    }

    var clockOverride: (() -> String)? = null
    private fun now() = clockOverride?.invoke() ?: clock()

    fun setSource(label: String) = _state.update { it.copy(source = label) }

    // ───────────────────────── 차량 상태 ─────────────────────────

    fun onDriveState(driving: Boolean) {
        val prev = _state.value.driveState
        if (driving && prev == DriveState.PARKED) {
            tripMoments = mutableListOf()
            tripChecks = mutableListOf()
            tripStartedAt = now()
            tripStartMillis = millis()
            tripPasses = mutableMapOf()
            tripDistanceKm = 0f
            tripRoute = mutableListOf()
            locationDistanceM = 0.0
            tripRainy = _state.value.signals.rainy
            tripNight = _state.value.signals.night
            cameras.clear()
            resetManeuver()
            _state.update { it.copy(driveState = DriveState.DRIVING, coaching = null, coachedCount = 0) }
        } else if (!driving && prev == DriveState.DRIVING) {
            finishTrip()
        }
    }

    fun onBrake(pressed: Boolean) {
        brakeDataSeen = true
        val wasPressed = _state.value.signals.brake
        _state.update { it.copy(signals = it.signals.copy(brake = pressed)) }
        if (!pressed || wasPressed) return
        val type = activeType ?: return
        val first = !brakeSeenThisManeuver
        brakeSeenThisManeuver = true
        // 구간 직전 제동: 감속이 필요한 구간에서 '처음' 밟은 제동이 기준 거리(50m) 이내
        if (first && lastDistance in 1..LATE_BRAKE_METERS && type in DECELERATION_TYPES) {
            lateBrakeMomentId = record(
                MomentKind.LATE_BRAKE, type,
                "${type.label.substringBefore('·')} 지점 ${lastDistance}m 전에서야 브레이크를 밟았어요",
                evidence = "이 구간 첫 제동 · ${lastDistance}m 전 (기준 ${LATE_BRAKE_METERS}m 이내)",
                remain = lastDistance,
            )
        }
    }

    fun onTurnSignal(side: SignalSide) {
        signalDataSeen = true
        _state.update { it.copy(signals = it.signals.copy(turnSignal = side)) }
        if (lastDistance <= SIGNAL_WINDOW_METERS && matches(side, activeDir)) signalUsedThisManeuver = true
    }

    fun onSteering(angle: Float) {
        steeringDataSeen = true
        _state.update { it.copy(signals = it.signals.copy(steeringAngle = angle)) }
        if (kotlin.math.abs(angle) >= TURN_STEER_DEGREES && lastDistance <= SIGNAL_WINDOW_METERS) {
            steeredThisManeuver = true
            steerAngleThisManeuver = maxOf(steerAngleThisManeuver, kotlin.math.abs(angle))
        }
    }

    /** 직접 지정 (시나리오·테스트). 실차에서는 [onWiper]와 [onNight]를 씁니다. */
    fun onConditions(rainy: Boolean, night: Boolean) {
        if (rainy) tripRainy = true
        if (night) tripNight = true
        _state.update { it.copy(signals = it.signals.copy(rainy = rainy, night = night)) }
    }

    /** 앞유리 와이퍼 상태. 일정 시간 이상 계속 켜져 있으면 '우천 추정' */
    fun onWiper(on: Boolean) {
        wiperDataSeen = true
        val t = millis()
        if (!on) {
            wiperOnSince = null
        } else if (wiperOnSince == null) {
            wiperOnSince = t
        }
        updateRainEstimate()
    }

    private fun updateRainEstimate() {
        if (!wiperDataSeen) return
        val since = wiperOnSince
        val rainy = since != null && millis() - since >= WIPER_RAIN_MS
        if (rainy) tripRainy = true
        if (rainy != _state.value.signals.rainy) _state.update { it.copy(signals = it.signals.copy(rainy = rainy)) }
    }

    /** 위치·시각으로 계산한 일몰 여부 */
    fun onNight(night: Boolean) {
        if (night && _state.value.driveState == DriveState.DRIVING) tripNight = true
        if (night != _state.value.signals.night) _state.update { it.copy(signals = it.signals.copy(night = night)) }
    }

    // ───────────────────────── 내비게이션 ─────────────────────────

    /** NaviHelper onTBTInfo: 다음 회전 안내의 유형·방향과 남은 거리 */
    fun onTbt(type: RoadType, remainMeters: Int, description: String, direction: Direction = Direction.NONE) {
        updateRainEstimate()
        if (activeType == null && type == recentlyClosed && remainMeters <= PASS_METERS) return
        val newManeuver = type != activeType || remainMeters.toLong() > lastDistance.toLong() + 150
        if (newManeuver) {
            closeManeuver()
            recentlyClosed = null
            activeType = type
            activeDir = direction
        }
        lastDistance = remainMeters
        if (remainMeters <= SIGNAL_WINDOW_METERS && matches(_state.value.signals.turnSignal, activeDir)) {
            signalUsedThisManeuver = true
        }
        _state.update { it.copy(next = NextManeuver(type, remainMeters, description, direction)) }

        // 진입 전 코칭: 약점 유형 + 기준 거리 이내 + 너무 가깝지 않음 + 이번 구간에서 아직 안내 안 함
        val start = RoadTypeMapper.coachingDistance(type)
        if (!coachedThisManeuver && type in weakTypes && remainMeters in (start / 4)..start) {
            coachedThisManeuver = true
            val card = coachingFor(type, direction, remainMeters)
            val voiced = speak("${card.headline}이에요. ${card.action}")
            _state.update { it.copy(coaching = card.copy(voiced = voiced), coachedCount = it.coachedCount + 1) }
        }

        if (remainMeters <= 0) closeManeuver()
    }

    /** 경로 재탐색 완료 / 경로 취소: 지나가지 않은 구간은 판정하지 않고, 남은 카드와 음성을 거둡니다. */
    fun onRouteChanged() {
        recentlyClosed = null
        if (_state.value.coaching != null) stopSpeaking()
        resetManeuver()
        _state.update { it.copy(coaching = null, next = null) }
    }

    /** NaviHelper onCautionPointInfo (표시·통과 집계만, 습관 판정에는 쓰지 않음) */
    fun onCautionPoint(type: RoadType, remainMeters: Int) {
        if (type == RoadType.OTHER) return
        if (remainMeters <= PASS_METERS && lastCautionCounted != type) {
            lastCautionCounted = type
            tripPasses[type] = (tripPasses[type] ?: 0) + 1
        } else if (remainMeters > 100 && lastCautionCounted == type) {
            lastCautionCounted = null
        }
        if (activeType == null || remainMeters < lastDistance) {
            _state.update { it.copy(next = NextManeuver(type, remainMeters, type.label)) }
        }
    }

    /**
     * NaviHelper onCameraAlert. 카메라 id마다 한 번만 판정합니다.
     * 안내를 받은 카메라는 모두 분모(통과한 단속 구간), 과속 안내가 한 번이라도 나오면 위험.
     */
    fun onCameraAlert(id: String, overSpeed: Boolean, speedLimit: Int, inSchoolZone: Boolean, remainMeters: Int = 0) {
        val type = if (inSchoolZone) RoadType.SCHOOL_ZONE else RoadType.OTHER
        val prev = cameras[id]
        if (prev == null) cameras[id] = Triple(type, false, null)
        if (overSpeed && cameras[id]?.second != true) {
            val mid = record(
                MomentKind.SPEEDING, type, "제한속도 ${speedLimit}km/h 단속 구간이었어요",
                evidence = "내비 과속 안내 · 카메라 ${remainMeters}m 전 (카메라 1대당 1회)",
            )
            cameras[id] = Triple(type, true, mid)
        }
    }

    fun onDestinationArrived() = onDriveState(false)

    private fun matches(side: SignalSide, dir: Direction) = when (dir) {
        Direction.NONE -> side != SignalSide.OFF
        Direction.LEFT -> side == SignalSide.LEFT
        Direction.RIGHT -> side == SignalSide.RIGHT
    }

    private fun resetManeuver() {
        activeType = null
        activeDir = Direction.NONE
        lastDistance = Int.MAX_VALUE
        coachedThisManeuver = false
        signalUsedThisManeuver = false
        lateBrakeMomentId = null
        brakeSeenThisManeuver = false
        steeredThisManeuver = false
        steerAngleThisManeuver = 0f
    }

    private fun closeManeuver() {
        val type = activeType
        if (type != null && lastDistance <= PASS_METERS) {
            recentlyClosed = type
            tripPasses[type] = (tripPasses[type] ?: 0) + 1
            // 구간 직전 제동
            if (type in DECELERATION_TYPES) {
                val outcome = when {
                    lateBrakeMomentId != null -> Outcome.RISK
                    brakeDataSeen -> Outcome.OK
                    else -> Outcome.UNDETERMINED
                }
                tripChecks += Check(Behavior.PRE_ENTRY_BRAKE, type, Direction.NONE, outcome, lateBrakeMomentId)
            }
            // 방향지시등: 신호를 받고 있었고, 조향으로 실제 회전·분기한 근거가 있을 때만 판정
            if (RoadTypeMapper.needsSignal(type)) {
                val outcome = when {
                    !signalDataSeen || !steeringDataSeen || !steeredThisManeuver -> Outcome.UNDETERMINED
                    signalUsedThisManeuver -> Outcome.OK
                    else -> Outcome.RISK
                }
                val id = if (outcome == Outcome.RISK) {
                    val place = placeLabel(type, activeDir)
                    record(
                        MomentKind.NO_SIGNAL, type, "$place 중 방향지시등이 꺼져 있었어요",
                        evidence = "조향 ${steerAngleThisManeuver.toInt()}° (${SIGNAL_WINDOW_METERS}m 이내) · " +
                            (if (activeDir == Direction.NONE) "방향지시등 OFF" else "${activeDir.label} 방향지시등 OFF"),
                        direction = activeDir,
                    )
                } else null
                tripChecks += Check(Behavior.SIGNAL, type, activeDir, outcome, id)
            }
        }
        if (_state.value.coaching?.roadType == type) _state.update { it.copy(coaching = null) }
        resetManeuver()
    }

    private fun record(
        kind: MomentKind,
        type: RoadType,
        cause: String,
        evidence: String,
        remain: Int? = null,
        direction: Direction = Direction.NONE,
    ): Int {
        val pos = MAP_SLOTS[tripMoments.size % MAP_SLOTS.size]
        val s = _state.value.signals
        val here = reliableLocation()
        val id = nextId++
        tripMoments += RiskMoment(
            id = id, time = now(), kind = kind, roadType = type,
            rainy = s.rainy, night = s.night, cause = cause, mapX = pos.first, mapY = pos.second,
            lat = here?.lat, lng = here?.lng,
            judgement = if (type == RoadType.OTHER) Judgement.PENDING else Judgement.HABIT_SUSPECTED,
            remainMeters = remain, direction = direction, evidence = evidence,
        )
        return id
    }

    private fun finishTrip() {
        closeManeuver()
        cameras.values.forEach { (type, over, mid) ->
            tripChecks += Check(Behavior.ZONE_SPEED, type, Direction.NONE, if (over) Outcome.RISK else Outcome.OK, mid)
        }
        cameras.clear()
        val end = now()
        lastLocation?.let { if (tripRoute.lastOrNull() != it) tripRoute += it }
        val km = if (tripDistanceKm > 0f) tripDistanceKm else (locationDistanceM / 1000.0).toFloat()
        tripDistanceKm = km
        val record = TripRecord(
            id = millis(),
            startedAt = tripStartMillis,
            startLabel = tripStartedAt ?: end,
            endLabel = end,
            distanceKm = tripDistanceKm,
            rainy = tripRainy,
            night = tripNight,
            passes = tripPasses.toMap(),
            moments = tripMoments.toList(),
            route = simplify(tripRoute, MAX_ROUTE_POINTS),
            checks = tripChecks.toList(),
        )
        history = history + record
        _state.update {
            it.copy(
                driveState = DriveState.PARKED,
                coaching = null,
                next = null,
                moments = record.moments,
                highlightIsSample = false,
                route = record.route,
                tripChecks = record.checks,
                tripSummary = if (tripDistanceKm > 0f) "최근 주행 · ${"%.1f".format(Locale.US, tripDistanceKm)}km" else "최근 주행",
                tripStart = record.startLabel,
                tripEnd = end,
                requestHighlightTab = millis(),
            )
        }
        recompute(notify = true)
    }

    // ───────────────────────── 정차 중 조작 ─────────────────────────

    /** 하이라이트에서 '앞에 돌발 상황이 있었어요' / '맞아요' */
    fun setJudgement(id: Int, external: Boolean) {
        val j = if (external) Judgement.EXCLUDED_BY_DRIVER else Judgement.HABIT_SUSPECTED
        _state.update { st -> st.copy(moments = st.moments.map { if (it.id == id) it.copy(judgement = j) else it }) }
        if (history.any { t -> t.moments.any { it.id == id } }) {
            history = history.map { t -> t.copy(moments = t.moments.map { if (it.id == id) it.copy(judgement = j) else it }) }
            recompute(notify = true)
        }
    }

    /** '이번 주 요약 듣기': 요약 문장을 화면에 띄우고 음성으로 읽어 줍니다 (음성 인식은 하지 않음). */
    fun answerWeekly() {
        val answer = _state.value.report.summary
        _state.update { it.copy(assistantAnswer = answer) }
        speak(answer)
    }

    private fun coachingFor(type: RoadType, dir: Direction, remain: Int): CoachingCard {
        val rounded = (remain / 100.0).let { kotlin.math.round(it) * 100 }.toInt().coerceAtLeast(100)
        val report = _state.value.report
        // 이 구간에서 평균 대비 가장 두드러진 행동에 맞춰 안내 문장을 고릅니다.
        val cell = report.weakRoads.filter { it.type == type }.maxByOrNull { it.ratio }
        val place = placeLabel(type, dir)
        val count = cell?.let { " (최근 ${it.risk}/${it.judged}회)" } ?: ""
        return when {
            cell?.behavior == Behavior.SIGNAL -> CoachingCard(
                type, rounded, "${rounded}m 앞 $place",
                if (dir == Direction.NONE) "방향지시등을 미리 켜 주세요" else "${dir.label} 방향지시등을 미리 켜 주세요",
                "$place 때 방향지시등을 자주 놓쳤어요$count",
            )
            type == RoadType.MERGE -> CoachingCard(
                type, rounded, "${rounded}m 앞 합류 구간", "지금 미리 속도를 맞춰 보세요",
                "합류 직전에서야 브레이크를 밟은 적이 많았어요$count",
            )
            type == RoadType.ROUNDABOUT -> CoachingCard(
                type, rounded, "${rounded}m 앞 회전교차로", "진입 전에 충분히 속도를 줄여 주세요",
                "회전교차로 직전에서야 브레이크를 밟은 적이 많았어요$count",
            )
            type == RoadType.IC_EXIT -> CoachingCard(
                type, rounded, "${rounded}m 앞 $place", "미리 차로를 옮기고 속도를 줄여 주세요",
                "IC 진출 직전 제동이 많았어요$count",
            )
            else -> CoachingCard(
                type, rounded, "${rounded}m 앞 $place", "미리 준비해 주세요",
                "이 구간에서 위험 판정이 많았어요$count",
            )
        }
    }

    companion object {
        const val LATE_BRAKE_METERS = 50
        const val PASS_METERS = 30
        const val ROUTE_STEP_METERS = 20.0
        const val MAX_ROUTE_POINTS = 400
        /** 이보다 오래된 위치는 위험 순간에 붙이지 않음 ('위치 확인 불가') */
        const val LOCATION_STALE_MS = 5_000L
        /** 위치 정확도(m)가 이보다 크면 붙이지 않음 (초기값) */
        const val LOCATION_MAX_ERROR_M = 50f
        /** 와이퍼가 이 시간 이상 계속 켜져 있으면 우천 추정 (초기값) */
        const val WIPER_RAIN_MS = 30_000L

        /** 두 좌표 사이 거리 (m, 하버사인) */
        fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLng = Math.toRadians(b.lng - a.lng)
            val h = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(a.lat)) * kotlin.math.cos(Math.toRadians(b.lat)) *
                kotlin.math.sin(dLng / 2).let { it * it }
            return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h))
        }

        /** 점이 너무 많으면 일정 간격으로 솎아 냄 (처음과 끝은 유지) */
        fun simplify(points: List<GeoPoint>, max: Int): List<GeoPoint> {
            if (points.size <= max) return points.toList()
            val step = points.size.toDouble() / (max - 1)
            return (0 until max - 1).map { points[(it * step).toInt()] } + points.last()
        }
        const val SIGNAL_WINDOW_METERS = 100
        const val TURN_STEER_DEGREES = 30f
        /** 진입 전에 미리 감속해야 하는 회전 안내 유형 (구간 직전 제동 판정 대상) */
        val DECELERATION_TYPES = setOf(
            RoadType.MERGE, RoadType.IC_EXIT, RoadType.ROUNDABOUT, RoadType.SHARP_TURN,
        )
        private val MAP_SLOTS = listOf(0.24f to 0.66f, 0.58f to 0.42f, 0.80f to 0.18f, 0.40f to 0.55f)
    }
}
