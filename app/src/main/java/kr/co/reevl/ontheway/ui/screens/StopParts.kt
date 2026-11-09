package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.format.AreaUnit
import kr.co.reevl.ontheway.format.ComplexFacts
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.ui.components.CardHead
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.SkeletonBar
import kr.co.reevl.ontheway.ui.components.StatGrid
import kr.co.reevl.ontheway.ui.components.StatRow
import kr.co.reevl.ontheway.ui.components.StatSection
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.TextUse

// Shared pieces of a stop's facts — STOP_DETAIL and the VISIT_LIST summary pane draw the same rows with the same
// words (ComplexFacts = web KeyStats; trades = web TradeHighlights "최근 실거래").

/** 핵심 정보 (KeyStats 주요 rows). No date over the block — only 전세가율 carries its own as-of (review Minor 5). */
@Composable
fun FactsSection(c: CarComplex?, loading: Boolean, stop: PlanStop, keys: Set<String>? = null) {
    if (stop.cid == null) {
        Text("리블 단지가 아닌 방문지라 단지 정보가 없어요.", style = CarTheme.type.body.copy(color = CarTheme.colors.sub))
        return
    }
    if (c == null) {
        if (loading) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { repeat(4) { SkeletonBar(560.dp, 44.dp) } }
        return
    }
    val rows = ComplexFacts.rows(c).filter { keys == null || it.key in keys }
    if (rows.isEmpty()) return
    StatSection("핵심 정보") {
        StatGrid(rows.map { r -> { StatRow(r.label, r.value, chip = r.chip, note = r.note, tag = "fact_${r.key}") } })
    }
}

/**
 * TradeHighlights.tsx 최근 실거래: basis `같은 평형 직전 거래 대비` (TradeHighlights.tsx:150, same words), then rows of
 * price · ▲▼ · date / 평형(전용㎡) · 층. The trade-data date sits on this block only.
 */
@Composable
fun TradesCard(c: CarComplex?, loading: Boolean, modifier: Modifier = Modifier, max: Int = 5) {
    val col = CarTheme.colors
    val t = CarTheme.type
    ReevlCard(modifier.fillMaxWidth(), tag = "trades") {
        CardHead("최근 실거래", sub = listOfNotNull("같은 평형 직전 거래 대비", "매매 계약일", c?.tradeAsOf?.let { "$it 수집" }).joinToString(" · "))
        when {
            c == null && loading -> Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) { repeat(3) { SkeletonBar(640.dp, 48.dp) } }
            c == null -> Unit
            c.recentTrades == null -> Text("실거래를 불러오지 못했어요", style = t.body.copy(color = col.sub), modifier = Modifier.padding(top = 16.dp))
            c.recentTrades.isEmpty() -> Text("최근 매매 신고가 없어요", style = t.body.copy(color = col.sub), modifier = Modifier.padding(top = 16.dp))
            else -> Column(Modifier.padding(top = 12.dp)) {
                val rows = c.recentTrades
                rows.take(max).forEachIndexed { i, tr -> TradeRow(tr, deltaOf(rows, i), Modifier.testTag("trade_$i")) }
            }
        }
        if (c != null) {
            Spacer(Modifier.padding(top = 16.dp))
            Text(c.notice, style = t.cap.copy(fontWeight = FontWeight.SemiBold))
            c.sources.firstOrNull { it.item == "최근 실거래" }?.source?.let { Text("출처 $it", style = t.cap, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/**
 * Previous same-area trade (floor of ㎡, TradeHighlights `Math.floor(t[2])`) inside the five we received. When it is
 * there it IS the immediately previous one (the five are the newest overall); otherwise blank, never "첫 거래".
 */
fun deltaOf(rows: List<CarTrade>, i: Int): Double? {
    val k = floor(rows[i].areaM2)
    for (j in i + 1 until rows.size) if (floor(rows[j].areaM2) == k) return (rows[i].price - rows[j].price).toDouble()
    return null
}

@Composable
private fun TradeRow(tr: CarTrade, delta: Double?, modifier: Modifier = Modifier) {
    val col = CarTheme.colors
    val t = CarTheme.type
    // TradeHighlights row content in two lines: at car type sizes one 288 px web row does not fit a 1:1 window, so the
    // price leads and the rest sits under it. Fixed widths keep every price at the same x (TradeHighlights.tsx:153-162).
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(ReevlFormat.wonShort(tr.price.toDouble()), style = t.value.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, modifier = Modifier.width(250.dp))
            Text(delta?.let { ReevlFormat.deltaText(it) } ?: "", maxLines = 1,
                style = TextUse.delta(t).copy(color = when { delta == null || delta == 0.0 -> col.sub; delta > 0 -> col.up; else -> col.down }, fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f))
            // Year included (TradeHighlights `mdD`, "26.09.28"): five trades can span two years.
            Text(ReevlFormat.mdD(tr.date), style = t.body.copy(color = col.sub, fontFeatureSettings = "tnum"))
        }
        Text("${ReevlFormat.areaFmtBoth(tr.areaM2, AreaUnit.PY)}${tr.floor?.let { " · ${it}층" } ?: ""}",
            style = t.body.copy(color = col.sub, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp))
    }
}
