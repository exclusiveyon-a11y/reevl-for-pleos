package kr.co.reevl.ontheway.ui.screens

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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.format.ComplexFacts
import kr.co.reevl.ontheway.plan.CustomerView
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.DrivingLock
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.OrderBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.StopState
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalDriving

/**
 * 고객 보기 (v7 PBV 03 / slide 11, §2 "고객 공유 화면") — what the broker may show the client, parked only: the visit
 * order, names, brand avatars, public complex facts (KeyStats rows) and confirmed recent trades. It never shows the
 * client pseudonym, appointment times, meeting places, notes, the plan title or any other plan ([CustomerView]).
 * One tap (`중개사 화면으로`) returns to the broker view.
 */
@Composable
fun CustomerViewScreen(plan: Plan?, complexOf: (String) -> Synced<CarComplex>?, onBack: () -> Unit) {
    val driving = LocalDriving.current
    ScreenFrame(
        tag = "screen_customer",
        header = {
            DepthHeader("고객 보기", onBack, subtitle = "공개 단지 정보만 보여요 · 메모·약속·다른 고객 정보 없음", backTag = "customer_back") {
                CarButton("중개사 화면으로", onBack, size = BtnSize.M, icon = Lucide.UserRound, tag = "broker_view")
            }
        },
    ) {
        if (!DrivingPolicy.allows(ParkedAction.CustomerView, driving)) {
            Column(Modifier.padding(CarDims.gutter)) { DrivingLock(DrivingPolicy.LOCK_TEXT) }
            return@ScreenFrame
        }
        if (plan == null) return@ScreenFrame
        val items = CustomerView.items(plan)
        val stops = PlanLogic.ordered(plan).filter { it.visitStatus != PlanLogic.SKIPPED }
        var selected by rememberSaveable { mutableStateOf<Int?>(null) }
        val pick = stops.getOrNull((selected ?: 1) - 1) ?: stops.firstOrNull()
        val wide = LocalWide.current
        val list: @Composable () -> Unit = {
            Text(CustomerView.headline(plan), style = CarTheme.type.titleLg, modifier = Modifier.testTag("customer_headline"))
            items.forEachIndexed { i, it ->
                val s = stops.getOrNull(i)
                val cx = it.cid?.let(complexOf)?.value
                ReevlCard(Modifier.fillMaxWidth().heightIn(min = CarDims.rowMin), onClick = { selected = it.position }, tag = "cv_${it.position}", padding = 24.dp,
                    face = if (wide && s?.id == pick?.id) CarTheme.colors.brandSoft.copy(alpha = 0.6f).compositeOver(CarTheme.colors.card) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        OrderBadge(it.position, if (it.visitStatus == PlanLogic.VISITED) StopState.Visited else StopState.Planned)
                        PlaceAvatar(it.kind, it.name, it.cid, cx?.brand, CarDims.avatarList)
                        Column(Modifier.weight(1f)) {
                            Text(it.name, style = CarTheme.type.title.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            (cx?.let { k -> ComplexFacts.summaryLine(k)?.split(" · ")?.take(2)?.joinToString(" · ") } ?: PlanLogic.kindLabel(it.kind)).let { line ->
                                Text(line, style = CarTheme.type.cap, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        if (wide) Icon(Lucide.ChevronRight, null, tint = CarTheme.colors.sub, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) { list() }
                Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) {
                    if (pick != null) PublicFacts(pick, pick.cid?.let(complexOf))
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(CarDims.gutter),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                list()
                if (pick != null) { Spacer(Modifier.size(8.dp)); PublicFacts(pick, pick.cid?.let(complexOf)) }
            }
        }
    }
}

/** Public facts of one stop: hero (no status), 핵심 정보, 최근 실거래 — the same parts as STOP_DETAIL, nothing private. */
@Composable
private fun PublicFacts(stop: PlanStop, complex: Synced<CarComplex>?) {
    val c = complex?.value
    // A copy without anything private, so even the shared Hero/Facts parts cannot show it.
    val public = stop.copy(note = null, appointmentAt = null, meetingPoint = null, phone = null)
    ReevlCard(Modifier.fillMaxWidth(), tag = "cv_detail") {
        Hero(public, c, null, showAddress = true)
        FactsSection(c, loading = c == null, stop = public)
    }
    if (public.cid != null) {
        Spacer(Modifier.size(CarDims.cardGap))
        TradesCard(c, loading = c == null, max = 3)
    }
}
