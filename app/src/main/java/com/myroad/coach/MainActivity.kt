package com.myroad.coach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.myroad.coach.data.TripRepository
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.DriveState
import com.myroad.coach.engine.ScenarioPlayer
import com.myroad.coach.sdk.LiveSources
import com.myroad.coach.sdk.Speaker
import com.myroad.coach.ui.Chip
import com.myroad.coach.ui.DrivingCoachScreen
import com.myroad.coach.ui.GrowthScreen
import com.myroad.coach.ui.HabitScreen
import com.myroad.coach.ui.HighlightScreen
import com.myroad.coach.ui.MicIcon
import com.myroad.coach.ui.MoreMenu
import com.myroad.coach.ui.PageNavigation
import com.myroad.coach.ui.PleosColor
import com.myroad.coach.ui.PleosToast
import com.myroad.coach.ui.SuffixIcon
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker
    private lateinit var engine: CoachEngine
    private lateinit var scenario: ScenarioPlayer
    private lateinit var live: LiveSources

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Speaker(this).also { it.initialize() }
        val trips = TripRepository(this)
        engine = CoachEngine(speak = { speaker.speak(it) }, onHistoryChanged = { trips.save(it) })
        engine.setHistory(trips.load())
        scenario = ScenarioPlayer(engine, lifecycleScope)
        live = LiveSources(this, engine).also { it.start() }

        setContent {
            PleosAppTheme {
                AppRoot(
                    engine = engine,
                    onPlayScenario = { scenario.play() },
                    onResetHistory = { engine.setHistory(trips.resetToSample()) },
                    sdkStatus = { live.status.map { (k, v) -> "$k · $v" } + "음성 · ${speaker.engineName}" },
                )
            }
        }
    }

    override fun onDestroy() {
        scenario.stop()
        live.stop()
        speaker.release()
        super.onDestroy()
    }
}

/** 드롭다운 메뉴 등 Material 컴포넌트가 Pleos 라이트 테마 색을 쓰도록 맞춥니다. */
@Composable
private fun PleosAppTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = PleosColor.positive,
        background = PleosColor.background,
        surface = PleosColor.background,
        surfaceContainer = PleosColor.background,
        onSurface = PleosColor.text1,
        onBackground = PleosColor.text1,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides PleosColor.text1) { content() }
    }
}

private val PAGES = listOf("오늘의 하이라이트", "나의 운전 습관", "성장 리포트")

@Composable
private fun AppRoot(
    engine: CoachEngine,
    onPlayScenario: () -> Unit,
    onResetHistory: () -> Unit,
    sdkStatus: () -> List<String>,
) {
    val state by engine.state.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.requestHighlightTab) {
        if (state.requestHighlightTab != 0L) {
            page = 0
            toast = "주행이 끝났어요. 오늘의 하이라이트를 확인해 보세요"
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2800)
            toast = null
        }
    }
    val driving = state.driveState == DriveState.DRIVING

    Box(Modifier.fillMaxSize().background(PleosColor.background)) {
        Column(Modifier.fillMaxSize()) {
            // ── Page Navigation (Pleos 가이드: 앱 아이콘 + 제목▼ + Suffix) ──
            PageNavigation(
                title = if (driving) "주행 코치" else PAGES[page],
                pages = PAGES,
                onSelectPage = if (driving) null else ({ i: Int -> page = i }),
            ) {
                if (driving) {
                    // 주행 중에는 조작 요소를 숨기고 상태만 표시
                    Chip("● 코칭 켜짐", bg = PleosColor.activeSoft, fg = PleosColor.active)
                } else {
                    SuffixIcon(onClick = {
                        page = 2
                        engine.answerWeekly()
                    }) { MicIcon() }
                    MoreMenu(
                        items = listOf(
                            "시연 시나리오 재생" to {
                                onPlayScenario()
                                toast = "출근길 시나리오를 재생해요"
                            },
                            "주행 기록을 샘플로 초기화" to {
                                onResetHistory()
                                toast = "주행 기록을 시연용 샘플로 되돌렸어요"
                            },
                        ),
                        footer = sdkStatus(),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(PleosColor.line))

            // ── 본문 ──
            Box(Modifier.fillMaxSize()) {
                if (driving) {
                    DrivingCoachScreen(state)
                } else {
                    when (page) {
                        0 -> HighlightScreen(state, onJudge = { id, external ->
                            engine.setJudgement(id, external)
                            toast = if (external) "돌발 상황으로 표시했어요. 습관 분석에서 제외할게요" else "습관 분석에 반영할게요"
                        })
                        1 -> HabitScreen(state)
                        else -> GrowthScreen(state, onAsk = { engine.answerWeekly() })
                    }
                }
            }
        }

        // ── Toast (하단 중앙, 여백 24) ──
        toast?.let { PleosToast(it, Modifier.align(Alignment.BottomCenter)) }
    }
}
