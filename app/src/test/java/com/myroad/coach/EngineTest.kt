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
        assertEquals("08:42", st.moments[0].time)
        assertEquals("08:55", st.moments[1].time)
        assertEquals("09:02", st.moments[2].time)
        assertEquals("08:31", st.tripStart)
        assertEquals("09:07", st.tripEnd)
        assertTrue(st.moments.all { it.rainy })
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
