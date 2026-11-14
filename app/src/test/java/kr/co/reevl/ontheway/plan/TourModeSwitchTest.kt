package kr.co.reevl.ontheway.plan

import kr.co.reevl.ontheway.BuildConfig
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The personal Pleos build hides every 고객 투어 모드 surface through one switch; the tour build keeps them. */
class TourModeSwitchTest {
    private val stop = PlanStop(id = "s", position = 1, kind = "apartment_complex", name = "A", visitStatus = "planned",
        appointmentAt = "2026-11-09T14:00:00+09:00")
    private val tour = Plan(id = "p", title = "t", status = "active", version = 1, updatedAt = 0, stops = listOf(stop), mode = "tour", clientLabel = "고객 A")

    @After fun reset() { Tour.uiEnabled = true }

    @Test fun defaultBuild_isPersonal_tourModeOff() {
        assertFalse("this build must ship with TOUR_MODE=false (use -PtourMode=true for the Kia tour build)", BuildConfig.TOUR_MODE)
    }

    @Test fun switchOff_hidesBadgeClientAppointmentsAndFirstAppointment() {
        Tour.uiEnabled = false
        assertFalse(Tour.isTour(tour))
        assertNull(Tour.modeLine(tour))
        assertNull(Tour.clientLabel(tour))
        assertNull(Schedule.stopTimeLabel(tour, stop))
        assertNull(Schedule.firstAppointment(tour))
    }

    @Test fun switchOn_keepsTheTourUi() {
        Tour.uiEnabled = true
        assertTrue(Tour.isTour(tour))
        assertEquals("고객 투어 모드 · 고객 A", Tour.modeLine(tour))
        assertEquals("14:00 약속", Schedule.stopTimeLabel(tour, stop))
    }
}
