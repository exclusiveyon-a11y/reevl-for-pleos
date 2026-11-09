package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.NearbyCenter
import kr.co.reevl.ontheway.NearbyRow
import kr.co.reevl.ontheway.NearbyTab
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.NearbyComplexes
import kr.co.reevl.ontheway.data.NearbyOffice
import kr.co.reevl.ontheway.data.NearbyOffices
import kr.co.reevl.ontheway.format.AreaUnit
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.ui.components.AptAvatar
import kr.co.reevl.ontheway.ui.components.BadgeTone
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.DrivingLock
import kr.co.reevl.ontheway.ui.components.EmptyState
import kr.co.reevl.ontheway.ui.components.Hairline
import kr.co.reevl.ontheway.ui.components.IconAction
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.ReevlBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.SkeletonBar
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalDriving

/**
 * NEARBY (v7 §3, §3-A, §3-C 기능 2 · slides 07 & 12) — parked only. Two Pleos Line tabs `단지` / `중개사무소`.
 * 단지: candidates from `GET /api/car/complexes/nearby` around the car's fix; sides computed on the car
 * ([NearbyEngine]) and shown as **candidates** (`오른쪽 후보 · 직선 약 140m`), laid out like the slide: a road with the
 * car in the middle, left candidates on the left, right ones on the right; ahead/behind below. No location → the
 * stop being visited is the centre (`방문 중인 아파트 주변`) and no side is claimed.
 * 중개사무소: verified demo dataset only; `available:false` → `근처 중개사무소 정보는 개발 예정이에요`.
 * Selecting opens CHANGE_CONFIRM. Nothing is added or routed from here.
 */
@Composable
fun NearbyScreen(
    tab: NearbyTab,
    center: NearbyCenter?,
    complexes: NearbyComplexes?,
    rows: List<NearbyRow>,
    offices: NearbyOffices?,
    loading: Boolean,
    message: String?,
    onTab: (NearbyTab) -> Unit,
    onPickComplex: (NearbyRow) -> Unit,
    onPickOffice: (NearbyOffice) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val driving = LocalDriving.current
    val subtitle = when (center) {
        is NearbyCenter.Car -> "현재 위치 주변"
        is NearbyCenter.AtStop -> "방문 중인 아파트 주변 · ${center.stop.name}"
        null -> null
    }
    ScreenFrame(
        tag = "screen_nearby",
        header = { DepthHeader("현재 위치에서 추가", onBack, subtitle = subtitle) { IconAction(Lucide.RefreshCw, "새로고침", onRefresh, tag = "nearby_refresh", enabled = !loading) } },
    ) {
        if (!DrivingPolicy.allows(ParkedAction.Nearby, driving)) {
            Column(Modifier.padding(CarDims.gutter)) { DrivingLock(DrivingPolicy.LOCK_TEXT) }
            return@ScreenFrame
        }
        LineTabs(listOf("단지" to NearbyTab.COMPLEX, "중개사무소" to NearbyTab.OFFICE), tab, onTab)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = CarDims.gutter, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (message != null) Banner(message, BannerKind.Error)
            when (tab) {
                NearbyTab.COMPLEX -> ComplexTab(center, complexes, rows, loading, onPickComplex)
                NearbyTab.OFFICE -> OfficeTab(offices, loading, onPickOffice)
            }
        }
    }
}

