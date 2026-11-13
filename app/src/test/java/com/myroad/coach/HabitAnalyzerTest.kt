package com.myroad.coach

import com.myroad.coach.data.HabitAnalyzer
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitAnalyzerTest {

    private val now = 1_763_000_000_000L

    @Test
    fun seedProducesExpectedHabitsAndWeakRoads() {
        val r = HabitAnalyzer.analyze(SeedTrips.generate(now))
        assertTrue(r.isEnough)
        assertEquals(3, r.habits.size)
        assertTrue(r.habits.first().title.startsWith("합류"))
        assertEquals(setOf(RoadType.ROUNDABOUT, RoadType.MERGE), r.weakTypes)
        assertEquals(RoadType.MERGE, r.focusType)
        assertEquals(RoadType.IC_EXIT, r.signalFocusType)
        assertTrue((r.focusChangePercent ?: 0) >= 30, "합류 급제동이 지난주보다 30% 이상 줄어야 함")
        assertEquals("회전교차로", r.nextTarget)
        assertEquals(4, r.weekly.size)
        assertTrue(r.weekly.first().merge > r.weekly.last().merge)
    }

    @Test
    fun excludedMomentsAreNotCounted() {
        val trips = SeedTrips.generate(now)
        val excludedAll = trips.map { t ->
            t.copy(moments = t.moments.map { if (it.kind == MomentKind.SPEEDING) it.copy(judgement = Judgement.EXCLUDED_BY_DRIVER) else it })
        }
        val r = HabitAnalyzer.analyze(excludedAll)
        assertTrue(r.habits.none { it.metricLabel == "주당 과속 안내" })
        assertEquals(trips.sumOf { t -> t.moments.count { it.kind == MomentKind.SPEEDING } }, r.excludedCount)
    }

    @Test
    fun emptyHistoryIsNotEnough() {
        val r = HabitAnalyzer.analyze(emptyList())
        assertTrue(!r.isEnough)
        assertTrue(r.habits.isEmpty())
    }

    @Test
    fun josa() {
        assertEquals("을", HabitAnalyzer.objectJosa("IC 진출"))
        assertEquals("를", HabitAnalyzer.objectJosa("회전교차로"))
    }

    @Test
    fun scenarioTripIsRecordedAndUsesHistoryWeakTypes() = runTest {
        var saved = 0
        val engine = CoachEngine(speak = {}, onHistoryChanged = { saved = it.size })
        engine.setHistory(SeedTrips.generate(now))
        ScenarioPlayer(engine, this).play()
        advanceUntilIdle()
        assertEquals(33, saved, "시나리오 주행 1건이 기록에 추가되어 저장 요청")
        // 약점: 합류(급제동) + IC 진출(깜빡이 습관) → 코칭 2회
        assertEquals(2, engine.state.value.coachedCount)
        assertNotNull(engine.state.value.report.focusType)
    }
}
