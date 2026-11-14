package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

sealed interface Leading {
    data class Avatar(val name: String, val cid: String?, val brand: CarBrand?) : Leading
    /** Visit order: [done] = ✓, [next] = brand face. */
    data class Order(val n: Int, val next: Boolean, val done: Boolean) : Leading
    data class Glyph(val icon: ImageVector) : Leading
}

/**
 * One list row (car-ds-v2 §5.3): name 1 line + numbers 1 line; trailing chip 0–1 + Compact button 0–1 or ›.
 * [onClick] null = the list decides (wide: select for preview, compact: open). [inert] = not pressable.
 */
data class RowSpec(
    val key: String,
    val primary: String,
    val secondary: String? = null,
    val leading: Leading? = null,
    val chip: ChipSpec? = null,
    val button: Btn? = null,
    val chevron: Boolean = false,
    val enabled: Boolean = true,
    val inert: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val tag: String? = null,
    /** Read-only sentence rows (동네 insights): the primary line may take 2 lines so a number is never cut by "…" (§15.7). */
    val wrap: Boolean = false,
)

@Composable
fun ListRow(s: RowSpec, selected: Boolean, onTap: () -> Unit) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val h = if (s.secondary != null) CarSpace.rowDouble else CarSpace.rowSingle
    // Narrow window (§15, QA round 5): a chip and/or a button leave ~200 dp for the text — the numbers line wraps instead of
    // ending in `최근 …`, and a status chip yields to the row's button (the section title already says it: `…에 왔어요`).
    val narrow = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow
    val wrap = s.wrap || narrow
    val chip = if (narrow && s.button != null) null else s.chip
    val base = Modifier.fillMaxWidth().then(if (wrap) Modifier.heightIn(min = h).padding(vertical = CarSpace.gapTight) else Modifier.height(h)).then(if (selected) Modifier.background(r.chip) else Modifier)
    Box(if (s.inert) base.tag(s.tag ?: "row_${s.key}") else base.press(RectangleShape, enabled = s.enabled, tag = s.tag ?: "row_${s.key}", onClick = s.onClick ?: onTap)) {
        Row(Modifier.fillMaxWidth().then(if (wrap) Modifier else Modifier.fillMaxHeight()).padding(horizontal = CarSpace.item), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
            val dim = if (s.enabled) 1f else r.disabledAlpha
            when (val l = s.leading) {
                is Leading.Avatar -> AptAvatar(l.name, l.cid, l.brand, CarSpace.avatar, Modifier.alpha(dim))
                is Leading.Order -> OrderMark(l, Modifier.alpha(dim))
                is Leading.Glyph -> Icon(l.icon, null, tint = r.ink2, modifier = Modifier.size(CarSpace.icon).alpha(dim))
                null -> Unit
            }
            Column(Modifier.weight(1f)) {
                Text(s.primary, style = t.rowPrimary.copy(color = if (s.enabled) r.ink else r.sub), maxLines = if (wrap) 2 else 1, overflow = TextOverflow.Ellipsis)
                s.secondary?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), maxLines = if (wrap) 3 else 1, overflow = TextOverflow.Ellipsis) }
            }
            chip?.let { StatusChip(it) }
            s.button?.let { CompactButton(it) }
            if (s.chevron && s.button == null) Icon(Lucide.ChevronRight, null, tint = r.sub, modifier = Modifier.size(CarSpace.icon))
        }
    }
}

@Composable
private fun OrderMark(o: Leading.Order, modifier: Modifier) {
    val r = LocalRoles.current
    // v2.1: next = ink square with the number in white; others = outlined; done = ✓ in posText.
    val shape = RoundedCornerShape(CarSpace.radiusS)
    val base = modifier.size(CarSpace.orderBadge)
    Box(when { o.next -> base.background(r.primaryFace, shape); o.done -> base; else -> base.border(CarSize.Stroke.thin, r.lineStrong, shape) }, contentAlignment = Alignment.Center) {
        if (o.done) Icon(Lucide.Check, "완료", tint = r.posText, modifier = Modifier.size(CarSize.Icon.mark))
        else Text("${o.n}", style = LocalTypeV2.current.value.copy(color = if (o.next) r.onPrimary else r.ink))
    }
}

/** v2.1: rows sit directly on the page, 1 dp lines between them (built-in Settings / Phone lists), no card. */
@Composable
fun RowGroup(rows: List<RowSpec>, selectedKey: String?, onTap: (RowSpec) -> Unit, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
            ListRow(row, selected = row.key == selectedKey) { onTap(row) }
        }
    }
}
