package kr.co.reevl.ontheway.ui.ds.tmpl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.CompactButton
import kr.co.reevl.ontheway.ui.ds.comp.IconBtn
import kr.co.reevl.ontheway.ui.ds.comp.IconSpec
import kr.co.reevl.ontheway.ui.ds.comp.Skeleton
import kr.co.reevl.ontheway.ui.ds.comp.SourceFooter
import kr.co.reevl.ontheway.ui.ds.comp.Tabs
import kr.co.reevl.ontheway.ui.ds.comp.TabsSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.comp.statColor
import kr.co.reevl.ontheway.ui.ds.comp.tag
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

data class CompareColSpec(val key: String, val name: String, val brand: CarBrand?, val facts: String?, val loading: Boolean, val error: Boolean,
                          val guide: Btn, val open: Btn, val onRemove: () -> Unit)
data class CompareCellSpec(val value: String?, val sub: String?, val color: String?, val best: Boolean)
data class CompareRowSpec(val key: String, val label: String, val cells: List<CompareCellSpec>)

/**
 * 비교 (web CompareView, car-sized): a fixed label column + one column per complex (wide window 3, 840 window 2 at a time with
 * `‹ ›` to page — never squeezed below a readable value). Header = brand avatar · name · facts · ×; tabs = DETAIL's tabs;
 * rows = flat label/value cells, the better value on `brand-soft` (no bold, no cards); per column [안내] [상세] at the bottom.
 */
@Composable
fun CompareTemplate(top: TopBarSpec, cols: List<CompareColSpec>, tabs: TabsSpec?, rows: List<CompareRowSpec>, onAdd: (() -> Unit)?,
                    footer: String?, toast: ToastSlot? = null) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val wide = LocalWideWindow.current
    val cls = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current
    // §15: 3 columns wide, 2 in the 1:1 window, 1 in the narrow window — a value is never squeezed below ~300 dp.
    val visible = comparePageSize(cls)
    // Saveable: the page survives a window-bar resize and a trip to the background (the tray itself lives in the ViewModel).
    var first by androidx.compose.runtime.saveable.rememberSaveable(cols.size) { mutableIntStateOf(0) }
    val start = first.coerceIn(0, (cols.size - visible).coerceAtLeast(0))
    val shown = (start until minOf(cols.size, start + visible)).toList()
    val last = start + visible >= cols.size
    val actions = buildList {
        if (!wide && cols.size > visible) add(IconSpec(if (last) Lucide.ChevronLeft else Lucide.ChevronRight, "다른 단지",
            { first = if (last) 0 else start + visible }, text = if (last) "앞" else "다음", tag = "cmp_page"))
        onAdd?.let { add(IconSpec(Lucide.Plus, "담기", it, text = "담기", tag = "cmp_add")) }
    }
    // The per-column [안내] [상세] row sits inside the body, so the toast is lifted above it (it covered them before).
    Frame("screen_compare", top.copy(actions = top.actions + actions), null, null, toast, onePane = true, toastLift = CarSpace.actionBar) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = CarSpace.margin)) {
            val labelW: Dp = when (cls) { kr.co.reevl.ontheway.ui.ds.token.WindowClass.Wide -> CarSize.Measure.compareLabelWide; kr.co.reevl.ontheway.ui.ds.token.WindowClass.Compact -> CarSize.Measure.compareLabelCompact; else -> CarSize.Measure.compareLabelNarrow }
            // Fewer complexes than slots (2 in the wide window) share the width — no empty third column (§15.5).
            val colW: Dp = (maxWidth - labelW) / minOf(visible, cols.size).coerceAtLeast(1)
            Column(Modifier.fillMaxSize()) {
                // ── column heads ──
                Row(Modifier.fillMaxWidth().padding(top = CarSpace.gapTight)) {
                    Box(Modifier.width(labelW))
                    shown.forEach { i -> val c = cols[i]
                      androidx.compose.runtime.key(c.key) {
                        // avatar · × on one line, the name below on up to 2 lines — a 288 column never cuts the name to 3 letters
                        Column(Modifier.width(colW).padding(start = CarSpace.gapTight).tag("cmp_col_$i")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AptAvatar(c.name, c.key, c.brand, CarSize.Control.avatarCompact)
                                Box(Modifier.weight(1f))
                                IconBtn(IconSpec(Lucide.X, "${c.name} 빼기", c.onRemove, tag = "cmp_remove_$i"))
                            }
                            Text(c.name, style = t.rowPrimary.copy(color = r.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            (if (c.error) "불러오지 못했어요" else c.facts)?.let { Text(it, style = t.caption.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                      }
                    }
                }
                tabs?.let { Box(Modifier.padding(top = CarSpace.item)) { Tabs(it) } }
                // ── rows ──
                androidx.compose.runtime.key(tabs?.selected) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (rows.isEmpty() && cols.any { it.loading }) repeat(4) { Box(Modifier.padding(vertical = CarSpace.item)) { Skeleton(CarSize.Row.skeleton) } }
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = CarSize.Row.compare).tag("cmp_row_${row.key}"), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.label, style = t.rowSecondary.copy(color = r.sub), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(labelW).padding(end = CarSpace.gapTight))
                                shown.forEach { i ->
                                    val c = row.cells.getOrNull(i)
                                    // `best` is a comparison: with one column on screen (narrow) there is nothing beside it to be
                                    // better than → no highlight (QA 2026-11-10, N_N_COMPARE showed a lone column half blue).
                                    val best = c?.best == true && shown.size >= 2
                                    Column(Modifier.width(colW).fillMaxHeight().padding(start = CarSpace.item, top = CarSpace.tight, bottom = CarSpace.tight)
                                        .then(if (best) Modifier.background(r.selectFace) else Modifier)
                                        .padding(horizontal = CarSpace.block).tag(if (best) "cmp_best" else null),
                                        verticalArrangement = Arrangement.Center) {
                                        val v = c?.value ?: "—"
                                        Text(v, style = t.rowPrimary.copy(color = if (c?.value == null) r.sub else statColor(r, c.color, v)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        c?.sub?.let { Text(it, style = t.caption.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
                        }
                        footer?.let { SourceFooter(it) }
                    }
                }
                // ── per-column actions ──
                Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
                Row(Modifier.fillMaxWidth().padding(vertical = CarSpace.actionPad)) {
                    Box(Modifier.width(labelW))
                    shown.forEach { i -> val c = cols[i]
                        Row(Modifier.width(colW).padding(start = CarSpace.gapTight), horizontalArrangement = Arrangement.spacedBy(CarSpace.block)) {
                            CompactButton(c.guide.copy(tag = "cmp_guide_$i"), Modifier.weight(1f))
                            CompactButton(c.open.copy(tag = "cmp_open_$i"), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Columns shown at once per window class (§15): Wide 3 · Compact 2 · Narrow 1. */
fun comparePageSize(c: kr.co.reevl.ontheway.ui.ds.token.WindowClass): Int = when (c) {
    kr.co.reevl.ontheway.ui.ds.token.WindowClass.Wide -> 3
    kr.co.reevl.ontheway.ui.ds.token.WindowClass.Compact -> 2
    kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow -> 1
}
