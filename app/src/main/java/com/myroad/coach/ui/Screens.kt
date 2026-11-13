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
                Column {
                    Chip("나의 약점 구간", bg = Color.White, fg = PleosColor.cautionText)
                    Spacer(Modifier.height(26.dp))
                    Text(card.headline, style = PleosType.headlineS.copy(fontSize = 60.sp, lineHeight = 76.sp))
                    Spacer(Modifier.height(22.dp))
                    Text(card.action, style = PleosType.titleM.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(36.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpeakerIcon(PleosColor.cautionText)
                        Spacer(Modifier.width(14.dp))
                        Text("음성으로 안내하고 있어요", style = PleosType.bodyM.copy(color = PleosColor.cautionText, fontWeight = FontWeight.SemiBold))
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
                        Text("${next.remainMeters}m · ${next.type.label}", style = PleosType.titleM)
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
        if (s.rainy) Chip("우천", bg = PleosColor.positiveSoft, fg = PleosColor.positive)
    }
}

// ═══════════════════════════ 02 정차 후: 오늘의 하이라이트 ═══════════════════════════

@Composable
fun HighlightScreen(state: CoachUiState, onJudge: (Int, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                TripMap(state.moments, state.tripSummary, Modifier.width(620.dp).fillMaxHeight())
                MomentList(state, onJudge, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TripMap(state.moments, state.tripSummary, Modifier.fillMaxWidth().height(380.dp))
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
private fun TripMap(moments: List<RiskMoment>, summary: String, modifier: Modifier) {
    Box(modifier.clip(CardShape).background(Color(0xFFEEF1EC))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 블록
            val block = Color(0xFFE1E7DC)
            drawRoundRect(block, Offset(w * 0.06f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.14f), androidx.compose.ui.geometry.CornerRadius(12f))
            drawRoundRect(block, Offset(w * 0.64f, h * 0.42f), androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(12f))
            drawRoundRect(block, Offset(w * 0.09f, h * 0.64f), androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.15f), androidx.compose.ui.geometry.CornerRadius(12f))
            // 경로
            val route = Path().apply {
                moveTo(w * 0.16f, h * 0.90f)
                cubicTo(w * 0.24f, h * 0.76f, w * 0.30f, h * 0.70f, w * 0.40f, h * 0.62f)
                cubicTo(w * 0.52f, h * 0.52f, w * 0.60f, h * 0.48f, w * 0.65f, h * 0.40f)
                cubicTo(w * 0.72f, h * 0.28f, w * 0.80f, h * 0.20f, w * 0.85f, h * 0.10f)
            }
            drawPath(route, Color.White, style = Stroke(width = 44f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, PleosColor.positive, style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color.White, 16f, Offset(w * 0.16f, h * 0.90f))
            drawCircle(PleosColor.text1, 16f, Offset(w * 0.16f, h * 0.90f), style = Stroke(6f))
            drawCircle(PleosColor.text1, 16f, Offset(w * 0.85f, h * 0.10f))
            moments.forEach { m ->
                val c = if (m.judgement == Judgement.EXCLUDED_BY_DRIVER) PleosColor.text4 else momentColor(m.kind)
                drawCircle(Color.White, 38f, Offset(w * m.mapX, h * m.mapY))
                drawCircle(c, 32f, Offset(w * m.mapX, h * m.mapY))
            }
        }
        // 번호
        BoxWithConstraints(Modifier.fillMaxSize()) {
            moments.forEachIndexed { i, m ->
                Box(
                    Modifier
                        .padding(start = maxWidth * m.mapX - 13.dp, top = maxHeight * m.mapY - 17.dp)
                        .size(26.dp, 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = PleosType.label.copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(summary, bg = Color.White)
            if (moments.any { it.rainy }) Chip("우천", bg = Color.White, fg = PleosColor.positive)
        }
    }
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
            Text(SampleData.averageMoments, style = PleosType.bodyM.copy(color = PleosColor.text3))
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
                Chip(m.roadType.label)
                if (m.rainy) Chip("우천", bg = PleosColor.positiveSoft, fg = PleosColor.positive)
            }
            Text(
                if (excluded) "돌발 상황으로 표시해 습관 분석에서 제외했어요" else m.cause,
                style = PleosType.bodyM.copy(color = PleosColor.text2),
                modifier = Modifier.padding(start = 70.dp, top = 14.dp),
            )
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

// ═══════════════════════════ 03 나의 운전 습관 ═══════════════════════════

@Composable
fun HabitScreen(state: CoachUiState) {
    val excluded = state.moments.count { it.judgement == Judgement.EXCLUDED_BY_DRIVER }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        val habits: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("나의 대표 습관", style = PleosType.headlineS.copy(fontSize = 44.sp))
                    Spacer(Modifier.width(18.dp))
                    Text("최근 4주 · 612km · 31회 주행", style = PleosType.bodyS.copy(color = PleosColor.text3))
                }
                SampleData.habits.forEachIndexed { i, h ->
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = PleosType.headlineS.copy(color = PleosColor.caution), modifier = Modifier.width(52.dp))
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
                                    style = PleosType.headlineS.copy(fontSize = 60.sp),
                                )
                                Text(h.metricLabel, style = PleosType.caption)
                            }
                        }
                    }
                }
                if (excluded > 0) {
                    Text("돌발 상황으로 표시한 기록 ${excluded}건은 분석에서 제외했어요", style = PleosType.caption)
                }
            }
        }
        val side: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("약점 도로 유형", style = PleosType.titleM)
                        Text("위험 순간 비율 · 나의 평균 대비", style = PleosType.label.copy(color = PleosColor.text3))
                        Spacer(Modifier.height(30.dp))
                        val max = SampleData.weakRoads.maxOf { it.ratio }
                        SampleData.weakRoads.forEach { r ->
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.type.label, style = PleosType.bodyL, modifier = Modifier.width(190.dp))
                                Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                                    Box(
                                        Modifier.fillMaxWidth(r.ratio / max).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                            .background(if (r.ratio >= 1.5f) PleosColor.caution else PleosColor.text4),
                                    )
                                }
                                Text("${r.ratio}×", style = PleosType.bodyL.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 18.dp).width(80.dp))
                            }
                        }
                    }
                }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("조건별 위험 순간", style = PleosType.titleS)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Chip(SampleData.conditions[0], bg = PleosColor.positiveSoft, fg = PleosColor.positive)
                            Chip(SampleData.conditions[1], bg = PleosColor.autonomousSoft, fg = PleosColor.autonomous)
                            Chip(SampleData.conditions[2], bg = Color.White)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        val stats: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SurfaceCard(Modifier.fillMaxWidth(), bg = PleosColor.activeSoft) {
                    Column {
                        Text("합류 구간 급제동", style = PleosType.bodyM.copy(color = PleosColor.text2))
                        Text("40% ↓", style = PleosType.headlineS.copy(fontSize = 96.sp, lineHeight = 110.sp, color = Color(0xFF03914C)))
                        Text("지난주보다 줄었어요 · 코칭 12회", style = PleosType.bodyM.copy(color = PleosColor.text2))
                    }
                }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("방향지시등 사용률 (회전·분기)", style = PleosType.bodyM.copy(color = PleosColor.text2))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("78%", style = PleosType.headlineS.copy(fontSize = 72.sp, lineHeight = 86.sp))
                            Spacer(Modifier.width(14.dp))
                            Text("+16%p", style = PleosType.titleS.copy(color = Color(0xFF03914C)), modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Chip("다음 코칭 목표", bg = Color.White, fg = PleosColor.cautionText)
                        Spacer(Modifier.height(20.dp))
                        Text("비 오는 날 회전교차로", style = PleosType.titleL)
                        Spacer(Modifier.height(8.dp))
                        Text("합류 구간이 좋아져서\n다음 약점으로 바꿨어요", style = PleosType.bodyS.copy(color = PleosColor.text3))
                    }
                }
            }
        }
        val chart: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.clip(CardShape).background(PleosColor.surface).padding(36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("100km당 급제동 · 최근 4주", style = PleosType.titleM, modifier = Modifier.weight(1f))
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(PleosColor.caution))
                    Text(" 합류 구간   ", style = PleosType.label)
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(PleosColor.text4))
                    Text(" 전체", style = PleosType.label)
                }
                WeeklyChart(Modifier.fillMaxWidth().height(420.dp).padding(top = 20.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MicIcon(PleosColor.text2, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("“이번 주 어땠어?”", style = PleosType.bodyL)
                    }
                }
                val answer = state.assistantAnswer
                if (answer != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        answer,
                        style = PleosType.bodyL.copy(color = Color.White),
                        modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(PleosColor.buttonFilled).padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    PleosButton("마이크를 누르고 물어보기", filled = true, onClick = onAsk)
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

@Composable
private fun WeeklyChart(modifier: Modifier) {
    val data = SampleData.weekly
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(start = 40.dp, bottom = 40.dp, top = 30.dp, end = 30.dp)) {
            val w = size.width
            val h = size.height
            val maxV = 6f
            for (g in 0..3) {
                val y = h - h * (g * 2f / maxV)
                drawLine(Color(0xFFE1E3E7), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            }
            fun pt(i: Int, v: Float) = Offset(w * (0.08f + 0.84f * i / (data.size - 1)), h - h * (v / maxV))
            val overall = Path().apply { data.forEachIndexed { i, d -> if (i == 0) moveTo(pt(i, d.overall).x, pt(i, d.overall).y) else lineTo(pt(i, d.overall).x, pt(i, d.overall).y) } }
            val merge = Path().apply { data.forEachIndexed { i, d -> if (i == 0) moveTo(pt(i, d.merge).x, pt(i, d.merge).y) else lineTo(pt(i, d.merge).x, pt(i, d.merge).y) } }
            drawPath(overall, PleosColor.text4, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(merge, PleosColor.caution, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            data.forEachIndexed { i, d ->
                drawCircle(PleosColor.text4, 9f, pt(i, d.overall))
                drawCircle(PleosColor.caution, if (i == data.lastIndex) 14f else 11f, pt(i, d.merge))
            }
        }
        // 축 라벨
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 40.dp, end = 30.dp), horizontalArrangement = Arrangement.SpaceAround) {
            data.forEach { Text(it.label, style = PleosType.label) }
        }
        Text("${data.first().merge}", style = PleosType.label.copy(color = PleosColor.cautionText), modifier = Modifier.align(Alignment.TopStart).padding(start = 70.dp))
        Text("${data.last().merge}", style = PleosType.label.copy(color = PleosColor.cautionText), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp, top = 40.dp))
    }
}
