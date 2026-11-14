package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.ontheway.ui.theme.LocalMotion
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** A button as data: 2–4 character verb, optional leading icon. */
data class Btn(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val icon: ImageVector? = null,
    val tag: String? = null,
    /** The AI action (찾기). v2.2: every primary is the REEVL brand face, so this only marks the AI action for tests/semantics. */
    val ai: Boolean = false,
    /** Icon alone, [label] becomes its a11y name (narrow window: `말하기` did not fit 128 and showed `말…`). */
    val iconOnly: Boolean = false,
)

/** TopBar / pane-header icon action; [text] makes it a labelled toggle (`저장`). [on] = toggled (brandText). */
data class IconSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val on: Boolean = false,
    val text: String? = null,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val tag: String? = null,
)

private enum class Kind { Primary, Secondary, Compact }

@Composable
private fun DsButton(b: Btn, kind: Kind, modifier: Modifier, fill: Boolean = false) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val active = b.enabled && !b.loading
    // Disabled = the same face at 40 % (web Button `disabled:opacity-40`, Button.tsx:50) — never a grey look-alike.
    val (face, fg) = when {
        kind == Kind.Primary && b.ai -> r.aiFace to r.onAi
        kind == Kind.Primary -> r.primaryFace to r.onPrimary
        else -> r.secondaryFace to r.onSecondary
    }
    // v2.2/§14.5: REEVL Button — primary `bg-brand text-white rounded-xl` (#3182F6 day), secondary `bg-brand-soft text-brand`, no outline,
    // no shadow (Button.tsx:42, :51; CompareView.tsx:671 `bg-brand-soft text-brand rounded-lg`). Radius = web px × 2.
    val compact = kind == Kind.Compact
    val shape = RoundedCornerShape(if (compact) CarSpace.radiusM else CarSpace.radius)
    // Compact: 80 visual inside a 100 hit box.
    Box(modifier.height(CarSpace.hit).press(RoundedCornerShape(if (compact) CarSpace.radiusM else CarSpace.radius), enabled = active, tag = b.tag, onClick = b.onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.height(if (compact) CarSpace.chipVisual else CarSpace.button).then(if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(min = if (compact) CarSize.Control.buttonMinCompact else CarSize.Control.buttonMin))
            .then(if (b.enabled) Modifier else Modifier.alpha(0.4f))
            .background(face, shape).padding(horizontal = CarSize.Inset.l), contentAlignment = Alignment.Center) {
            if (b.loading) Spinner(fg, CarSpace.icon)
            else if (b.iconOnly && b.icon != null) Icon(b.icon, b.label, tint = fg, modifier = Modifier.size(CarSpace.icon))
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.block)) {
                b.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(CarSize.Icon.inline)) }
                Text(b.label, style = t.label.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** [fill] = take the width the parent gives (action bar halves, the search button). */
@Composable fun PrimaryButton(b: Btn, modifier: Modifier = Modifier, fill: Boolean = false) = DsButton(b, Kind.Primary, modifier, fill)
@Composable fun SecondaryButton(b: Btn, modifier: Modifier = Modifier, fill: Boolean = false) = DsButton(b, Kind.Secondary, modifier, fill)
@Composable fun CompactButton(b: Btn, modifier: Modifier = Modifier) = DsButton(b, Kind.Compact, modifier)

/** Hit 100 × 100, visual 80 circle, icon 40; with [IconSpec.text] a pill `☆ 저장` — on = brand-soft face (web ToolbarBtn, Button.tsx:66). */
@Composable
fun IconBtn(s: IconSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val tint = when { !s.enabled -> r.sub.copy(alpha = r.disabledAlpha); s.on -> r.selectInk; else -> r.ink }
    val shape = if (s.text == null) CircleShape else RoundedCornerShape(CarSpace.radiusM)
    val pill = s.text != null && s.on
    Box(modifier.height(CarSpace.hit).widthIn(min = CarSpace.hit).press(shape, enabled = s.enabled && !s.loading, tag = s.tag, label = s.label, onClick = s.onClick),
        contentAlignment = Alignment.Center) {
        Row(Modifier.then(if (pill) Modifier.height(CarSpace.chipVisual).background(r.selectFace, shape) else Modifier)
            .padding(horizontal = if (s.text == null) 0.dp else CarSize.Inset.m), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarSpace.card)) {
            if (s.loading) Spinner(tint, CarSize.Icon.mark) else Icon(s.icon, if (s.text == null) s.label else null, tint = tint, modifier = Modifier.size(CarSpace.icon))
            s.text?.let { Text(it, style = t.label.copy(color = tint), maxLines = 1) }
        }
    }
}

/** Pleos Loading: rotating arc; still (¾ arc) when motion is off. */
@Composable
fun Spinner(color: Color, size: Dp = CarSpace.icon, modifier: Modifier = Modifier) {
    val angle = if (LocalMotion.current) {
        val tr = rememberInfiniteTransition(label = "spin")
        tr.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a").value
    } else 0f
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension * 0.12f
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w))
        drawArc(color, angle - 90f, 270f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Round))
    }
}
