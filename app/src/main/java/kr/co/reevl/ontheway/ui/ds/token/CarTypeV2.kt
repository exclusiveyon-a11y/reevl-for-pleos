package kr.co.reevl.ontheway.ui.ds.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import kr.co.reevl.design.car.ReevlCarTheme
import kr.co.reevl.ontheway.ui.theme.Pretendard

/**
 * car-ds-v2 §6 + §13: nine roles, Compact / Wide. Sizes, weights, leading and tracking come from the generated Reevl car
 * tokens ([ReevlCarTheme.TypeCompact] / [ReevlCarTheme.TypeWide], reevl design-tokens) — this file only adds the font
 * (Pretendard), Korean phrase breaking and centred line boxes. Minimum [ReevlCarTheme.MIN_TYPE_SP] sp.
 *
 * Primary row text is held to [ReevlCarTheme.PRIMARY_ROW_TYPE_SP] (32 sp, Google "primary text 32 dp", AAOS-TYPE-1) in both
 * classes. The v2.1 measurement (§13.1: the built-in menu row ≈ 30 sp) put Compact at 30; the guideline wins (founder
 * 2026-11-13 "남은 것 다"). While the generated Compact role still says 30, it is lifted here with the same leading multiple.
 */
@Immutable
data class CarTypeV2(
    val display: TextStyle,
    val title: TextStyle,
    val topTitle: TextStyle,
    val rowPrimary: TextStyle,
    val rowSecondary: TextStyle,
    val value: TextStyle,
    val label: TextStyle,
    val chip: TextStyle,
    val caption: TextStyle,
) {
    val all: List<TextStyle> get() = listOf(display, title, topTitle, rowPrimary, rowSecondary, value, label, chip, caption)

    companion object {
        private val KoBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase)

        /** Generated role + the app's font and line rules. */
        private fun car(t: TextStyle) = t.merge(TextStyle(
            fontFamily = Pretendard, lineBreak = KoBreak,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ))

        /** Lift a role to at least [sp], keeping its line-height multiple. */
        private fun atLeast(t: TextStyle, sp: Int): TextStyle {
            if (t.fontSize.value >= sp) return t
            val mult = t.lineHeight.value / t.fontSize.value
            return t.copy(fontSize = sp.sp, lineHeight = (sp * mult).sp)
        }

        val Compact = with(ReevlCarTheme.TypeCompact) {
            CarTypeV2(car(display), car(title), car(topTitle), car(atLeast(rowPrimary, ReevlCarTheme.PRIMARY_ROW_TYPE_SP)), car(rowSecondary),
                car(value), car(label), car(chip), car(caption))
        }
        val Wide = with(ReevlCarTheme.TypeWide) {
            CarTypeV2(car(display), car(title), car(topTitle), car(atLeast(rowPrimary, ReevlCarTheme.PRIMARY_ROW_TYPE_SP)), car(rowSecondary),
                car(value), car(label), car(chip), car(caption))
        }
    }
}

val LocalTypeV2 = staticCompositionLocalOf { CarTypeV2.Compact }
