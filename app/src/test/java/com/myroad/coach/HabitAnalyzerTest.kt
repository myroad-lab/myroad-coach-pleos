package com.myroad.coach

import com.myroad.coach.data.Behavior
import com.myroad.coach.data.Direction
import com.myroad.coach.data.HabitAnalyzer
import com.myroad.coach.data.HabitStatus
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RoadType
import com.myroad.coach.data.SeedTrips
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.ScenarioPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitAnalyzerTest {

    private val now = 1_763_000_000_000L

    @Test
    fun seedProducesExpectedHabitsAndWeakRoads() {
        val r = HabitAnalyzer.analyze(SeedTrips.generate(now))
        r.habits.forEach { println("${it.status} ${it.behavior} ${it.value}${it.unit} ${it.countText} | ${it.where}") }
        r.weakRoads.forEach { println("weak ${it.place} ${it.behavior} ${it.risk}/${it.judged} ${it.ratio}") }
        println("conditions=${r.conditions} weekly=${r.weekly} compare=${r.focusCompareText} signal=${r.signalUseRate} ${r.signalUseCountText} d=${r.signalUseDelta}")
        println("summary=${r.summary} next=${r.nextTarget} undetermined=${r.undeterminedCount}")
        assertTrue(r.isEnough)
        assertEquals(3, r.habits.size)
        assertTrue(r.habits.all { it.status == HabitStatus.HABIT })
        assertTrue(r.habits.all { Regex("""\d+/\d+회""").matches(it.countText) })
        assertEquals(setOf(RoadType.ROUNDABOUT, RoadType.MERGE, RoadType.IC_EXIT), r.weakTypes)
        assertEquals(RoadType.MERGE, r.focusType)
        assertEquals(Behavior.PRE_ENTRY_BRAKE, r.focus?.behavior)
        assertEquals("지난주 3/8회 → 이번 주 1/8회", r.focusCompareText)
        assertTrue((r.focusChangePercent ?: 0) >= 30)
        assertNotNull(r.nextTarget)
        assertEquals(Direction.RIGHT, r.weakRoads.first { it.behavior == Behavior.SIGNAL }.direction)
        assertEquals(4, r.weekly.size)
        assertTrue(r.weekly.first().merge!! > r.weekly.last().merge!!)
        assertTrue(r.undeterminedCount > 0)
    }

    @Test
    fun fewSamplesShowInsufficientInsteadOfZero() {
        val oneWeek = SeedTrips.generate(now).take(1)
        val r = HabitAnalyzer.analyze(oneWeek)
        assertTrue(r.habits.all { it.status == HabitStatus.INSUFFICIENT })
        assertNull(r.focusCompareText)
        assertTrue(r.weekly.all { it.merge == null })
        assertTrue(r.conditions.isEmpty())
    }

    @Test
    fun excludedMomentsAreNotCounted() {
        val trips = SeedTrips.generate(now)
        val excludedAll = trips.map { t ->
            t.copy(moments = t.moments.map { if (it.kind == MomentKind.SPEEDING) it.copy(judgement = Judgement.EXCLUDED_BY_DRIVER) else it })
        }
        val r = HabitAnalyzer.analyze(excludedAll)
        assertTrue(r.habits.none { it.behavior == Behavior.ZONE_SPEED && it.status == HabitStatus.HABIT })
        assertEquals(trips.sumOf { t -> t.moments.count { it.kind == MomentKind.SPEEDING } }, r.excludedCount)
    }

    @Test
    fun emptyHistoryIsNotEnough() {
        val r = HabitAnalyzer.analyze(emptyList())
        assertTrue(!r.isEnough)
        assertTrue(r.habits.none { it.status == HabitStatus.HABIT })
    }

    @Test
    fun josa() {
        assertEquals("을", HabitAnalyzer.objectJosa("IC 진출"))
        assertEquals("를", HabitAnalyzer.objectJosa("회전교차로"))
    }

    @Test
    fun scenarioTripIsRecordedAndUsesHistoryWeakTypes() = runTest {
        var saved = 0
        val engine = CoachEngine(speak = { true }, onHistoryChanged = { saved = it.size })
        engine.setHistory(SeedTrips.generate(now))
        ScenarioPlayer(engine, this).play()
        advanceUntilIdle()
        assertEquals(33, saved, "시나리오 주행 1건이 기록에 추가되어 저장 요청")
        // 약점: 합류(직전 제동) + IC 진출(방향지시등) → 코칭 2회
        assertEquals(2, engine.state.value.coachedCount)
        assertNotNull(engine.state.value.report.focusType)
    }
}
