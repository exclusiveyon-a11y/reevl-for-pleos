package kr.co.reevl.ontheway.ui

import java.time.LocalDate
import kr.co.reevl.ontheway.Screen
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarStation
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.gleo.GleoActions
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.state.PlaceRowUi
import kr.co.reevl.ontheway.ui.state.RowLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/design/screen-ux-research.md P0-1, P0-2, P0-3, P0-5 (+ P2 Gleo particles). */
class UxP0Test {

    // ── P0-1: AI 검색 row price = the trade that met the condition ──

    private fun trade(price: Long, date: String = "2026-09-28") = LatestTrade(date, 84.9, price = price)

    @Test fun p0_1_searchRow_showsEvidenceTrade_notTheNewestOne() {
        // 조건 `8억 이하`: newest sale is 8억 3,000만, the sale that met the condition is 5억 6,000만.
        val item = SearchItem(cid = "a|1", name = "정동", lat = 37.5, lng = 127.0, distanceM = 586, households = 36,
            latestTrade = trade(83_000), evidenceTrade = trade(56_000, "2026-03-02"))
        val row = RowLines.searchRow(item, Side.RIGHT, inPlan = false)
        assertEquals(56_000L, row.trade!!.price)
        assertNull(row.priceLabel) // value only — the price is the condition's, not simply "최근"
        assertEquals("586m · 36세대 · 5.6억", RowLines.place(row))
    }

    @Test fun p0_1_withoutPriceCondition_fallsBackToNewestTrade_labelledRecent() {
        val item = SearchItem(cid = "a|2", name = "x", lat = 37.5, lng = 127.0, distanceM = 120, households = 1148, latestTrade = trade(236_500))
        val row = RowLines.searchRow(item, Side.UNKNOWN, inPlan = true)
        assertEquals(236_500L, row.trade!!.price)
        assertEquals("120m · 1,148세대 · 최근 23.6억", RowLines.place(row))
    }

    // ── P0-2: HOME never shows a past plan as "오늘 방문" ──

    private val today = LocalDate.of(2026, 11, 10)
    private fun plan(id: String, date: String?) = PlanSummary(id, "임장 $id", date, "planned", 1, 0, 3)

    @Test fun p0_2_onlyPastPlans_homeIsEmpty() {
        assertNull(PlanLogic.homePlan(listOf(plan("sat", "2026-11-07"), plan("old", "2026-10-01")), today))
        assertNull(PlanLogic.homePlan(emptyList(), today))
    }

    @Test fun p0_2_todayFirst_thenSoonestUpcoming_pastSkipped() {
        val past = plan("past", "2026-11-07")
        val next = plan("next", "2026-11-15")
        val later = plan("later", "2026-11-20")
        val now = plan("now", "2026-11-10")
        assertEquals("now", PlanLogic.homePlan(listOf(past, later, now, next), today)!!.id)
        assertEquals("next", PlanLogic.homePlan(listOf(past, later, next), today)!!.id)
        // undated plans are not stale — shown when nothing is dated today or later
        assertEquals("u", PlanLogic.homePlan(listOf(past, plan("u", null)), today)!!.id)
    }

    @Test fun p0_2_sectionLine_saysTodayOrTheShortDate_neverTheYear() {
        assertEquals("임장 now · 오늘", PlanLogic.homeSection(plan("now", "2026-11-10"), today))
        assertEquals("임장 next · 11.15 (일)", PlanLogic.homeSection(plan("next", "2026-11-15"), today))
        assertEquals("임장 u", PlanLogic.homeSection(plan("u", null), today))
    }

    // ── P0-3: lock lifted on NEARBY → reload for the new spot ──

    @Test fun p0_3_unlockOnNearby_reloads_onlyOnTheFallingEdge() {
        assertTrue(DrivingPolicy.reloadNearbyOnUnlock(wasDriving = true, nowDriving = false, screen = Screen.NEARBY))
        assertFalse(DrivingPolicy.reloadNearbyOnUnlock(wasDriving = false, nowDriving = false, screen = Screen.NEARBY)) // repeat parked signal
        assertFalse(DrivingPolicy.reloadNearbyOnUnlock(wasDriving = false, nowDriving = true, screen = Screen.NEARBY)) // locking
        assertFalse(DrivingPolicy.reloadNearbyOnUnlock(wasDriving = true, nowDriving = true, screen = Screen.NEARBY))
        for (s in Screen.values().filter { it != Screen.NEARBY })
            assertFalse("$s keeps its data", DrivingPolicy.reloadNearbyOnUnlock(wasDriving = true, nowDriving = false, screen = s))
    }

    // ── P0-5: one numbers line on every complex list ──

    private fun row(dist: Int?, hh: Int?, price: Long?, label: String? = RowLines.RECENT) =
        PlaceRowUi("k", "n", "k", null, dist, price?.let { trade(it) }, households = hh, priceLabel = label)

    @Test fun p0_5_order_isDistance_households_recentPrice() {
        assertEquals("120m · 1,148세대 · 최근 23.6억", RowLines.place(row(120, 1148, 236_500)))
        assertEquals("1.2km · 300세대 · 최근 8,800만", RowLines.place(row(1234, 300, 8_800)))
        assertEquals("50m · 1,148세대 · 최근 10억", RowLines.place(row(50, 1148, 100_000)))
    }

    @Test fun p0_5_missingPiece_dropsOnlyItself() {
        assertEquals("120m · 최근 23.6억", RowLines.place(row(120, null, 236_500)))
        assertEquals("120m · 1,148세대", RowLines.place(row(120, 1148, null)))
        assertEquals("1,148세대 · 최근 23.6억 · 11.08 저장", RowLines.place(row(null, 1148, 236_500), tail = "11.08 저장")) // 저장: no distance
        assertNull(RowLines.place(row(null, null, null)))
        assertEquals("120m", RowLines.place(row(120, 0, null))) // 0 households = unknown
    }

    @Test fun p0_5_planStop_keepsStationLead_thenHouseholds_andNewestTrade() {
        val c = CarComplex(cid = "c", name = "경희궁자이(3단지)", households = 1148, nearestStation = CarStation("서대문역", distanceM = 361),
            recentTrades = listOf(CarTrade("2026-09-28", 84.9, price = 236_500), CarTrade("2026-08-01", 84.9, price = 230_000)))
        assertEquals("서대문역 361m · 1,148세대 · 최근 23.6억", RowLines.stop(c, "서울 종로구"))
        assertEquals("서울 종로구", RowLines.stop(null, "서울 종로구"))
        assertEquals("1,148세대", RowLines.stop(c.copy(nearestStation = null, recentTrades = emptyList()), "서울 종로구"))
    }

    // ── P2: Gleo replies are English sentences since 2026-11-11 (action_callback.md §2.1); Korean particles are gone —
    //    see GleoTest.everyCallbackMessage_isAnEnglishSentence. Names stay as they are, without a particle.

}
