package kr.co.reevl.ontheway.ui.ds.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kr.co.reevl.ontheway.ui.theme.ReevlWeb

/**
 * car-ds-v2 §7.1 + §14 (v2.2 brand layer): car colour roles → Reevl tokens (`ReevlWeb.Light/Dark`, values = the generated
 * `kr.co.reevl.design.ReevlPalette` from reevl design-tokens), no hex in this file.
 * CTAs (2026-11-13, Reevl contrast plan 1 — web `--brand-fill`): primary = `brandFill` #1D6CE3 face + white label, 4.88:1, the
 * same in both modes (the old night `--app` label on #4D9FFF and the day #1B64DA face are retired). Secondary = `brandSoft` face
 * + `brandInk` label by day (4.83) / `brand` by night (5.78). Price-direction text uses `upText`/`downText` (= `upInk`/`downInk`, ≥ 4.5 on app); marks keep `up`/`down`.
 * `dim` is not a role: the car's text hierarchy is ink / ink2 / sub (the generated car theme also maps dim → sub).
 */
@Immutable
data class CarRoles(
    val dark: Boolean,
    val app: Color,
    val surface: Color,
    val chip: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val sub: Color,
    /** REEVL primary = web Button primary `bg-brand-fill text-white`: #1D6CE3 day and night, white label 4.88:1. */
    val primaryFace: Color,
    val onPrimary: Color,
    /** v2.2: REEVL secondary = brand-soft face + brand text (web ToolbarBtn on / Chip brand): `--brand-ink` by day, `--brand` by night. */
    val secondaryFace: Color,
    val onSecondary: Color,
    /** v2.2: selected left-menu row / toggled pill = brand-soft face + brand text (not the v2.1 ink face). */
    val selectFace: Color,
    val selectInk: Color,
    val aiFace: Color,
    val onAi: Color,
    val brandText: Color,
    val brandSoft: Color,
    /** Selection bar (8 dp), focus ring — not text. */
    val brand: Color,
    val posText: Color,
    val posSoft: Color,
    val warnText: Color,
    val warnSoft: Color,
    /** ▲ / ▼ price direction (Korean convention: up red, down blue) for marks — arrows, bars, charts, error borders: `--up` / `--down`. */
    val up: Color,
    val upSoft: Color,
    val down: Color,
    val downSoft: Color,
    /** Price-direction TEXT (numbers, change badges): `--up-ink` #D22030 / `--down-ink` #1B64DA by day, = up/down by night (founder 2026-11-13). */
    val upText: Color,
    val downText: Color,
    val inverse: Color,
    val ai: Color,
    val aiInk: Color,
) {
    val pressedOverlay: Color get() = ink.copy(alpha = 0.10f)
    val disabledAlpha: Float get() = 0.32f

    companion object {
        val Day = with(ReevlWeb.Light) {
            CarRoles(false, app = app, surface = bg, chip = chip, line = line, lineStrong = lineStrong, ink = ink, ink2 = ink2, sub = sub,
                primaryFace = brandFill, onPrimary = onBrand, secondaryFace = brandSoft, onSecondary = brandInk, selectFace = brandSoft, selectInk = aiInk,
                aiFace = brandFill, onAi = onBrand, brandText = aiInk, brandSoft = brandSoft, brand = brand,
                posText = posStrong, posSoft = posSoft, warnText = warnStrong, warnSoft = warnSoft, up = up, upSoft = upSoft, down = down, downSoft = downSoft, upText = upInk, downText = downInk,
                inverse = bg, ai = ai, aiInk = aiInk)
        }
        val Night = with(ReevlWeb.Dark) {
            CarRoles(true, app = app, surface = bg, chip = chip, line = line, lineStrong = lineStrong, ink = ink, ink2 = ink2, sub = sub,
                primaryFace = brandFill, onPrimary = onBrand, secondaryFace = brandSoft, onSecondary = brand, selectFace = brandSoft, selectInk = brand,
                aiFace = brandFill, onAi = onBrand, brandText = brand, brandSoft = brandSoft, brand = brand,
                posText = posStrong, posSoft = posSoft, warnText = warnStrong, warnSoft = warnSoft, up = up, upSoft = upSoft, down = down, downSoft = downSoft, upText = upInk, downText = downInk,
                inverse = bg, ai = ai, aiInk = aiInk)
        }
    }
}

val LocalRoles = staticCompositionLocalOf { CarRoles.Day }
