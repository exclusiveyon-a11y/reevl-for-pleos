package kr.co.reevl.ontheway.plan

import java.time.LocalDate
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLogicTest {

    private fun stop(id: String, pos: Int, status: String = "planned", verified: Boolean = true, lat: Double? = 37.5, lng: Double? = 126.9) =
        PlanStop(id = id, position = pos, kind = "apartment_complex", name = "단지$id", address = "서울", lat = lat, lng = lng,
            locationVerified = verified, visitStatus = status)

    private fun plan(vararg s: PlanStop) = Plan("p", "토요일 임장", "2026-11-07", "planned", 1, 0, s.toList())

    @Test fun nextStop_isFirstPlannedByPosition_notByArrayOrder() {
        val p = plan(stop("c", 3), stop("a", 1, "visited"), stop("b", 2, "skipped"))
        assertEquals("c", PlanLogic.nextStop(p)!!.id)
        assertNull(PlanLogic.nextStop(plan(stop("a", 1, "visited"))))
    }

    @Test fun statusLabels_andGuideLabel() {
        val fresh = plan(stop("a", 1), stop("b", 2), stop("c", 3))
        val next = PlanLogic.nextStop(fresh)
        assertEquals(listOf("다음 방문", "방문 예정", "방문 예정"), PlanLogic.ordered(fresh).map { PlanLogic.statusLabel(it, next) })
        assertEquals("첫 장소 안내", PlanLogic.guideLabel(fresh))

        val after = plan(stop("a", 1, "visited"), stop("b", 2), stop("c", 3, "skipped"))
        val n2 = PlanLogic.nextStop(after)
        assertEquals(listOf("방문 완료", "다음 방문", "건너뜀"), PlanLogic.ordered(after).map { PlanLogic.statusLabel(it, n2) })
        assertEquals("다음 장소 안내", PlanLogic.guideLabel(after))
    }

    @Test fun unverifiedOrNullCoords_cannotGuide() {
        assertTrue(PlanLogic.canGuide(stop("a", 1)))
        assertFalse(PlanLogic.canGuide(stop("a", 1, verified = false)))
        assertFalse(PlanLogic.canGuide(stop("a", 1, lat = null)))
        assertFalse(PlanLogic.canGuide(stop("a", 1, lat = 126.9, lng = 37.5))) // swapped
    }

    @Test fun toNaviStop_keepsLatLngAsIs() {
        val s = PlanLogic.toNaviStop(stop("a", 1, lat = 37.569205, lng = 126.964152))
        assertEquals(37.569205, s.lat!!, 0.0)
        assertEquals(126.964152, s.lng!!, 0.0)
        assertEquals("a", s.id)
    }

    @Test fun headline_andDates() {
        val today = LocalDate.of(2026, 10, 26)
        val sat = PlanSummary("p", "토요일 임장", "2026-11-07", "planned", 1, 0, 3)
        assertEquals("11월 7일 (토) 갈 곳 3곳", PlanLogic.headline(sat, today))
        assertEquals("오늘 갈 곳 3곳", PlanLogic.headline(sat, LocalDate.of(2026, 11, 7)))
        assertEquals("갈 곳 3곳", PlanLogic.headline(sat.copy(plannedDate = null), today)) // M-10: no "오늘" without a date
        assertEquals("2026년 11월 7일 (토)", PlanLogic.dateLabel("2026-11-07"))
        assertEquals("날짜 미정", PlanLogic.dateLabel(null))
    }

    @Test fun sortForToday_todayThenSoonestUpcomingThenRest() {
        val today = LocalDate.of(2026, 10, 26)
        fun s(id: String, d: String?) = PlanSummary(id, id, d, "planned", 1, 0, 1)
        val sorted = PlanLogic.sortForToday(listOf(s("past", "2026-10-01"), s("far", "2026-12-01"), s("none", null), s("soon", "2026-11-07"), s("today", "2026-10-26")), today)
        assertEquals(listOf("today", "soon", "far", "past", "none"), sorted.map { it.id })
    }

    @Test fun distanceAndDuration() {
        assertEquals("24km", PlanLogic.distance(24000))
        assertEquals("8.5km", PlanLogic.distance(8500))
        assertEquals("2km", PlanLogic.distance(2000))
        assertEquals("800m", PlanLogic.distance(800))
        assertEquals("37분", PlanLogic.duration(2220))
        assertEquals("1시간 5분", PlanLogic.duration(3900))
        assertNull(PlanLogic.distance(null))
    }
}
