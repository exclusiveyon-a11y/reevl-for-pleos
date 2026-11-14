package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles

/** car-ds-v2 §8.1 Pleos press: scale 0.95 + ink 10 % face while held; cancelled when the finger leaves. */
fun Modifier.press(shape: Shape, enabled: Boolean = true, role: Role = Role.Button, tag: String? = null, label: String? = null,
                   onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val overlay = LocalRoles.current.pressedOverlay
    this
        .graphicsLayer { val s = if (pressed && enabled) 0.95f else 1f; scaleX = s; scaleY = s }
        .clip(shape)
        .then(if (pressed && enabled) Modifier.background(overlay, shape) else Modifier)
        .clickable(enabled = enabled, interactionSource = src, indication = null, role = role, onClickLabel = label, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
}

fun Modifier.tag(tag: String?): Modifier = if (tag != null) this.testTag(tag) else this
