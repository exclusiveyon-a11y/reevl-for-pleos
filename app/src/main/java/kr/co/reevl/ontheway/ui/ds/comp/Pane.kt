package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

sealed interface TradesSpec {
    data object Loading : TradesSpec
    /** recentTrades == null → `실거래를 불러오지 못했어요`. */
    data object Error : TradesSpec
    /** [] → `최근 매매 신고 없음`. */
    data object Empty : TradesSpec
    data class Items(val items: List<TradeSpec>) : TradesSpec
    /** Not a Reevl complex / vanished cid: nothing to say. */
    data object None : TradesSpec
}

/** One complex as a pane (car-ds-v2 §4.3): header 2 lines, ≤ 1 check-chip line, ≤ 4 trades, one source line, ≤ 2 actions. */
data class PaneSpec(
    val key: String,
    val avatarName: String,
    val cid: String?,
    val brand: CarBrand?,
    val name: String,
    val facts: String?,
    val chip: ChipSpec? = null,
    val save: IconSpec? = null,
    val checks: List<String> = emptyList(),
    val trades: TradesSpec = TradesSpec.None,
    val footer: String? = null,
    /** One quiet line under the footer (complex detail only: the 참고용 disclaimer). */
    val disclaimer: String? = null,
    val actions: Actions? = null,
    /** DETAIL tabs (요약 · 시세 · 전세 · 입지 · 학군 · 단지) — null = the old single pane. */
    val tabs: TabsSpec? = null,
    /** REEVL metric rows of the selected tab (label / value / sub). */
    val stats: List<StatSpec> = emptyList(),
    /** Trades in this tab (요약 = newest 1 · 시세 = ≤ 4 · other tabs = 0). */
    val tradeLimit: Int = 4,
    /** 내 메모 (written on the phone, read-only here) — one flat row under the checks. */
    val memo: RowSpec? = null,
)

data class StatSpec(val key: String, val label: String, val value: String, val sub: String?, val color: String?)

@Composable
fun PaneHeader(p: PaneSpec, showSave: Boolean) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Row(Modifier.fillMaxWidth().heightIn(min = CarSpace.rowDouble), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
        AptAvatar(p.avatarName, p.cid, p.brand, CarSpace.avatarHeader)
        Column(Modifier.weight(1f)) {
            Text(p.name, style = t.title.copy(color = r.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("pane_name"))
            p.facts?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        p.chip?.let { StatusChip(it) }
        if (showSave) p.save?.let { IconBtn(it) }
    }
}

/** Pane body without its action bar (the template places that). With [PaneSpec.tabs] the header and tabs are drawn by the template. */
@Composable
fun PaneBody(p: PaneSpec, showSave: Boolean, header: Boolean = true) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(Modifier.fillMaxWidth().tag("pane_${p.key}"), verticalArrangement = Arrangement.spacedBy(if (p.tabs != null) 0.dp else CarSpace.gapTight)) {
        if (header) PaneHeader(p, showSave)
        if (p.checks.isNotEmpty()) Row(Modifier.padding(top = CarSpace.item), horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            p.checks.take(4).forEach { CheckChip(it) }
        }
        p.memo?.let { RowGroup(listOf(it), null, { }) }
        if (p.tradeLimit > 0) {
            if (p.tabs != null && p.trades !is TradesSpec.None) StatHead(if (p.tradeLimit == 1) "최근 실거래" else "최근 실거래 ${minOf(p.tradeLimit, 4)}건")
            when (val tr = p.trades) {
                TradesSpec.Loading -> repeat(minOf(2, p.tradeLimit)) { Skeleton(CarSpace.tradeLine) }
                TradesSpec.Error -> Text("실거래를 불러오지 못했어요", style = t.rowSecondary.copy(color = r.sub), modifier = Modifier.heightIn(min = CarSize.Row.line))
                TradesSpec.Empty -> Text("최근 매매 신고 없음", style = t.rowSecondary.copy(color = r.sub), modifier = Modifier.heightIn(min = CarSize.Row.line))
                is TradesSpec.Items -> tr.items.take(minOf(4, p.tradeLimit)).forEachIndexed { i, it -> TradeLine(it, Modifier.tag("trade_$i")) }
                TradesSpec.None -> Unit
            }
        }
        if (p.stats.isNotEmpty()) {
            if (p.tradeLimit > 0 && p.trades !is TradesSpec.None) androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = CarSpace.gapTight))
            StatGrid(p.stats)
        }
        p.footer?.let { SourceFooter(it) }
        p.disclaimer?.let { Text(it, style = LocalTypeV2.current.caption.copy(color = LocalRoles.current.sub), maxLines = 1,
            modifier = Modifier.tag("disclaimer")) }
    }
}

@Composable
fun Skeleton(h: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().heightIn(min = h).background(LocalRoles.current.chip, RoundedCornerShape(CarSpace.radiusS)))
}
