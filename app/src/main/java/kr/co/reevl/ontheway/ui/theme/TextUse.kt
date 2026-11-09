package kr.co.reevl.ontheway.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The text styles used for coloured text whose contrast is below 4.5:1 in day mode. Components take their style from
 * here so ContrastTest can lock each one at WCAG 2.x "large text" (≥ 24 px, or ≥ 18.66 px and bold) where 3:1 is the
 * AA threshold (Ruling 37). sp = CSS px here (1 sp = 1 px at the emulator's 160 dpi, the guide's own unit).
 */
object TextUse {
    /** CarButton label — Body L 30, ExtraBold on Large, Bold on Medium. Day primary = white on #3182F6 (3.71:1). */
    fun buttonLabel(t: CarTypography, large: Boolean): TextStyle = t.title.copy(fontWeight = if (large) FontWeight.ExtraBold else FontWeight.Bold)

    /** ▲▼ change vs the previous same-area trade — up/down on the card face (3.71:1 day). */
    fun delta(t: CarTypography): TextStyle = t.label

    /** Banner sentence — up on up-soft for errors (3.24:1 day), warn-strong on warn-soft for offline. */
    fun banner(t: CarTypography): TextStyle = t.body.copy(fontWeight = FontWeight.SemiBold)

    /** Badge / status chip label — brand on brand-soft (3.32:1 day) etc. (Badge.tsx `font-extrabold`). */
    fun badge(t: CarTypography): TextStyle = t.label.copy(fontWeight = FontWeight.ExtraBold)

    /** AI gradient label (`.ai-text-grad`, ai ↔ ai-ink on the card: 3.71–5.81:1 day). */
    fun aiLabel(t: CarTypography): TextStyle = t.label.copy(fontWeight = FontWeight.ExtraBold)
}
