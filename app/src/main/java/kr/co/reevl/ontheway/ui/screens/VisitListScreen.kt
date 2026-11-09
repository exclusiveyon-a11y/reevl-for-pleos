package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kr.co.reevl.ontheway.ActiveGuide
import kr.co.reevl.ontheway.LastInsert
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.data.UserMessages
import kr.co.reevl.ontheway.format.ComplexFacts
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.plan.Tour
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.ui.StopPart
import kr.co.reevl.ontheway.ui.components.AiGradientText
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.BadgeTone
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.DrivingLock
import kr.co.reevl.ontheway.ui.components.EmptyState
import kr.co.reevl.ontheway.ui.components.IconAction
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.OrderBadge
import kr.co.reevl.ontheway.ui.components.ReevlBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.StopState
import kr.co.reevl.ontheway.ui.components.Tone
import kr.co.reevl.ontheway.ui.components.aiLiveGrad
import kr.co.reevl.ontheway.ui.components.aiSoftGlow
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalDriving
import kr.co.reevl.ontheway.ui.theme.TextUse

/** v7 additions to the list (tour mode, 현장에서 추가, undo, 고객 보기). Callbacks only — state lives in the VM. */
class ListV7(
    val today: LocalDate,
    val eta: (PlanStop?) -> Schedule.Eta?,
    val lastInsert: LastInsert?,
    val notice: String?,
    val onNearby: () -> Unit,
    val onCustomerView: () -> Unit,
    val onUndo: () -> Unit,
    val onGuideMeeting: (PlanStop) -> Unit,
)

/**
 * VISIT_LIST — ordered stops.
 * Landscape (2:0): master–detail like the Pleos native apps (settings / phone: list left, content right) — the stop
 * list on the left, the selected stop's summary on the right (default = the next stop, drawn as the Reevl AI card:
 * ai-soft-glow + ai-live-grad + ai-text-grad label). Tapping a row selects it; `단지 상세` opens STOP_DETAIL.
 * 1:1: next-stop card on top, list below; tapping a row opens STOP_DETAIL.
 * Driving: [DrivingPolicy] — same parts hidden as on STOP_DETAIL; v7 parked-only entries (현재 위치에서 추가, 고객 보기,
 * 실행 취소) are not offered.
 * v7 tour mode (`mode = tour`): work-mode label + client pseudonym, the next appointment card, each stop's time,
 * `만남 장소로 안내` for a verified meeting point, `다음 장소로 안내`.
 */
