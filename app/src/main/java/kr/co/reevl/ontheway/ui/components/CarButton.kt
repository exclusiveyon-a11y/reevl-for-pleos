package kr.co.reevl.ontheway.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalMotion

/**
 * Button hierarchy: Reevl Button.tsx (`primary` = brand fill, `outline` = quiet) × Pleos Basic button
 * (sizes Large 100/r32 · Medium 80/r24, states Pressed · Disabled · Loading).
 */
enum class Tone {
    /** One per screen (Button.tsx "화면의 주 동작(한 화면에 하나)"). Day brand/white, night brandSoft/brand. */
    Primary,
    /** Pleos "Primary" basic button look: ink-5 % fill (we use the Reevl `--chip` token) + ink label. */
    Secondary,
    /** Reevl outline: 1 dp line + sub label. */
    Quiet,
}

enum class BtnSize(val height: Dp, val radius: Dp) { L(CarDims.buttonL, CarDims.radiusL), M(CarDims.buttonM, CarDims.radiusM) }

@Composable
fun CarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Secondary,
    size: BtnSize = BtnSize.M,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    /** Pleos Basic button Icon(Suffix) — e.g. a chevron for "open". */
    iconEnd: ImageVector? = null,
    tag: String? = null,
) {
    val c = CarTheme.colors
    val t = CarTheme.type
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !loading
    val shape = RoundedCornerShape(size.radius)
    val (fill, label) = when (tone) {
        Tone.Primary -> c.primaryFill to c.primaryText
        Tone.Secondary -> c.chip to c.ink
        Tone.Quiet -> Color.Transparent to c.sub
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = if (size == BtnSize.L) 240.dp else 160.dp, minHeight = size.height)
            .height(size.height)
            // Pleos Interaction: pressed = scale down to 95 % + background change by mode.
            .graphicsLayer { val s = if (pressed && active) 0.95f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (enabled) fill else c.chip, shape)
            .then(if (pressed && active) Modifier.background(c.pressOverlay, shape) else Modifier)
            .then(if (tone == Tone.Quiet) Modifier.border(2.dp, c.lineStrong, shape) else Modifier)
            .clickable(enabled = active, interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = if (size == BtnSize.L) 40.dp else 28.dp),
    ) {
        if (loading) {
            Spinner(color = label, size = if (size == BtnSize.L) 40.dp else 32.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val fg = if (enabled) label else c.disabledText
                if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (size == BtnSize.L) 36.dp else 30.dp))
                // Label = Body L 30 (Google primary 32 / Pleos Body L): ExtraBold on Large, Bold on Medium.
                Text(text, style = kr.co.reevl.ontheway.ui.theme.TextUse.buttonLabel(t, size == BtnSize.L).copy(color = fg),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (iconEnd != null) Icon(iconEnd, null, tint = fg, modifier = Modifier.size(if (size == BtnSize.L) 36.dp else 30.dp))
            }
        }
    }
}

/** Round icon-only button (Pleos Icon button "Circle", 80 dp target ≥ Google 76). */
@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, enabled: Boolean = true) {
    val c = CarTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(CarDims.buttonM)
            .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(CarDims.radiusM))
            .background(if (pressed) c.pressOverlay else Color.Transparent)
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Icon(icon, label, tint = if (enabled) c.ink else c.disabledText, modifier = Modifier.size(40.dp))
    }
}

/** Pleos "Loading" / Animation Linear: a rotating arc. Still (¾ arc) when motion is off. */
@Composable
fun Spinner(color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val angle = if (LocalMotion.current) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a").value
    } else 0f
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension * 0.12f
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w), style = Stroke(w))
        drawArc(color, angle - 90f, 270f, false, topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}
