package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** One period of the 동's price index: `1개월 −1.5%`. [dir] = sign (up red / down blue, web dirColor). */
data class AreaPeriodCell(val label: String, val value: String, val dir: Int)

/** One fact of the 동 (`단지 3곳`). */
data class AreaFact(val label: String, val value: String, val key: String)

/** The 동's 1-year change next to its 구's — two bars on one scale (raw fractions, null = no value). */
data class AreaCompare(val dongLabel: String, val dong: Double?, val dongText: String, val guLabel: String, val gu: Double?, val guText: String)

data class AreaInsightLine(val icon: ImageVector, val text: String, val key: String)

/**
 * 동네 요약 (2026-11-14 redesign, founder "요약화면 저게 디자인이 된게 맞아"): before, one big number and a list of sentences.
 * Now the same server data in a read-at-a-glance order, flat on the pane like the built-in apps (no cards, no shadows):
 *  left  — where (region · 동), the 1-year change as the one big figure, the 1·3·12-month strip, the 동 vs 구 bars;
 *  right — four facts of the 동 (단지 · 세대 · 1년 거래 · 역), up to two 눈여겨볼 점, then the 주목 단지 / 뉴스 rows.
 * One column in a narrow pane. Weight: Regular/Medium only (car-ds-v2: hierarchy by size and colour, not bold).
 */
data class AreaSummarySpec(
    val region: String, val place: String, val headLabel: String, val headValue: String, val headDir: Int,
    val periods: List<AreaPeriodCell>, val compare: AreaCompare?, val facts: List<AreaFact>, val insights: List<AreaInsightLine>,
)

private fun dirColor(r: CarRoles, dir: Int): Color = when { dir > 0 -> r.upText; dir < 0 -> r.downText; else -> r.ink2 }

@Composable
fun AreaSummary(s: AreaSummarySpec, rows: List<RowSpec>, onRow: (RowSpec) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().tag("area_summary")) {
        val wide = maxWidth >= CarSize.Measure.statTwoCol
        if (wide) Row(Modifier.fillMaxWidth().padding(top = CarSpace.block), horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
            Column(Modifier.weight(1f)) { SummaryLead(s) }
            Column(Modifier.weight(1f)) { SummaryFacts(s, rows, onRow) }
        } else Column(Modifier.fillMaxWidth().padding(top = CarSpace.block)) {
            SummaryLead(s)
            SummaryFacts(s, rows, onRow)
        }
    }
}

@Composable
private fun SummaryLead(s: AreaSummarySpec) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Text(s.region, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("area_region"))
    Text(s.place, style = t.title.copy(color = r.ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = CarSpace.snug).tag("area_place"))
    Row(Modifier.padding(top = CarSpace.item), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CarSpace.card)) {
        Text(s.headValue, style = t.display.copy(color = dirColor(r, s.headDir), fontWeight = FontWeight.Medium), maxLines = 1, modifier = Modifier.tag("area_value"))
        Text(s.headLabel, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, modifier = Modifier.padding(bottom = CarSpace.snug))
    }
    // 1 · 3 · 12 months on one strip, hairlines between — one glance instead of a sentence.
    Row(Modifier.fillMaxWidth().padding(top = CarSpace.section, bottom = CarSpace.block).height(IntrinsicSize.Min).tag("area_periods")) {
        s.periods.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.width(CarSpace.hairline).fillMaxHeight().background(r.line))
            Column(Modifier.weight(1f).padding(start = if (i == 0) CarSpace.hair else CarSpace.card)) {
                Text(p.label, style = t.caption.copy(color = r.sub), maxLines = 1)
                Text(p.value, style = t.value.copy(color = dirColor(r, p.dir)), maxLines = 1, modifier = Modifier.padding(top = CarSpace.snug))
            }
        }
    }
    s.compare?.let { CompareBars(it) }
}

@Composable
private fun CompareBars(c: AreaCompare) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val max = listOfNotNull(c.dong, c.gu).maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: return
    // Bars grow in once when the summary opens (one 600 ms ease, no loop — a parked glance, never while driving: the pane is locked then).
    val grow = remember(c) { Animatable(0f) }
    LaunchedEffect(c) { grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    StatHead("${c.guLabel}와 비교 · 1년")
    Column(Modifier.fillMaxWidth().tag("area_compare"), verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
        for ((label, v, text, strong) in listOf(Quad(c.dongLabel, c.dong, c.dongText, true), Quad(c.guLabel, c.gu, c.guText, false))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.card)) {
                Text(label, style = t.rowSecondary.copy(color = if (strong) r.ink else r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = CarSize.Measure.statValue, max = CarSize.Measure.statValue))
                Box(Modifier.weight(1f).height(CarSpace.item).clip(RoundedCornerShape(CarSpace.radiusS)).background(r.chip)) {
                    val f = ((v?.let { kotlin.math.abs(it) } ?: 0.0) / max).toFloat() * grow.value
                    val dir = v?.compareTo(0.0) ?: 0
                    val fill = when { dir > 0 -> if (strong) r.up else r.upSoft; dir < 0 -> if (strong) r.down else r.downSoft; else -> r.line }
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(CarSpace.radiusS)).background(fill))
                }
                Text(text, style = t.value.copy(color = dirColor(r, v?.compareTo(0.0) ?: 0)), maxLines = 1)
            }
        }
    }
}

private data class Quad(val a: String, val b: Double?, val c: String, val d: Boolean)

@Composable
private fun SummaryFacts(s: AreaSummarySpec, rows: List<RowSpec>, onRow: (RowSpec) -> Unit) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    if (s.facts.isNotEmpty()) {
        StatHead("이 동네")
        Column(Modifier.fillMaxWidth().padding(bottom = CarSpace.block).tag("area_facts"), verticalArrangement = Arrangement.spacedBy(CarSpace.section)) {
            s.facts.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { f ->
                        Column(Modifier.weight(1f).tag("fact_${f.key}")) {
                            Text(f.label, style = t.caption.copy(color = r.sub), maxLines = 1)
                            Text(f.value, style = t.value.copy(color = r.ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = CarSpace.snug))
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
    if (s.insights.isNotEmpty()) {
        StatHead("눈여겨볼 점")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
            s.insights.forEach { i ->
                Row(Modifier.tag("insight_${i.key}"), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(CarSpace.card)) {
                    Icon(i.icon, null, tint = r.brandText, modifier = Modifier.padding(top = CarSpace.hair).size(CarSpace.iconSmall))
                    Text(i.text, style = t.rowSecondary.copy(color = r.ink2), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (rows.isNotEmpty()) Box(Modifier.padding(top = CarSpace.gapTight)) { RowGroup(rows, null, onRow) }
}