@Composable
fun VisitListScreen(
    plan: Synced<Plan>?,
    loading: Boolean,
    message: String?,
    busyStopId: String?,
    activeGuide: ActiveGuide?,
    complexOf: (String) -> Synced<CarComplex>?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGuide: (PlanStop) -> Unit,
    onVisited: (PlanStop) -> Unit,
    onSkip: (PlanStop) -> Unit,
    onInfo: (PlanStop) -> Unit,
    onLogout: () -> Unit,
    v7: ListV7,
) {
    val p = plan?.value
    val stops = p?.let { PlanLogic.ordered(it) }.orEmpty()
    val next = p?.let { PlanLogic.nextStop(it) }
    val done = stops.count { it.visitStatus != PlanLogic.PLANNED }
    val line = UserMessages.screenLine(plan, message)
    val wide = LocalWide.current
    val driving = LocalDriving.current
    val tour = Tour.isTour(p)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenFrame(
        tag = "screen_list",
        header = {
            DepthHeader(
                title = p?.title ?: "방문 순서",
                subtitle = p?.let { "${PlanLogic.dateLabel(it.plannedDate)} · ${stops.size}곳 중 ${done}곳 완료" },
                onBack = onBack, onLogout = onLogout,
            ) {
                if (tour && DrivingPolicy.allows(ParkedAction.CustomerView, driving)) {
                    CarButton("고객 보기", v7.onCustomerView, size = BtnSize.M, icon = Lucide.Eye, tag = "customer_view")
                    Spacer(Modifier.size(12.dp))
                }
                IconAction(Lucide.RefreshCw, "새로고침", onRefresh, tag = "pull", enabled = !loading)
            }
        },
    ) {
        if (p == null) {
            Column(Modifier.fillMaxWidth().padding(CarDims.gutter)) {
                if (line != null) Banner(line, BannerKind.Error)
                EmptyState(if (loading) "계획을 불러오는 중이에요" else "계획을 열 수 없어요", "잠시 후 새로고침해 주세요.")
            }
            return@ScreenFrame
        }
        val apptCard = Schedule.appointmentCard(p, v7.eta(next), v7.today)
        val etaAt = activeGuide?.takeIf { it.stopId == next?.id }?.route?.reportedAtMs
        val top: @Composable () -> Unit = {
            if (line != null) Banner(line, if (plan.fromCache) BannerKind.Offline else BannerKind.Error)
            TourModeRow(p)
            if (apptCard != null) AppointmentCard(apptCard, etaAt)
            InsertResult(v7.lastInsert, v7.notice, stops, busyStopId, driving, v7.onUndo, onGuide)
        }
        val addButton: @Composable () -> Unit = {
            if (DrivingPolicy.allows(ParkedAction.Nearby, driving)) {
                CarButton("현재 위치에서 추가", v7.onNearby, Modifier.fillMaxWidth(), icon = Lucide.MapPinPlus, tag = "add_here")
            }
        }
        val guideLabel = PlanLogic.guideLabel(p).let { if (tour && it == PlanLogic.GUIDE_NEXT) "다음 장소로 안내" else it }
        if (wide) {
            val selected = stops.firstOrNull { it.id == selectedId } ?: next ?: stops.firstOrNull()
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 32.dp).testTag("stops"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    top()
                    stops.forEach { s ->
                        StopRow(s, stateOf(s, next, activeGuide), complexOf, selected = s.id == selected?.id, driving = driving,
                            time = Schedule.stopTimeLabel(p, s), isNew = s.id == v7.lastInsert?.stopId) { selectedId = s.id }
                    }
                    addButton()
                }
                // Room for the glow (drawn outside the card): 40 dp around inside the scroll area.
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 40.dp)) {
                    if (selected == null) {
                        ReevlCard(Modifier.fillMaxWidth()) { EmptyState("모든 장소를 다녀왔어요", "휴대폰 리블에서 메모와 비교를 이어 볼 수 있어요.", icon = Lucide.Check) }
                    } else {
                        StopSummary(p, selected, isNext = selected.id == next?.id, guiding = activeGuide?.stopId == selected.id, guide = activeGuide,
                            busy = busyStopId == selected.id, guideLabel = guideLabel, complexOf, onGuide, onVisited, onSkip, onInfo,
                            v7.onGuideMeeting, full = true, modifier = Modifier.fillMaxWidth())
                        // The selected stop's latest trades as their own card under the summary (no card inside a card).
                        val cx = selected.cid?.let(complexOf)?.value
                        if (DrivingPolicy.shows(StopPart.Trades, driving) && cx?.recentTrades?.isNotEmpty() == true) {
                            Spacer(Modifier.size(CarDims.cardGap))
                            TradesCard(cx, loading = false, max = 3)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = CarDims.gutter, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                top()
                if (next != null) StopSummary(p, next, isNext = true, guiding = activeGuide?.stopId == next.id, guide = activeGuide, busy = busyStopId == next.id,
                    guideLabel = guideLabel, complexOf, onGuide, onVisited, onSkip, onInfo, v7.onGuideMeeting, full = false, modifier = Modifier.fillMaxWidth())
                else ReevlCard(Modifier.fillMaxWidth()) { EmptyState("모든 장소를 다녀왔어요", "휴대폰 리블에서 메모와 비교를 이어 볼 수 있어요.", icon = Lucide.Check) }
                Column(Modifier.testTag("stops"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    stops.forEach { s -> StopRow(s, stateOf(s, next, activeGuide), complexOf, selected = false, driving = driving,
                        time = Schedule.stopTimeLabel(p, s), isNew = s.id == v7.lastInsert?.stopId) { onInfo(s) } }
                }
                addButton()
            }
        }
    }
}

/**
 * After `추가`: what changed (server-saved) + `실행 취소` + `이곳으로 안내` — the navigation is never retargeted by itself
 * (v7 §3-C 기능 2 step 6). After an undo: one line that the order is back.
 */
@Composable
private fun InsertResult(li: LastInsert?, notice: String?, stops: List<PlanStop>, busyStopId: String?, driving: Boolean,
                         onUndo: () -> Unit, onGuide: (PlanStop) -> Unit) {
    val added = li?.let { l -> stops.firstOrNull { it.id == l.stopId } }
    if (added != null) {
        ReevlCard(Modifier.fillMaxWidth(), tag = "insert_result") {
            Banner("${added.name}${kr.co.reevl.ontheway.plan.Josa.eulReul(added.name)} ${added.position}번째로 추가했어요", BannerKind.Done)
            Text("방문 순서 ${stops.joinToString(" → ") { it.name }}", style = CarTheme.type.body.copy(color = CarTheme.colors.ink2), maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp).testTag("new_order"))
            if (DrivingPolicy.allows(ParkedAction.EditPlan, driving)) {
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                    CarButton("실행 취소", onUndo, Modifier.weight(1f), icon = Lucide.Undo2, loading = busyStopId == added.id, tag = "undo")
                    CarButton("이곳으로 안내", { onGuide(added) }, Modifier.weight(1f), icon = Lucide.Navigation, enabled = PlanLogic.canGuide(added), tag = "guide_added")
                }
            }
        }
    } else if (notice != null) {
        Banner(notice, BannerKind.Info, icon = Lucide.Undo2, modifier = Modifier.testTag("undo_notice"))
    }
}

