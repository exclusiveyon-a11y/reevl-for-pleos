package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.AiGradientText
import kr.co.reevl.ontheway.ui.components.AiIcon
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.aiChipFill
import kr.co.reevl.ontheway.ui.components.aiSoftGlow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/**
 * The voice panel under the AI field (voice/Voice.kt). Flat (no card): mic mark + one title line, then what to say.
 * [listening] = in-app speech running: [heard] is the live text and the action is `그만`.
 */
data class VoicePanelSpec(val title: String, val lines: List<String>, val note: String?, val listening: Boolean, val heard: String,
                          val onClose: () -> Unit)

@Composable
fun VoicePanel(s: VoicePanelSpec) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(Modifier.fillMaxWidth().tag("voice_panel").padding(top = CarSpace.item), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.section)) {
            Box(Modifier.size(CarSpace.avatar).background(r.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
                AiIcon(Lucide.Mic, null, Modifier.size(CarSpace.icon))
            }
            // The AI speaking = `.ai-text-grad` (web palette label "검색 — 자연어로 물어보세요", CommandPalette.tsx:998).
            AiGradientText(s.title, t.rowPrimary, Modifier.weight(1f))
            IconBtn(IconSpec(Lucide.X, if (s.listening) "그만" else "닫기", s.onClose, tag = "voice_close"))
        }
        if (s.listening) Text(s.heard.ifEmpty { "…" }, style = t.display.copy(color = r.ink), maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = CarSpace.item).tag("voice_heard"))
        else s.lines.forEachIndexed { i, l ->
            Text(l, style = t.rowPrimary.copy(color = r.brandText), maxLines = 2, modifier = Modifier.padding(start = CarSpace.item).tag("voice_say_$i"))
        }
        s.note?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), maxLines = 2, modifier = Modifier.padding(start = CarSpace.item)) }
        Box(Modifier.fillMaxWidth().padding(top = CarSpace.item).height(CarSpace.hairline).background(r.line))
    }
}

/** One-tap requests (no keyboard): Compact buttons in a flow, ≥ 100 hit each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestChips(items: List<String>, onTap: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    FlowRow(modifier.fillMaxWidth().tag("suggest_chips"), horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
        items.forEachIndexed { i, q -> AiExampleChip(q, { onTap(q) }, "chip_suggest_$i") }
    }
}

/**
 * The web's AI example chip (CommandPalette.tsx:75 ExampleChips): `ai-soft-glow ai-chip-hover rounded-lg bg-bg/60 text-sub` — no
 * border, the glow says "AI". A car has no hover: the flowing `ai-chip-hover` fill shows while the chip is held. Hit 100, visual 80.
 */
@Composable
fun AiExampleChip(text: String, onClick: () -> Unit, tag: String) {
    val r = LocalRoles.current
    val shape = RoundedCornerShape(CarSpace.radiusM)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(Modifier.height(CarSpace.hit).clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick).tag(tag),
        contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { val k = if (pressed) 0.95f else 1f; scaleX = k; scaleY = k }
            .height(CarSpace.chipVisual).aiSoftGlow(CarSpace.radiusM).background(r.surface, shape).clip(shape).aiChipFill(pressed)
            .padding(horizontal = CarSize.Inset.l), contentAlignment = Alignment.Center) {
            Text(text, style = LocalTypeV2.current.label.copy(color = if (pressed) r.ink else r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One quiet line: mic + the Gleo phrase (HOME, lock). */
@Composable
fun VoiceHintLine(text: String, modifier: Modifier = Modifier, tag: String = "voice_hint") {
    val r = LocalRoles.current
    Row(modifier.tag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.block)) {
        Icon(Lucide.Mic, null, tint = r.sub, modifier = Modifier.size(CarSpace.iconSmall))
        // 2 lines in a narrow window rather than an ellipsis in the middle of what to say.
        Text(text, style = LocalTypeV2.current.rowSecondary.copy(color = r.sub), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
