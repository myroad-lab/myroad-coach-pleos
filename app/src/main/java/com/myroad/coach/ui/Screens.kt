package com.myroad.coach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.HabitAnalyzer
import com.myroad.coach.data.HabitStatus
import com.myroad.coach.data.Outcome
import com.myroad.coach.data.WeeklyPoint
import kotlin.math.roundToInt
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RiskMoment
import com.myroad.coach.data.SampleData
import com.myroad.coach.engine.CoachUiState
import com.myroad.coach.engine.SignalSide

// ═══════════════════════════ 01 주행 중: 진입 전 코칭 ═══════════════════════════

@Composable
fun DrivingCoachScreen(state: CoachUiState) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        val card = state.coaching
        if (card != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(PleosColor.cautionSoft)
                    .padding(horizontal = 44.dp, vertical = 40.dp),
            ) {
                // Pleos 'Driving Notification' 구조: 아이콘 + 제목 + 설명
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CautionIcon()
                        Spacer(Modifier.width(20.dp))
                        Chip("나의 약점 구간", bg = Color.White, fg = PleosColor.cautionText)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(card.headline, style = PleosType.headlineS.copy(fontSize = 60.sp, lineHeight = 76.sp))
                    Spacer(Modifier.height(22.dp))
                    Text(card.action, style = PleosType.titleM.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(36.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpeakerIcon(PleosColor.cautionText)
                        Spacer(Modifier.width(14.dp))
                        Text(if (card.voiced) "음성으로 안내하고 있어요" else "다른 음성 안내 중이라 화면으로만 알려 드려요", style = PleosType.bodyM.copy(color = PleosColor.cautionText, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("안내 이유", style = PleosType.label.copy(color = PleosColor.text3))
                    Spacer(Modifier.height(10.dp))
                    Text(card.reason, style = PleosType.bodyL)
                }
            }
        } else {
            // 코칭 대기 화면: 다음 구간만 짧게 표시
            SurfaceCard(Modifier.fillMaxWidth(), bg = PleosColor.activeSoft) {
                Column {
                    Chip("코칭 켜짐", bg = Color.White, fg = Color(0xFF03914C))
                    Spacer(Modifier.height(22.dp))
                    Text("약점 구간에 가까워지면\n음성으로 알려 드릴게요", style = PleosType.titleL)
                }
            }
            val next = state.next
            if (next != null) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("다음 구간", style = PleosType.label.copy(color = PleosColor.text3))
                        Spacer(Modifier.height(10.dp))
                        Text("${next.remainMeters}m · ${com.myroad.coach.data.placeLabel(next.type, next.direction)}", style = PleosType.titleM)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SignalStrip(state)
    }
}

/** 실제로 받고 있는 차량 신호를 작게 보여 줍니다 (PT 시연 시 SDK 연동 확인용). */
@Composable
private fun SignalStrip(state: CoachUiState) {
    val s = state.signals
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(state.source, style = PleosType.caption)
        Chip("브레이크 ${if (s.brake) "ON" else "OFF"}", bg = if (s.brake) PleosColor.negativeSoft else PleosColor.buttonBasic,
            fg = if (s.brake) PleosColor.negative else PleosColor.text2)
        Chip(
            "깜빡이 " + when (s.turnSignal) { SignalSide.LEFT -> "좌"; SignalSide.RIGHT -> "우"; SignalSide.OFF -> "OFF" },
        )
        Chip("조향 ${s.steeringAngle.toInt()}°")
        if (s.night) Chip("야간", bg = PleosColor.autonomousSoft, fg = PleosColor.autonomous)
        if (s.rainy) Chip("우천 추정", bg = PleosColor.positiveSoft, fg = PleosColor.positive)
    }
}

// ═══════════════════════════ 02 정차 후: 오늘의 하이라이트 ═══════════════════════════

@Composable
fun HighlightScreen(state: CoachUiState, onJudge: (Int, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                TripMapCard(state, Modifier.width(620.dp).fillMaxHeight())
                MomentList(state, onJudge, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TripMapCard(state, Modifier.fillMaxWidth().height(380.dp))
                MomentList(state, onJudge, Modifier.weight(1f))
            }
        }
    }
}