fun stateOf(s: PlanStop, next: PlanStop?, guide: ActiveGuide?): StopState = when {
    s.visitStatus == PlanLogic.VISITED -> StopState.Visited
    s.visitStatus == PlanLogic.SKIPPED -> StopState.Skipped
    !PlanLogic.canGuide(s) -> StopState.NeedsLocation
    guide?.stopId == s.id -> StopState.Guiding
    s.id == next?.id -> StopState.Next
    else -> StopState.Planned
}

/** Badge text/tone per state — PlanLogic labels (D2 copy), Reevl Badge tones. */
@Composable
fun StatusBadge(state: StopState, guide: ActiveGuide?, modifier: Modifier = Modifier, tag: String? = null) {
    val (text, tone) = when (state) {
        StopState.Visited -> PlanLogic.LABEL_VISITED to BadgeTone.Pos
        StopState.Skipped -> PlanLogic.LABEL_SKIPPED to BadgeTone.Neutral
        StopState.NeedsLocation -> PlanLogic.NEEDS_LOCATION to BadgeTone.Warn
        StopState.Guiding -> ("안내 중" + (guide?.route?.let { r -> listOfNotNull(PlanLogic.distance(r.distanceM), PlanLogic.duration(r.durationS)).joinToString(" · ").let { if (it.isBlank()) "" else " · $it" } } ?: "")) to BadgeTone.Brand
        StopState.Next -> PlanLogic.LABEL_NEXT to BadgeTone.Brand
        StopState.Planned -> PlanLogic.LABEL_PLANNED to BadgeTone.Neutral
    }
    ReevlBadge(text, tone, modifier, icon = if (state == StopState.Guiding) Lucide.Navigation else null, tag = tag)
}

