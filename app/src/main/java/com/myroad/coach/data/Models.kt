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

/** 위험 순간의 종류 = 습관의 단위 */
enum class MomentKind(val label: String) {
    HARD_BRAKE("급제동"),
    LATE_BRAKE("늦은 감속"),
    NO_SIGNAL("방향지시등 미사용"),
    SPEEDING("과속 안내"),
    SHARP_STEER("급조향"),
}

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
)

data class Habit(
    val title: String,
    val where: String,
    val value: String,
    val unit: String,
    val metricLabel: String,
)

data class WeakRoad(val type: RoadType, val ratio: Float)

data class WeeklyPoint(val label: String, val merge: Float, val overall: Float)

data class CoachingCard(
    val roadType: RoadType,
    val remainMeters: Int,
    val headline: String,
    val action: String,
    val reason: String,
)

/** 정차 중 화면용 예시 데이터. 실제 주행 기록이 쌓이면 CoachEngine이 이 값을 대체합니다. */
object SampleData {
    val tripSummary = "출근 · 24.6km · 36분"
    val tripStart = "08:31"
    val tripEnd = "09:07"
    val averageMoments = "최근 출근 평균 4.2건"

    val moments = listOf(
        RiskMoment(
            1, "08:42", MomentKind.HARD_BRAKE, RoadType.MERGE, rainy = true, night = false,
            cause = "합류 지점 40m 전에서야 브레이크를 밟았어요", mapX = 0.24f, mapY = 0.66f,
        ),
        RiskMoment(
            2, "08:55", MomentKind.NO_SIGNAL, RoadType.IC_EXIT, rainy = true, night = false,
            cause = "우측 진출 중 방향지시등이 꺼져 있었어요", mapX = 0.58f, mapY = 0.42f,
        ),
        RiskMoment(
            3, "09:02", MomentKind.SPEEDING, RoadType.SCHOOL_ZONE, rainy = true, night = false,
            cause = "제한속도 30km/h 단속 구간이었어요", mapX = 0.80f, mapY = 0.18f,
        ),
    )

    val habits = listOf(
        Habit("회전·분기할 때 방향지시등을\n자주 놓쳐요", "주로 IC 진출과 우회전에서 나와요", "30", "%", "미사용 비율"),
        Habit("합류 직전에 늦게 감속해\n급제동해요", "비 오는 날 합류 구간에 집중돼요", "2.7", "회", "100km당 급제동"),
        Habit("어린이보호구역에서\n속도를 늦게 줄여요", "단속 카메라 과속 안내 기준", "3", "회", "주당 과속 안내"),
    )

    val weakRoads = listOf(
        WeakRoad(RoadType.MERGE, 3.1f),
        WeakRoad(RoadType.IC_EXIT, 2.2f),
        WeakRoad(RoadType.ROUNDABOUT, 1.6f),
        WeakRoad(RoadType.TUNNEL, 0.8f),
    )

    val conditions = listOf("비 오는 날 1.8×", "야간 1.3×", "맑은 낮 1.0×")

    val weekly = listOf(
        WeeklyPoint("4주 전", 5.6f, 3.2f),
        WeeklyPoint("3주 전", 4.8f, 2.9f),
        WeeklyPoint("지난주", 4.0f, 2.6f),
        WeeklyPoint("이번 주", 2.4f, 2.2f),
    )

    /** 습관 분석에서 '약점'으로 판정된 도로 유형. 진입 전 코칭의 대상이 됩니다. */
    val weakTypes = setOf(RoadType.MERGE, RoadType.IC_EXIT, RoadType.ROUNDABOUT)
}
