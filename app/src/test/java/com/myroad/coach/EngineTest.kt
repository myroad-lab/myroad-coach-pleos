package com.myroad.coach

import com.myroad.coach.data.Behavior
import com.myroad.coach.data.Direction
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.Outcome
import com.myroad.coach.data.RoadType
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.DriveState
import com.myroad.coach.engine.ScenarioPlayer
import com.myroad.coach.engine.SignalSide
import com.myroad.coach.sdk.NightCalc
import com.myroad.coach.sdk.RoadTypeMapper
import ai.pleos.playground.navi.constants.TurnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineTest {

    private fun engine(spoken: MutableList<String> = mutableListOf(), voice: Boolean = true, t: LongArray = longArrayOf(1_000_000L)) =
        CoachEngine(speak = { spoken += it; voice }, millis = { t[0] })

    /** 신호 구독 직후 첫 값을 받은 상태로 출발 */
    private fun CoachEngine.start() {
        onDriveState(true)
        onTurnSignal(SignalSide.OFF)
        onSteering(0f)
        onBrake(false)
    }

    @Test
    fun scenarioProducesCoachingAndThreeMoments() = runTest {
        val spoken = mutableListOf<String>()
        val engine = CoachEngine(speak = { spoken += it; true })
        ScenarioPlayer(engine, this).play()
        advanceUntilIdle()
        val st = engine.state.value
        println("spoken=$spoken")
        st.moments.forEach { println("${it.time} ${it.kind} ${it.roadType} ${it.direction} | ${it.cause} | ${it.evidence}") }
        st.tripChecks.forEach { println(it) }
        assertEquals(DriveState.PARKED, st.driveState)
        assertEquals(2, st.coachedCount, "합류 + IC 진출 두 번 코칭")
        assertTrue(spoken.any { it.startsWith("500m 앞 합류 구간") })
        assertTrue(spoken.any { it.contains("우측 IC 진출") }, "방향을 붙여 안내")
        assertEquals(listOf(MomentKind.LATE_BRAKE, MomentKind.NO_SIGNAL, MomentKind.SPEEDING), st.moments.map { it.kind })
        assertEquals(40, st.moments[0].remainMeters)
        assertEquals(Direction.RIGHT, st.moments[1].direction)
        assertTrue(st.moments.all { it.evidence != null })
        assertEquals(listOf("08:34", "08:43", "08:49"), st.moments.map { it.time })
        assertEquals("08:31", st.tripStart)
        assertEquals("08:52", st.tripEnd)
        assertTrue(st.moments.all { it.rainy })
        assertTrue(st.moments.all { it.lat != null && it.lng != null })
        assertTrue(st.route.size > 50, "경로 점 ${st.route.size}개")
        val km = st.tripSummary.substringAfter("· ").substringBefore("km").toFloat()
        assertTrue(km in 14.5f..16f, "주행 거리 $km km (경로 약 15.2km)")
        val merge = com.myroad.coach.data.DemoRoute.points[com.myroad.coach.data.DemoRoute.MERGE_INDEX]
        val d = CoachEngine.distanceMeters(com.myroad.coach.data.GeoPoint(st.moments[0].lat!!, st.moments[0].lng!!), merge)
        assertTrue(d < 200.0, "합류 지점과의 거리 ${d}m")
        // 판정 기회: 합류 직전 제동(위험), 합류 깜빡이(조향 근거 없음→판정 불가), IC 직전 제동(정상), IC 깜빡이(위험), 카메라 1대(위험)
        val c = st.tripChecks
        assertEquals(Outcome.RISK, c.first { it.behavior == Behavior.PRE_ENTRY_BRAKE && it.roadType == RoadType.MERGE }.outcome)
        assertEquals(Outcome.UNDETERMINED, c.first { it.behavior == Behavior.SIGNAL && it.roadType == RoadType.MERGE }.outcome)
        assertEquals(Outcome.OK, c.first { it.behavior == Behavior.PRE_ENTRY_BRAKE && it.roadType == RoadType.IC_EXIT }.outcome)
        assertEquals(Outcome.RISK, c.first { it.behavior == Behavior.SIGNAL && it.roadType == RoadType.IC_EXIT }.outcome)
        assertEquals(1, c.count { it.behavior == Behavior.ZONE_SPEED }, "같은 카메라 반복 안내는 1회")
    }

    @Test
    fun locationWhileParkedIsNotRecorded() {
        val engine = engine()
        engine.onLocation(37.4, 127.1)
        engine.onLocation(37.5, 127.0)
        engine.onDriveState(true)
        engine.onDriveState(false)
        assertTrue(engine.state.value.route.size <= 1)
    }

    @Test
    fun matchingSignalIsOk() {
        val engine = engine()
        engine.start()
        listOf(300, 200, 100, 60).forEach { engine.onTbt(RoadType.IC_EXIT, it, "", Direction.RIGHT) }
        engine.onTurnSignal(SignalSide.RIGHT)
        engine.onSteering(40f)
        engine.onTbt(RoadType.IC_EXIT, 0, "", Direction.RIGHT)
        engine.onDriveState(false)
        val st = engine.state.value
        assertTrue(st.moments.none { it.kind == MomentKind.NO_SIGNAL })
        assertEquals(Outcome.OK, st.tripChecks.first { it.behavior == Behavior.SIGNAL }.outcome)
    }

    @Test
    fun wrongSideSignalIsRisk() {
        val engine = engine()
        engine.start()
        listOf(300, 100, 60).forEach { engine.onTbt(RoadType.IC_EXIT, it, "", Direction.RIGHT) }
        engine.onTurnSignal(SignalSide.LEFT)
        engine.onSteering(40f)
        engine.onTbt(RoadType.IC_EXIT, 0, "", Direction.RIGHT)
        engine.onDriveState(false)
        assertEquals(Outcome.RISK, engine.state.value.tripChecks.first { it.behavior == Behavior.SIGNAL }.outcome)
    }

    @Test
    fun noSignalDataIsUndetermined() {
        val engine = engine()
        engine.onDriveState(true) // 방향지시등·조향 신호를 한 번도 받지 못함
        listOf(300, 100, 0).forEach { engine.onTbt(RoadType.RIGHT_TURN, it, "", Direction.RIGHT) }
        engine.onDriveState(false)
        val st = engine.state.value
        assertEquals(Outcome.UNDETERMINED, st.tripChecks.single().outcome)
        assertTrue(st.moments.isEmpty())
    }

    @Test
    fun earlyBrakeIsOkAndNotRecorded() {
        val engine = engine()
        engine.start()
        engine.onTbt(RoadType.MERGE, 300, "")
        engine.onBrake(true) // 300m 전 첫 제동 → 정상
        engine.onBrake(false)
        engine.onTbt(RoadType.MERGE, 40, "")
        engine.onBrake(true) // 이미 감속을 시작했으므로 직전 제동 아님
        engine.onTbt(RoadType.MERGE, 0, "")
        engine.onDriveState(false)
        val st = engine.state.value
        assertTrue(st.moments.none { it.kind == MomentKind.LATE_BRAKE })
        assertEquals(Outcome.OK, st.tripChecks.first { it.behavior == Behavior.PRE_ENTRY_BRAKE }.outcome)
    }

    @Test
    fun rerouteBeforePassingIsNotJudged() {
        var stopped = 0
        val engine = CoachEngine(speak = { true }, stopSpeaking = { stopped++ })
        engine.start()
        engine.onTbt(RoadType.MERGE, 500, "") // 코칭 시작
        assertTrue(engine.state.value.coaching != null)
        engine.onRouteChanged()
        assertNull(engine.state.value.coaching)
        assertEquals(1, stopped)
        engine.onDriveState(false)
        assertTrue(engine.state.value.tripChecks.isEmpty(), "지나가지 않은 구간은 분모에 넣지 않음")
    }

    @Test
    fun tooCloseIsNotCoachedAndFocusDeniedIsCardOnly() {
        val spoken = mutableListOf<String>()
        val e1 = engine(spoken)
        e1.start()
        e1.onTbt(RoadType.MERGE, 80, "") // 처음 받은 안내가 이미 너무 가까움
        assertEquals(0, e1.state.value.coachedCount)

        val e2 = engine(voice = false)
        e2.start()
        e2.onTbt(RoadType.MERGE, 500, "")
        assertFalse(e2.state.value.coaching!!.voiced)
    }

    @Test
    fun staleLocationIsNotAttached() {
        val t = longArrayOf(1_000_000L)
        val engine = engine(t = t)
        engine.start()
        engine.onLocation(37.4, 127.1, timeMillis = t[0])
        t[0] += 60_000 // 1분 동안 위치 갱신 없음
        engine.onTbt(RoadType.MERGE, 100, "")
        engine.onTbt(RoadType.MERGE, 40, "")
        engine.onBrake(true)
        val m = engine.state.value.let { engine.onDriveState(false); engine.state.value.moments.first() }
        assertNull(m.lat, "오래된 위치는 '위치 확인 불가'")
    }

    @Test
    fun wiperMustStayOnForRain() {
        val t = longArrayOf(0L)
        val engine = engine(t = t)
        engine.start()
        engine.onWiper(true)
        assertFalse(engine.state.value.signals.rainy)
        t[0] += 40_000
        engine.onTbt(RoadType.LEFT_TURN, 500, "")
        assertTrue(engine.state.value.signals.rainy)
    }

    @Test
    fun nonWeakTypeIsNotCoached() {
        val spoken = mutableListOf<String>()
        val engine = engine(spoken)
        engine.start()
        engine.onTbt(RoadType.LEFT_TURN, 150, "")
        assertEquals(0, engine.state.value.coachedCount)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun driverCanExcludeMoment() {
        val engine = engine()
        val id = engine.state.value.moments.first().id
        engine.setJudgement(id, external = true)
        assertEquals(Judgement.EXCLUDED_BY_DRIVER, engine.state.value.moments.first().judgement)
    }

    @Test
    fun mapperKeepsDirection() {
        assertEquals(RoadType.MERGE, RoadTypeMapper.fromTurn(TurnType.TURN_DEVIDE_MERGE))
        assertEquals(RoadType.IC_EXIT, RoadTypeMapper.fromTurn(TurnType.TURN_EXIT_IC_R))
        assertEquals(Direction.RIGHT, RoadTypeMapper.directionOf(TurnType.TURN_EXIT_IC_R))
        assertEquals(Direction.LEFT, RoadTypeMapper.directionOf(TurnType.TURN_KEEP_L))
        assertEquals(Direction.LEFT, RoadTypeMapper.directionOf(TurnType.TURN_UTURN))
        assertEquals(Direction.NONE, RoadTypeMapper.directionOf(TurnType.TURN_DEVIDE_MERGE))
        assertEquals(RoadType.ROUNDABOUT, RoadTypeMapper.fromTurn(TurnType.TURN_ENTER_ROUNDABOUT_03))
    }

    @Test
    fun nightBySunset() {
        // 서울, 2026-06-21: 12:00 KST(03:00Z)는 낮, 23:00 KST(14:00Z)는 밤
        assertFalse(NightCalc.isNight(37.5, 127.0, 1_782_010_800_000L))
        assertTrue(NightCalc.isNight(37.5, 127.0, 1_782_050_400_000L))
    }
}
