package com.myroad.coach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ───────────────────────── Interaction ─────────────────────────

/**
 * Pleos 가이드 'Button interaction': 모든 터치 요소는 누르는 동안 95%로 축소(shrink)됩니다.
 * 손가락이 영역 밖으로 나가면 취소됩니다 (clickable 기본 동작).
 */
fun Modifier.pleosPress(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "pleosPress")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** 누름 상태에 따라 배경색이 바뀌고 95%로 축소되는 컨테이너 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    shape: Shape,
    color: Color,
    pressedColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "pressable")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (pressed) pressedColor else color)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ───────────────────────── Shadow ─────────────────────────

/** Pleos 가이드 Shadow Level 1~3 (Y 6/8/20, Blur 24/32/40, 8~24%)를 Compose 그림자로 근사 */
object PleosShadow {
    fun Modifier.level1(shape: Shape): Modifier =
        shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))

    fun Modifier.level2(shape: Shape): Modifier =
        shadow(10.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x29000000))

    fun Modifier.level3(shape: Shape): Modifier =
        shadow(20.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x3D000000))
}

// ───────────────────────── Icons ─────────────────────────

@Composable
fun CaretDownIcon(color: Color = PleosColor.text1, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path()
        p.moveTo(w * 0.18f, h * 0.36f)
        p.lineTo(w * 0.82f, h * 0.36f)
        p.lineTo(w * 0.5f, h * 0.72f)
        p.close()
        drawPath(p, color)
    }
}

@Composable
fun MoreIcon(color: Color = PleosColor.text1, size: Dp = 36.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.width * 0.075f
        val cy = this.size.height / 2
        listOf(0.22f, 0.5f, 0.78f).forEach { x -> drawCircle(color, r, Offset(this.size.width * x, cy)) }
    }
}

@Composable
fun BackIcon(color: Color = PleosColor.text1, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path()
        p.moveTo(w * 0.62f, h * 0.18f)
        p.lineTo(w * 0.3f, h * 0.5f)
        p.lineTo(w * 0.62f, h * 0.82f)
        drawPath(p, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

/** 주행 알림용 경고 아이콘 (원 + 느낌표) */
@Composable
fun CautionIcon(bg: Color = PleosColor.caution, size: Dp = 64.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            drawLine(Color.White, Offset(w / 2, h * 0.12f), Offset(w / 2, h * 0.6f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
            drawCircle(Color.White, w * 0.09f, Offset(w / 2, h * 0.86f))
        }
    }
}

// ───────────────────────── Page Navigation ─────────────────────────

/**
 * Pleos 가이드 'Page Navigation'
 *  Main  : 앱 아이콘 + 제목▼(Primary menu: 페이지 전환) + Suffix 아이콘
 *  Depth : 뒤로가기 + 제목 + Suffix 아이콘
 */
@Composable
fun PageNavigation(
    title: String,
    pages: List<String>,
    onSelectPage: ((Int) -> Unit)?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    suffix: @Composable () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(128.dp).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(72.dp).pleosPress(onBack), contentAlignment = Alignment.Center) { BackIcon() }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(with(PleosShadow) { Modifier.level1(RoundedCornerShape(16.dp)) }) { AppMark(68.dp) }
            Spacer(Modifier.width(28.dp))
        }
        Box {
            val clickable = onSelectPage != null && pages.size > 1
            Row(
                if (clickable) Modifier.pleosPress { expanded = true } else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = PleosType.titleL)
                if (clickable) {
                    Spacer(Modifier.width(10.dp))
                    CaretDownIcon()
                }
            }
            if (clickable) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    pages.forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    style = PleosType.bodyL.copy(fontWeight = if (label == title) FontWeight.Bold else FontWeight.Normal),
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelectPage?.invoke(i)
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { suffix() }
    }
}

/** Suffix 영역의 아이콘 버튼 (72dp 터치 영역) */
@Composable
fun SuffixIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        pressedColor = PleosColor.buttonBasicPressed,
        modifier = Modifier.size(72.dp),
    ) { content() }
}

/** '···' 메뉴: 항목을 누르면 해당 동작 실행 */
@Composable
fun MoreMenu(items: List<Pair<String, () -> Unit>>, footer: List<String> = emptyList()) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SuffixIcon(onClick = { expanded = true }) { MoreIcon() }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = PleosType.bodyL, modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            if (footer.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    footer.forEach { Text(it, style = PleosType.caption) }
                }
            }
        }
    }
}

// ───────────────────────── Toast ─────────────────────────

/** Pleos 가이드 'Toast': 앱 패널 하단, 좌우·하단 여백 24, 최대 폭 792 (메시지 712) */
@Composable
fun PleosToast(message: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .widthIn(max = 792.dp)
            .then(with(PleosShadow) { Modifier.level2(shape) })
            .clip(shape)
            .background(PleosColor.buttonFilled)
            .padding(horizontal = 40.dp, vertical = 26.dp),
    ) {
        Text(message, style = PleosType.bodyL.copy(color = Color.White), modifier = Modifier.widthIn(max = 712.dp))
    }
}
