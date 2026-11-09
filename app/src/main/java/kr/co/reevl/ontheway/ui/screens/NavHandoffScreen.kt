package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.Handoff
import kr.co.reevl.ontheway.HandoffPhase
import kr.co.reevl.ontheway.plan.GuideFailureText
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.components.BtnSize
import kr.co.reevl.ontheway.ui.components.CarButton
import kr.co.reevl.ontheway.ui.components.DepthHeader
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.ReevlCard
import kr.co.reevl.ontheway.ui.components.Spinner
import kr.co.reevl.ontheway.ui.components.Tone
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/**
 * NAV_HANDOFF. D2 measured that the navigation comes to the front after every route request (5 s preview), so this
 * screen is mostly what the driver sees when coming BACK — one centred result card (Pleos System popup, Large
 * width 1200): sending → spinner; success → check + destination + the navigation's distance/time; failure →
 * sentence + retry. No raw error codes (D2 review M-10).
 */
@Composable
fun NavHandoffScreen(handoff: Handoff?, onRetry: () -> Unit, onBack: () -> Unit) {
    val c = CarTheme.colors
    val t = CarTheme.type
    ScreenFrame(tag = "handoff", header = { DepthHeader("길 안내", onBack, subtitle = handoff?.title) }) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(CarDims.gutter), contentAlignment = Alignment.Center) {
            ReevlCard(Modifier.widthIn(max = 1200.dp).fillMaxWidth(), padding = 56.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (val ph = handoff?.phase) {
                        null, HandoffPhase.Sending -> {
                            Spinner(c.brand, 72.dp)
                            Text("차량 내비게이션에 목적지를 전달하고 있습니다", style = t.headline, textAlign = TextAlign.Center)
                            handoff?.title?.let { Text(it, style = t.title.copy(color = c.sub)) }
                        }
                        is HandoffPhase.Success -> {
                            ResultIcon(Lucide.Check, c.brandSoft, c.brand)
                            Text("길 안내를 시작했어요", style = t.display, textAlign = TextAlign.Center, modifier = Modifier.testTag("handoff_success"))
                            Text(handoff.title, style = t.titleLg.copy(color = c.ink2), textAlign = TextAlign.Center, modifier = Modifier.testTag("handoff_title"))
                            val route = listOfNotNull(PlanLogic.distance(ph.route?.distanceM), PlanLogic.duration(ph.route?.durationS)).joinToString(" · ")
                            if (route.isNotEmpty()) Text("$route (차량 내비게이션 기준)", style = t.body.copy(color = c.sub))
                            Box(Modifier.size(8.dp))
                            CarButton("목록으로", onBack, tone = Tone.Secondary, size = BtnSize.L, tag = "back_to_list")
                            Text("잠시 후 목록으로 돌아가요", style = t.cap)
                        }
                        is HandoffPhase.Failure -> {
                            ResultIcon(Lucide.CircleAlert, c.upSoft, c.up)
                            Text(GuideFailureText.GENERIC, style = t.headline, textAlign = TextAlign.Center, modifier = Modifier.testTag("handoff_failure"))
                            GuideFailureText.detail(ph.code)?.let { Text(it, style = t.body.copy(color = c.sub), textAlign = TextAlign.Center) }
                            Box(Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap)) {
                                CarButton("다시 시도", onRetry, tone = Tone.Primary, size = BtnSize.L, icon = Lucide.RefreshCw, tag = "retry")
                                CarButton("목록으로", onBack, tone = Tone.Secondary, size = BtnSize.L, tag = "back_to_list")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(112.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(56.dp))
    }
}
