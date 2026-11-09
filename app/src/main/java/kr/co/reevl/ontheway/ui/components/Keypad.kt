package kr.co.reevl.ontheway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/**
 * Pleos Keypad (components/actions/keypad, SVG keypad_numberic): number key = white face + light ring, function key
 * (⌫) = grey face. Pressed = 95 % + ink overlay (Pleos Interaction). Key 100 dp tall, radius 24, gap 16.
 */
@Composable
fun CarKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit, submitEnabled: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CarDims.keyGap)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(CarDims.keyGap)) {
                r.forEach { d -> Key(Modifier.weight(1f), tag = "key_$d", onClick = { onDigit(d) }) { KeyLabel("$d") } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CarDims.keyGap)) {
            Key(Modifier.weight(1f), function = true, tag = "key_del", label = "지우기", onClick = onDelete) {
                Icon(Lucide.Delete, "지우기", tint = CarTheme.colors.ink, modifier = Modifier.size(40.dp))
            }
            Key(Modifier.weight(1f), tag = "key_0", onClick = { onDigit('0') }) { KeyLabel("0") }
            CarButton("연결", onSubmit, Modifier.weight(1f), tone = Tone.Primary, size = BtnSize.L, enabled = submitEnabled, loading = busy, tag = "connect")
        }
    }
}

@Composable
private fun KeyLabel(t: String) = Text(t, style = CarTheme.type.valueLg.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold))

@Composable
private fun Key(modifier: Modifier, function: Boolean = false, tag: String, label: String? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = CarTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(CarDims.keyRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(CarDims.key)
            .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (function) c.chip else c.card, shape)
            .then(if (function) Modifier else Modifier.border(2.dp, c.lineStrong, shape))
            .then(if (pressed) Modifier.background(c.pressOverlay, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag),
    ) { content() }
}

/** Pleos Number Field states: filled = ink digit, next = brand ring (Focused), error = up ring (Invalid). */
@Composable
fun CodeCells(code: String, invalid: Boolean, modifier: Modifier = Modifier, cell: Dp = 104.dp) {
    val c = CarTheme.colors
    Row(modifier.testTag("code_cells"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(6) { i ->
            val ch = code.getOrNull(i)
            val ring = when {
                invalid -> c.up
                i == code.length -> c.brand
                else -> c.lineStrong
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(cell).height(cell * 1.15f).clip(RoundedCornerShape(24.dp)).background(c.card)
                    .border(if (i == code.length || invalid) 3.dp else 2.dp, ring, RoundedCornerShape(24.dp)),
            ) {
                Text(ch?.toString() ?: "", style = CarTheme.type.valueXl)
            }
        }
    }
}

@Composable
fun FullWidthSpacer(h: Dp) = Box(Modifier.fillMaxWidth().height(h))
