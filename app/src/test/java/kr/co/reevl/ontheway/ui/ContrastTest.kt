package kr.co.reevl.ontheway.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.pow
import kr.co.reevl.ontheway.ui.theme.CarColors
import kr.co.reevl.ontheway.ui.theme.CarTypography
import kr.co.reevl.ontheway.ui.theme.TextUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast of every coloured-text pair the car UI draws, against the rule that applies to the style it is drawn in
 * (WCAG 2.x 1.4.3: normal text 4.5:1, large text — ≥ 24 px, or ≥ 18.66 px bold — 3:1). Ruling 37: the day primary
 * keeps the web's white on #3182F6 as large text; Ruling 38: the night primary is #4D9FFF + #0E0F11.
 * The numbers in car-ui-spec §1.3 come from here.
 */
class ContrastTest {

    private fun lin(c: Float): Double { val v = c.toDouble(); return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    private fun large(s: TextStyle): Boolean {
        val px = s.fontSize.value
        val bold = (s.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
        return px >= 24f || (px >= 18.66f && bold)
    }
    private fun required(s: TextStyle) = if (large(s)) 3.0 else 4.5

    private data class Use(val name: String, val style: TextStyle, val fg: Color, val bg: Color)

    private fun uses(c: CarColors): List<Use> {
        val t = CarTypography.create(c)
        return listOf(
            Use("primary label (L)", TextUse.buttonLabel(t, large = true), c.primaryText, c.primaryFill),
            Use("primary label (M)", TextUse.buttonLabel(t, large = false), c.primaryText, c.primaryFill),
            Use("secondary label", TextUse.buttonLabel(t, false), c.ink, c.chip),
            Use("quiet label", TextUse.buttonLabel(t, false), c.sub, c.card),
            Use("▲ up on card", TextUse.delta(t), c.up, c.card),
            Use("▼ down on card", TextUse.delta(t), c.down, c.card),
            Use("error banner", TextUse.banner(t), c.up, c.upSoft),
            Use("offline banner", TextUse.banner(t), c.warnStrong, c.warnSoft),
            Use("badge brand", TextUse.badge(t), c.brand, c.brandSoft),
            Use("badge pos", TextUse.badge(t), c.posStrong, c.posSoft),
            Use("badge warn", TextUse.badge(t), c.warnStrong, c.warnSoft),
            Use("badge neutral", TextUse.badge(t), c.sub, c.chip),
            Use("ai label (ai end)", TextUse.aiLabel(t), c.ai, c.card),
            Use("ai label (ai-ink end)", TextUse.aiLabel(t), c.aiInk, c.card),
            Use("caption on card", t.cap, c.sub, c.card),
            Use("caption on page", t.cap, c.sub, c.page),
            Use("body on card", t.body, c.ink2, c.card),
            Use("title on page", t.headline, c.ink, c.page),
        )
    }

    @Test fun everyUsedPair_meetsTheRuleForItsStyle_dayAndNight() {
        val bad = mutableListOf<String>()
        for (c in listOf(CarColors.Light, CarColors.Dark)) for (u in uses(c)) {
            val r = ratio(u.fg, u.bg)
            if (r < required(u.style)) bad += "${if (c.dark) "night" else "day"} ${u.name}: ${"%.2f".format(r)} < ${required(u.style)}"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // Ruling 37: the day primary stays the web value — only valid while the label is large text. Lock both.
    @Test fun dayPrimary_isTheWebValue_andItsLabelStaysLargeText() {
        val c = CarColors.Light
        val t = CarTypography.create(c)
        assertEquals(3.71, ratio(c.primaryText, c.primaryFill), 0.01)
        for (s in listOf(TextUse.buttonLabel(t, true), TextUse.buttonLabel(t, false))) {
            assertTrue("label ${s.fontSize} must stay ≥ 24 sp", s.fontSize.value >= 24f)
            assertTrue("label weight ${s.fontWeight} must stay ≥ Bold", (s.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold)
        }
    }

    // The day pairs below 4.5:1 are exactly these — all large text. A new one must be added here on purpose.
    @Test fun dayPairsBelow45_areOnlyTheKnownLargeTextOnes() {
        val below = uses(CarColors.Light).filter { ratio(it.fg, it.bg) < 4.5 }.map { it.name }.toSet()
        assertEquals(setOf("primary label (L)", "primary label (M)", "▲ up on card", "▼ down on card", "error banner",
            "badge brand", "ai label (ai end)"), below)
        assertTrue(uses(CarColors.Light).filter { it.name in below }.all { large(it.style) })
    }

    // Ruling 38: night primary = dark --brand fill + dark --app label, the strongest control on screen.
    @Test fun nightPrimary_isBrightFillDarkLabel_andTheStrongestAction() {
        val c = CarColors.Dark
        assertEquals(Color(0xFF4D9FFF), c.primaryFill)
        assertEquals(Color(0xFF0E0F11), c.primaryText)
        assertEquals(7.05, ratio(c.primaryText, c.primaryFill), 0.01)
        // fill stands out from the page far more than the secondary chip does (native_08 inversion)
        assertTrue(ratio(c.primaryFill, c.page) > 3 * ratio(c.chip, c.page))
        assertTrue(ratio(Color.White, c.brand) < 3.0) // why the web's dark primary (white on #4D9FFF) is not used
    }

    // Why car captions use --sub: the web caption colour --dim misses 4.5:1 on the light page (4.38) and on chips (3.9 dark).
    @Test fun dim_isBelow45_soTheCarDoesNotUseItForText() {
        assertTrue(ratio(CarColors.Light.dim, CarColors.Light.page) < 4.5)
        assertTrue(ratio(CarColors.Dark.dim, CarColors.Dark.chip) < 4.5)
    }
}
