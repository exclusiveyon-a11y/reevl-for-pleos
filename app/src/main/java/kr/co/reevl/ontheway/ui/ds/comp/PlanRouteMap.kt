package kr.co.reevl.ontheway.ui.ds.comp

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.co.reevl.ontheway.plan.PlanGeo
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

// 동선 지도 (web /plan PlanMap.tsx, car version). A real basemap — MapLibre Native (OpenGL ES) with OpenFreeMap vector tiles, the
// same keyless source and palette as the web ([PlanMapStyle]) — under numbered pins joined by STRAIGHT lines (there is no route API;
// the navigation knows the road). Pins + lines are a Compose overlay placed with the map's own projection, so they look exactly
// like the list's order marks. The map does not move by hand (car: no accidental pans; the camera fits the stops).
// No GL / style failure → the same overlay on a flat field with one line saying so — never a blank box.
// No animation (Pleos: 자동 애니메이션 금지): the camera jumps, it does not fly.

/** One stop on the map. [n] = the number shown (= the list's order mark). */
data class RoutePin(val n: Int, val name: String, val lat: Double?, val lng: Double?,
                    val next: Boolean = false, val done: Boolean = false, val skipped: Boolean = false)

/**
 * [dashed] = a proposal (순서 정리 미리보기), like the web's dashed preview line.
 * [padBottom] = extra fit padding at the bottom (something drawn over the map's lower edge).
 */
data class PlanRouteSpec(val pins: List<RoutePin>, val dashed: Boolean = false, val tag: String = "plan_map", val padBottom: Int = 0)

@Composable
fun PlanRouteMap(s: PlanRouteSpec, modifier: Modifier = Modifier, shape: RoundedCornerShape? = RoundedCornerShape(CarSpace.radiusS)) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val located = s.pins.map { p -> if (PlanGeo.hasLL(p.lat, p.lng)) PlanGeo.LL(p.lat!!, p.lng!!) else null }
    // Screen points from the map's projection once the camera is fitted; null = the map is not ready (or failed) → own fit.
    var projected by remember(s.pins) { mutableStateOf<List<Pair<Float, Float>?>?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Under the driving lock the GL map is not composed at all (its DisposableEffect pauses, stops and destroys the MapView):
    // nothing renders or fetches tiles behind the lock; it comes back, re-fitted, when the lock lifts.
    val useMap = !LocalInspectionMode.current && !failed && !LocalDrivingLock.current
    val label = s.pins.filter { PlanGeo.hasLL(it.lat, it.lng) }.joinToString(", ") { "${it.n} ${it.name}" }
    val base = modifier.then(if (shape != null) Modifier.background(r.chip, shape).clipToBounds() else Modifier.background(r.chip))
        .tag(s.tag).semantics { contentDescription = "동선 지도: $label" }
    androidx.compose.foundation.layout.BoxWithConstraints(base) {
        // Small maps (narrow window, the 순서 정리 pair) fit tighter: a big pad zoomed them out to the whole city (loop round 2).
        val small = minOf(maxWidth, maxHeight) < CarSize.Panel.mapSmall
        if (useMap) BaseMap(dark = r.dark, points = located, padPx = with(density) { (if (small) 44.dp else CarSpace.orderBadge + 28.dp).toPx() }.toInt(), // layout: map fit pad (small map / pin + room)
            padBottomPx = with(density) { (s.padBottom.dp + 36.dp).toPx() }.toInt(), // layout: attribution room
            onProjected = { projected = it }, onFailed = { failed = true; projected = null })
        Canvas(Modifier.fillMaxSize()) {
            // Small maps: 44 dp pins instead of 56 so 5 stops in a town do not pile up (loop round 1).
            val pinR = (if (small) CarSize.Route.pinSmall else CarSpace.orderBadge / 2).toPx()
            val nextR = pinR + CarSize.Route.nextRing.toPx()
            val pts = projected ?: PlanGeo.fit(located, size.width, size.height, nextR + 20.dp.toPx()) // layout: fit pad when MapLibre is off
            drawRoute(s, pts, r, t.value.copy(fontWeight = FontWeight.Medium), measurer, pinR, nextR)
        }
        // Required attribution (OpenFreeMap · © OpenStreetMap), on the map, small and quiet; or why there is no map.
        Text(if (projected != null) PlanMapStyle.ATTRIBUTION else if (failed) "지도를 못 불러왔어요" else "",
            style = t.caption.copy(color = r.sub), // caption = 24 sp floor (ReevlCarTheme.MIN_TYPE_SP); was × 0.75 = 18 sp
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = CarSpace.block, vertical = CarSpace.tight)
                .background(r.surface.copy(alpha = if (projected != null || failed) 0.72f else 0f), RoundedCornerShape(6.dp)) // layout: attribution label
                .padding(horizontal = CarSpace.item, vertical = CarSpace.hair).tag("${s.tag}_attrib"))
    }
}

