package kr.co.reevl.ontheway.ui

import androidx.compose.ui.text.font.FontWeight
import kr.co.reevl.ontheway.ui.ds.token.CarTypeV2

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.theme.ReevlWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** car-ds-v2 §7.3 / §10.5: every text pair the DS uses ≥ 4.5:1 day and night (no large-text exception); icon-only ≥ 3.0. */
class ContrastTest {

    private fun lin(c: Float): Double { val v = c.toDouble(); return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    private fun textPairs(r: CarRoles) = listOf(
        "ink/app" to (r.ink to r.app), "ink/surface" to (r.ink to r.surface), "ink2/surface" to (r.ink2 to r.surface), "ink2/app" to (r.ink2 to r.app),
        "sub/surface" to (r.sub to r.surface), "sub/app" to (r.sub to r.app), "sub/chip" to (r.sub to r.chip), "sub/brandSoft" to (r.sub to r.brandSoft),
        "ink/chip" to (r.ink to r.chip), "ink/brandSoft" to (r.ink to r.brandSoft),
        "brandText/surface" to (r.brandText to r.surface), "brandText/brandSoft" to (r.brandText to r.brandSoft), "brandText/app" to (r.brandText to r.app),
        "brandText/chip" to (r.brandText to r.chip), "posText/posSoft" to (r.posText to r.posSoft), "warnText/warnSoft" to (r.warnText to r.warnSoft),
        "ink/upSoft" to (r.ink to r.upSoft), "inverse/ink" to (r.inverse to r.ink),
        // v2.2 brand layer (§14): selected menu row, chips/badges, value text on the selected row. CTA labels: see v25_cta_* (§14.5).
        "selectInk/selectFace" to (r.selectInk to r.selectFace),
        "ink2/chip" to (r.ink2 to r.chip), "brandText/surface(tab)" to (r.brandText to r.surface),
    )

    @Test fun everyTextPair_atLeast45_dayAndNight() {
        val bad = mutableListOf<String>()
        for ((mode, r) in listOf("day" to CarRoles.Day, "night" to CarRoles.Night)) for ((n, p) in textPairs(r)) {
            val x = ratio(p.first, p.second)
            if (x < 4.5) bad += "$mode $n ${"%.2f".format(x)}"
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
    }

    @Test fun iconOnlyPairs_atLeast3() {
        for (r in listOf(CarRoles.Day, CarRoles.Night)) {
            assertTrue(ratio(r.up, r.surface) >= 3.0)
            assertTrue(ratio(r.down, r.surface) >= 3.0)
            assertTrue(ratio(r.up, r.upSoft) >= 3.0)
        }
    }

    /**
     * 2026-11-13 (Reevl contrast plan 1, web `--brand-fill`): primary CTA = `brandFill` #1D6CE3 face + white label, 4.88:1, the
     * same in both modes — the generated token, no car-only hex. Secondary = brand-soft face + `brandInk` (day) / `brand` (night).
     */
    @Test fun v25_cta_isTheWebButton_brandFill() {
        for (r in listOf(CarRoles.Day, CarRoles.Night)) {
            assertEquals(kr.co.reevl.design.ReevlPalette.Light.brandFill, r.primaryFace)
            assertEquals(Color(0xFF1D6CE3), r.primaryFace)
            assertEquals(Color.White, r.onPrimary)
            assertEquals(4.88, ratio(r.onPrimary, r.primaryFace), 0.01)
            assertEquals(r.primaryFace, r.aiFace); assertEquals(r.onPrimary, r.onAi)
            assertEquals(r.brandSoft, r.secondaryFace)
            assertEquals(r.brandSoft, r.selectFace); assertEquals(r.brandText, r.selectInk)
            assertTrue("no ink-black primary any more", r.primaryFace != r.ink)
        }
        assertEquals(ReevlWeb.Light.brandInk, CarRoles.Day.onSecondary)
        assertEquals(ReevlWeb.Dark.brand, CarRoles.Night.onSecondary)
        assertEquals(ReevlWeb.Light.brand, CarRoles.Day.brand) // lines, pins, the mark: the web brand
        assertEquals(4.83, ratio(CarRoles.Day.onSecondary, CarRoles.Day.secondaryFace), 0.01)
        assertEquals(5.78, ratio(CarRoles.Night.onSecondary, CarRoles.Night.secondaryFace), 0.01)
        assertTrue("web white on night brand fails large text", ratio(ReevlWeb.Dark.onBrand, ReevlWeb.Dark.brand) < 3.0)
    }

    @Test fun v25_cta_labels_atLeast45_dayAndNight() {
        for (r in listOf(CarRoles.Day, CarRoles.Night)) {
            assertTrue(ratio(r.onPrimary, r.primaryFace) >= 4.5)
            assertTrue(ratio(r.onSecondary, r.secondaryFace) >= 4.5)
            assertTrue(ratio(r.onAi, r.aiFace) >= 4.5)
            // AI gradient text (.ai-text-grad) at its lighter `ai` end on the surface (decorative gradient, large text)
            assertTrue(ratio(r.ai, r.surface) >= 3.0)
        }
        for (t in listOf(CarTypeV2.Compact, CarTypeV2.Wide)) {
            assertTrue("CTA label ≥ 28 sp", t.label.fontSize.value >= 28f)
            assertEquals(FontWeight.Medium, t.label.fontWeight)
            assertTrue("AI text ≥ 24 sp (WCAG 18 pt)", t.rowSecondary.fontSize.value >= 24f)
        }
    }

    /** Price-direction digits (r.up / r.down as text) ≥ 4.5 on every surface they sit on, day and night (was 3.71 by day). */
    @Test fun v22_priceDirectionText_atLeast45() {
        for (r in listOf(CarRoles.Day, CarRoles.Night)) for (bg in listOf(r.surface, r.app)) {
            assertTrue(ratio(r.upText, bg) >= 4.5); assertTrue(ratio(r.downText, bg) >= 4.5)
        }
        assertTrue(ratio(CarRoles.Day.downText, CarRoles.Day.app) >= 5.0)
        assertTrue(ratio(CarRoles.Day.upText, CarRoles.Day.app) >= 4.9)
        // founder 2026-11-13: text = ink tokens by day, marks (arrows, charts) keep up/down; night text = up/down
        assertEquals(Color(0xFFD22030), CarRoles.Day.upText); assertEquals(Color(0xFF1B64DA), CarRoles.Day.downText)
        assertEquals(ReevlWeb.Light.up, CarRoles.Day.up); assertEquals(ReevlWeb.Light.down, CarRoles.Day.down)
        assertEquals(CarRoles.Night.up, CarRoles.Night.upText); assertEquals(CarRoles.Night.down, CarRoles.Night.downText)
    }

    @Test fun webBrand_failsAsTextOnChip_soTheDsDoesNotUseIt() {
        // Since the web contrast decision (2026-11-13 decide-1·8) the web brand #1A69E0 passes 4.5 on chip; the car still keeps
        // brand for marks (bar, ring) and uses brandText (= aiInk) for text by day.
        assertTrue(ratio(ReevlWeb.Light.brand, ReevlWeb.Light.chip) >= 4.5)
        // `dim` passes 4.5 since the web contrast plan 1 (#686F7B), but the car still has no `dim` role (hierarchy = ink/ink2/sub).
        // no role carries the web brand as a text colour by day
        assertTrue(CarRoles.Day.brandText != ReevlWeb.Light.brand)
    }
}
