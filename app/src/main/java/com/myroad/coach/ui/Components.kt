package com.myroad.coach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun Chip(text: String, bg: Color = PleosColor.buttonBasic, fg: Color = PleosColor.text2, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PleosType.label.copy(color = fg),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** Pleos 'Basic button': 누르면 배경색이 진해지고 95%로 축소 */
@Composable
fun PleosButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (filled) PleosColor.buttonFilled else PleosColor.buttonBasic,
        pressedColor = if (filled) PleosColor.buttonFilledPressed else PleosColor.buttonBasicPressed,
    ) {
        Text(
            text,
            style = PleosType.bodyM.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (filled) Color.White else PleosColor.text1,
            ),
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 18.dp),
        )
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    bg: Color = PleosColor.surface,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    var m = modifier.clip(CardShape).background(bg)
    if (borderColor != null) m = m.border(2.dp, borderColor, CardShape)
    Box(m.padding(horizontal = 34.dp, vertical = 30.dp)) { content() }
}

@Composable
fun NumberBadge(n: Int, color: Color, size: Dp = 52.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = PleosType.bodyM.copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
    }
}

/** 앱 아이콘 (그라데이션 사각형 + 경로 선) */
@Composable
fun AppMark(size: Dp = 60.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.linearGradient(listOf(PleosColor.active, PleosColor.positive))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.56f)) {
            val cw = this.size.width
            val ch = this.size.height
            val p = Path()
            p.moveTo(0f, ch * 0.9f)
            p.lineTo(cw * 0.32f, ch * 0.1f)
            p.lineTo(cw * 0.58f, ch * 0.62f)
            p.lineTo(cw, ch * 0.08f)
            drawPath(p, Color.White, style = Stroke(width = cw * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** 세그먼트 메뉴 (Pleos segmented menu 컴포넌트 형태) */
@Composable
fun SegmentedMenu(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PleosColor.buttonBasic)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = PleosType.bodyS.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) PleosColor.text1 else PleosColor.text2,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun IconButtonBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = PleosColor.buttonBasic,
        pressedColor = PleosColor.buttonBasicPressed,
        modifier = Modifier.size(72.dp),
    ) { content() }
}

/** 마이크 아이콘 */
@Composable
fun MicIcon(color: Color = PleosColor.text1, size: Dp = 36.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
            style = stroke,
        )
        drawArc(color, 0f, 180f, false, topLeft = Offset(w * 0.2f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.5f), style = stroke)
        drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.9f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

/** 스피커(음성 안내) 아이콘 */
@Composable
fun SpeakerIcon(color: Color, size: Dp = 48.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.38f); lineTo(w * 0.28f, h * 0.38f); lineTo(w * 0.48f, h * 0.2f)
            lineTo(w * 0.48f, h * 0.8f); lineTo(w * 0.28f, h * 0.62f); lineTo(w * 0.12f, h * 0.62f); close()
        }
        val s = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(body, color, style = s)
        drawArc(color, -45f, 90f, false, topLeft = Offset(w * 0.42f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.36f), style = s)
        drawArc(color, -50f, 100f, false, topLeft = Offset(w * 0.4f, h * 0.18f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.64f), style = s)
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))
