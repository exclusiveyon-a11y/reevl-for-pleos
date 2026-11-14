package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import kr.co.reevl.design.car.ReevlCarTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** §5.11 Pleos keypad: keys 100 high, R 24, gap 16 (≥ 32 between targets is met by the 100 dp keys' own spacing rule — see tests). */
@Composable
fun Keypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
        rows.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                line.forEach { d -> Key(Modifier.weight(1f), "key_$d", label = "$d") { onDigit(d) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
            Key(Modifier.weight(1f), "key_del", function = true, onClick = onDelete, icon = true)
            Key(Modifier.weight(1f), "key_0", label = "0") { onDigit('0') }
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Key(modifier: Modifier, tag: String, function: Boolean = false, label: String? = null, icon: Boolean = false, onClick: () -> Unit) {
    val r = LocalRoles.current
    val shape = RoundedCornerShape(CarSpace.radiusM)
    Box(modifier.height(CarSpace.hit).background(if (function) r.chip else r.surface, shape)
        .press(shape, tag = tag, label = if (icon) "지우기" else label, onClick = onClick), contentAlignment = Alignment.Center) {
        if (icon) Icon(Lucide.Delete, "지우기", tint = r.ink, modifier = Modifier.size(CarSpace.icon))
        else Text(label.orEmpty(), style = LocalTypeV2.current.value.copy(fontSize = ReevlCarTheme.TypeWide.title.fontSize, lineHeight = ReevlCarTheme.TypeWide.title.lineHeight, fontWeight = FontWeight.Normal, color = r.ink))
    }
}

/** Six code cells 104 × 120; [invalid] = up ring. */
@Composable
fun CodeCells(code: String, invalid: Boolean, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    // Cells share the column (≤ 104 each): 6 × 104 + 5 × 16 = 704 fits 720; the narrow window (≤ 512) shrinks them evenly.
    Row(modifier.fillMaxWidth().widthIn(max = CarSize.Control.keypadMax), horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight, androidx.compose.ui.Alignment.CenterHorizontally)) {
        repeat(6) { i ->
            val ch = code.getOrNull(i)
            val focus = i == code.length
            val shape = RoundedCornerShape(CarSpace.radiusM)
            Box(Modifier.weight(1f).widthIn(max = CarSize.Control.keypadCell).height(CarSize.Control.keypadCellHeight).background(r.surface, shape)
                .border(if (focus || invalid) CarSize.Stroke.focus else CarSpace.hairline, when { invalid -> r.up; focus -> r.brand; else -> r.lineStrong }, shape).tag("cell_$i"),
                contentAlignment = Alignment.Center) {
                Text(ch?.toString() ?: "", style = LocalTypeV2.current.display.copy(color = r.ink))
            }
        }
    }
}

