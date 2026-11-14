package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

// 동네 히트맵 (area-context-car-spec §3.2): REEVL web heatmap colours on a 5×5 grid of the 동 around the car — no map SDK.
// Flat (no shadow, no card), corner = radiusS like badges; text Regular name + Medium number; no animation (Pleos: 자동 애니메이션 금지).

/** One 동 tile. [col]/[row] in −r..r. [guLevel] = the 동 has no index; its colour is the 구's (drawn at 55 %). */
data class AreaTileSpec(val key: String, val name: String, val value: String, val bg: Color, val fg: Color, val col: Int, val row: Int,
                        val current: Boolean, val guLevel: Boolean)

data class AreaMapSpec(
    val tiles: List<AreaTileSpec>,
    val radius: Int,
    /** true = ahead is up (car heading known); false = north up. Stated in [basis]. */
    val headingUp: Boolean,
    val selectedKey: String?,
    val onSelect: (String) -> Unit,
    val periods: List<String>,
    val period: Int,
    val onPeriod: (Int) -> Unit,
    /** Legend ends (`−20%` · `+20%`) and the stops (blue → neutral → red). */
    val legendMin: String,
    val legendMax: String,
    val legend: List<Color>,
    val basis: String,
)

/** Page header of 동네: region line, the 동 in display type, then the main number in up/down colour. */
data class AreaHeroSpec(val region: String, val place: String, val value: String, val dir: Int, val periods: String?)

@Composable
fun AreaHero(s: AreaHeroSpec) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(Modifier.fillMaxWidth().padding(top = CarSpace.item, bottom = CarSpace.gapTight).tag("area_hero"), verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
        Text(s.region, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
            Text(s.place, style = t.display.copy(color = r.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("area_place"))
            Text(s.value, style = t.display.copy(color = when { s.dir > 0 -> r.upText; s.dir < 0 -> r.downText; else -> r.ink2 }, fontWeight = FontWeight.Medium),
                maxLines = 1, modifier = Modifier.tag("area_value"))
        }
        // 2 lines: a narrow window cut `수정구 1년 +1…` mid-number (QA 2026-11-10, N_N_AREA).
        s.periods?.let { Text(it, style = t.rowSecondary.copy(color = r.ink2), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("area_periods")) }
    }
}

@Composable
fun AreaMap(s: AreaMapSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val measurer = rememberTextMeasurer()
    val n = s.radius * 2 + 1
    val nameStyle = t.caption.copy(textAlign = TextAlign.Center)                                   // 24 sp Regular
    val valueStyle = t.rowSecondary.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) // 26 sp Medium, tnum
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        PeriodToggle(s.periods, s.period, s.onPeriod)
        Canvas(Modifier.widthIn(max = CarSize.Measure.areaMap).fillMaxWidth().aspectRatio(1f).tag("area_map")
            .semantics { contentDescription = s.tiles.joinToString(", ") { "${it.name} ${it.value}" } }
            .pointerInput(s.tiles, s.radius) {
                detectTapGestures { p ->
                    val cell = size.width / n.toFloat()
                    val c = floor(p.x / cell).toInt() - s.radius
                    val w = floor(p.y / cell).toInt() - s.radius
                    s.tiles.firstOrNull { it.col == c && it.row == w }?.let { s.onSelect(it.key) }
                }
            }) {
            val cell = size.width / n
            val gap = CarSpace.tight.toPx()
            val rad = CornerRadius(CarSpace.radiusS.toPx() / 2, CarSpace.radiusS.toPx() / 2)
            for (tile in s.tiles) {
                val x = (tile.col + s.radius) * cell + gap / 2
                val y = (tile.row + s.radius) * cell + gap / 2
                val sz = cell - gap
                drawRoundRect(tile.bg.copy(alpha = if (tile.guLevel) 0.55f else 1f), Offset(x, y), Size(sz, sz), rad)
                val sel = tile.key == s.selectedKey
                if (tile.current || sel) {
                    val sw = (if (tile.current) CarSize.Stroke.heavy else CarSize.Stroke.focus).toPx()
                    drawRoundRect(if (tile.current) r.ink else r.brand, Offset(x + sw / 2, y + sw / 2), Size(sz - sw, sz - sw), rad, style = Stroke(sw))
                }
                val fg = if (tile.guLevel) r.ink else tile.fg
                val cons = Constraints(maxWidth = (sz - CarSpace.block.toPx()).toInt().coerceAtLeast(1))
                val nameL = measurer.measure(tile.name, nameStyle.copy(color = fg), overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = cons)
                val valL = measurer.measure(tile.value, valueStyle.copy(color = fg), maxLines = 1, constraints = cons)
                val top = y + sz / 2 - (nameL.size.height + valL.size.height) / 2f + (if (tile.current) 10.dp.toPx() else 0f) // layout: room for the current-tile arrow
                drawText(nameL, topLeft = Offset(x + (sz - nameL.size.width) / 2, top))
                drawText(valL, topLeft = Offset(x + (sz - valL.size.width) / 2, top + nameL.size.height))
                if (tile.current) { // the car: a small arrow at the top of its own tile (pointing up = ahead when headingUp)
                    val cx = x + sz / 2; val ay = y + 14.dp.toPx(); val aw = 12.dp.toPx(); val ah = 16.dp.toPx() // layout: current-tile arrow
                    val path = Path().apply { moveTo(cx, ay); lineTo(cx + aw, ay + ah); lineTo(cx, ay + ah * 0.7f); lineTo(cx - aw, ay + ah); close() }
                    drawPath(path, fg)
                }
            }
        }
        // Legend: the same stops as the tiles, ends = the period's saturation cap.
        Column(Modifier.widthIn(max = CarSize.Measure.areaMap).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
            Box(Modifier.fillMaxWidth().height(16.dp).background(Brush.horizontalGradient(s.legend), RoundedCornerShape(8.dp)).tag("area_legend")) // layout: legend bar 16 high, r 8
            Row(Modifier.fillMaxWidth()) {
                Text(s.legendMin, style = t.caption.copy(color = r.sub))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("0", style = t.caption.copy(color = r.sub)) }
                Text(s.legendMax, style = t.caption.copy(color = r.sub))
            }
            Text(s.basis, style = t.caption.copy(color = r.sub), maxLines = 2, modifier = Modifier.tag("area_basis"))
        }
    }
}

/** 1개월 · 3개월 · 1년 — brand-soft pill on the selected one (v2.2 selection), 80 dp visual inside a 100 dp hit row. */
@Composable
private fun PeriodToggle(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Row(Modifier.height(CarSpace.hit), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(Modifier.height(CarSpace.chipVisual).widthIn(min = CarSize.Control.chipMin)
                .background(if (on) r.selectFace else r.chip, RoundedCornerShape(CarSpace.chipVisual / 2))
                .press(RoundedCornerShape(CarSpace.chipVisual / 2), tag = "period_$i") { onPick(i) }
                .padding(horizontal = CarSize.Inset.l), contentAlignment = Alignment.Center) {
                Text(l, style = t.label.copy(color = if (on) r.selectInk else r.ink2, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal))
            }
        }
    }
}
