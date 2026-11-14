package com.myroad.coach.data

/** 앱이 다루는 도로 유형. NaviHelper의 TurnType / CautionPointType을 이 유형으로 묶어서 씁니다. */
enum class RoadType(val label: String) {
    MERGE("합류·분기"),
    IC_EXIT("IC 진출"),
    IC_ENTER("IC 진입"),
    ROUNDABOUT("회전교차로"),
    LEFT_TURN("좌회전"),
    RIGHT_TURN("우회전"),
    U_TURN("유턴"),
    SHARP_TURN("급회전"),
    TUNNEL("터널"),
    TOLL("톨게이트"),
    SCHOOL_ZONE("어린이보호구역"),
    ACCIDENT_PRONE("사고다발지역"),
    SLIPPERY("미끄러운 도로"),
    DOWNHILL("내리막"),
    OTHER("기타"),
}

/** 위도·경도 */
data class GeoPoint(val lat: Double, val lng: Double)

/** 위험 순간의 종류 */
enum class MomentKind(val label: String) {
    /** 실제 감속도 기반 급제동 (차량 속도 API가 생기면 사용, 지금은 기록하지 않음) */
    HARD_BRAKE("급제동"),
    /** 감속이 필요한 구간 직전(기준 거리 이내)에서야 처음 제동 */
    LATE_BRAKE("구간 직전 제동"),
    NO_SIGNAL("방향지시등 미사용"),
    SPEEDING("구간 과속"),
    SHARP_STEER("급조향"),
}

/** 판정하는 행동. 각 행동은 '판정한 기회(분모)' 대비 '위험 판정(분자)'로 계산합니다. */
enum class Behavior(val label: String, val unitLabel: String) {
    PRE_ENTRY_BRAKE("구간 직전 제동", "판정한 감속 구간"),
    SIGNAL("방향지시등 미사용", "판정한 회전·분기"),
    ZONE_SPEED("구간 과속", "통과한 단속 구간"),
}

/** 판정 결과: 정상 / 위험 / 판정 불가(신호 없음·통과 근거 없음 → 분모에서 제외) */
enum class Outcome(val label: String) { OK("정상"), RISK("위험"), UNDETERMINED("판정 불가") }

/** 회전·분기 방향 (TurnType의 _L/_R 등에서 얻음) */
enum class Direction(val label: String) { NONE(""), LEFT("좌측"), RIGHT("우측") }

/** 판정 기회 1건 (습관 비율의 분모). 위험이면 해당 위험 순간 id를 연결합니다. */
data class Check(
    val behavior: Behavior,
    val roadType: RoadType,
    val direction: Direction = Direction.NONE,
    val outcome: Outcome,
    val momentId: Int? = null,
)

fun MomentKind.behavior(): Behavior? = when (this) {
    MomentKind.LATE_BRAKE -> Behavior.PRE_ENTRY_BRAKE
    MomentKind.NO_SIGNAL -> Behavior.SIGNAL
    MomentKind.SPEEDING -> Behavior.ZONE_SPEED
    else -> null
}

/** '우측 IC 진출'처럼 방향을 붙인 구간 이름 */
fun placeLabel(type: RoadType, dir: Direction) =
    if (dir == Direction.NONE || type in setOf(RoadType.LEFT_TURN, RoadType.RIGHT_TURN, RoadType.U_TURN)) type.label
    else "${dir.label} ${type.label}"

/** 급제동 분류 (외부 상황 구분) */
enum class Judgement(val label: String) {
    HABIT_SUSPECTED("습관 의심"),
    EXTERNAL_POSSIBLE("외부 상황 가능성"),
    PENDING("판단 보류"),
    EXCLUDED_BY_DRIVER("운전자가 제외"),
}

