package kr.co.reevl.ontheway.ui

import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.CarTypeV2
import kr.co.reevl.ontheway.ui.ds.token.isWidePane
import kr.co.reevl.design.car.ReevlCarTheme as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** car-ds-v2 §3.2, §5, §6, §10.5 (+ §13 v2.1): touch 100 dp, gap 32 dp, text ≥ 24 sp, row line 1 ≥ 30 sp. */
class DsSizeTest {
    @Test fun touchTargets_100dp_gaps_32dp_margins_32dp() {
        for (h in listOf(CarSpace.hit, CarSpace.button, CarSpace.rowSingle, CarSpace.topBar)) assertTrue("$h", h >= 100.dp)
        assertTrue(CarSpace.rowDouble >= 112.dp) // 12.5 mm on a 12.9" panel
        assertEquals(32.dp, CarSpace.gap)
        assertEquals(32.dp, CarSpace.margin)
        assertEquals(148.dp, CarSpace.actionBar)
        assertEquals(CarSpace.actionBar, CarSpace.actionPad * 2 + CarSpace.button)
    }

    @Test fun everyTextRole_atLeast24sp_rowPrimary_atLeast32sp() {
        for (t in listOf(CarTypeV2.Compact, CarTypeV2.Wide)) {
            t.all.forEach { assertTrue("${it.fontSize}", it.fontSize.value >= 24f) }
            assertTrue(t.rowPrimary.fontSize.value >= 32f) // AAOS-TYPE-1: primary text 32 sp (was 30 by the v2.1 measurement)
            assertTrue(t.label.fontSize.value >= 30f)
        }
        assertEquals(48f, CarTypeV2.Compact.display.fontSize.value)
        assertEquals(56f, CarTypeV2.Wide.display.fontSize.value)
        assertEquals(CarTypeV2.Compact.topTitle.fontSize, CarTypeV2.Wide.topTitle.fontSize) // TopBar size never changes
    }

    /** The car sizes come from the generated Reevl car tokens (reevl design-tokens/dist/aaos), not app literals. */
    @Test fun sizes_areTheGeneratedCarTokens() {
        assertEquals(T.touchTarget, CarSpace.hit); assertEquals(T.touchTarget, CarSpace.button)
        assertTrue(CarSpace.hitMin >= 76.dp)
        assertEquals(T.Space.margin, CarSpace.margin); assertEquals(T.Space.rowSingle, CarSpace.rowSingle)
        assertEquals(T.Radius.xl, CarSpace.radius)
        assertEquals(T.TypeWide.display.fontSize, CarTypeV2.Wide.display.fontSize)
        assertEquals(T.TypeCompact.caption.fontSize, CarTypeV2.Compact.caption.fontSize)
        assertEquals(T.PRIMARY_ROW_TYPE_SP.toFloat(), CarTypeV2.Compact.rowPrimary.fontSize.value)
        for (t in listOf(CarTypeV2.Compact, CarTypeV2.Wide)) t.all.forEach { assertTrue(it.fontSize.value >= T.MIN_TYPE_SP) }
    }

    /**
     * Source gate (REEVL-20261113-004): colours and sizes come from the generated Reevl tokens. Outside the generated files
     * (kr/co/reevl/design) and the token wrappers (ui/ds/token), no hex colour and no non-zero dp literal — except a line
     * marked `// layout:` (one-off geometry: map pads, arrow and legend drawing).
     */
    @Test fun noHexOrDpLiterals_outsideTokens() {
        val hex = Regex("Color\\(0x[0-9A-Fa-f]{8}\\)|\"#[0-9A-Fa-f]{6}\"")
        val dp = Regex("\\b(?!0\\.dp)[0-9]+(\\.[0-9]+)?\\.dp\\b")
        val hits = java.io.File("src/main/java/kr/co/reevl/ontheway").walk()
            .filter { it.extension == "kt" && !it.path.contains("/ui/ds/token/") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l ->
                val code = l.substringBefore("//")
                if ((hex.containsMatchIn(code) || dp.containsMatchIn(code)) && !l.contains("// layout:")) "${f.name}:${i + 1}" else null } }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun paneClass_byPaneWidth_notWindow() {
        assertTrue(isWidePane(1200.dp))
        assertTrue(isWidePane(1100.dp))
        assertFalse(isWidePane(1059.dp)) // preview pane in the wide window keeps Compact roles
        assertFalse(isWidePane(840.dp))
        assertTrue(CarSpace.wideWindow > 840.dp && CarSpace.wideWindow < 1680.dp)
        assertEquals(620.dp, CarSpace.split)
        assertEquals(1200.dp, CarSpace.readWidth)
    }
}