/** Lines (driven part quiet, the rest brand; dashed for a proposal), then the pins — next on top, close pins spread apart. */
private fun DrawScope.drawRoute(s: PlanRouteSpec, raw: List<Pair<Float, Float>?>, r: CarRoles, numStyle: TextStyle, measurer: TextMeasurer,
                                pinR: Float, nextR: Float) {
    val located = s.pins.indices.filter { raw[it] != null }
    val lineW = CarSize.Route.line.toPx()
    val dash = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(CarSize.Route.dash.toPx(), CarSize.Route.dashGap.toPx())) else null
    // White-ish casing under the line so it reads on any basemap (web: route-case under route).
    for (k in 0 until located.size - 1) {
        val a = raw[located[k]]!!; val b = raw[located[k + 1]]!!
        drawLine(r.surface, Offset(a.first, a.second), Offset(b.first, b.second), lineW + CarSize.Route.casing.toPx(), StrokeCap.Round)
    }
    for (k in 0 until located.size - 1) {
        val a = raw[located[k]]!!; val b = raw[located[k + 1]]!!
        val to = s.pins[located[k + 1]]
        val c = if (to.done || to.skipped) r.sub.copy(alpha = 0.6f) else r.brand
        drawLine(c, Offset(a.first, a.second), Offset(b.first, b.second), lineW, StrokeCap.Round, dash)
    }
    // Close stops: pins move apart (PlanGeo.spread) and keep a dot + leader to where they really are.
    val pts = PlanGeo.spread(raw, 2 * nextR + 4.dp.toPx()) // layout: min gap between spread pins
    for (i in located) {
        val (x0, y0) = raw[i]!!; val (x, y) = pts[i]!!
        if (kotlin.math.hypot(x - x0, y - y0) > 2f) {
            drawLine(r.ink2, Offset(x0, y0), Offset(x, y), CarSize.Stroke.thin.toPx())
            drawCircle(r.ink2, 5.dp.toPx(), Offset(x0, y0)) // layout: true-position dot
        }
    }
    val order = located.sortedBy { if (s.pins[it].next) 1 else 0 }
    for (i in order) {
        val p = s.pins[i]; val (x, y) = pts[i]!!
        val c = Offset(x, y)
        val rr = if (p.next) nextR else pinR
        val ink = when { p.next -> r.onPrimary; p.done -> r.posText; p.skipped -> r.sub; else -> r.ink }
        if (p.next) {
            drawCircle(r.surface, rr + CarSize.Route.pinHalo.toPx(), c)
            drawCircle(r.primaryFace, rr, c)
        } else {
            drawCircle(r.surface, rr, c)
            drawCircle(when { p.done -> r.posText; p.skipped -> r.line; else -> r.lineStrong }, rr - CarSize.Stroke.focus.toPx() / 2, c, style = Stroke(CarSize.Stroke.focus.toPx()))
        }
        val txt = measurer.measure("${p.n}", numStyle.copy(color = ink), maxLines = 1)
        drawText(txt, topLeft = Offset(x - txt.size.width / 2f, y - txt.size.height / 2f))
    }
}

/**
 * The MapLibre view: inline style (no style request), gestures off, MapLibre's own logo / ⓘ off (we print the attribution as text),
 * TextureView so it composes and screenshots like any view. The camera fits [points] with [padPx] (+ [padBottomPx] below), max zoom
 * 16; then [onProjected] gets every point in view pixels. Lifecycle follows the screen.
 */
@Composable
private fun BaseMap(dark: Boolean, points: List<PlanGeo.LL?>, padPx: Int, padBottomPx: Int,
                    onProjected: (List<Pair<Float, Float>?>) -> Unit, onFailed: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<MapLibreMap>(1) }
    val latest = remember { arrayOfNulls<Any>(2) }
    latest[0] = points; latest[1] = onProjected
    val view = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true).logoEnabled(false).attributionEnabled(false)
            .compassEnabled(false).localIdeographFontFamily("sans-serif")).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onCreate(null)
            addOnDidFailLoadingMapListener { onFailed() }
        }
    }
    fun project(map: MapLibreMap) {
        @Suppress("UNCHECKED_CAST") val pts = latest[0] as List<PlanGeo.LL?>
        @Suppress("UNCHECKED_CAST") val cb = latest[1] as (List<Pair<Float, Float>?>) -> Unit
        cb(pts.map { p -> p?.let { map.projection.toScreenLocation(LatLng(it.lat, it.lng)).let { s -> s.x to s.y } } })
    }
    fun fit(map: MapLibreMap) {
        @Suppress("UNCHECKED_CAST") val pts = (latest[0] as List<PlanGeo.LL?>).filterNotNull()
        if (pts.isEmpty() || view.width == 0) return
        val pad = intArrayOf(padPx, padPx, padPx, padPx + padBottomPx)
        val cam = if (pts.size == 1) null else map.getCameraForLatLngBounds(LatLngBounds.Builder().includes(pts.map { LatLng(it.lat, it.lng) }).build(), pad)
        if (cam != null) map.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
        else map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].lat, pts[0].lng), 15.0))
        if (map.cameraPosition.zoom > 16.0) map.moveCamera(CameraUpdateFactory.zoomTo(16.0))
        project(map)
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            view.onPause(); view.onStop(); view.onDestroy()
        }
    }
    DisposableEffect(dark) {
        view.getMapAsync { map ->
            holder[0] = map
            map.uiSettings.setAllGesturesEnabled(false)
            map.setStyle(Style.Builder().fromJson(PlanMapStyle.json(dark))) { fit(map) }
        }
        onDispose { }
    }
    DisposableEffect(points) { holder[0]?.let { if (it.style?.isFullyLoaded == true) fit(it) }; onDispose { } }
    DisposableEffect(view) {
        // A window resize (1:1 ↔ 2:0, narrow) refits.
        val l = android.view.View.OnLayoutChangeListener { _, l, tp, rt, b, ol, ot, orr, ob ->
            if (rt - l != orr - ol || b - tp != ob - ot) holder[0]?.let { m -> if (m.style?.isFullyLoaded == true) fit(m) }
        }
        view.addOnLayoutChangeListener(l)
        onDispose { view.removeOnLayoutChangeListener(l) }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}