@Composable
private fun StopRow(s: PlanStop, state: StopState, complexOf: (String) -> Synced<CarComplex>?, selected: Boolean, driving: Boolean,
                    time: String? = null, isNew: Boolean = false, onTap: () -> Unit) {
    val c = CarTheme.colors
    val cx = s.cid?.let(complexOf)?.value
    // Selected row = Reevl TradeHighlights selected row (`bg-brand-soft/60`, TradeHighlights.tsx:103).
    ReevlCard(Modifier.fillMaxWidth().heightIn(min = CarDims.rowMin), onClick = onTap, tag = "stop_${s.position}", padding = 24.dp,
        face = if (selected) c.brandSoft.copy(alpha = 0.6f).compositeOver(c.card) else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OrderBadge(s.position, state)
            PlaceAvatar(s.kind, s.name, s.cid, cx?.brand, CarDims.avatarList)
            Column(Modifier.weight(1f)) {
                Text(s.name, style = CarTheme.type.title.copy(fontWeight = FontWeight.ExtraBold,
                    color = if (state == StopState.Skipped) c.sub else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    StatusBadge(state, null, tag = "status_${s.position}")
                    if (isNew) ReevlBadge("새로 추가", BadgeTone.Ink, icon = Lucide.Plus, tag = "new_${s.position}")
                    if (time != null) ReevlBadge(time, BadgeTone.Neutral, icon = Lucide.Clock, tag = "time_${s.position}")
                    else if (!isNew && DrivingPolicy.shows(StopPart.Facts, driving)) Text(rowFact(s, cx), style = CarTheme.type.cap, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Lucide.ChevronRight, "선택", tint = if (selected) c.brand else c.sub, modifier = Modifier.size(36.dp))
        }
    }
}

/** One short fact per row (glanceable): nearest station, else the address. */
private fun rowFact(s: PlanStop, cx: CarComplex?): String =
    cx?.nearestStation?.let { st -> listOfNotNull(ReevlFormat.stName(st.name), st.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }).joinToString(" ") }
        ?: s.address.orEmpty()

/**
 * The selected/next stop. [full] (landscape right pane) adds 핵심 정보 and the latest trades so the pane carries the
 * content (native master–detail); compact (1:1) keeps one facts line. While driving: identity + guide + lock line.
 */
@Composable
private fun StopSummary(
    plan: Plan, s: PlanStop, isNext: Boolean, guiding: Boolean, guide: ActiveGuide?, busy: Boolean, guideLabel: String,
    complexOf: (String) -> Synced<CarComplex>?,
    onGuide: (PlanStop) -> Unit, onVisited: (PlanStop) -> Unit, onSkip: (PlanStop) -> Unit, onInfo: (PlanStop) -> Unit,
    onGuideMeeting: (PlanStop) -> Unit,
    full: Boolean, modifier: Modifier,
) {
    val c = CarTheme.colors
    val t = CarTheme.type
    val driving = LocalDriving.current
    val cx = s.cid?.let(complexOf)?.value
    val canGuide = PlanLogic.canGuide(s)
    val shape = RoundedCornerShape(CarDims.radiusCard)
    val planned = s.visitStatus == PlanLogic.PLANNED
    val tour = Tour.isTour(plan)
    // AI card for the next stop (globals.css .ai-soft-glow: glow only, no border, + flowing .ai-live-grad tint).
    Column(
        modifier
            .then(if (isNext) Modifier.aiSoftGlow(CarDims.radiusCard) else Modifier)
            .clip(shape)
            .background(c.card)
            .then(if (isNext) Modifier.aiLiveGrad() else Modifier)
            .padding(CarDims.cardPad)
            .testTag("next_card"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val label = when {
            guiding && guide?.target == kr.co.reevl.ontheway.GuideTarget.MEETING_POINT -> "만남 장소로 안내 중"
            guiding -> "안내 중인 장소"
            isNext -> "${s.position}번째 · 다음 방문"
            else -> "${s.position}번째 · ${PlanLogic.statusLabel(s, null)}"
        }
        if (isNext || guiding) AiGradientText(label, TextUse.aiLabel(t)) else Text(label, style = TextUse.aiLabel(t).copy(color = c.sub))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PlaceAvatar(s.kind, s.name, s.cid, cx?.brand, CarDims.avatarHero)
            Column(Modifier.weight(1f)) {
                Text(s.name, style = t.titleLg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (DrivingPolicy.shows(StopPart.Address, driving)) Text(s.address.orEmpty(), style = t.body.copy(color = c.sub), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (tour && DrivingPolicy.shows(StopPart.Facts, driving)) {
            val appt = Schedule.appointment(s)
            if (appt != null || s.meetingPoint != null) {
                Text(listOfNotNull(appt?.let { "약속 ${Schedule.hhmm(it)}" }, Schedule.meetingLine(s.meetingPoint)).joinToString(" · "),
                    style = t.body.copy(color = c.ink2, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_appt"))
            }
        }
        if (DrivingPolicy.shows(StopPart.Facts, driving)) {
            if (full) FactsSection(cx, loading = cx == null, stop = s, keys = setOf("households", "built", "station", "jeonse"))
            else cx?.let { k -> ComplexFacts.summaryLine(k) }?.let { Text(it, style = t.body.copy(color = c.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (guiding) StatusBadge(StopState.Guiding, guide, tag = "guiding")
        if (!canGuide) Banner("위치 확인이 필요해 길 안내를 보낼 수 없어요. 휴대폰 리블에서 위치를 확인해 주세요.", BannerKind.Offline)
        if (!DrivingPolicy.shows(StopPart.Facts, driving)) DrivingLock(DrivingPolicy.LOCK_TEXT)
        // I-3/M-9: while this stop is being guided the main action is no longer "send again".
        if (planned || guiding) {
            if (guiding) CarButton("다시 안내", { onGuide(s) }, Modifier.fillMaxWidth(), tone = Tone.Secondary, size = BtnSize.L, icon = Lucide.Navigation, tag = "guide_next")
            else CarButton(if (isNext) guideLabel else "길 안내", { onGuide(s) }, Modifier.fillMaxWidth(), tone = Tone.Primary, size = BtnSize.L,
                enabled = canGuide, icon = Lucide.Navigation, tag = "guide_next")
        }
        // v7 기능 4: only a server-verified meeting point gets the button (verified:false → none).
        if (tour && planned && Schedule.canGuideToMeeting(s.meetingPoint)) {
            CarButton("만남 장소로 안내", { onGuideMeeting(s) }, Modifier.fillMaxWidth(), tone = Tone.Secondary, size = BtnSize.L,
                icon = Lucide.Handshake, tag = "guide_meeting")
        }
        if (DrivingPolicy.shows(StopPart.VisitActions, driving)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                if (DrivingPolicy.shows(StopPart.DetailButton, driving)) CarButton("단지 상세", { onInfo(s) }, Modifier.weight(1f), iconEnd = Lucide.ChevronRight, tag = "open_detail")
                if (planned) {
                    CarButton("방문 완료", { onVisited(s) }, Modifier.weight(1f), loading = busy, tag = "visited_${s.position}")
                    CarButton("건너뛰기", { onSkip(s) }, Modifier.weight(1f), tone = Tone.Quiet, enabled = !busy, tag = "skip_${s.position}")
                }
            }
        }
    }
}
