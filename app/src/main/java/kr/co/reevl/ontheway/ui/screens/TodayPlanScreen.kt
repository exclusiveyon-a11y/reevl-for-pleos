package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.plan.Tour
import kr.co.reevl.ontheway.ui.components.BadgeTone
import kr.co.reevl.ontheway.ui.components.ReevlBadge
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.data.UserMessages
import kr.co.reevl.ontheway.format.ComplexFacts
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.CardHead
import kr.co.reevl.ontheway.ui.components.EmptyState
import kr.co.reevl.ontheway.ui.components.Hairline
import kr.co.reevl.ontheway.ui.components.IconAction
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.MainHeader
import kr.co.reevl.ontheway.ui.components.OrderBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.SkeletonBar
import kr.co.reevl.ontheway.ui.components.Tone
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/** Stops shown in a plan card before `+N곳`. */
private const val ROUTE_MAX = 4

/**
 * TODAY_PLAN — "갈 곳 N곳" + plans.
 * Landscape (2:0): master–detail (native settings/phone pattern) — plan cards left, the selected plan's visit order
 * right (every stop with brand avatar and one facts line) with the one primary `계획 열기`.
 * 1:1: plan cards with the route inside the first card (one stop per line, `+N곳` after four).
 * Empty: phone/Gleo empty-state pattern.
 */
@Composable
fun TodayPlanScreen(
    plans: Synced<List<PlanSummary>>?,
    loading: Boolean,
    message: String?,
    stopsOf: (String) -> List<PlanStop>?,
    complexOf: (String) -> Synced<CarComplex>?,
    /** v7: the whole plan when we have it (tour mode, client pseudonym and appointments live there, not in the summary). */
    planOf: (String) -> Plan? = { null },
    onRefresh: () -> Unit,
    onOpen: (PlanSummary) -> Unit,
    onPreview: (PlanSummary) -> Unit,
    onLogout: () -> Unit,
) {
    val today = LocalDate.now()
    val sorted = plans?.value?.let { PlanLogic.sortForToday(it, today) }
    val line = UserMessages.screenLine(plans, message)
    val wide = LocalWide.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenFrame(
        tag = "screen_today",
        header = { MainHeader("임장 계획", onLogout = onLogout) { IconAction(Lucide.RefreshCw, "새로고침", onRefresh, tag = "pull") } },
    ) {
        val bannerAndHeadline: @Composable () -> Unit = {
            if (line != null) Banner(line, if (plans?.fromCache == true) BannerKind.Offline else BannerKind.Error)
            if (!sorted.isNullOrEmpty()) Text(PlanLogic.headline(sorted.first(), today), style = CarTheme.type.titleLg, modifier = Modifier.testTag("headline"))
        }
        when {
            sorted == null && loading -> Column(Modifier.padding(CarDims.gutter), verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                SkeletonBar(520.dp, 52.dp); repeat(2) { SkeletonBar(1200.dp, 160.dp) }
            }
            sorted.isNullOrEmpty() -> Column(Modifier.padding(CarDims.gutter)) {
                bannerAndHeadline()
                EmptyState("휴대폰에서 계획 만들기", "리블 → 내 계정 → 임장 계획에서 갈 곳을 담으면 여기에 나타나요.", icon = Lucide.Smartphone, modifier = Modifier.testTag("empty"))
            }
            wide -> {
                val selected = sorted.firstOrNull { it.id == selectedId } ?: sorted.first()
                LaunchedEffect(selected.id) { onPreview(selected) }
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                    Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter),
                        verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                        bannerAndHeadline()
                        sorted.forEach { p -> PlanCard(p, selected = p.id == selected.id, stopsOf(p.id), complexOf, showButton = false, full = planOf(p.id)) { selectedId = p.id } }
                    }
                    Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) {
                        RoutePane(selected, stopsOf(selected.id), complexOf, onOpen, planOf(selected.id))
                    }
                }
            }
            else -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(CarDims.gutter),
                verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                bannerAndHeadline()
                sorted.forEachIndexed { i, p -> PlanCard(p, selected = false, stopsOf(p.id), complexOf, showButton = true, primary = i == 0, withRoute = i == 0, full = planOf(p.id)) { onOpen(p) } }
            }
        }
    }
}

