package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.PendingAdd
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.plan.Insert
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.plan.Tour
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.BadgeTone
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.CardHead
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.DrivingLock
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.OrderBadge
import kr.co.reevl.ontheway.ui.components.ReevlBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.StopState
import kr.co.reevl.ontheway.ui.components.Tone
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalDriving

/**
 * CHANGE_CONFIRM (v7 master §9-2: "A 다음에 중개사무소를 방문할까요?" · `추가` / `취소`). One question + the new order with
 * the new place marked + a fixed action row (memory feedback-vehicle-ui-craft). In tour mode each stop keeps its
 * appointment time so the broker sees what the change does to the remaining appointments (slide 12). Parked only.
 */
@Composable
fun ChangeConfirmScreen(
    plan: Plan?,
    add: PendingAdd?,
    busy: Boolean,
    message: String?,
    complexOf: (String) -> Synced<CarComplex>?,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val driving = LocalDriving.current
    val allowed = DrivingPolicy.allows(ParkedAction.ChangeConfirm, driving)
    ScreenFrame(
        tag = "screen_confirm",
        header = { DepthHeader("방문 순서 바꾸기", onCancel, subtitle = add?.name) },
        actions = if (!allowed || plan == null || add == null) null else ({
            CarButton("추가", onAdd, Modifier.weight(1.4f), tone = Tone.Primary, size = BtnSize.L, icon = Lucide.Plus, loading = busy, tag = "confirm_add")
            CarButton("취소", onCancel, Modifier.weight(1f), size = BtnSize.L, enabled = !busy, tag = "confirm_cancel")
        }),
    ) {
        if (!allowed) {
            Column(Modifier.padding(CarDims.gutter)) { DrivingLock(DrivingPolicy.LOCK_TEXT) }
            return@ScreenFrame
        }
        if (plan == null || add == null) return@ScreenFrame
        val wide = LocalWide.current
        val question: @Composable () -> Unit = {
            Text(Insert.question(plan, add.name), style = CarTheme.type.titleLg, modifier = Modifier.testTag("confirm_question"))
            ReevlCard(Modifier.fillMaxWidth().padding(top = 24.dp), tag = "confirm_place") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AptAvatar(add.name, add.cid, add.brand ?: add.cid?.let(complexOf)?.value?.brand, CarDims.avatarHero)
                    Column(Modifier.weight(1f)) {
                        Text(add.name, style = CarTheme.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        add.sub?.let { Text(it, style = CarTheme.type.body.copy(color = CarTheme.colors.sub), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
            Text("추가해도 차량 내비게이션 목적지는 바뀌지 않아요. 안내는 목록에서 직접 시작해요.", style = CarTheme.type.body.copy(color = CarTheme.colors.sub),
                modifier = Modifier.padding(top = 20.dp))
            if (message != null) Banner(message, BannerKind.Error, Modifier.padding(top = 16.dp))
        }
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) { question() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = CarDims.gutter)) { NewOrder(plan, add, complexOf) }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(CarDims.gutter),
                verticalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                question()
                NewOrder(plan, add, complexOf)
            }
        }
    }
}

@Composable
private fun NewOrder(plan: Plan, add: PendingAdd, complexOf: (String) -> Synced<CarComplex>?) {
    val c = CarTheme.colors
    val ordered = PlanLogic.ordered(plan)
    val rows = Insert.preview(plan, add.name)
    val tour = Tour.isTour(plan)
    ReevlCard(Modifier.fillMaxWidth(), tag = "confirm_order") {
        CardHead("새 방문 순서", sub = if (tour) "고객 요청으로 바뀌는 순서 · 남은 약속 시각은 그대로예요" else "방문한 곳은 그대로 두고 그다음에 넣어요")
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var k = 0
            rows.forEach { r ->
                val s = if (r.isNew) null else ordered.getOrNull(k++)
                val state = when {
                    r.isNew -> StopState.Next
                    s?.visitStatus == PlanLogic.VISITED -> StopState.Visited
                    s?.visitStatus == PlanLogic.SKIPPED -> StopState.Skipped
                    else -> StopState.Planned
                }
                ReevlCard(Modifier.fillMaxWidth().heightIn(min = 104.dp), padding = 20.dp, tag = "preview_${r.position}",
                    face = if (r.isNew) c.brandSoft.copy(alpha = 0.6f).compositeOver(c.card) else c.page) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        OrderBadge(r.position, state)
                        AptAvatar(r.name, if (r.isNew) add.cid else s?.cid, if (r.isNew) add.brand else s?.cid?.let(complexOf)?.value?.brand, CarDims.avatarStack)
                        Text(r.name, style = CarTheme.type.title.copy(fontWeight = if (r.isNew) FontWeight.ExtraBold else FontWeight.Bold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        when {
                            r.isNew -> ReevlBadge("새로 추가", BadgeTone.Ink, icon = Lucide.Plus)
                            s?.visitStatus == PlanLogic.VISITED -> ReevlBadge(PlanLogic.LABEL_VISITED, BadgeTone.Pos)
                            tour && s != null -> Schedule.appointment(s)?.let { ReevlBadge("${Schedule.hhmm(it)} 약속", BadgeTone.Neutral, icon = Lucide.Clock) }
                        }
                    }
                }
            }
        }
    }
}