/** Pleos Line tab (native settings): 100 dp, label Body L, active = ink ExtraBold + 8 dp bar (Reevl `--brand`). */
@Composable
private fun LineTabs(tabs: List<Pair<String, NearbyTab>>, selected: NearbyTab, onTab: (NearbyTab) -> Unit) {
    val c = CarTheme.colors
    Column(Modifier.fillMaxWidth().background(c.card)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = CarDims.gutter)) {
            tabs.forEach { (label, t) ->
                val on = t == selected
                Column(Modifier.width(280.dp).height(100.dp).clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onTab(t) }
                    .testTag("tab_${t.name.lowercase()}"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = CarTheme.type.title.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) c.ink else c.sub))
                    }
                    Box(Modifier.width(200.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (on) c.brand else c.card))
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun ComplexTab(center: NearbyCenter?, data: NearbyComplexes?, rows: List<NearbyRow>, loading: Boolean, onPick: (NearbyRow) -> Unit) {
    val c = CarTheme.colors
    val t = CarTheme.type
    val wide = LocalWide.current
    if (data == null) {
        if (loading) repeat(3) { SkeletonBar(1200.dp, 120.dp) }
        return
    }
    // Header note: how the sides were made (+ the server's notice, verbatim).
    val heading = (center as? NearbyCenter.Car)?.snapshot?.heading
    val basis = when {
        center is NearbyCenter.AtStop -> "현재 위치를 확인할 수 없어 방문 중인 아파트 주변을 보여 줘요 · 방향 없이 거리순"
        heading == null -> "진행 방향을 확인할 수 없어 좌우 없이 거리순으로 보여 줘요"
        heading.estimated -> "멈추기 직전 진행 방향 기준(방향 추정) · 반경 ${data.radiusM}m"
        else -> "현재 진행 방향 기준 · 반경 ${data.radiusM}m"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("nearby_basis")) {
        Text(basis, style = t.body.copy(color = c.ink2, fontWeight = FontWeight.SemiBold))
        data.notice?.let { Text("$it · 직선거리", style = t.cap, modifier = Modifier.testTag("nearby_notice")) }
    }
    if (rows.isEmpty()) {
        EmptyState("반경 ${data.radiusM}m 안에 리블 단지가 없어요", "다른 곳에서 다시 찾아 주세요.", icon = Lucide.Building2)
        return
    }
    val sided = rows.any { it.side == Side.LEFT || it.side == Side.RIGHT }
    if (wide && sided) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SideHead("왼쪽 후보", rows.count { it.side == Side.LEFT })
                rows.filter { it.side == Side.LEFT }.take(4).forEach { CandidateCard(it, onPick) }
            }
            Road(Modifier.width(140.dp).heightIn(min = 420.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SideHead("오른쪽 후보", rows.count { it.side == Side.RIGHT })
                rows.filter { it.side == Side.RIGHT }.take(4).forEach { CandidateCard(it, onPick) }
            }
        }
        val rest = rows.filter { it.side != Side.LEFT && it.side != Side.RIGHT } +
            rows.filter { it.side == Side.LEFT }.drop(4) + rows.filter { it.side == Side.RIGHT }.drop(4)
        if (rest.isNotEmpty()) {
            SideHead("그 밖의 후보", rest.size)
            Grid2(rest.sortedBy { it.c.distanceM }) { CandidateCard(it, onPick) }
        }
    } else {
        rows.forEach { CandidateCard(it, onPick) }
    }
}

@Composable
private fun SideHead(text: String, n: Int) {
    Text("$text $n", style = CarTheme.type.cap.copy(color = CarTheme.colors.sub, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 8.dp))
}

/** The slide-07 road: a lane with the car (navigation arrow pointing up = the way it was heading). */
@Composable
private fun Road(modifier: Modifier) {
    val c = CarTheme.colors
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(CarDims.radiusCard)).background(c.chip).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Lucide.ChevronRight, null, tint = c.sub, modifier = Modifier.size(36.dp).rotate(-90f))
        Text("진행\n방향", style = CarTheme.type.cap.copy(color = c.sub), textAlign = TextAlign.Center)
        repeat(3) { Box(Modifier.width(8.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(c.lineStrong)) }
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
            Icon(Lucide.Navigation, "내 차", tint = c.onBrand, modifier = Modifier.size(44.dp).rotate(-45f))
        }
        Text("내 차", style = CarTheme.type.cap.copy(color = c.ink2, fontWeight = FontWeight.Bold))
        repeat(2) { Box(Modifier.width(8.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(c.lineStrong)) }
    }
}