@Composable
private fun PlanCard(
    p: PlanSummary, selected: Boolean, stops: List<PlanStop>?, complexOf: (String) -> Synced<CarComplex>?,
    showButton: Boolean, primary: Boolean = false, withRoute: Boolean = false, full: Plan? = null, onTap: () -> Unit,
) {
    val c = CarTheme.colors
    ReevlCard(Modifier.fillMaxWidth(), onClick = onTap, tag = "plan_${p.id}", face = if (selected) c.brandSoft.copy(alpha = 0.6f).compositeOver(c.card) else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text(p.title, style = if (primary) CarTheme.type.titleLg else CarTheme.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${PlanLogic.dateLabel(p.plannedDate)} · ${p.stopCount}곳", style = CarTheme.type.body.copy(color = c.sub), maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp))
                // v7 tour: work-mode badge with the pseudonym + the first appointment (slide 09 "업무 모드 레이블").
                if (Tour.isTour(full)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 14.dp).testTag("plan_tour_${p.id}")) {
                    ReevlBadge(Tour.modeLine(full)!!, BadgeTone.Ink, icon = Lucide.UserRound)
                    full?.let { Schedule.firstAppointment(it) }?.let { ReevlBadge("첫 약속 ${Schedule.hhmm(it)}", BadgeTone.Neutral, icon = Lucide.Clock) }
                }
                if (!withRoute) AvatarRow(stops, p.stopCount, complexOf, Modifier.padding(top = 16.dp))
            }
            if (showButton) CarButton("계획 열기", onTap, tone = if (primary) Tone.Primary else Tone.Secondary, size = if (primary) BtnSize.L else BtnSize.M,
                iconEnd = Lucide.ChevronRight, tag = "open_${p.id}")
        }
        if (withRoute && !stops.isNullOrEmpty()) {
            Box(Modifier.padding(top = 24.dp)) { Hairline() }
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                stops.take(ROUTE_MAX).forEach { s -> RouteStop(s, stops, complexOf, withFacts = false, time = full?.let { Schedule.stopTimeLabel(it, s) }) }
                MoreStops(stops.size)
            }
        }
    }
}

/** Brand avatars of a plan's stops (Reevl AptAvatar 56 dp), side by side, then `+N`. */
@Composable
private fun AvatarRow(stops: List<PlanStop>?, total: Int, complexOf: (String) -> Synced<CarComplex>?, modifier: Modifier = Modifier) {
    val shown = stops.orEmpty().take(3)
    if (shown.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { s -> AptAvatar(s.name, s.cid, s.cid?.let(complexOf)?.value?.brand, CarDims.avatarStack) }
        if (total > shown.size) Text("+${total - shown.size}곳", style = CarTheme.type.label.copy(color = CarTheme.colors.sub), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RouteStop(s: PlanStop, all: List<PlanStop>, complexOf: (String) -> Synced<CarComplex>?, withFacts: Boolean, time: String? = null) {
    val next = all.firstOrNull { it.visitStatus == PlanLogic.PLANNED }
    val cx = s.cid?.let(complexOf)?.value
    Row(Modifier.fillMaxWidth().testTag("route_${s.position}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        OrderBadge(s.position, stateOf(s, next, null))
        AptAvatar(s.name, s.cid, cx?.brand, if (withFacts) CarDims.avatarList else CarDims.avatarStack)
        Column(Modifier.weight(1f)) {
            Text(s.name, style = CarTheme.type.title.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // With an appointment badge on the right the line is shorter: station · 세대 (준공 stays on the detail).
            if (withFacts && !kr.co.reevl.ontheway.ui.theme.LocalDriving.current) (cx?.let { if (time != null) ComplexFacts.summaryLine(it)?.split(" · ")?.take(2)?.joinToString(" · ") else ComplexFacts.summaryLine(it) } ?: s.address)?.let {
                Text(it, style = CarTheme.type.cap, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (time != null) ReevlBadge(time, BadgeTone.Neutral, icon = Lucide.Clock, tag = "route_time_${s.position}")
    }
}

@Composable
private fun MoreStops(total: Int) {
    if (total > ROUTE_MAX) Text("+${total - ROUTE_MAX}곳", style = CarTheme.type.label.copy(color = CarTheme.colors.sub), modifier = Modifier.testTag("route_more"))
}

/** Right pane in landscape: the selected plan's visit order + the screen's one primary action. */
@Composable
private fun RoutePane(p: PlanSummary, stops: List<PlanStop>?, complexOf: (String) -> Synced<CarComplex>?, onOpen: (PlanSummary) -> Unit, full: Plan? = null) {
    ReevlCard(Modifier.fillMaxWidth(), tag = "route_pane") {
        CardHead(if (Tour.isTour(full)) "고객과 방문할 곳" else "방문 순서",
            sub = listOfNotNull(Tour.modeLine(full), "${PlanLogic.dateLabel(p.plannedDate)} · ${p.stopCount}곳").joinToString(" · "))
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (stops == null) repeat(minOf(p.stopCount, 3)) { SkeletonBar(640.dp, 68.dp) }
            else {
                stops.take(ROUTE_MAX + 2).forEach { s -> RouteStop(s, stops, complexOf, withFacts = true, time = full?.let { Schedule.stopTimeLabel(it, s) }) }
                if (stops.size > ROUTE_MAX + 2) Text("+${stops.size - ROUTE_MAX - 2}곳", style = CarTheme.type.label.copy(color = CarTheme.colors.sub))
            }
        }
        Box(Modifier.size(32.dp))
        CarButton("계획 열기", { onOpen(p) }, Modifier.fillMaxWidth(), tone = Tone.Primary, size = BtnSize.L, iconEnd = Lucide.ChevronRight, tag = "open_${p.id}")
    }
}
