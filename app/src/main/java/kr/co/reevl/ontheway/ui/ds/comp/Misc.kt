package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

// ── TradeLine (§5.7) ──
/** [delta] 만원 vs the previous same-area trade (null = none). */
data class TradeSpec(val price: String, val delta: String?, val up: Boolean, val date: String, val sub: String)

@Composable
fun TradeLine(s: TradeSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(modifier.fillMaxWidth().heightIn(min = CarSpace.tradeLine), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            Text(s.price, style = t.value.copy(color = r.ink), maxLines = 1)
            if (s.delta != null) {
                // v2.2: REEVL price direction — arrow and digits both up red / down blue, tnum (web ChangePill.tsx:12–13).
                // Arrow = mark (`up`/`down`), digits = text (`upText`/`downText`, ink tokens).
                Icon(if (s.up) Lucide.ArrowUp else Lucide.ArrowDown, null, tint = if (s.up) r.up else r.down, modifier = Modifier.size(CarSpace.iconSmall))
                Text(s.delta, style = t.rowSecondary.copy(color = if (s.up) r.upText else r.downText, fontWeight = FontWeight.Medium), maxLines = 1, modifier = Modifier.tag("trade_delta"))
            }
            Box(Modifier.weight(1f))
            Text(s.date, style = t.rowSecondary.copy(color = r.sub), maxLines = 1)
        }
        Text(s.sub, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Banner (§5.8) ──
enum class BannerKind { Offline, Error, Info }
data class BannerSpec(val kind: BannerKind, val text: String, val action: Btn? = null)

@Composable
fun Banner(s: BannerSpec) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val (bg, fg, icon) = when (s.kind) {
        BannerKind.Offline -> Triple(r.warnSoft, r.warnText, Lucide.WifiOff)
        BannerKind.Error -> Triple(r.upSoft, r.ink, Lucide.CircleAlert)
        BannerKind.Info -> Triple(r.chip, r.ink2, Lucide.Info)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = CarSpace.banner).background(bg).padding(horizontal = CarSpace.section).tag("banner"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        Icon(icon, null, tint = if (s.kind == BannerKind.Error) r.up else fg, modifier = Modifier.size(CarSize.Icon.inline))
        Text(s.text, style = t.rowSecondary.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        s.action?.let { CompactButton(it) }
    }
}

// ── SourceFooter (§5.9) ──
@Composable
fun SourceFooter(text: String) {
    Text(text, style = LocalTypeV2.current.caption.copy(color = LocalRoles.current.sub), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().heightIn(min = CarSpace.footer).padding(top = CarSpace.gapTight).tag("footer"))
}

// ── Tabs (§5.12) ──
data class TabsSpec(val tabs: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

@Composable
fun Tabs(s: TabsSpec) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
     val bw = maxWidth
     Column(Modifier.fillMaxWidth()) {
        // ≤ 6 tabs (DETAIL 요약·시세·전세·입지·학군·단지). §15: when 100 dp each does not fit (narrow window: 6 × 100 > 512) the
        // tabs share the width evenly (≥ 80 wide, 100 high) instead of scrolling sideways and hiding the last tab.
        val n = s.tabs.take(6).size
        val natural = CarSpace.gapTight * 2 + (if (n > 4) CarSize.Control.tabMinDense else CarSize.Control.tabMin) * n + (if (n > 4) CarSpace.snug else CarSpace.gapTight) * (n - 1)
        val share = bw < natural && bw >= CarSpace.item * 2 + CarSize.Control.tabShareMin * n // shared tabs keep 8 dp sides (compare pane in the narrow window = 496)
        Row(Modifier.fillMaxWidth().then(if (share) Modifier else Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())).padding(horizontal = if (share) CarSpace.item else CarSpace.gapTight),
            horizontalArrangement = Arrangement.spacedBy(if (share) 0.dp else if (s.tabs.size > 4) CarSpace.snug else CarSpace.gapTight)) {
            s.tabs.take(6).forEachIndexed { i, label ->
                val on = i == s.selected
                Column((if (share) Modifier.weight(1f) else Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).widthIn(min = if (s.tabs.size > 4) CarSize.Control.tabMinDense else CarSize.Control.tabMin)).height(CarSpace.hit).press(androidx.compose.ui.graphics.RectangleShape, role = androidx.compose.ui.semantics.Role.Tab, tag = "tab_$i") { s.onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).padding(horizontal = if (share) 0.dp else CarSize.Inset.s), contentAlignment = Alignment.Center) {
                        Text(label, style = t.rowPrimary.copy(color = if (on) r.brandText else r.sub, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal))
                    }
                    // v2.2: REEVL underline tab — 1.5 px → 3 dp indicator in brand on the 1 dp line (TabsBar.tsx:95–96; brand per §14.3).
                    Box(Modifier.fillMaxWidth().height(CarSpace.tabIndicator).background(if (on) r.brand else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
     }
    }
}

// ── Toast (§5.13) ──
data class ToastSpec(val id: Long, val text: String, val action: Btn? = null)

@Composable
fun Toast(s: ToastSpec, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    LaunchedEffect(s.id) { delay(if (s.action != null) 8_000 else 4_000); onDismiss() }
    Row(modifier.widthIn(max = CarSpace.toastMax).height(CarSpace.toastH).background(r.ink, RoundedCornerShape(CarSpace.radiusS)).padding(horizontal = CarSpace.section).tag("toast"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
        Text(s.text, style = LocalTypeV2.current.rowSecondary.copy(color = r.inverse), maxLines = 1)
        s.action?.let { a ->
            Box(Modifier.height(CarSpace.hit).press(RoundedCornerShape(CarSpace.radiusM), tag = a.tag) { a.onClick(); onDismiss() }.padding(horizontal = CarSpace.gapTight),
                contentAlignment = Alignment.Center) { Text(a.label, style = LocalTypeV2.current.label.copy(color = r.inverse)) }
        }
    }
}

// ── ActionBar (§5.15) ──
data class Actions(val primary: Btn? = null, val secondary: Btn? = null,
                   /** §15.3 single-purpose screens: the bar is centred under the focal column (≤ focusWidth), one button fills it. */
                   val centered: Boolean = false)

/** §5.15 bottom action bar: 1 dp line, 24 / 100 / 24. ≤ 2 buttons; in the narrow window two buttons share the row at half width. */
@Composable
fun ActionBar(a: Actions, maxWidth: androidx.compose.ui.unit.Dp? = null) {
    val r = LocalRoles.current
    val mw = maxWidth ?: if (a.centered) CarSpace.focusWidth else null
    val pad = kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current)
    Column(Modifier.fillMaxWidth().background(r.surface)) {
        Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
        Box(Modifier.fillMaxWidth().padding(horizontal = pad, vertical = CarSpace.actionPad), contentAlignment = Alignment.Center) {
            Row(Modifier.then(if (mw != null) Modifier.widthIn(max = mw) else Modifier).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (pad < CarSpace.margin) CarSpace.gapTight else CarSpace.gap)) {
                a.primary?.let { PrimaryButton(it, Modifier.weight(1f), fill = true) }
                a.secondary?.let { SecondaryButton(it, Modifier.weight(1f), fill = true) }
                if ((a.primary == null || a.secondary == null) && !a.centered) Box(Modifier.weight(1f))
            }
        }
    }
}

// ── Message (§4.6 content) ──
enum class MsgTone { Brand, Error, Neutral }
data class MessageSpec(val icon: androidx.compose.ui.graphics.vector.ImageVector?, val title: String, val value: String? = null,
                       /** v2.1: one plain sentence under the title, like the built-in Phone's empty state. */
                       val primary: Btn? = null, val secondary: Btn? = null, val tone: MsgTone = MsgTone.Neutral, val spinner: Boolean = false,
                       val tag: String? = null)

/** v2.1 (native_04 empty state): title (row size, ink) · one grey line · ink button; no icon circle. */
@Composable
fun MessageContent(s: MessageSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(modifier.fillMaxWidth().tag(s.tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        if (s.spinner) Spinner(r.ink2, CarSize.Icon.progress)
        else if (s.tone != MsgTone.Neutral) s.icon?.let { Icon(it, null, tint = if (s.tone == MsgTone.Error) r.up else r.posText, modifier = Modifier.size(CarSize.Icon.hero)) }
        Text(s.title, style = t.title.copy(color = r.ink), textAlign = TextAlign.Center, maxLines = 2)
        s.value?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (s.primary != null || s.secondary != null) Row(Modifier.padding(top = CarSpace.section), horizontalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
            s.primary?.let { PrimaryButton(it, Modifier.width(CarSize.Control.messageButton), fill = true) }
            s.secondary?.let { SecondaryButton(it, Modifier.width(CarSize.Control.messageButton), fill = true) }
        }
    }
}
