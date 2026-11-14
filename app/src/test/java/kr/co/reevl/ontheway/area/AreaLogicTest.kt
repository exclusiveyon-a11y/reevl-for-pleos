package kr.co.reevl.ontheway.area

import java.io.File
import kotlinx.coroutines.test.runTest
import kr.co.reevl.ontheway.data.AreaInfo
import kr.co.reevl.ontheway.data.AreaNeighbor
import kr.co.reevl.ontheway.data.AreaNews
import kr.co.reevl.ontheway.data.AreaResponse
import kr.co.reevl.ontheway.data.AreaRet
import kr.co.reevl.ontheway.data.Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaLogicTest {

    private fun nb(dong: String, rel: Int?, d: Int, east: Int = 0, north: Int = 0, current: Boolean = false, y: Double? = 0.1) =
        AreaNeighbor(key = "경기 성남시 수정구 $dong", dong = dong, gu = "성남시 수정구", ret = AreaRet(y1 = y), eastM = east, northM = north,
            distanceM = d, relDeg = rel, complexes = 3, current = current)

    @Test fun pct_signsAndRounding() {
        assertEquals("+26.4%", AreaLogic.pct(0.2639))
        assertEquals("−2.3%", AreaLogic.pct(-0.0227))
        assertEquals("0.0%", AreaLogic.pct(0.0))
        assertEquals("—", AreaLogic.pct(null))
    }

    /** Same stops as the web heatColor (rt.ts): t 0 = blue, 0.5 = neutral, 1 = red; text ink on light, white on strong. */
    @Test fun heat_matchesWebStops() {
        assertEquals(0xFF4D9FFF.toInt(), AreaLogic.heat(0.0, dark = false).bg)
        assertEquals(0xFFF4F6F9.toInt(), AreaLogic.heat(0.5, dark = false).bg)
        assertEquals(0xFFF04452.toInt(), AreaLogic.heat(1.0, dark = false).bg)
        assertEquals(0xFF2B2E37.toInt(), AreaLogic.heat(0.5, dark = true).bg)
        assertEquals(0xFF191F28.toInt(), AreaLogic.heat(0.5, dark = false).fg)
        assertEquals(0xFFFFFFFF.toInt(), AreaLogic.heat(1.0, dark = false).fg)
        // saturation: 1년 +20 % and beyond = full red; 0 % = neutral
        assertEquals(1.0, AreaLogic.tOf(0.35, AreaLogic.Period.Y1.cap), 1e-9)
        assertEquals(0.5, AreaLogic.tOf(0.0, AreaLogic.Period.M1.cap), 1e-9)
    }

    @Test fun grid_currentInCentre_aheadUp_rightRight_noOverlap() {
        val ns = listOf(nb("신흥동", 0, 100, current = true), nb("앞동", 0, 900), nb("오른동", 90, 900), nb("왼동", -90, 900), nb("뒤동", 180, 900),
            nb("앞동2", 5, 950), nb("앞동3", -5, 980))
        val g = AreaLogic.grid(ns)
        assertTrue(g.headingUp)
        val at = g.cells.associate { it.n.dong to (it.col to it.row) }
        assertEquals(0 to 0, at["신흥동"])
        assertEquals(0 to -1, at["앞동"])           // ahead = up (row −1)
        assertEquals(1 to 0, at["오른동"])
        assertEquals(-1 to 0, at["왼동"])
        assertEquals(0 to 1, at["뒤동"])
        assertEquals(ns.size, g.cells.map { it.col to it.row }.toSet().size) // every 동 its own cell
        assertTrue(g.cells.all { it.col in -2..2 && it.row in -2..2 })
    }

    @Test fun grid_withoutHeading_isNorthUp() {
        val g = AreaLogic.grid(listOf(nb("여기", null, 50, current = true), nb("북동", null, 800, east = 0, north = 800), nb("동동", null, 800, east = 800)))
        assertFalse(g.headingUp)
        val at = g.cells.associate { it.n.dong to (it.col to it.row) }
        assertEquals(0 to -1, at["북동"])
        assertEquals(1 to 0, at["동동"])
    }

    @Test fun grid_moreThanCells_dropsTheFarthest() {
        val ns = (0 until 30).map { nb("동$it", it * 12, 300 + it * 50, current = it == 0) }
        val g = AreaLogic.grid(ns)
        assertEquals(25, g.cells.size)
        assertTrue(g.cells.any { it.n.dong == "동0" && it.col == 0 && it.row == 0 })
    }

    @Test fun labels() {
        val a = AreaInfo("경기", "성남시 수정구", "신흥동", "경기 성남시 수정구 신흥동", ret = AreaRet(y1 = 0.2639), complexes = 26, households = 10738, trades12 = 511,
            stations = listOf("신흥역", "산성역"), schools = listOf("희망대초"))
        assertEquals("신흥동 · 1년 +26.4%", AreaLogic.homeValue(a))
        assertEquals("신흥동 +26.4%", AreaLogic.railValue(a))
        assertEquals("단지 26곳 · 10,738세대 · 최근 1년 거래 511건", AreaLogic.factsLine(a))
        assertEquals("신흥역·산성역 · 희망대초", AreaLogic.placesLine(a))
        val gu = a.copy(level = "gu", dong = "누상동")
        assertEquals("수정구", AreaLogic.placeName(gu))
        assertTrue(AreaLogic.regionLine(gu).contains("구 지수"))
        assertEquals("한국경제 · 11.09 · 두산 기사", AreaLogic.newsLine(AreaNews("t", "한국경제", "2026.11.09 18:07", "complex", "두산")))
    }

    @Test fun areaRef_words() {
        assertTrue(AreaLogic.isAreaRef("지금 지나는 동네"))
        assertTrue(AreaLogic.isAreaRef("여기 어때"))
        assertFalse(AreaLogic.isAreaRef("경희궁자이"))
        assertFalse(AreaLogic.isAreaRef(null))
    }

    @Test fun cache_perDong_sixHours() {
        val c = AreaCache()
        val r = AreaResponse(area = AreaInfo("경기", "성남시 수정구", "신흥동", "K"))
        c.put(r, 0)
        assertNotNull(c.get("K", 6 * 3600_000L))
        assertNull(c.get("K", 6 * 3600_000L + 1))
        assertNull(c.get("other", 0))
    }

    /** The fake server's fixture is a real /api/car/area answer — the app model must read it as is. */
    @Test fun realSnapshot_decodes() = runTest {
        val f = File("../scripts/fixtures/area_snapshot.json")
        val r = Api.json.decodeFromString(AreaResponse.serializer(), f.readText())
        assertEquals("신흥동", r.area!!.dong)
        assertTrue(r.neighbors.first().current)
        assertTrue(r.summary.length <= 120)
        assertTrue(r.news.all { it.title.isNotBlank() })
    }
}
