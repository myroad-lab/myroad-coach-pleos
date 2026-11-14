package com.myroad.coach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myroad.coach.data.GeoPoint
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RiskMoment
import com.myroad.coach.engine.CoachUiState
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * 하이라이트 경로 그림.
 * 기록된 위치 좌표가 있으면 실제 도로 모양 그대로(인터넷 없이 좌표만으로) 그리고,
 * 위험 지점도 같은 좌표로 찍어 항상 경로 선 위에 놓이게 합니다.
 * 좌표가 없으면 기본 곡선을 쓰고, 위험 지점을 선을 따라 일정한 간격으로 배치합니다.
 */
@Composable
fun TripMapCard(state: CoachUiState, modifier: Modifier) {
    val hasRoute = state.route.size >= 2
    Box(modifier.clip(CardShape).background(SKETCH_BG)) {
        RouteSketch(state.route, state.moments, Modifier.fillMaxSize())
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pill = RoundedCornerShape(999.dp)
            Chip(state.tripSummary, bg = Color.White, modifier = with(PleosShadow) { Modifier.level1(pill) })
            when {
                !hasRoute -> Chip("경로 예시", bg = Color.White, fg = PleosColor.text3, modifier = with(PleosShadow) { Modifier.level1(pill) })
                state.highlightIsSample -> Chip("시연 경로", bg = Color.White, fg = PleosColor.text3, modifier = with(PleosShadow) { Modifier.level1(pill) })
            }
            if (state.moments.any { it.rainy }) {
                Chip("우천 추정", bg = Color.White, fg = PleosColor.positive, modifier = with(PleosShadow) { Modifier.level1(pill) })
            }
        }
    }
}

private val SKETCH_BG = Color(0xFFF1F3F5)
private const val MARKER_RADIUS_DP = 18f

private fun momentColor(m: RiskMoment): Color = when {
    m.judgement == Judgement.EXCLUDED_BY_DRIVER -> PleosColor.text4
    m.kind == MomentKind.HARD_BRAKE || m.kind == MomentKind.LATE_BRAKE -> PleosColor.negative
    m.kind == MomentKind.NO_SIGNAL -> PleosColor.caution
    m.kind == MomentKind.SPEEDING -> PleosColor.positive
    else -> PleosColor.autonomous
}

/** 그림 안 픽셀 좌표로 변환된 경로와 위험 지점 */
private class SketchLayout(val path: List<Offset>, val markers: List<Offset>)

