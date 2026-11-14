package kr.co.reevl.ontheway.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Reevl colours for the car, light and dark — every value is a generated Reevl token ([ReevlWeb] → kr.co.reevl.design,
 * copied from reevl design-tokens by scripts/sync_reevl_tokens.sh). Names follow the car spec (docs/design/car-ui-spec.md §1.1):
 * `page` = `--app`, `card` = `--bg`, the rest keep the web names.
 *
 * Day/night follows the system screen mode (Pleos Settings › 디스플레이 › 화면 모드 자동·라이트·다크, native_03 /
 * native_07 captures) — never a forced dark app. Night = light text on dark (Google Design for Driving).
 */
@Immutable
data class CarColors(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val chip: Color,
    val hover: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val sub: Color,
    val dim: Color,
    val brand: Color,
    val brandStrong: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val up: Color,
    val upSoft: Color,
    val down: Color,
    val downSoft: Color,
    val pos: Color,
    val posSoft: Color,
    val posStrong: Color,
    val warn: Color,
    val warnSoft: Color,
    val warnStrong: Color,
    val ai: Color,
    val aiInk: Color,
) {
    /**
     * Primary action — always the strongest control on screen: web Button primary `bg-brand-fill text-white` (the generated
     * token `brandFill` #1D6CE3, the same in both modes) with a white label, 4.88:1 — locked by ContrastTest.
     */
    val primaryFill: Color get() = ReevlWeb.Light.brandFill
    val primaryText: Color get() = onBrand

    /** Pleos Basic button "Pressed": background changes by mode — the ink at 10 % over the fill (guide SVG basic_state). */
    val pressOverlay: Color get() = ink.copy(alpha = 0.10f)

    /** Pleos Basic button "Disabled": label at 32 % (guide colour SVGs use #1B1C1E @ 0.32). */
    val disabledText: Color get() = ink.copy(alpha = 0.32f)

    companion object {
        val Light = with(ReevlWeb.Light) {
            CarColors(false, app, bg, chip, hover, line, lineStrong, ink, ink2, sub, dim, brand, brandStrong, brandSoft, onBrand,
                up, upSoft, down, downSoft, pos, posSoft, posStrong, warn, warnSoft, warnStrong, ai, aiInk)
        }
        val Dark = with(ReevlWeb.Dark) {
            CarColors(true, app, bg, chip, hover, line, lineStrong, ink, ink2, sub, dim, brand, brandStrong, brandSoft, onBrand,
                up, upSoft, down, downSoft, pos, posSoft, posStrong, warn, warnSoft, warnStrong, ai, aiInk)
        }
    }
}

val LocalCarColors = staticCompositionLocalOf { CarColors.Light }

/**
 * Motion allowed? false while driving (Pleos "운전 중 동영상·애니메이션·자동 스크롤 금지") or when the system animator
 * scale is 0 (the web's `prefers-reduced-motion`). The three moving Reevl effects (ai-live-grad, ai-text-grad,
 * reevl-spark) then draw their still frame.
 */
val LocalMotion = staticCompositionLocalOf { true }

/** Driving restriction as the screens see it (D3 wires CarUxRestrictions; debug intent for verification). */
val LocalDriving = staticCompositionLocalOf { false }

object CarTheme {
    val colors: CarColors @Composable get() = LocalCarColors.current
    val type: CarTypography @Composable get() = LocalCarType.current
}

@Composable
fun ReevlCarTheme(driving: Boolean = false, moving: Boolean = false, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val animatorScale = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    }
    val colors = if (dark) CarColors.Dark else CarColors.Light
    // [moving] = DriveContext says moving / idling (vehicle signals), [driving] = CarUxRestrictions: either freezes the AI gradients.
    val motion = kr.co.reevl.ontheway.ui.components.AiMotion.allowed(driving, moving, animatorScale)
    CompositionLocalProvider(
        LocalCarColors provides colors,
        LocalCarType provides CarTypography.create(colors),
        LocalDriving provides driving,
        LocalMotion provides motion,
    ) { kr.co.reevl.ontheway.ui.components.ProvideAiClock(motion, content) }
}
