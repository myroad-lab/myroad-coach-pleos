package com.myroad.coach.sdk

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import com.myroad.coach.data.RoadType

/** NaviHelper SDK의 회전 안내 유형(TurnType)과 주의 구간 유형(CautionPointType)을 앱의 도로 유형으로 변환합니다. */
object RoadTypeMapper {

    fun fromTurn(type: TurnType): RoadType = when (type) {
        TurnType.TURN_DEVIDE_MERGE,
        TurnType.TURN_KEEP_L, TurnType.TURN_KEEP_R -> RoadType.MERGE

        TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_C -> RoadType.IC_EXIT
        TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_C -> RoadType.IC_ENTER

        TurnType.TURN_ENTER_ROUNDABOUT_02, TurnType.TURN_ENTER_ROUNDABOUT_03,
        TurnType.TURN_ENTER_ROUNDABOUT_04, TurnType.TURN_ENTER_ROUNDABOUT_06,
        TurnType.TURN_ENTER_ROUNDABOUT_08, TurnType.TURN_ENTER_ROUNDABOUT_09,
        TurnType.TURN_ENTER_ROUNDABOUT_10, TurnType.TURN_ENTER_ROUNDABOUT_12,
        TurnType.TURN_EXIT_ROUNDABOUT -> RoadType.ROUNDABOUT

        TurnType.TURN_LEFT -> RoadType.LEFT_TURN
        TurnType.TURN_RIGHT -> RoadType.RIGHT_TURN
        TurnType.TURN_UTURN -> RoadType.U_TURN
        TurnType.TURN_SHARP_L, TurnType.TURN_SHARP_R -> RoadType.SHARP_TURN
        TurnType.TURN_TUNNEL -> RoadType.TUNNEL
        TurnType.TURN_TOLL -> RoadType.TOLL
        else -> RoadType.OTHER
    }

    fun fromCaution(type: CautionPointType): RoadType = when (type) {
        CautionPointType.SCHOOL_ZONE -> RoadType.SCHOOL_ZONE
        CautionPointType.ACCIDENT_PRONE_AREA -> RoadType.ACCIDENT_PRONE
        CautionPointType.SLIPPERY_ROAD_ALERT -> RoadType.SLIPPERY
        CautionPointType.DOWNHILL_CAUTION_AREA -> RoadType.DOWNHILL
        else -> RoadType.OTHER
    }

    /** 방향지시등을 켜야 하는 유형인지 (방향지시등 미사용 판별 대상) */
    fun needsSignal(type: RoadType): Boolean = type in setOf(
        RoadType.MERGE, RoadType.IC_EXIT, RoadType.IC_ENTER, RoadType.ROUNDABOUT,
        RoadType.LEFT_TURN, RoadType.RIGHT_TURN, RoadType.U_TURN,
    )

    /** 진입 전 코칭을 시작할 남은 거리(m). 고속도로성 구간은 길게, 일반도로는 짧게. */
    fun coachingDistance(type: RoadType): Int = when (type) {
        RoadType.MERGE, RoadType.IC_EXIT, RoadType.IC_ENTER, RoadType.TOLL -> 500
        else -> 200
    }
}
