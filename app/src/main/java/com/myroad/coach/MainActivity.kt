package com.myroad.coach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.DriveState
import com.myroad.coach.engine.ScenarioPlayer
import com.myroad.coach.sdk.LiveSources
import com.myroad.coach.sdk.Speaker
import com.myroad.coach.ui.AppMark
import com.myroad.coach.ui.Chip
import com.myroad.coach.ui.DrivingCoachScreen
import com.myroad.coach.ui.GrowthScreen
import com.myroad.coach.ui.HabitScreen
import com.myroad.coach.ui.HighlightScreen
import com.myroad.coach.ui.IconButtonBox
import com.myroad.coach.ui.MicIcon
import com.myroad.coach.ui.PleosButton
import com.myroad.coach.ui.PleosColor
import com.myroad.coach.ui.PleosType
import com.myroad.coach.ui.SegmentedMenu
import com.myroad.coach.ui.Spacer1

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker
    private lateinit var engine: CoachEngine
    private lateinit var scenario: ScenarioPlayer
    private lateinit var live: LiveSources

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Speaker(this).also { it.initialize() }
        engine = CoachEngine(speak = { speaker.speak(it) })
        scenario = ScenarioPlayer(engine, lifecycleScope)
        live = LiveSources(this, engine).also { it.start() }

        setContent {
            AppRoot(
                engine = engine,
                onPlayScenario = { scenario.play() },
            )
        }
    }

    override fun onDestroy() {
        scenario.stop()
        live.stop()
        speaker.release()
        super.onDestroy()
    }
}

private val TABS = listOf("오늘의 하이라이트", "나의 운전 습관", "성장 리포트")

@Composable
private fun AppRoot(engine: CoachEngine, onPlayScenario: () -> Unit) {
    val state by engine.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.requestHighlightTab) { if (state.requestHighlightTab != 0L) tab = 0 }
    val driving = state.driveState == DriveState.DRIVING

    BoxWithConstraints(Modifier.fillMaxSize().background(PleosColor.background)) {
    val narrow = maxWidth < 1300.dp
    Column(Modifier.fillMaxSize()) {
        // ── 앱 헤더 ──
        Row(
            Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark()
            Spacer(Modifier.width(22.dp))
            Text("마이로드 코치", style = PleosType.titleL.copy(fontSize = PleosType.titleS.fontSize))
            Spacer1()
            if (driving) {
                // 주행 중에는 조작 요소를 숨기고 상태만 표시
                Chip("● 코칭 켜짐", bg = PleosColor.activeSoft, fg = PleosColor.active)
            } else {
                PleosButton("시나리오 재생", onClick = onPlayScenario)
                if (!narrow) {
                    Spacer(Modifier.width(18.dp))
                    SegmentedMenu(TABS, tab) { tab = it }
                }
                Spacer(Modifier.width(18.dp))
                IconButtonBox(onClick = { tab = 2; engine.answerWeekly() }) { MicIcon() }
            }
        }
        if (narrow && !driving) {
            Row(Modifier.padding(start = 40.dp, end = 40.dp, bottom = 20.dp)) {
                SegmentedMenu(TABS, tab) { tab = it }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PleosColor.line))

        // ── 본문 ──
        Box(Modifier.fillMaxSize()) {
            if (driving) {
                DrivingCoachScreen(state)
            } else {
                when (tab) {
                    0 -> HighlightScreen(state, onJudge = { id, external -> engine.setJudgement(id, external) })
                    1 -> HabitScreen(state)
                    else -> GrowthScreen(state, onAsk = { engine.answerWeekly() })
                }
            }
        }
    }
    }
}