private fun momentColor(kind: MomentKind) = when (kind) {
    MomentKind.HARD_BRAKE, MomentKind.LATE_BRAKE -> PleosColor.negative
    MomentKind.NO_SIGNAL -> PleosColor.caution
    MomentKind.SPEEDING -> PleosColor.positive
    MomentKind.SHARP_STEER -> PleosColor.autonomous
}

@Composable
private fun MomentList(state: CoachUiState, onJudge: (Int, Boolean) -> Unit, modifier: Modifier) {
    val counted = state.moments.count { it.judgement != Judgement.EXCLUDED_BY_DRIVER }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    append("오늘 위험 순간 ")
                    withStyle(SpanStyle(color = PleosColor.negative)) { append("${counted}건") }
                },
                style = PleosType.headlineS,
            )
            Spacer(Modifier.width(20.dp))
            if (state.report.tripCount > 0) {
                Text("최근 주행 평균 ${state.report.avgMomentsPerTrip}건", style = PleosType.bodyM.copy(color = PleosColor.text3))
            }
        }
        if (state.highlightIsSample) {
            SampleBadge("예시 주행이에요 · ··· → 시연 시나리오 재생 후 실제 기록으로 바뀌어요")
        } else if (state.tripChecks.isNotEmpty()) {
            val c = state.tripChecks
            Text(
                "이번 주행 판정 ${c.count { it.outcome != Outcome.UNDETERMINED }}건 · 정상 ${c.count { it.outcome == Outcome.OK }} · " +
                    "위험 ${c.count { it.outcome == Outcome.RISK }} · 판정 불가 ${c.count { it.outcome == Outcome.UNDETERMINED }}",
                style = PleosType.bodyS.copy(color = PleosColor.text3),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.moments.isEmpty()) {
                SurfaceCard(Modifier.fillMaxWidth(), bg = PleosColor.activeSoft) {
                    Text("이번 주행에서는 위험 순간이 없었어요.\n좋은 운전이었어요!", style = PleosType.titleS)
                }
            }
            state.moments.forEachIndexed { i, m -> MomentCard(i + 1, m, expanded = i == 0, onJudge = onJudge) }
        }
        Timeline(state)
    }
}

