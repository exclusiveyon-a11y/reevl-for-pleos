package kr.co.reevl.ontheway.ui

import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.tmpl.comparePageSize
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.MENU_ROWS_MAX
import kr.co.reevl.ontheway.ui.ds.token.WindowClass
import kr.co.reevl.ontheway.ui.ds.token.foldMenu
import kr.co.reevl.ontheway.ui.ds.token.marginFor
import kr.co.reevl.ontheway.ui.ds.token.windowClassOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** car-ds-v2 §15: Pleos window contexts (2:0 · 1:1 next to the navigation · Connect-W narrow side). */
class WindowClassTest {
    @Test fun windowClass_byMeasuredPleosWidths() {
        assertEquals(WindowClass.Wide, windowClassOf(1682.dp))   // 2:0, measured `am stack list` w1682dp
        assertEquals(WindowClass.Compact, windowClassOf(841.dp)) // 1:1 next to the navigation, measured w841dp
        assertEquals(WindowClass.Narrow, windowClassOf(720.dp))
        assertEquals(WindowClass.Narrow, windowClassOf(560.dp))
        assertEquals(WindowClass.Compact, windowClassOf(CarSpace.narrowWindow))
        assertEquals(WindowClass.Wide, windowClassOf(CarSpace.wideWindow))
        assertEquals(24.dp, marginFor(WindowClass.Narrow))
        assertEquals(32.dp, marginFor(WindowClass.Compact))
    }

    private val all = listOf("nearby", "area", "search", "today", "saved", "compare", "settings")

    @Test fun foldedMenu_atMostSixRows_settingsMovesToHeader() {
        for (c in listOf(WindowClass.Compact, WindowClass.Narrow)) {
            val (rows, more) = foldMenu(all, { it }, c)
            assertTrue("$c ${rows.size}", rows.size <= MENU_ROWS_MAX)
            assertEquals(listOf("nearby", "area", "search", "today", "saved", "compare"), rows)
            assertEquals("settings", more)
        }
    }

    @Test fun foldedMenu_sixItems_unchanged_and_wideRail_keepsEverything() {
        val six = all - "compare"
        assertEquals(six to null, foldMenu(six, { it }, WindowClass.Compact))
        val (rows, more) = foldMenu(all, { it }, WindowClass.Wide)
        assertEquals(all, rows)
        assertNull(more)
    }

    @Test fun compareColumns_perWindow() {
        assertEquals(3, comparePageSize(WindowClass.Wide))
        assertEquals(2, comparePageSize(WindowClass.Compact))
        assertEquals(1, comparePageSize(WindowClass.Narrow))
    }

    /**
     * Live resize (window bar drag / swap / 3-finger swipe) must not restart the activity: the manifest handles every
     * size-related change itself, so the ViewModel and the composition (scroll, tab, typed text) stay as they are.
     */
    @Test fun manifest_handlesEveryResizeConfigChange() {
        val m = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        val changes = Regex("android:configChanges=\"([^\"]+)\"").find(m)!!.groupValues[1].split('|').toSet()
        for (c in listOf("orientation", "screenSize", "screenLayout", "smallestScreenSize", "density", "uiMode"))
            assertTrue("configChanges lacks $c", c in changes)
    }

    /** State that must survive a resize is never kept in a plain `remember` in the templates (scroll = rememberScrollState, saveable). */
    @Test fun templates_keepNoPlainRememberedState() {
        val dir = listOf(File("src/main/java/kr/co/reevl/ontheway/ui/ds/tmpl"), File("app/src/main/java/kr/co/reevl/ontheway/ui/ds/tmpl")).first { it.exists() }
        dir.listFiles()!!.filter { it.extension == "kt" }.forEach { f ->
            val bad = Regex("""by remember\(""").findAll(f.readText()).count()
            assertEquals("${f.name}: use rememberSaveable for UI state", 0, bad)
        }
    }

    /** §15.7: HOME fills itself from memory / device caches only — the HOME composables never start a request. */
    @Test fun home_neverAsksTheServer() {
        val f = listOf(File("src/main/java/kr/co/reevl/ontheway/ui/screens/Screens.kt"), File("app/src/main/java/kr/co/reevl/ontheway/ui/screens/Screens.kt")).first { it.exists() }.readText()
        val home = f.substring(f.indexOf("fun HomeScreen("), f.indexOf("fun stopRow("))
        for (bad in listOf("load(", "loadArea", "loadNearby", "loadPlans", "refresh", "Repository", "api.")) assertTrue("HomeScreen mentions $bad", !home.contains(bad))
    }
}