@Composable
private fun <T> Grid2(items: List<T>, cell: @Composable (T) -> Unit) {
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
            pair.forEach { Box(Modifier.weight(1f)) { cell(it) } }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/** One candidate: brand avatar + name + `오른쪽 후보 · 직선 약 140m` + latest 매매 line. */
@Composable
private fun CandidateCard(r: NearbyRow, onPick: (NearbyRow) -> Unit) {
    val c = CarTheme.colors
    val t = CarTheme.type
    ReevlCard(Modifier.fillMaxWidth().heightIn(min = CarDims.rowMin).alpha(if (r.inPlan) 0.6f else 1f), onClick = if (r.inPlan) null else ({ onPick(r) }),
        tag = "cand_${r.c.name}", padding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AptAvatar(r.c.name, r.c.cid, r.c.brand, CarDims.avatarList)
            Column(Modifier.weight(1f)) {
                Text(r.c.name, style = t.title.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    NearbyEngine.sideLabel(r.side)?.let { ReevlBadge(it, if (r.side == Side.LEFT || r.side == Side.RIGHT) BadgeTone.Brand else BadgeTone.Neutral, tag = "side_${r.c.name}") }
                    Text(NearbyEngine.straightLine(r.c.distanceM.toDouble()), style = t.body.copy(color = c.ink2, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    if (r.inPlan) ReevlBadge("방문 계획에 있음", BadgeTone.Neutral)
                }
                tradeLine(r.c.latestTrade)?.let { Text(it, style = t.cap, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (!r.inPlan) Icon(Lucide.Plus, "추가", tint = c.brand, modifier = Modifier.size(36.dp))
        }
    }
}

/**
 * `최근 매매 23억 6,500만 · 전용 59㎡ · 26.09.28` (web formatters `wonShort` / `areaFmtBoth(m², ㎡)` / `mdD`; the exact
 * 전용 area instead of the 평 estimate keeps the line inside a half-width card). No trade → no line.
 */
private fun tradeLine(tr: LatestTrade?): String? = tr?.let {
    "최근 매매 ${ReevlFormat.wonShort(it.price.toDouble())} · ${ReevlFormat.areaFmtBoth(it.areaM2, AreaUnit.M2)} · ${ReevlFormat.mdD(it.date)}"
}

@Composable
private fun OfficeTab(data: NearbyOffices?, loading: Boolean, onPick: (NearbyOffice) -> Unit) {
    val c = CarTheme.colors
    val t = CarTheme.type
    if (data == null) {
        if (loading) repeat(3) { SkeletonBar(1200.dp, 120.dp) }
        return
    }
    if (!data.available || data.items.isEmpty()) {
        ReevlCard(Modifier.fillMaxWidth(), tag = "offices_empty") {
            EmptyState(if (!data.available) "근처 중개사무소 정보는 개발 예정이에요" else "반경 안에 확인된 중개사무소가 없어요",
                "확인된 공공 자료가 있는 곳부터 보여 드릴 예정이에요.", icon = Lucide.Building2)
        }
        return
    }
    data.notice?.let { Text(it, style = t.body.copy(color = c.ink2, fontWeight = FontWeight.SemiBold), modifier = Modifier.testTag("offices_notice")) }
    // One full-width row per office (no grid): name, distance + address, phone + registration as the source states it,
    // and the source + checked date on every row (v7: "source + date"; registration ≠ open now ≠ listings).
    data.items.forEach { OfficeCard(it, onPick) }
}

@Composable
private fun OfficeCard(o: NearbyOffice, onPick: (NearbyOffice) -> Unit) {
    val c = CarTheme.colors
    val t = CarTheme.type
    ReevlCard(Modifier.fillMaxWidth().heightIn(min = CarDims.rowMin), onClick = { onPick(o) }, tag = "office_${o.id}", padding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(CarDims.avatarList).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(Lucide.Building2, null, tint = c.sub, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(o.name, style = t.title.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(NearbyEngine.straightLine(o.distanceM.toDouble()), o.address).joinToString(" · "), style = t.body.copy(color = c.ink2),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(listOfNotNull(o.phone?.let { "전화 $it" }, o.registrationNo?.let { "등록번호 $it" }, o.registrationStatus?.let { "등록 상태 $it" }).joinToString(" · "),
                    style = t.body.copy(color = c.sub), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(listOfNotNull(o.source?.let { "출처 $it" }, o.checkedAt?.let { "$it 확인" }).joinToString(" · "),
                    style = t.cap, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Lucide.Plus, "추가", tint = c.brand, modifier = Modifier.size(36.dp))
        }
    }
}
