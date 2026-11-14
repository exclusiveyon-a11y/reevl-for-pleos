package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

enum class Tone { Brand, Pos, Neutral, Sub, Warn }

data class ChipSpec(val text: String, val tone: Tone = Tone.Neutral, val icon: ImageVector? = null, val tag: String? = null)

/** v2.2 = web Badge tones (Badge.tsx:10–19): soft face + tone text; pos/warn use the -strong text (AA). */
fun Tone.colors(r: CarRoles) = when (this) {
    Tone.Brand -> r.brandSoft to r.brandText
    Tone.Pos -> r.posSoft to r.posText
    Tone.Neutral -> r.chip to r.ink2
    Tone.Sub -> r.chip to r.sub
    Tone.Warn -> r.warnSoft to r.warnText
}

/** v2.2: REEVL Badge — 48 high, `rounded-md` (8 → 16), soft tone face, Regular (the web's extrabold is dropped: §13.2 weights). */
@Composable
fun StatusChip(s: ChipSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val (bg, fg) = s.tone.colors(r)
    Row(modifier.height(CarSpace.statusChip).background(bg, RoundedCornerShape(CarSpace.radiusS)).padding(horizontal = CarSpace.gapTight).tag(s.tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.tight)) {
        s.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(CarSpace.iconSmall)) }
        Text(s.text, style = LocalTypeV2.current.chip.copy(color = fg), maxLines = 1)
    }
}

/** `✓ 8억 이하` — meaning colour on the icon only, the words stay grey. */
@Composable
fun CheckChip(text: String, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    Row(modifier.height(CarSpace.statusChip), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.tight)) {
        Icon(Lucide.Check, null, tint = r.posText, modifier = Modifier.size(CarSpace.iconSmall))
        Text(text, style = LocalTypeV2.current.chip.copy(color = r.ink2), maxLines = 1)
    }
}

/** Search condition: 80 visual (100 hit), full radius; warn = needs a decision (?). Tap = [onClick]. */
data class CondChipSpec(val text: String, val warn: Boolean, val onClick: () -> Unit, val tag: String? = null)

/** v2.2: REEVL Chip active, tone brand (Chip.tsx:10, :23 `rounded-full`) — brand-soft pill, brand text and ×; warn = warn-soft + ?. */
@Composable
fun ConditionChip(s: CondChipSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val (bg, fg) = if (s.warn) r.warnSoft to r.warnText else r.brandSoft to r.brandText
    val shape = CircleShape
    Box(modifier.height(CarSpace.hit).press(shape, tag = s.tag, onClick = s.onClick), contentAlignment = Alignment.Center) {
        Row(Modifier.height(CarSpace.chipVisual).background(bg, shape).padding(start = CarSize.Inset.l, end = CarSize.Inset.s),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.card)) {
            if (s.warn) Icon(Lucide.CircleQuestionMark, null, tint = fg, modifier = Modifier.size(CarSize.Icon.inline))
            Text(s.text, style = LocalTypeV2.current.chip.copy(color = fg), maxLines = 1)
            Icon(Lucide.X, "빼기", tint = fg, modifier = Modifier.size(CarSpace.iconSmall))
        }
    }
}
