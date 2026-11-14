package kr.co.reevl.ontheway.ui.ds.tmpl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.CompactButton
import kr.co.reevl.ontheway.ui.ds.comp.MessageSpec
import kr.co.reevl.ontheway.ui.ds.comp.PlanRouteMap
import kr.co.reevl.ontheway.ui.ds.comp.PlanRouteSpec
import kr.co.reevl.ontheway.ui.ds.comp.RowGroup
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.Skeleton
import kr.co.reevl.ontheway.ui.ds.comp.SourceFooter
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.comp.VoiceHintLine
import kr.co.reevl.ontheway.ui.ds.comp.tag
import kr.co.reevl.ontheway.ui.ds.comp.press
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass
import kr.co.reevl.ontheway.ui.ds.token.ProvidePane
import kr.co.reevl.ontheway.ui.ds.token.WindowClass
import kr.co.reevl.ontheway.ui.ds.token.marginFor
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/**
 * The route block of 오늘 방문: the map, and one glance line under/beside it — [title] `남은 직선 9.0km · 3곳` (the number the
 * driver wants), [sub] `다음 3번 가상푸른숲1단지`, at most one secondary action ([optimize] `순서 정리`, only when a shorter order
 * exists — 안내 on the list row stays THE primary), and the Gleo phrase for the same request (voice first).
 */
data class PlanMapBlock(val route: PlanRouteSpec, val title: String, val sub: String?, val optimize: Btn?, val voiceHint: String?)

/**
 * 오늘 방문 with its route map — laid out like the built-in navigation / phone apps: the map is the hero.
 * - Wide (2:0): list 620 on the driver side (left) │ the map fills the rest of the pane, edge to edge, full height.
 * - 1:1 / narrow: the map edge to edge on top (fixed, 440 / 340), the glance line, then the list scrolls under it.
 * No map (fewer than 2 located stops) → a plain list. Loading / empty → the list template's states.
 */
@Composable
fun PlanTemplate(tag: String, top: TopBarSpec, banner: BannerSpec?, map: PlanMapBlock?, rows: List<RowSpec>, onOpen: (String) -> Unit,
                 footer: String?, empty: MessageSpec?, loading: Boolean, toast: ToastSlot? = null) {
    val cls = LocalWindowClass.current
    val pad = marginFor(cls)
    Frame(tag, top, banner, null, toast, onePane = false) {
        if (empty != null && !(loading && rows.isEmpty())) { CenteredMessage(empty, pad); return@Frame }
        val list: @Composable ColumnScope.() -> Unit = {
            if (loading && rows.isEmpty()) Column(Modifier.padding(top = CarSpace.gapTight), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) { repeat(4) { Skeleton(CarSpace.rowDouble) } }
            else RowGroup(rows.take(12), null, { r -> onOpen(r.key) })
            footer?.let { SourceFooter(it) }
        }
        when {
            map == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin)) { list() }
            cls == WindowClass.Wide -> Row(Modifier.fillMaxSize()) {
                ProvidePane(CarSpace.split) {
                    Column(Modifier.width(CarSpace.split).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin)) {
                        Glance(map, Modifier.padding(top = CarSpace.gapTight, bottom = CarSpace.item))
                        list()
                    }
                }
                PlanRouteMap(map.route, Modifier.weight(1f).fillMaxHeight(), shape = null)
            }
            else -> Column(Modifier.fillMaxSize()) {
                PlanRouteMap(map.route, Modifier.fillMaxWidth().height(if (cls == WindowClass.Narrow) CarSize.Panel.planNarrow else CarSize.Panel.plan), shape = null)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin)) {
                    Glance(map, Modifier.padding(top = CarSpace.gapTight, bottom = CarSpace.item))
                    list()
                }
            }
        }
    }
}

/** `남은 직선 9.0km · 3곳` / `다음 3번 …` + [순서 정리], then the Gleo phrase — the 2-second read of the route. */
@Composable
private fun Glance(m: PlanMapBlock, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            Column(Modifier.weight(1f)) {
                Text(m.title, style = t.value.copy(color = r.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("plan_map_summary"))
                m.sub?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.tag("plan_map_next")) }
            }
            m.optimize?.let { CompactButton(it) }
        }
        m.voiceHint?.let { VoiceHintLine(it, tag = "plan_voice_hint") }
    }
}

/** One side of the 순서 정리 preview: a label on the map (`지금` / `바꾸면`) and its route. */
data class RouteSide(val label: String, val route: PlanRouteSpec, val emphasis: Boolean)