class DsV2FixesTest {
    @org.junit.Test fun saveLook_threeStates() {
        val on = kr.co.reevl.ontheway.ui.ds.comp.SaveLook.of(true)
        val off = kr.co.reevl.ontheway.ui.ds.comp.SaveLook.of(false)
        val unk = kr.co.reevl.ontheway.ui.ds.comp.SaveLook.of(null)
        org.junit.Assert.assertTrue(on.filled && on.on && on.enabled && on.label == "저장됨")
        org.junit.Assert.assertTrue(!off.filled && !off.on && off.enabled && off.label == "저장")
        org.junit.Assert.assertTrue(!unk.filled && !unk.on && !unk.enabled && unk.label == "저장")
    }

    @org.junit.Test fun v21_weights_regularRows_mediumTitles_nothingAboveSemiBold() {
        for (t in listOf(CarTypeV2.Compact, CarTypeV2.Wide)) {
            t.all.forEach { org.junit.Assert.assertTrue("${it.fontWeight}", it.fontWeight!!.weight <= 500) }
            org.junit.Assert.assertEquals(400, t.rowPrimary.fontWeight!!.weight)
            org.junit.Assert.assertEquals(400, t.rowSecondary.fontWeight!!.weight)
        }
        // source gate: no Bold / ExtraBold / SemiBold anywhere in the design system or the screens
        val files = listOf("src/main/java/kr/co/reevl/ontheway/ui/ds", "src/main/java/kr/co/reevl/ontheway/ui/screens")
            .flatMap { java.io.File(it).walk().filter { f -> f.extension == "kt" }.toList() }
        val hits = files.filter { Regex("FontWeight\\.(Bold|ExtraBold|SemiBold|Black)").containsMatchIn(it.readText()) }.map { it.name }
        org.junit.Assert.assertEquals(emptyList<String>(), hits)
    }

    @org.junit.Test fun v22_reevlRadius_webPxTimesTwo_and_leftMenuWidth() {
        // globals.css:191 --radius-xl 12 / -lg 10 / -md 8 → × 2
        org.junit.Assert.assertEquals(24.dp, CarSpace.radius)
        org.junit.Assert.assertEquals(20.dp, CarSpace.radiusM)
        org.junit.Assert.assertEquals(16.dp, CarSpace.radiusS)
        org.junit.Assert.assertEquals(100.dp, CarSpace.rowSingle)
        org.junit.Assert.assertEquals(400.dp, CarSpace.rail)
    }

    @org.junit.Test fun basis_onlyInTheSourceLine() {
        val conds = listOf(kr.co.reevl.ontheway.data.SearchCond("area", "30평대", "applied", basis = "공급면적 기준"),
            kr.co.reevl.ontheway.data.SearchCond("price", "10억 이하", "applied"))
        org.junit.Assert.assertEquals("30평대 공급면적 기준", kr.co.reevl.ontheway.search.AiSearch.basisLine(conds))
        org.junit.Assert.assertNull(kr.co.reevl.ontheway.search.AiSearch.basisLine(conds.drop(1)))
        val c = kr.co.reevl.ontheway.data.Api.json.decodeFromString(kr.co.reevl.ontheway.data.SearchCond.serializer(), """{"key":"area","label":"30평대","status":"applied","basis":"공급면적 기준"}""")
        org.junit.Assert.assertEquals("공급면적 기준", c.basis)
    }
}
