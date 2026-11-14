package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.CarTypeV2
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** One destination of the app's left menu (built-in Settings structure, native_02). */
data class RailItem(val key: String, val icon: ImageVector, val label: String, val value: String?, val onClick: () -> Unit)

data class RailSpec(val items: List<RailItem>, val selected: String?, val onMenu: () -> Unit)

/** Provided once by the activity for every screen; null on LINK (no account yet). */
val LocalRail = staticCompositionLocalOf<RailSpec?> { null }

/**
 * Left menu: 100 dp rows, icon 40 + Regular label (built-in Settings structure, native_02). v2.2 selection = REEVL:
 * brand-soft face inset 16 / 8 with `rounded-lg` (20), brand icon + label Medium (web Sidebar.tsx:29–30, :131, ToolbarBtn on
 * Button.tsx:66). Line on the right.
 */
@Composable
fun Rail(s: RailSpec) {
    val r = LocalRoles.current
    val t = CarTypeV2.Compact
    val shape = RoundedCornerShape(CarSpace.radiusM)
    Row(Modifier.width(CarSpace.rail).fillMaxHeight().background(r.surface)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            s.items.forEach { it ->
                val on = it.key == s.selected
                val fg = if (on) r.selectInk else r.ink
                Box(Modifier.fillMaxWidth().height(CarSpace.rowSingle).padding(horizontal = CarSpace.selectInsetH, vertical = CarSpace.selectInsetV)) {
                    Row(Modifier.fillMaxSize().then(if (on) Modifier.background(r.selectFace, shape) else Modifier)
                        .press(shape, tag = "nav_${it.key}", onClick = it.onClick).padding(horizontal = CarSize.Inset.s),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSize.Inset.s)) {
                        Icon(it.icon, null, tint = if (on) r.selectInk else r.ink2, modifier = Modifier.size(CarSpace.icon))
                        // The label never shrinks (v2.2 inset made `주변` collapse to `주` beside `오른쪽 1 · 왼쪽 0`); the value ellipsizes.
                        Text(it.label, style = t.rowPrimary.copy(color = fg, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal), maxLines = 1, softWrap = false)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            it.value?.let { v -> Text(v, style = t.rowSecondary.copy(color = if (on) r.selectInk else r.sub), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        Box(Modifier.width(CarSpace.hairline).fillMaxHeight().background(r.line))
    }
}
