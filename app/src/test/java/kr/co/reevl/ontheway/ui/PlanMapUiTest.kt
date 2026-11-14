package kr.co.reevl.ontheway.ui

import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.state.planMapUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 오늘 방문 동선 지도 block: when it shows, what the line says (straight line only), when 순서 최적화 is offered. */
class PlanMapUiTest {
    private fun st(id: String, pos: Int, lat: Double?, status: String = PlanLogic.PLANNED) =
        PlanStop(id = id, position = pos, kind = "apartment_complex", cid = "c|$id", name = id, lat = lat, lng = lat?.let { 127.0 },
            locationVerified = lat != null, visitStatus = status)
    private fun plan(vararg s: PlanStop) = Plan("p", "t", null, "active", 1, 0, s.toList())

    @Test fun oneLocatedStop_noMap() {
        assertNull(planMapUi(plan(st("a", 1, 37.0), st("b", 2, null))))
    }

    @Test fun crossingOrder_offersOptimize_andSaysStraightLine() {
        val m = planMapUi(plan(st("a", 1, 37.00), st("d", 2, 37.03), st("b", 3, 37.01), st("c", 4, 37.02)))!!
        assertTrue(m.canOptimize)
        assertTrue(m.hint!!.startsWith("정리하면 "))
        assertTrue(m.summary, m.summary.startsWith("직선 ") && m.summary.contains("4곳"))
        assertFalse(m.summary.contains("분")) // never a drive time
        assertEquals(listOf(1, 2, 3, 4), m.pins.map { it.n })
        assertTrue(m.pins.first().next)
    }

    @Test fun shortestAlready_saysSo_noButton_andMissingStopsCounted() {
        val m = planMapUi(plan(st("a", 1, 37.00), st("b", 2, 37.01), st("c", 3, 37.02), st("x", 4, null)))!!
        assertFalse(m.canOptimize)
        assertNull(m.hint)
        assertEquals("1곳은 위치를 몰라 빠졌어요", m.next)
    }

    @Test fun doneAndNextMarks_followTheList() {
        val m = planMapUi(plan(st("a", 1, 37.00, PlanLogic.VISITED), st("b", 2, 37.01), st("c", 3, 37.02)))!!
        assertTrue(m.pins[0].done)
        assertTrue(m.pins[1].next)
    }

    @Test fun glance_numberFirst_thenNextStop() {
        val m = planMapUi(plan(st("a", 1, 37.00, PlanLogic.VISITED), st("b", 2, 37.01), st("c", 3, 37.02)))!!
        assertTrue(m.title, m.title.startsWith("남은 직선 ") && m.title.endsWith("2곳"))
        assertNull(m.next) // 다음 = the brand row; no shorter order → no hint
        val all = planMapUi(plan(st("a", 1, 37.00, PlanLogic.VISITED), st("b", 2, 37.01, PlanLogic.VISITED)))!!
        assertEquals("모두 다녀왔어요", all.title)
    }
}
