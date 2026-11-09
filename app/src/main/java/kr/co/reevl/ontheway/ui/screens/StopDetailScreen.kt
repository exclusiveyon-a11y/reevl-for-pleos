package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ActiveGuide
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.data.UserMessages
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.plan.Tour
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.StopPart
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.DrivingLock
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.StopState
import kr.co.reevl.ontheway.ui.components.Tone
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalDriving

/**
 * STOP_DETAIL — car complex detail, `GET /api/car/complex/{cid}` (A7). Web structure (memory
 * reevl-complex-detail-structure, ComplexPanel): header line (avatar + name + sub) → TradeHighlights → KeyStats.
 * Landscape: hero + 핵심 정보 left, 최근 실거래 right. 1:1: hero → 최근 실거래 → 핵심 정보 (the web order — the price,
 * the 임장 user's first question, is above the fold). Header title is '단지 상세', not the name.
 * Parked only: [DrivingPolicy] — the same rule as the visit list.
 */
@Composable
fun StopDetailScreen(
    plan: Synced<Plan>?,
    stopId: String?,
    complex: Synced<CarComplex>?,
    loading: Boolean,
    message: String?,
    busyStopId: String?,
    activeGuide: ActiveGuide?,
    onBack: () -> Unit,
    onGuide: (PlanStop) -> Unit,
    onVisited: (PlanStop) -> Unit,
    onSkip: (PlanStop) -> Unit,
    onRetry: (PlanStop) -> Unit,
    onLogout: () -> Unit,
    /** v7 기능 4 — offered only in tour mode for a server-verified meeting point. */
    onGuideMeeting: (PlanStop) -> Unit = {},
) {
    val p = plan?.value
    val stop = p?.stops?.firstOrNull { it.id == stopId }
    val next = p?.let { PlanLogic.nextStop(it) }
    val driving = LocalDriving.current
    val wide = LocalWide.current
    val state = stop?.let { stateOf(it, next, activeGuide) }
    ScreenFrame(
        tag = "screen_detail",
        header = {
            DepthHeader("단지 상세", onBack, subtitle = stop?.let { "${it.position}번째 방문 · ${PlanLogic.statusLabel(it, next)}" }, onLogout = onLogout)
        },
        actions = stop?.let { s ->
            {
                val canGuide = PlanLogic.canGuide(s)
                val guiding = activeGuide?.stopId == s.id
                CarButton(if (guiding) "다시 안내" else "길 안내", { onGuide(s) }, Modifier.weight(1.4f),
                    tone = if (guiding) Tone.Secondary else Tone.Primary, size = BtnSize.L, enabled = canGuide, icon = Lucide.Navigation, tag = "guide")
                if (Tour.isTour(p) && s.visitStatus == PlanLogic.PLANNED && Schedule.canGuideToMeeting(s.meetingPoint)) {
                    CarButton("만남 장소로 안내", { onGuideMeeting(s) }, Modifier.weight(1.2f), size = BtnSize.L, icon = Lucide.Handshake, tag = "detail_guide_meeting")
                }
                if (DrivingPolicy.shows(StopPart.VisitActions, driving) && s.visitStatus == PlanLogic.PLANNED) {
                    CarButton("방문 완료", { onVisited(s) }, Modifier.weight(1f), size = BtnSize.L, loading = busyStopId == s.id, tag = "visited")
                    CarButton("건너뛰기", { onSkip(s) }, Modifier.weight(1f), tone = Tone.Quiet, size = BtnSize.L, enabled = busyStopId == null, tag = "skip")
                }
            }
        },
    ) {
        if (stop == null) {
            Column(Modifier.padding(CarDims.gutter)) { Banner(UserMessages.PLAN_NOT_FOUND, BannerKind.Error) }
            return@ScreenFrame
        }
        val c = complex?.value
        val line = UserMessages.screenLine(complex, message)
        if (!DrivingPolicy.shows(StopPart.Facts, driving)) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(CarDims.gutter), verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                ReevlCard(Modifier.fillMaxWidth()) { Hero(stop, c, state, showAddress = false) }
                DrivingLock(DrivingPolicy.LOCK_TEXT)
            }
            return@ScreenFrame
        }
        val heroCard: @Composable () -> Unit = {
            ReevlCard(Modifier.fillMaxWidth(), tag = "detail_hero") {
                Hero(stop, c, state, showAddress = true)
                if (Tour.isTour(p)) {
                    val appt = Schedule.appointment(stop)
                    Text(listOfNotNull(appt?.let { "약속 ${Schedule.hhmm(it)}" }, Schedule.meetingLine(stop.meetingPoint)).joinToString(" · "),
                        style = CarTheme.type.body.copy(color = CarTheme.colors.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 16.dp).testTag("detail_appt"))
                }
                if (line != null) Banner(line, if (complex?.fromCache == true) BannerKind.Offline else BannerKind.Error)
                if (wide) FactsSection(c, loading, stop)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) { heroCard() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) { TradesCard(c, loading) }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(CarDims.gutter),
                verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                heroCard()
                TradesCard(c, loading)
                if (stop.cid != null) ReevlCard(Modifier.fillMaxWidth()) { FactsSection(c, loading, stop) }
            }
        }
    }
}

/** ComplexPanel header line (ComplexPanel.tsx:665-690): AptAvatar + name `t-headline extrabold` + one sub line. */
@Composable
fun Hero(stop: PlanStop, c: CarComplex?, state: StopState?, showAddress: Boolean) {
    val col = CarTheme.colors
    val t = CarTheme.type
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        AptAvatar(c?.name ?: stop.name, stop.cid, c?.brand, CarDims.avatarHero)
        Column(Modifier.weight(1f)) {
            Text(c?.name ?: stop.name, style = t.titleLg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("detail_name"))
            if (showAddress) {
                Text(c?.address ?: stop.address.orEmpty(), style = t.body.copy(color = col.sub), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
                Text("대표 위치 기준 · 주차장 입구와 다를 수 있어요", style = t.cap, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (state != null) StatusBadge(state, null)
    }
}
