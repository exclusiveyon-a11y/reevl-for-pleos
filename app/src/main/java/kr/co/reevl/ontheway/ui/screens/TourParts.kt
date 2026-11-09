package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.plan.Tour
import kr.co.reevl.ontheway.ui.components.BadgeTone
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.ReevlBadge
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.theme.CarTheme

// v7 PBV 고객 투어 모드 parts. Vocabulary = the existing Reevl car kit only (ReevlBadge Ink = Badge.tsx `ink` tone —
// chips are "blue or black", memory reevl-chip-design; ReevlCard + CardHead hierarchy; Banner tones; StatRow-like
// cells on the `--chip` face). No new colours.

/** Work-mode label (slide 09: "동일한 방문 계획 리스트 위에 `고객 동행 투어`라는 업무 모드 레이블" — renamed `고객 투어 모드` by the 2026-10-28 brand decision). */
@Composable
fun TourModeRow(plan: Plan?, modifier: Modifier = Modifier) {
    if (!Tour.isTour(plan)) return
    Row(modifier.testTag("tour_mode"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ReevlBadge(Tour.MODE_LABEL, BadgeTone.Ink, icon = Lucide.UserRound)
        Tour.clientLabel(plan)?.let { Text(it, style = CarTheme.type.headline, maxLines = 1, modifier = Modifier.testTag("client_label")) }
    }
}

/**
 * `다음 약속 14:00 · <단지명>` + meeting place + (navi-verified ETA only) 고객 약속 / 차량 내비 도착 예정 cells + the §3-B
 * sentence. Not today → the time and the day, no comparison.
 */
@Composable
fun AppointmentCard(card: Schedule.AppointmentCard, etaReportedAt: Long?, modifier: Modifier = Modifier) {
    val c = CarTheme.colors
    val t = CarTheme.type
    ReevlCard(modifier.fillMaxWidth(), tag = "appt_card") {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Lucide.Clock, null, tint = c.brand, modifier = Modifier.padding(top = 2.dp).size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, style = t.headline, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("appt_title"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Lucide.Handshake, null, tint = c.sub, modifier = Modifier.size(28.dp))
                    Text(card.meetingLine, style = t.body.copy(color = c.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("appt_meeting"))
                }
            }
        }
        if (card.etaLine != null) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeCell("고객 약속", Schedule.hhmm(card.appointment), Modifier.weight(1f))
                TimeCell("차량 내비 도착 예정", card.etaLine.removePrefix("도착 예정 "), Modifier.weight(1f), tag = "appt_eta")
            }
            etaReportedAt?.let {
                Text("차량 내비게이션이 ${Schedule.hhmm(java.time.Instant.ofEpochMilli(it))}에 알려 준 남은 시간 기준", style = t.cap, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (card.dayNote != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                ReevlBadge(card.dayNote, BadgeTone.Neutral, icon = Lucide.Calendar, tag = "appt_day")
                Text("당일 안내 중에 차량 내비 도착 예정과 비교해요", style = t.cap, maxLines = 2)
            }
        }
        if (card.sentence != null) {
            Banner(card.sentence, if (card.warn) BannerKind.Warn else BannerKind.Info, Modifier.padding(top = 20.dp).testTag("appt_sentence"), icon = Lucide.Clock)
        }
    }
}

/**
 * A stop's avatar: complexes keep the Reevl brand avatar (AptAvatar); an inserted broker office (no cid, no brand)
 * gets the same office mark NEARBY uses — not a made-up initial colour.
 */
@Composable
fun PlaceAvatar(kind: String, name: String, cid: String?, brand: kr.co.reevl.ontheway.data.CarBrand?, size: androidx.compose.ui.unit.Dp) {
    if (kind == "real_estate_office" && cid == null) {
        val c = CarTheme.colors
        androidx.compose.foundation.layout.Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(c.chip),
            contentAlignment = Alignment.Center) {
            Icon(Lucide.Building2, null, tint = c.sub, modifier = Modifier.size(size / 2))
        }
    } else {
        kr.co.reevl.ontheway.ui.components.AptAvatar(name, cid, brand, size)
    }
}

@Composable
private fun TimeCell(label: String, value: String, modifier: Modifier = Modifier, tag: String? = null) {
    val c = CarTheme.colors
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(c.chip).padding(horizontal = 24.dp, vertical = 16.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Text(label, style = CarTheme.type.cap.copy(color = c.sub, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(value, style = CarTheme.type.valueLg.copy(color = c.ink), maxLines = 1)
    }
}
