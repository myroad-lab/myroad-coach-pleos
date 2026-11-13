package com.myroad.coach.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myroad.coach.data.GeoPoint
import com.myroad.coach.data.Judgement
import com.myroad.coach.data.MomentKind
import com.myroad.coach.data.RiskMoment
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * 실제 지도 (MapLibre + OpenFreeMap 'positron' 밝은 회색 스타일, OpenStreetMap 데이터).
 * 경로는 Pleos informative_positive 파란색, 위험 지점은 하이라이트 목록과 같은 색·번호로 표시합니다.
 * 지도 타일을 불러오지 못하면 [onLoadFailed]가 호출되어 기존 도식 지도로 대체됩니다.
 */
@Composable
fun RealTripMap(
    route: List<GeoPoint>,
    moments: List<RiskMoment>,
    modifier: Modifier = Modifier,
    onLoadFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder() }
    val mapView = remember { createMapView(context, holder, onLoadFailed) }

    // MapView 생명주기를 화면 생명주기에 맞춤
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { holder.show(route, moments) },
    )
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val SRC_ROUTE = "myroad-route"
private const val SRC_ENDS = "myroad-ends"
private const val SRC_MOMENTS = "myroad-moments"

/** 지도·스타일이 준비되기 전에 들어온 데이터를 보관했다가 준비되면 그립니다. */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    private var pendingRoute: List<GeoPoint> = emptyList()
    private var pendingMoments: List<RiskMoment> = emptyList()
    private var shownKey: Int? = null

    fun show(route: List<GeoPoint>, moments: List<RiskMoment>) {
        pendingRoute = route
        pendingMoments = moments
        render()
    }

    fun render() {
        val st = style ?: return
        val m = map ?: return
        val key = pendingRoute.hashCode() * 31 + pendingMoments.hashCode()
        if (key == shownKey) return
        shownKey = key

        st.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(routeGeoJson(pendingRoute))
        st.getSourceAs<GeoJsonSource>(SRC_ENDS)?.setGeoJson(endsGeoJson(pendingRoute))
        st.getSourceAs<GeoJsonSource>(SRC_MOMENTS)?.setGeoJson(momentsGeoJson(pendingMoments))

        // 경로와 위험 지점이 모두 보이도록 화면 맞춤
        val all = pendingRoute.map { LatLng(it.lat, it.lng) } +
            pendingMoments.mapNotNull { mo -> mo.lat?.let { LatLng(it, mo.lng ?: return@mapNotNull null) } }
        if (all.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(all).build()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        } else if (all.size == 1) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), 14.0))
        }
    }
}

private fun createMapView(context: Context, holder: MapHolder, onLoadFailed: () -> Unit): MapView {
    MapLibre.getInstance(context)
    val options = MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true) // 둥근 모서리 카드 안에서 잘리도록 TextureView 사용
        .logoEnabled(false)
        .compassEnabled(false)
        .attributionEnabled(true) // OpenStreetMap / OpenFreeMap 출처 표시 (필수)
    val view = MapView(context, options)
    view.onCreate(null)
    view.addOnDidFailLoadingMapListener { onLoadFailed() }
    view.getMapAsync { map ->
        holder.map = map
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
            addLayers(style)
            holder.style = style
            holder.render()
        }
    }
    return view
}

private fun addLayers(style: Style) {
    val empty = """{"type":"FeatureCollection","features":[]}"""
    style.addSource(GeoJsonSource(SRC_ROUTE, empty))
    style.addSource(GeoJsonSource(SRC_ENDS, empty))
    style.addSource(GeoJsonSource(SRC_MOMENTS, empty))

    // 경로: 흰 테두리 + Pleos 파란 선
    style.addLayer(
        LineLayer("myroad-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(11f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("myroad-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#0064FF"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    // 출발(흰 원) · 도착(검은 원)
    style.addLayer(
        CircleLayer("myroad-ends", SRC_ENDS).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(Expression.get("fill")),
            PropertyFactory.circleStrokeColor("#121417"),
            PropertyFactory.circleStrokeWidth(4f),
        ),
    )
    // 위험 지점: 색 원 + 번호
    style.addLayer(
        CircleLayer("myroad-moments-circle", SRC_MOMENTS).withProperties(
            PropertyFactory.circleRadius(17f),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4f),
        ),
    )
    style.addLayer(
        SymbolLayer("myroad-moments-label", SRC_MOMENTS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(16f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
}

private fun routeGeoJson(route: List<GeoPoint>): String {
    val features = JSONArray()
    if (route.size >= 2) {
        val coords = JSONArray()
        route.forEach { coords.put(JSONArray().put(it.lng).put(it.lat)) }
        features.put(feature(JSONObject().put("type", "LineString").put("coordinates", coords), JSONObject()))
    }
    return collection(features)
}

private fun endsGeoJson(route: List<GeoPoint>): String {
    val features = JSONArray()
    route.firstOrNull()?.let { features.put(point(it.lat, it.lng, JSONObject().put("fill", "#FFFFFF"))) }
    if (route.size >= 2) route.last().let { features.put(point(it.lat, it.lng, JSONObject().put("fill", "#121417"))) }
    return collection(features)
}

private fun momentsGeoJson(moments: List<RiskMoment>): String {
    val features = JSONArray()
    moments.forEachIndexed { i, m ->
        val lat = m.lat ?: return@forEachIndexed
        val lng = m.lng ?: return@forEachIndexed
        val color = when {
            m.judgement == Judgement.EXCLUDED_BY_DRIVER -> "#B9BDC4"
            m.kind == MomentKind.HARD_BRAKE || m.kind == MomentKind.LATE_BRAKE -> "#FE3D16"
            m.kind == MomentKind.NO_SIGNAL -> "#F29A00"
            m.kind == MomentKind.SPEEDING -> "#0064FF"
            else -> "#5A46FA"
        }
        features.put(point(lat, lng, JSONObject().put("color", color).put("label", "${i + 1}")))
    }
    return collection(features)
}

private fun point(lat: Double, lng: Double, props: JSONObject) =
    feature(JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lng).put(lat)), props)

private fun feature(geometry: JSONObject, props: JSONObject) =
    JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props)

private fun collection(features: JSONArray) =
    JSONObject().put("type", "FeatureCollection").put("features", features).toString()