@Composable
private fun MomentCard(index: Int, m: RiskMoment, expanded: Boolean, onJudge: (Int, Boolean) -> Unit) {
    val excluded = m.judgement == Judgement.EXCLUDED_BY_DRIVER
    val color = if (excluded) PleosColor.text4 else momentColor(m.kind)
    SurfaceCard(
        Modifier.fillMaxWidth(),
        bg = if (expanded && !excluded) Color.White else PleosColor.surface,
        borderColor = if (expanded && !excluded) color.copy(alpha = 0.35f) else null,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NumberBadge(index, color)
                Text("${m.time} ${m.kind.label}", style = PleosType.titleM.copy(color = if (excluded) PleosColor.text3 else PleosColor.text1))
                Chip(com.myroad.coach.data.placeLabel(m.roadType, m.direction))
                if (m.rainy) Chip("우천 추정", bg = PleosColor.positiveSoft, fg = PleosColor.positive)
                if (m.lat == null) Chip("위치 확인 불가", bg = PleosColor.buttonBasic, fg = PleosColor.text3)
            }
            Text(
                if (excluded) "돌발 상황으로 표시해 습관 분석에서 제외했어요" else m.cause,
                style = PleosType.bodyM.copy(color = PleosColor.text2),
                modifier = Modifier.padding(start = 70.dp, top = 14.dp),
            )
            if (!excluded && expanded && m.evidence != null) {
                Text(
                    "판정 근거 · ${m.evidence}",
                    style = PleosType.bodyS.copy(color = PleosColor.text3),
                    modifier = Modifier.padding(start = 70.dp, top = 8.dp),
                )
            }
            if (expanded || excluded) {
                Row(Modifier.padding(start = 70.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (excluded) {
                        PleosButton("다시 포함하기") { onJudge(m.id, false) }
                    } else {
                        PleosButton("맞아요") { onJudge(m.id, false) }
                        PleosButton("앞에 돌발 상황이 있었어요") { onJudge(m.id, true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(state: CoachUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.tripStart, style = PleosType.caption)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(PleosColor.buttonBasicPressed))
            val n = state.moments.size.coerceAtLeast(1)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                state.moments.forEachIndexed { i, m ->
                    val x = maxWidth * (0.25f + 0.6f * i / n)
                    Box(
                        Modifier.padding(start = x).size(30.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (m.judgement == Judgement.EXCLUDED_BY_DRIVER) PleosColor.text4 else momentColor(m.kind)),
                    )
                }
            }
        }
        Spacer(Modifier.width(18.dp))
        Text(state.tripEnd, style = PleosType.caption)
    }
}

/** 시연용 샘플 데이터임을 알리는 배지 */
@Composable
fun SampleBadge(text: String = "시연용 샘플 기록 포함", modifier: Modifier = Modifier) {
    Chip(text, bg = PleosColor.cautionSoft, fg = PleosColor.cautionText, modifier = modifier)
}

// ═══════════════════════════ 03 나의 운전 습관 ═══════════════════════════

@Composable
fun HabitScreen(state: CoachUiState) {
    val r = state.report
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        val habits: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("나의 대표 습관", style = PleosType.headlineS.copy(fontSize = 44.sp))
                    Spacer(Modifier.width(18.dp))
                    Text(
                        "최근 4주 · ${r.totalKm.roundToInt()}km · ${r.tripCount}회 주행",
                        style = PleosType.bodyS.copy(color = PleosColor.text3),
                    )
                }
                if (r.sampleTripCount > 0) {
                    SampleBadge("시연용 샘플 기록 ${r.sampleTripCount}회 포함 · ··· 메뉴에서 초기화")
                }
                if (r.habits.none { it.status == HabitStatus.HABIT } && r.habits.any { it.status == HabitStatus.OK }) {
                    SurfaceCard(Modifier.fillMaxWidth(), bg = PleosColor.activeSoft) {
                        Text("기준을 넘는 나쁜 습관이 없어요.\n지금처럼 운전해 주세요!", style = PleosType.titleS)
                    }
                }
                r.habits.forEachIndexed { i, h ->
                    val isHabit = h.status == HabitStatus.HABIT
                    SurfaceCard(Modifier.fillMaxWidth(), bg = if (isHabit) PleosColor.surface else PleosColor.background,
                        borderColor = if (isHabit) null else PleosColor.line) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isHabit) "${i + 1}" else "·",
                                style = PleosType.headlineS.copy(color = if (isHabit) PleosColor.caution else PleosColor.text4),
                                modifier = Modifier.width(52.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(h.title, style = PleosType.titleM)
                                Spacer(Modifier.height(10.dp))
                                Text(h.where, style = PleosType.bodyS.copy(color = PleosColor.text2))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    buildAnnotatedString {
                                        append(h.value)
                                        withStyle(SpanStyle(fontSize = 32.sp)) { append(h.unit) }
                                    },
                                    style = PleosType.headlineS.copy(
                                        fontSize = if (isHabit) 60.sp else 44.sp,
                                        color = if (isHabit) PleosColor.text1 else PleosColor.text3,
                                    ),
                                )
                                Text(
                                    if (h.status == HabitStatus.INSUFFICIENT) "데이터 부족" else "${h.countText} · ${h.metricLabel}",
                                    style = PleosType.caption,
                                )
                            }
                        }
                    }
                }
                val notes = buildList {
                    if (r.excludedCount > 0) add("돌발 상황으로 표시한 ${r.excludedCount}건")
                    if (r.undeterminedCount > 0) add("판정 불가 ${r.undeterminedCount}건")
                }
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" · ") + "은 분모·분자에서 모두 제외했어요", style = PleosType.caption)
                }
            }
        }
        val side: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("약점 구간", style = PleosType.titleM)
                        Text("행동 × 도로 유형 · 같은 행동의 내 평균 대비", style = PleosType.label.copy(color = PleosColor.text3))
                        Spacer(Modifier.height(30.dp))
                        val max = (r.weakRoads.maxOfOrNull { it.ratio } ?: 1f).coerceAtLeast(0.1f)
                        if (r.weakRoads.isEmpty()) {
                            Text("구간마다 ${HabitAnalyzer.MIN_PASSES_FOR_WEAK}번 이상 판정하면 보여 드릴게요", style = PleosType.bodyS.copy(color = PleosColor.text3))
                        }
                        r.weakRoads.forEach { wr ->
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(250.dp)) {
                                    Text(wr.place, style = PleosType.bodyL)
                                    Text("${HabitAnalyzer.behaviorShort(wr.behavior)} ${wr.risk}/${wr.judged}회", style = PleosType.label.copy(color = PleosColor.text3))
                                }
                                Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                                    Box(
                                        Modifier.fillMaxWidth((wr.ratio / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                            .background(if (wr.ratio >= HabitAnalyzer.WEAK_RATIO) PleosColor.caution else PleosColor.text4),
                                    )
                                }
                                Text("${wr.ratio}×", style = PleosType.bodyL.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 18.dp).width(80.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "기준(초기값): ${HabitAnalyzer.MIN_PASSES_FOR_WEAK}회 이상 판정 · 평균의 ${HabitAnalyzer.WEAK_RATIO}배 이상이면 약점",
                            style = PleosType.caption,
                        )
                    }
                }
                if (r.conditions.isNotEmpty()) {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column {
                            Text("조건별 위험 비율", style = PleosType.titleS)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                r.conditions.forEach { c ->
                                    when {
                                        c.startsWith("우천") -> Chip(c, bg = PleosColor.positiveSoft, fg = PleosColor.positive)
                                        c.startsWith("야간") -> Chip(c, bg = PleosColor.autonomousSoft, fg = PleosColor.autonomous)
                                        else -> Chip(c, bg = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                habits(Modifier.weight(1.2f))
                side(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                habits(Modifier.fillMaxWidth())
                side(Modifier.fillMaxWidth())
            }
        }
    }
}

// ═══════════════════════════ 04 주간 성장 리포트 ═══════════════════════════

@Composable
fun GrowthScreen(state: CoachUiState, onAsk: () -> Unit) {
    val r = state.report
    val green = Color(0xFF03914C)
    val focusName = r.focus?.place
    val focusMetric = r.focus?.let { HabitAnalyzer.behaviorShort(it.behavior) } ?: "직전 제동"
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        val stats: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val change = r.focusChangePercent
                SurfaceCard(Modifier.fillMaxWidth(), bg = if (change != null && change > 0) PleosColor.activeSoft else PleosColor.surface) {
                    Column {
                        Text("${focusName ?: "약점"} 구간 $focusMetric 비율", style = PleosType.bodyM.copy(color = PleosColor.text2))
                        Text(
                            when {
                                change == null -> "비교 보류"
                                change >= 0 -> "$change% ↓"
                                else -> "${-change}% ↑"
                            },
                            style = PleosType.headlineS.copy(
                                fontSize = 96.sp, lineHeight = 110.sp,
                                color = if (change != null && change > 0) green else PleosColor.text1,
                            ),
                        )
                        Text(
                            r.focusCompareText ?: "주당 ${HabitAnalyzer.MIN_WEEKLY_SAMPLES}회 이상 판정해야 비교해요",
                            style = PleosType.bodyM.copy(color = PleosColor.text2),
                        )
                    }
                }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "방향지시등 사용률 · 이번 주" + (r.signalUseCountText?.let { " $it" } ?: ""),
                            style = PleosType.bodyM.copy(color = PleosColor.text2),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(r.signalUseRate?.let { "$it%" } ?: "—", style = PleosType.headlineS.copy(fontSize = 72.sp, lineHeight = 86.sp))
                            val d = r.signalUseDelta
                            if (d != null && d != 0) {
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    (if (d > 0) "+" else "") + "${d}%p",
                                    style = PleosType.titleS.copy(color = if (d > 0) green else PleosColor.negative),
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Chip("다음 코칭 목표", bg = Color.White, fg = PleosColor.cautionText)
                        Spacer(Modifier.height(20.dp))
                        Text(r.nextTarget ?: (r.focus?.let { "${it.place} $focusMetric" } ?: "분석 중"), style = PleosType.titleL)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (r.nextTarget != null) "$focusName 구간이 30% 이상 좋아져서\n다음 약점을 함께 연습해요" else "지금 약점 구간을 계속 코칭할게요",
                            style = PleosType.bodyS.copy(color = PleosColor.text3),
                        )
                    }
                }
            }
        }
        val chart: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.clip(CardShape).background(PleosColor.surface).padding(36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$focusMetric 비율(%) · 최근 4주", style = PleosType.titleM, modifier = Modifier.weight(1f))
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(PleosColor.caution))
                    Text(" ${focusName ?: "약점"} 구간   ", style = PleosType.label)
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(PleosColor.text4))
                    Text(" 내 평균", style = PleosType.label)
                }
                if (r.sampleTripCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    SampleBadge()
                }
                WeeklyChart(r.weekly, Modifier.fillMaxWidth().height(420.dp).padding(top = 20.dp))
                Spacer(Modifier.weight(1f))
                // 음성 '인식'(STT)은 쓰지 않습니다. 주간 요약을 화면에 띄우고 음성으로 '읽어 주는' 기능입니다.
                val answer = state.assistantAnswer
                if (answer != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(28.dp)).background(PleosColor.buttonFilled).padding(horizontal = 32.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeakerIcon(Color.White, 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(answer, style = PleosType.bodyL.copy(color = Color.White))
                    }
                    Spacer(Modifier.height(16.dp))
                    PleosButton("다시 듣기", onClick = onAsk)
                } else {
                    PleosButton("이번 주 요약 듣기", filled = true, onClick = onAsk)
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                stats(Modifier.width(560.dp))
                chart(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                stats(Modifier.fillMaxWidth())
                chart(Modifier.fillMaxWidth().height(760.dp))
            }
        }
    }
}

