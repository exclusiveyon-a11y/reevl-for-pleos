package kr.co.reevl.ontheway.ui

import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarDetail
import kr.co.reevl.ontheway.data.CarDetailTab
import kr.co.reevl.ontheway.data.CarMetric
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.ui.state.DetailLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** screen-ux-research §3.6 P1/P2 + DETAIL tabs + COMPARE (web CompareView bestSet). */
class DetailLogicTest {

    // ── facts (P1 ①) ──

    @Test fun facts_sideFirst_thenDistance_households_year() {
        assertEquals("오른쪽 586m · 1,248세대 · 2019년", DetailLogic.facts(Side.RIGHT, 586, 1248, 2019, 7))
    }

    @Test fun facts_noApprovalYear_usesBuildAge_andLeavesMissingPartsOut() {
        assertEquals("1.2km · 29년차", DetailLogic.facts(Side.UNKNOWN, 1234, null, null, 29))
        assertEquals("지나옴 · 36세대", DetailLogic.facts(Side.BEHIND, null, 36, null, null))
        assertNull(DetailLogic.facts(Side.UNKNOWN, null, null, null, null))
    }

    // ── footer (P1 ②) ──

    @Test fun footer_sourcePlusAtMostOneBasisFragment_directionDropped() {
        val src = "실거래 신고 · 국토교통부 · 10.26 기준"
        assertEquals("$src · 8억 이하 최근 1년 매매 실거래 기준",
            DetailLogic.footer(src, "오른쪽 진행 방향 기준 · 8억 이하 최근 1년 매매 실거래 기준 · 30평대 공급면적 기준"))
        assertEquals(src, DetailLogic.footer(src, null))
        assertEquals(src, DetailLogic.footer(src, "오른쪽 진행 방향 기준"))
    }

    // ── delta (P2 ③) ──

    private fun tr(date: String, price: Long, area: Double = 84.9) = CarTrade(date, area, price = price)

    @Test fun delta_sameAreaWithinAYear_isShown() {
        val rows = listOf(tr("2026-10-18", 74_000), tr("2026-09-30", 72_500), tr("2026-09-02", 61_000, 59.9))
        assertEquals(1_500L, DetailLogic.deltaOf(rows, 0))
        assertNull(DetailLogic.deltaOf(rows, 1)) // no older 84㎡
    }

    @Test fun delta_previousTradeOverAYearOld_noArrow() {
        val rows = listOf(tr("2026-10-18", 74_000), tr("2023-08-08", 52_000))
        assertNull(DetailLogic.deltaOf(rows, 0))
        val edge = listOf(tr("2026-10-18", 74_000), tr("2025-10-18", 70_000)) // exactly 365 days → still compared
        assertEquals(4_000L, DetailLogic.deltaOf(edge, 0))
    }

    @Test fun trades_newestFirst_atMostFour() {
        val rows = listOf(tr("2026-01-01", 1), tr("2026-10-18", 2), tr("2026-05-01", 3), tr("2026-09-01", 4), tr("2026-02-01", 5))
        assertEquals(listOf(2L, 4L, 3L, 5L), DetailLogic.trades(rows).map { it.price })
    }

    // ── tabs ──

    private val tabs = listOf(CarDetailTab("summary", "요약", listOf("rep", "fc12", "jr")), CarDetailTab("price", "시세", listOf("r36")),
        CarDetailTab("school", "학군", listOf("elem")))

    @Test fun tabRows_fixedOrder_presentKeysOnly_emptyTabsHidden() {
        val d = CarDetail(metrics = mapOf("jr" to CarMetric("전세가율", "39.8%"), "rep" to CarMetric("대표 시세", "13억 7,500만")), tabs = tabs, trades = "price")
        assertEquals(listOf("rep", "jr"), DetailLogic.tabRows(d, "summary").map { it.key })
        assertEquals(listOf("summary"), DetailLogic.visibleTabs(d, hasTrades = false).map { it.first })
        assertEquals(listOf("summary", "price"), DetailLogic.visibleTabs(d, hasTrades = true).map { it.first }) // 시세 lists the trades
    }

    @Test fun contract_parsesServerDetail_andToleratesOldServer() {
        val json = """{"cid":"a|1","name":"x","detail":{"v":1,"metrics":{"fc12":{"label":"AI 1년 예측","v":"+1.0%","n":0.0097,"better":"high","color":"ai"},
            "elem":{"label":"배정 초등학교","v":"서울논현초","sub":"확정 배정 · 799m"}},"tabs":[{"key":"summary","title":"요약","keys":["fc12","elem"]}],"trades":"price"}}"""
        val c = Api.json.decodeFromString(CarComplex.serializer(), json)
        assertEquals("ai", c.detail!!.metrics["fc12"]!!.color)
        assertEquals(listOf("AI 1년 예측", "배정 초등학교"), DetailLogic.tabRows(c.detail, "summary").map { it.label })
        assertNull(Api.json.decodeFromString(CarComplex.serializer(), """{"cid":"a|1","name":"x"}""").detail)
    }

    // ── compare ──

    private fun m(n: Double?, better: String? = "high") = n?.let { CarMetric("x", it.toString(), it, better) }

    @Test fun best_highOrLow_tiesAllWin_needsTwoValues() {
        assertEquals(setOf(1), DetailLogic.best(listOf(m(1.0), m(3.0), m(2.0))))
        assertEquals(setOf(0, 2), DetailLogic.best(listOf(m(5.0, "low"), m(7.0, "low"), m(5.0, "low"))))
        assertEquals(emptySet<Int>(), DetailLogic.best(listOf(m(5.0), null, null))) // a lone value is not "better"
        assertEquals(emptySet<Int>(), DetailLogic.best(listOf(m(4.6), m(4.6), m(4.6)))) // all tie → nothing to say
        assertEquals(emptySet<Int>(), DetailLogic.best(listOf(m(1.0, null), m(2.0, null)))) // neutral metric (대표 시세·연식)
        assertEquals(emptySet<Int>(), DetailLogic.best(listOf(CarMetric("배정 초등학교", "a"), CarMetric("배정 초등학교", "b")))) // text
    }

    @Test fun compareRows_unionInServerOrder_missingCellIsNull() {
        val a = CarDetail(metrics = mapOf("rep" to CarMetric("대표 시세", "1억")), tabs = tabs)
        val b = CarDetail(metrics = mapOf("jr" to CarMetric("전세가율", "50.0%")), tabs = tabs)
        val rows = DetailLogic.compareRows(listOf(a, b, null), "summary")
        assertEquals(listOf("rep", "jr"), rows.map { it.first })
        assertNull(rows[0].second[1])
        assertNull(rows[1].second[2])
    }

    @Test fun tray_noDuplicates_maxThree() {
        val same = { x: String, y: String -> x == y }
        assertEquals(listOf("a", "b"), DetailLogic.trayAdd(listOf("a", "b"), "a", same))
        assertEquals(listOf("a", "b", "c"), DetailLogic.trayAdd(listOf("a", "b"), "c", same))
        assertNull(DetailLogic.trayAdd(listOf("a", "b", "c"), "d", same))
        assertTrue(DetailLogic.COMPARE_MAX == 3)
    }
}
