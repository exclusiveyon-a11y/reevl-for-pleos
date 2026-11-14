package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.ontheway.ui.theme.LocalMotion

/**
 * true while the in-app lock covers the window (debug DO builds; release is not DO-declared — the platform blocks the activity
 * instead). Heavy views read it to stop rendering under the lock (PlanRouteMap: the MapLibre view is torn down, not drawn).
 */
val LocalDrivingLock = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Neutral wording, no feature promise (Pleos 주행 규제 정책: DO 미선언 Lifestyle 앱은 주행 중 사용 불가 — sdk-docs-2026-10-19/
 * pleos-only_vehicle-app-planning-guide_driving-restiction-policy.txt). Two short lines, 해요체.
 */
const val LOCK_TITLE = "주행 중에는 쓸 수 없어요"
const val LOCK_SUB = "정차하면 이어서 보여요"

/** §5.14: whole window `app`, Reevl mark 96 (still), one line. Swallows every touch; no animation in or out. */
@Composable
fun LockOverlay(modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    CompositionLocalProvider(LocalMotion provides false) {
        Column(modifier.fillMaxSize().background(r.app).clickable(remember { MutableInteractionSource() }, null) {}.testTag("lock").padding(bottom = CarSpace.gap * 3),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CarSpace.gap, Alignment.CenterVertically)) {
            // §15.3 optical centre: the mark + line sit a little above the geometric centre (measured 54 % down before).
            ReevlMark(CarSpace.lockMark)
            Text(LOCK_TITLE, style = LocalTypeV2.current.display.copy(color = r.ink), textAlign = TextAlign.Center)
            Text(LOCK_SUB, style = LocalTypeV2.current.rowSecondary.copy(color = r.sub), textAlign = TextAlign.Center, modifier = Modifier.testTag("lock_sub"))
            // The Gleo phrase only once Gleo is approved (deliver-schema: "승인 후에는 통합이 자동으로 활성화") — never a promise before.
            if (kr.co.reevl.ontheway.Features.GLEO)
                VoiceHintLine(kr.co.reevl.ontheway.voice.VoiceHints.DRIVING, Modifier.padding(horizontal = CarSpace.margin), tag = "lock_voice_hint")
        }
    }
}