@Composable
private fun RouteSketch(route: List<GeoPoint>, moments: List<RiskMoment>, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // 위쪽 칩 영역(약 90dp)과 가장자리 여백을 비우고 그림
        val pad = with(density) { 48.dp.toPx() }
        val top = with(density) { 96.dp.toPx() }
        val layout = layoutSketch(route, moments, left = pad, top = top, right = w - pad, bottom = h - pad)

        val casing = with(density) { 14.dp.toPx() }
        val line = with(density) { 7.dp.toPx() }
        val endR = with(density) { 9.dp.toPx() }
        val markerR = with(density) { MARKER_RADIUS_DP.dp.toPx() }
        val ring = with(density) { 4.dp.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            if (layout.path.size >= 2) {
                val p = Path()
                p.moveTo(layout.path.first().x, layout.path.first().y)
                layout.path.drop(1).forEach { p.lineTo(it.x, it.y) }
                drawPath(p, Color.White, style = Stroke(width = casing, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, PleosColor.positive, style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // 출발(흰 원) · 도착(검은 원)
                drawCircle(Color.White, endR, layout.path.first())
                drawCircle(PleosColor.text1, endR, layout.path.first(), style = Stroke(ring))
                drawCircle(PleosColor.text1, endR, layout.path.last())
            }
            layout.markers.forEachIndexed { i, c ->
                drawCircle(Color.White, markerR + ring, c)
                drawCircle(momentColor(moments[i]), markerR, c)
            }
        }
        // 번호 (원 중앙)
        val box = with(density) { (MARKER_RADIUS_DP * 2).dp.toPx() }
        layout.markers.forEachIndexed { i, c ->
            Box(
                Modifier
                    .offset { IntOffset((c.x - box / 2).roundToInt(), (c.y - box / 2).roundToInt()) }
                    .size((MARKER_RADIUS_DP * 2).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = PleosType.label.copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

private fun layoutSketch(
    route: List<GeoPoint>,
    moments: List<RiskMoment>,
    left: Float, top: Float, right: Float, bottom: Float,
): SketchLayout {
    val boxW = (right - left).coerceAtLeast(1f)
    val boxH = (bottom - top).coerceAtLeast(1f)

    if (route.size >= 2) {
        // 위경도 → 평면 (경도는 위도에 따라 줄여서 실제 모양 유지), 비율을 지키며 상자에 맞춤
        val all = route + moments.mapNotNull { m -> m.lat?.let { lat -> m.lng?.let { GeoPoint(lat, it) } } }
        val midLat = Math.toRadians(all.map { it.lat }.average())
        val k = cos(midLat)
        val xs = all.map { it.lng * k }
        val ys = all.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val scale = minOf(boxW / spanX, boxH / spanY)
        val offX = left + (boxW - spanX * scale) / 2
        val offY = top + (boxH - spanY * scale) / 2
        fun project(p: GeoPoint) = Offset(
            (offX + (p.lng * k - minX) * scale).toFloat(),
            (offY + (maxY - p.lat) * scale).toFloat(), // 북쪽이 위
        )
        val path = route.map(::project)
        val markers = moments.mapIndexed { i, m ->
            val lat = m.lat
            val lng = m.lng
            if (lat != null && lng != null) {
                // 좌표가 있으면 경로 위 가장 가까운 점에 붙여 선 위에 정확히 놓이게 함
                snapToPath(project(GeoPoint(lat, lng)), path)
            } else {
                pointAlong(path, (i + 1f) / (moments.size + 1f))
            }
        }
        return SketchLayout(path, markers)
    }

    // 좌표가 없을 때: 왼쪽 아래 → 오른쪽 위로 이어지는 기본 곡선
    val curve = Path().apply {
        moveTo(left + boxW * 0.12f, top + boxH * 0.95f)
        cubicTo(left + boxW * 0.20f, top + boxH * 0.70f, left + boxW * 0.30f, top + boxH * 0.62f, left + boxW * 0.45f, top + boxH * 0.52f)
        cubicTo(left + boxW * 0.62f, top + boxH * 0.40f, left + boxW * 0.72f, top + boxH * 0.30f, left + boxW * 0.88f, top + boxH * 0.05f)
    }
    val measure = PathMeasure().apply { setPath(curve, false) }
    val len = measure.length
    val samples = (0..60).map { measure.getPosition(len * it / 60f) }
    val markers = moments.indices.map { i -> measure.getPosition(len * (i + 1f) / (moments.size + 1f)) }
    return SketchLayout(samples, markers)
}

/** 경로 선분들 중 가장 가까운 위치로 점을 옮김 */
private fun snapToPath(p: Offset, path: List<Offset>): Offset {
    var best = path.first()
    var bestD = Float.MAX_VALUE
    for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        val t = if (len2 == 0f) 0f else (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f)
        val q = Offset(a.x + ab.x * t, a.y + ab.y * t)
        val d = (q - p).getDistanceSquared()
        if (d < bestD) {
            bestD = d
            best = q
        }
    }
    return best
}

/** 경로 전체 길이 중 [fraction] 위치의 점 */
private fun pointAlong(path: List<Offset>, fraction: Float): Offset {
    val segs = (0 until path.size - 1).map { (path[it + 1] - path[it]).getDistance() }
    var remain = segs.sum() * fraction.coerceIn(0f, 1f)
    for (i in segs.indices) {
        if (remain <= segs[i] || i == segs.lastIndex) {
            val t = if (segs[i] == 0f) 0f else (remain / segs[i]).coerceIn(0f, 1f)
            return path[i] + (path[i + 1] - path[i]) * t
        }
        remain -= segs[i]
    }
    return path.last()
}