/**
 * 순서 정리 미리보기 (web PlanView preview): the question, the two numbers big (`13.9km → 5.8km`), one note line, 지금 │ 바꾸면 on the
 * real basemap, the new order as rows, and [적용] [취소] in the action bar (the two big buttons of every confirm screen).
 * - Wide: question + numbers + rows left 620 (driver side) │ the two maps stacked, edge to edge, filling the pane height.
 * - 1:1: question + numbers, the two maps side by side (fixed), the rows scroll under them.
 * - Narrow: the same, maps stacked (side by side they were too small for 5 pins).
 */
@Composable
fun OptimizeTemplate(tag: String, top: TopBarSpec, banner: BannerSpec?, question: String, before: String, after: String, saved: String, note: String,
                     left: RouteSide, right: RouteSide, rows: List<RowSpec>, actions: Actions, toast: ToastSlot? = null) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val cls = LocalWindowClass.current
    val pad = marginFor(cls)
    val head: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
            Text(question, style = t.title.copy(color = r.ink), maxLines = 2, modifier = Modifier.padding(top = CarSpace.item).tag("optimize_question"))
            // The answer in one glance: before → after, the saving in brand.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                Text(before, style = t.display.copy(color = r.sub, fontWeight = FontWeight.Normal), maxLines = 1, modifier = Modifier.tag("optimize_before_km"))
                Text("→", style = t.display.copy(color = r.sub, fontWeight = FontWeight.Normal), maxLines = 1)
                Text(after, style = t.display.copy(color = r.brandText, fontWeight = FontWeight.Medium), maxLines = 1, modifier = Modifier.tag("optimize_after_km"))
            }
            if (saved.isNotEmpty()) Text(saved, style = t.rowPrimary.copy(color = r.brandText), maxLines = 1, modifier = Modifier.tag("optimize_saved"))
            Text(note, style = t.rowSecondary.copy(color = r.sub), maxLines = 3, modifier = Modifier.tag("optimize_note"))
        }
    }
    val side: @Composable (RouteSide, Modifier, Boolean) -> Unit = { s, m, rounded ->
        Box(m) {
            PlanRouteMap(s.route, Modifier.fillMaxSize(), shape = if (rounded) RoundedCornerShape(CarSpace.radiusS) else null)
            // Label on the map, top-left: the same pill as the selected chip (REEVL selection face) for 바꾸면, quiet for 지금.
            Text(s.label, style = t.label.copy(color = if (s.emphasis) r.selectInk else r.ink2),
                modifier = Modifier.padding(CarSpace.block).background(if (s.emphasis) r.selectFace else r.surface, RoundedCornerShape(CarSpace.radiusM))
                    .padding(horizontal = CarSize.Inset.s, vertical = CarSpace.item).tag("${s.route.tag}_label"))
        }
    }
    Frame(tag, top, banner, actions, toast, onePane = false) {
        when (cls) {
            WindowClass.Wide -> Row(Modifier.fillMaxSize()) {
                ProvidePane(CarSpace.split) {
                    Column(Modifier.width(CarSpace.split).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin),
                        verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                        head()
                        RowGroup(rows, null, { })
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(CarSpace.hair)) {
                    side(left, Modifier.fillMaxWidth().weight(1f), false)
                    side(right, Modifier.fillMaxWidth().weight(1f), false)
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin),
                verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                head()
                if (cls == WindowClass.Narrow) {
                    side(left, Modifier.fillMaxWidth().height(CarSize.Panel.sideStacked), true)
                    side(right, Modifier.fillMaxWidth().height(CarSize.Panel.sideStacked), true)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                    side(left, Modifier.weight(1f).height(CarSize.Panel.side), true)
                    side(right, Modifier.weight(1f).height(CarSize.Panel.side), true)
                }
                RowGroup(rows, null, { })
            }
        }
    }
}


/** Wide HOME side column: today's route map (tap → 오늘 방문) and its one row (`직선 13.9km · 5곳 · 더 짧은 순서가 있어요`). */
@Composable
fun RouteSideMap(route: PlanRouteSpec, row: RowSpec, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
        // The tap layer sits OVER the map: the MapLibre view would otherwise take the touch before a modifier on its parent.
        Box(Modifier.fillMaxWidth().height(CarSize.Panel.preview)) {
            PlanRouteMap(route, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().press(RoundedCornerShape(CarSpace.radiusS), tag = "home_route", label = "동선 지도", onClick = onOpen))
        }
        RowGroup(listOf(row), null, { onOpen() })
    }
}
