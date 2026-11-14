package kr.co.reevl.ontheway.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA loop 2026-11-10 source gates for layout fixes that a JVM test cannot render. */
class QaLoopGateTest {
    private fun src(p: String) = File("src/main/java/kr/co/reevl/ontheway/$p").readText()

    @Test fun nightHeader_usesTheTransparentMark_notTheWhiteLauncherSquare() {
        val top = src("ui/ds/comp/TopBar.kt")
        assertFalse(top.contains("reevl_app_icon"))
        assertFalse(top.contains("background(Color.White)"))
        assertTrue(File("src/main/res/drawable-nodpi/reevl_mark.png").exists())
        assertFalse(src("ui/ds/comp/Lock.kt").contains("reevl_app_icon"))
    }

    @Test fun areaSentences_wrap_soNumbersAreNeverCut() {
        val area = src("ui/screens/AreaScreen.kt")
        val top = area.lines().first { it.contains("tag = \"area_top") }
        assertTrue("area_top must wrap", top.contains("wrap = true"))
        // 2026-11-14 요약 redesign: insight sentences live in AreaSummary — two lines, never one cut line.
        val sum = src("ui/ds/comp/AreaSummary.kt")
        assertTrue(sum.contains("Text(i.text, style = t.rowSecondary.copy(color = r.ink2), maxLines = 2"))
    }

    @Test fun compareBest_needsTwoColumnsOnScreen() {
        assertTrue(src("ui/ds/tmpl/Compare.kt").contains("c?.best == true && shown.size >= 2"))
    }
}

class QaLoopHomeFieldGateTest {
    @Test fun homeField_isItsOwnEntry_notTheLastSearch() {
        val b = File("src/main/java/kr/co/reevl/ontheway/ui/state/UiBuilders.kt").readText()
        val home = b.substringAfter("fun AppViewModel.homeUi()").substringBefore("fun AppViewModel.homeEvents")
        assertTrue(home.contains("query = homeQuery"))
        assertFalse(home.contains("query = searchQuery"))
    }
}

class QaLoopNoticeTest {
    @Test fun offlineWords_areTheSameOnEveryScreen() {
        val off = kr.co.reevl.ontheway.data.UserMessages.OFFLINE
        org.junit.Assert.assertEquals("오프라인이에요", kr.co.reevl.ontheway.ui.state.Notices.short(off))
        val area = File("src/main/java/kr/co/reevl/ontheway/ui/state/AreaUi.kt").readText()
        assertTrue(area.contains("Notices.short(m)"))
    }
}

class QaLoopListNoticeTest {
    private val N = kr.co.reevl.ontheway.ui.state.Notices
    private val off = kr.co.reevl.ontheway.data.UserMessages.OFFLINE
    private val zone = java.time.ZoneId.of("Asia/Seoul")
    private val at = java.time.ZonedDateTime.of(2026, 11, 10, 20, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun offlineOverOldRows_saysWhenTheyAreFrom() {
        org.junit.Assert.assertEquals("오프라인 · 20:30 기준", N.forList(off, hasRows = true, syncedAt = at, zone = zone)?.text)
        assertTrue(N.forList(off, true, at, zone)!!.retry)
        org.junit.Assert.assertEquals("오프라인이에요", N.forList(off, hasRows = false, syncedAt = at, zone = zone)?.text)
        org.junit.Assert.assertNull(N.forList(null, true, at, zone))
    }
}

class QaLoopArrivalGateTest {
    @Test fun arrival_staysRightUnderTheField() {
        val t = File("src/main/java/kr/co/reevl/ontheway/ui/ds/tmpl/Templates.kt").readText()
        val home = t.substringAfter("fun HomeTemplate(").substringBefore("fun ListTemplate(")
        Regex("""SearchField\(field\)\s*\n\s*(\w+)\(""").findAll(home).forEach { assertTrue(it.groupValues[1] == "arrival") }
        assertTrue(File("src/main/java/kr/co/reevl/ontheway/ui/screens/Screens.kt").readText().contains("wrap = true, tag = \"stop_"))
    }
}

class QaLoopNarrowRowGateTest {
    @Test fun narrowRows_wrapAndChipYieldsToButton() {
        val row = File("src/main/java/kr/co/reevl/ontheway/ui/ds/comp/Row.kt").readText()
        assertTrue(row.contains("val wrap = s.wrap || narrow"))
        assertTrue(row.contains("val chip = if (narrow && s.button != null) null else s.chip"))
    }
}

class QaLoopNarrowChipsGateTest {
    @Test fun narrowHome_showsTwoChips() {
        assertTrue(File("src/main/java/kr/co/reevl/ontheway/ui/ds/tmpl/Templates.kt").readText().contains("suggest.take(if (narrowWin) 2 else 3)"))
    }
}