data class RiskMoment(
    val id: Int,
    val time: String,
    val kind: MomentKind,
    val roadType: RoadType,
    val rainy: Boolean,
    val night: Boolean,
    val cause: String,
    /** 지도 위 상대 위치 (0~1) */
    val mapX: Float,
    val mapY: Float,
    val judgement: Judgement = Judgement.HABIT_SUSPECTED,
    /** 위험 순간이 기록된 실제 위치 (위치 정보가 없으면 null) */
    val lat: Double? = null,
    val lng: Double? = null,
    /** 구간 진입까지 남은 거리 (구간 직전 제동일 때) */
    val remainMeters: Int? = null,
    val direction: Direction = Direction.NONE,
    /** 판정 근거 (어떤 신호를 보고 판단했는지) */
    val evidence: String? = null,
)

enum class HabitStatus { HABIT, OK, INSUFFICIENT }

data class Habit(
    val behavior: Behavior,
    val title: String,
    val where: String,
    /** 비율 (%) 문자열. 데이터 부족이면 "—" */
    val value: String,
    val unit: String,
    val metricLabel: String,
    /** 근거 건수. 예: "4/20회" */
    val countText: String = "",
    val status: HabitStatus = HabitStatus.HABIT,
)

/** 약점 프로필 한 칸 = 행동 × 도로 유형 */
data class WeakRoad(
    val type: RoadType,
    val ratio: Float,
    val behavior: Behavior = Behavior.PRE_ENTRY_BRAKE,
    val risk: Int = 0,
    val judged: Int = 0,
    val direction: Direction = Direction.NONE,
) {
    val place get() = placeLabel(type, direction)
}

/** 주간 추이: 위험 비율(%). 표본이 부족한 주는 null (0으로 채우지 않음) */
data class WeeklyPoint(val label: String, val merge: Float?, val overall: Float?, val countText: String? = null)

data class CoachingCard(
    val roadType: RoadType,
    val remainMeters: Int,
    val headline: String,
    val action: String,
    val reason: String,
    /** 음성으로도 안내했는지 (오디오 포커스를 못 받으면 화면 카드만) */
    val voiced: Boolean = true,
)

/** 정차 중 화면용 예시 데이터. 실제 주행 기록이 쌓이면 CoachEngine이 이 값을 대체합니다. */
object SampleData {
    val tripSummary = "출근 · ${DemoRoute.DISTANCE_KM}km · 21분"
    val tripStart = "08:31"
    val tripEnd = "08:52"
    val route: List<GeoPoint> = DemoRoute.points
    private val mergePoint = DemoRoute.points[DemoRoute.MERGE_INDEX - 1]
    private val icPoint = DemoRoute.points[DemoRoute.IC_EXIT_INDEX]
    private val schoolPoint = DemoRoute.points[DemoRoute.SCHOOL_INDEX]
    
    val moments = listOf(
        RiskMoment(
            1, "08:34", MomentKind.LATE_BRAKE, RoadType.MERGE, rainy = true, night = false,
            cause = "합류 지점 40m 전에서야 브레이크를 밟았어요", mapX = 0.24f, mapY = 0.66f,
            lat = mergePoint.lat, lng = mergePoint.lng, remainMeters = 40,
            evidence = "브레이크 첫 입력 · 합류 40m 전 (기준 50m 이내)",
        ),
        RiskMoment(
            2, "08:43", MomentKind.NO_SIGNAL, RoadType.IC_EXIT, rainy = true, night = false,
            cause = "우측 IC 진출 중 방향지시등이 꺼져 있었어요", mapX = 0.58f, mapY = 0.42f,
            lat = icPoint.lat, lng = icPoint.lng, direction = Direction.RIGHT,
            evidence = "조향 38° (진출 100m 이내) · 우측 방향지시등 OFF",
        ),
        RiskMoment(
            3, "08:49", MomentKind.SPEEDING, RoadType.SCHOOL_ZONE, rainy = true, night = false,
            cause = "제한속도 30km/h 단속 구간이었어요", mapX = 0.80f, mapY = 0.18f,
            lat = schoolPoint.lat, lng = schoolPoint.lng,
            evidence = "내비 단속 카메라 과속 안내 (카메라 1대당 1회)",
        ),
    )

    /** 습관 분석에서 '약점'으로 판정된 도로 유형. 진입 전 코칭의 대상이 됩니다. */
    val weakTypes = setOf(RoadType.MERGE, RoadType.IC_EXIT, RoadType.ROUNDABOUT)
}
