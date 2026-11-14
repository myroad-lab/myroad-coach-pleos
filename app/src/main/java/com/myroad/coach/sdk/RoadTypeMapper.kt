package com.myroad.coach.sdk

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import com.myroad.coach.data.Direction
import com.myroad.coach.data.RoadType

/** NaviHelper SDK의 회전 안내 유형(TurnType)과 주의 구간 유형(CautionPointType)을 앱의 도로 유형으로 변환합니다. */
object RoadTypeMapper {

    /**
     * KEEP_L/R(갈림길에서 한쪽 유지)도 차로를 옮기며 방향지시등이 필요한 '분기'라서 합류·분기로 묶습니다.
     * 방향은 [directionOf]로 따로 보존합니다.
     */
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

    /**
     * 회전 안내의 방향. 방향지시등은 이 방향과 같은 쪽이 켜졌을 때만 '정상'으로 봅니다.
     * 이름 끝의 _L/_R, 좌·우회전, 유턴(좌측), 회전교차로(우측 진출)로 판단하고, 모르면 NONE.
     */
    fun directionOf(type: TurnType): Direction {
        val n = type.name
        return when {
            type == TurnType.TURN_LEFT || type == TurnType.TURN_UTURN -> Direction.LEFT
            type == TurnType.TURN_RIGHT -> Direction.RIGHT
            n.contains("ROUNDABOUT") -> Direction.RIGHT
            n.endsWith("_L") -> Direction.LEFT
            n.endsWith("_R") -> Direction.RIGHT
            else -> Direction.NONE
        }
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
