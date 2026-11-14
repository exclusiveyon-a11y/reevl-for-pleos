package kr.co.reevl.ontheway.ui

import androidx.compose.ui.graphics.Color
import kr.co.reevl.ontheway.ui.components.AiMotion
import kr.co.reevl.ontheway.ui.theme.ReevlWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** car-ds-v2 §14.5: the AI gradients move only while parked; moving / restricted / animations off → the static mid-frame. */
class AiMotionTest {

    @Test fun movesOnlyWhenParkedUnrestrictedAndAnimationsOn() {
        assertTrue(AiMotion.allowed(restricted = false, moving = false, animatorScale = 1f))
        assertFalse("CarUxRestrictions active", AiMotion.allowed(restricted = true, moving = false, animatorScale = 1f))
        assertFalse("DriveContext moving / idling", AiMotion.allowed(restricted = false, moving = true, animatorScale = 1f))
        assertFalse("system animator scale 0", AiMotion.allowed(restricted = false, moving = false, animatorScale = 0f))
        assertFalse(AiMotion.allowed(restricted = true, moving = true, animatorScale = 0f))
    }

    @Test fun frozenPhaseIsTheMidFrame_whateverTheClockSays() {
        for (ms in listOf(0L, 1234L, 4499L, 99_999L)) for (p in listOf(ReevlWeb.Ai.FLOW_MS, ReevlWeb.Ai.FLOW2_MS, ReevlWeb.Ai.GLOW_MS))
            assertEquals(0.5f, AiMotion.phase(ms, p, allowed = false), 0f)
        assertEquals(1f, AiMotion.glowLevel(AiMotion.STATIC_PHASE), 1e-6f)
    }

    @Test fun parkedPhaseLoopsOverTheWebPeriods() {
        assertEquals(0f, AiMotion.phase(0, 4500, true), 0f)
        assertEquals(0.5f, AiMotion.phase(2250, 4500, true), 1e-6f)
        assertEquals(0f, AiMotion.phase(4500, 4500, true), 0f)
        assertEquals(0.25f, AiMotion.phase(6250, 5000, true), 1e-6f)
    }

    /** Token values = the generated Reevl palette (reevl design-tokens; brand #1A69E0 since the web contrast decision 2026-11-13 decide-1·8, fill stays #1D6CE3) + the computed --ai-ink. */
    @Test fun aiTokensMatchTheWeb() {
        assertEquals(kr.co.reevl.design.ReevlPalette.Light.brand, ReevlWeb.Light.brand)
        assertEquals(Color(0xFF1A69E0), ReevlWeb.Light.brand)
        assertEquals(Color(0xFF1D6CE3), ReevlWeb.Light.brandFill)
        assertEquals(Color(0xFF4D9FFF), ReevlWeb.Dark.brand)
        assertEquals(ReevlWeb.Light.brand, ReevlWeb.Light.ai)
        assertEquals(ReevlWeb.Dark.brand, ReevlWeb.Dark.ai)
        assertEquals(Color(0xFF1A53A9), ReevlWeb.Light.aiInk) // color-mix(srgb, #191F28 30 %, #1A69E0)
        assertEquals(Color(0xFF7DB7FB), ReevlWeb.Dark.aiInk)  // color-mix(srgb, #EDEEF1 30 %, #4D9FFF)
        assertEquals(Color(0xFFEAF3FF), ReevlWeb.Light.brandSoft)
        assertEquals(Color(0xFF16233A), ReevlWeb.Dark.brandSoft)
        assertEquals(5000, ReevlWeb.Ai.FLOW_MS); assertEquals(4500, ReevlWeb.Ai.FLOW2_MS); assertEquals(2600, ReevlWeb.Ai.GLOW_MS)
        assertEquals(listOf(14 to -8, 40 to -14, 80 to -26), ReevlWeb.Ai.softGlow.map { it.blurPx to it.spreadPx })
        assertEquals(listOf(0.3f, 0.22f, 0.16f), ReevlWeb.Ai.softGlow.map { it.alpha })
        // color-mix recomputed here, so a regenerated token file cannot drift silently
        fun mix(a: Color, b: Color, wa: Float) = Color(a.red * wa + b.red * (1 - wa), a.green * wa + b.green * (1 - wa), a.blue * wa + b.blue * (1 - wa))
        val l = mix(ReevlWeb.Light.ink, ReevlWeb.Light.ai, 0.3f); val d = mix(ReevlWeb.Dark.ink, ReevlWeb.Dark.ai, 0.3f)
        for ((x, y) in listOf(l to ReevlWeb.Light.aiInk, d to ReevlWeb.Dark.aiInk)) {
            assertEquals(y.red, x.red, 1f / 255); assertEquals(y.green, x.green, 1f / 255); assertEquals(y.blue, x.blue, 1f / 255)
        }
    }
}