/**
 * 주간 위험 비율(%) 차트. 표본이 부족한 주(null)는 점을 찍지 않고 선을 끊습니다 (0으로 채우지 않음).
 */
@Composable
private fun WeeklyChart(data: List<WeeklyPoint>, modifier: Modifier) {
    if (data.size < 2) return
    val values = data.flatMap { listOfNotNull(it.merge, it.overall) }
    if (values.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("주당 ${HabitAnalyzer.MIN_WEEKLY_SAMPLES}회 이상 판정한 주가 생기면 보여 드릴게요", style = PleosType.bodyM.copy(color = PleosColor.text3))
        }
        return
    }
    // 눈금: 10% 단위로 올림 (최소 20%)
    val maxV = (kotlin.math.ceil(values.max() / 10.0) * 10).toFloat().coerceAtLeast(20f)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(start = 40.dp, bottom = 76.dp, top = 30.dp, end = 30.dp)) {
            val w = size.width
            val h = size.height
            val steps = (maxV / 10).toInt()
            for (g in 0..steps) {
                val y = h - h * (g * 10f / maxV)
                drawLine(Color(0xFFE1E3E7), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            }
            fun pt(i: Int, v: Float) = Offset(w * (0.08f + 0.84f * i / (data.size - 1)), h - h * (v / maxV))
            fun line(get: (WeeklyPoint) -> Float?, color: Color, width: Float) {
                for (i in 1 until data.size) {
                    val a = get(data[i - 1]) ?: continue
                    val b = get(data[i]) ?: continue
                    drawLine(color, pt(i - 1, a), pt(i, b), strokeWidth = width, cap = StrokeCap.Round)
                }
            }
            line({ it.overall }, PleosColor.text4, 6f)
            line({ it.merge }, PleosColor.caution, 8f)
            data.forEachIndexed { i, d ->
                d.overall?.let { drawCircle(PleosColor.text4, 9f, pt(i, it)) }
                d.merge?.let { drawCircle(PleosColor.caution, if (i == data.lastIndex) 14f else 11f, pt(i, it)) }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 40.dp, end = 30.dp), horizontalArrangement = Arrangement.SpaceAround) {
            data.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.label, style = PleosType.label)
                    Text(d.countText ?: "표본 부족", style = PleosType.caption.copy(color = if (d.countText == null) PleosColor.text4 else PleosColor.cautionText))
                }
            }
        }
    }
}
