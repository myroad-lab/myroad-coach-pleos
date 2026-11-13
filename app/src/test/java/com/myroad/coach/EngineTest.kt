package com.myroad.coach

import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RoadType
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.DriveState
import com.myroad.coach.engine.ScenarioPlayer
import com.myroad.coach.engine.SignalSide
import com.myroad.coach.sdk.RoadTypeMapper
import ai.pleos.playground.navi.constants.TurnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineTest {

    @Test
    fun scenarioProducesCoachingAndThreeMoments() = runTest {
        val spoken = mutableListOf<String>()
        val engine = CoachEngine(speak = { spoken += it })
        val player = ScenarioPlayer(engine, this)
        player.play()
        advanceUntilIdle()
        val st = engine.state.value
        println("spoken=$spoken")
        st.moments.forEach { println("${it.time} ${it.kind} ${it.roadType} ${it.judgement} | ${it.cause}") }
        assertEquals(DriveState.PARKED, st.driveState)
        assertEquals(2, st.coachedCount, "합류 + IC 진출 두 번 코칭")
        assertTrue(spoken.any { it.startsWith("500m 앞 합류 구간") })
        assertEquals(listOf(MomentKind.HARD_BRAKE, MomentKind.NO_SIGNAL, MomentKind.SPEEDING), st.moments.map { it.kind })
        assertEquals("08:34", st.moments[0].time)
        assertEquals("08:43", st.moments[1].time)
        assertEquals("08:49", st.moments[2].time)
        assertEquals("08:31", st.tripStart)
        assertEquals("08:52", st.tripEnd)
        assertTrue(st.moments.all { it.rainy })
        // 실제 경로 좌표: 위험 순간마다 위치가 있고, 경로와 거리가 위치로 계산됨
        assertTrue(st.moments.all { it.lat != null && it.lng != null })
        assertTrue(st.route.size > 50, "경로 점 ${st.route.size}개")
        val km = st.tripSummary.substringAfter("· ").substringBefore("km").toFloat()
        assertTrue(km in 14.5f..16f, "주행 거리 $km km (경로 약 15.2km)")
        // 합류 급제동 위치는 실제 경부고속도로 합류 지점 근처 (200m 이내)
        val merge = com.myroad.coach.data.DemoRoute.points[com.myroad.coach.data.DemoRoute.MERGE_INDEX]
        val d = CoachEngine.distanceMeters(com.myroad.coach.data.GeoPoint(st.moments[0].lat!!, st.moments[0].lng!!), merge)
        assertTrue(d < 200.0, "합류 지점과의 거리 ${d}m")
    }

    @Test
    fun locationWhileParkedIsNotRecorded() {
        val engine = CoachEngine(speak = {})
        engine.onLocation(37.4, 127.1)
        engine.onLocation(37.5, 127.0)
        engine.onDriveState(true)
        engine.onDriveState(false)
        assertTrue(engine.state.value.route.size <= 1)
    }

    @Test
    fun signalUsedMeansNoNoSignalMoment() {
        val engine = CoachEngine(speak = {})
        engine.onDriveState(true)
        listOf(300, 200, 100, 60).forEach { engine.onTbt(RoadType.IC_EXIT, it, "") }
        engine.onTurnSignal(SignalSide.RIGHT)
        engine.onSteering(40f)
        engine.onTbt(RoadType.IC_EXIT, 0, "")
        engine.onDriveState(false)
        assertTrue(engine.state.value.moments.none { it.kind == MomentKind.NO_SIGNAL })
    }

    @Test
    fun earlyBrakeIsNotLate() {
        val engine = CoachEngine(speak = {})
        engine.onDriveState(true)
        engine.onTbt(RoadType.MERGE, 300, "")
        engine.onBrake(true) // 300m 전 제동 → 정상
        engine.onBrake(false)
        engine.onTbt(RoadType.MERGE, 0, "")
        engine.onDriveState(false)
        assertTrue(engine.state.value.moments.none { it.kind == MomentKind.HARD_BRAKE })
    }

    @Test
    fun nonWeakTypeIsNotCoached() {
        val spoken = mutableListOf<String>()
        val engine = CoachEngine(speak = { spoken += it })
        engine.onDriveState(true)
        engine.onTbt(RoadType.LEFT_TURN, 150, "")
        assertEquals(0, engine.state.value.coachedCount)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun driverCanExcludeMoment() {
        val engine = CoachEngine(speak = {})
        val id = engine.state.value.moments.first().id
        engine.setJudgement(id, external = true)
        assertEquals(Judgement.EXCLUDED_BY_DRIVER, engine.state.value.moments.first().judgement)
    }

    @Test
    fun mapperCoversMergeAndIc() {
        assertEquals(RoadType.MERGE, RoadTypeMapper.fromTurn(TurnType.TURN_DEVIDE_MERGE))
        assertEquals(RoadType.IC_EXIT, RoadTypeMapper.fromTurn(TurnType.TURN_EXIT_IC_R))
        assertEquals(RoadType.ROUNDABOUT, RoadTypeMapper.fromTurn(TurnType.TURN_ENTER_ROUNDABOUT_03))
    }
}
