package kr.co.reevl.ontheway.drive

import kr.co.reevl.ontheway.car.VehicleMapping
import kr.co.reevl.ontheway.drive.DriveContext.Event
import kr.co.reevl.ontheway.drive.DriveContext.Gear
import kr.co.reevl.ontheway.drive.DriveContext.Ignition
import kr.co.reevl.ontheway.drive.DriveContext.State
import kr.co.reevl.ontheway.nearby.NearbyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** driving-context-spec §1.2 thresholds and hysteresis; pleos-signals §2 signal priority. */
class DriveContextTest {

    private fun fix(lat: Double, lng: Double, t: Long, speed: Double? = null, acc: Double? = 5.0) =
        NearbyEngine.Fix(lat, lng, t, acc, speed, null)

    private fun stopped(evs: List<Event>) = evs.filterIsInstance<Event.Stopped>().firstOrNull()

    // ── vehicle properties (primary) ──

    @Test fun vehicle_P_D0_D20_P_isParkedIdlingMovingJustStopped() {
        val d = DriveContext()
        d.gear(Gear.PARK, 0); d.speed(0.0, 0)
        assertEquals(State.BEFORE_START, d.state)
        d.gear(Gear.DRIVE, 1_000)
        assertEquals("D at 0 km/h = idling (AOSP: gear not P, speed zero)", State.IDLING, d.state)
        d.speed(20 / 3.6, 2_000)
        assertEquals(State.MOVING, d.state)
        assertEquals("vehicle", d.basis)
        // 60 s at 20 km/h ≈ 333 m (speed integration — no GPS needed, works in tunnels)
        d.tick(62_000)
        d.speed(0.0, 62_000)
        assertEquals(State.IDLING, d.state)
        val ev = d.gear(Gear.PARK, 63_000)
        assertEquals(State.JUST_STOPPED, d.state)
        val s = stopped(ev)!!
        assertTrue("trip ≈ 333 m, got ${s.tripM}", s.tripM in 320.0..345.0)
        assertEquals(0.0, d.tripM, 0.0)
    }

    @Test fun vehicle_speedHysteresis_0_5_enter_0_2_leave() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0)
        d.speed(0.4, 1); assertEquals(State.IDLING, d.state)
        d.speed(0.5, 2); assertEquals(State.MOVING, d.state)
        d.speed(0.3, 3); assertEquals("0.3 is above the leave threshold", State.MOVING, d.state)
        d.speed(0.19, 4); assertEquals(State.IDLING, d.state)
    }

    @Test fun parkingBrake_countsAsParked_evenInDrive() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(0.0, 0)
        assertEquals(State.IDLING, d.state)
        d.parkingBrake(true, 1)
        assertEquals(State.STAYING, d.state) // never moved → no stop event, staying
    }

    @Test fun shortHop_under300m_isStaying_noStopEvent() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(5.0, 0)
        d.tick(20_000) // 100 m
        d.speed(0.0, 20_000)
        val ev = d.gear(Gear.PARK, 21_000)
        assertNull(stopped(ev))
        assertEquals(State.STAYING, d.state)
        assertTrue("trip keeps counting toward the next stop", d.tripM > 90)
    }

    @Test fun justStopped_foldsToStaying_after180s() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(10.0, 0)
        d.speed(0.0, 40_000) // 400 m
        d.gear(Gear.PARK, 41_000)
        assertEquals(State.JUST_STOPPED, d.state)
        d.tick(41_000 + 179_000); assertEquals(State.JUST_STOPPED, d.state)
        d.tick(41_000 + 180_000); assertEquals(State.STAYING, d.state)
    }

    @Test fun stopAfterLongLight_moreThan120sSinceMoving_isStaying() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(10.0, 0)
        d.speed(0.0, 40_000)
        d.tick(40_000 + 121_000)
        val ev = d.gear(Gear.PARK, 40_000 + 121_000)
        assertNull(stopped(ev))
        assertEquals(State.STAYING, d.state)
    }

    @Test fun ignitionOff_isAway_butNotWhileInDrive() {
        val d = DriveContext()
        d.gear(Gear.PARK, 0)
        d.ignition(Ignition.OFF, 1)
        assertEquals(State.AWAY, d.state)
        d.ignition(Ignition.ON, 2)
        assertEquals(State.BEFORE_START, d.state)
        d.gear(Gear.DRIVE, 3); d.speed(5.0, 3)
        d.ignition(Ignition.OFF, 4)
        assertEquals("OFF while moving in D is not trusted", State.MOVING, d.state)
    }

    // ── fallback: CarUxRestrictions + GPS ──

    @Test fun noVehicle_unrestricted_isParked_restrictedWithoutFix_isMoving_tunnelRule() {
        val d = DriveContext()
        d.ux(false, 0); assertEquals(State.BEFORE_START, d.state); assertEquals("ux", d.basis)
        d.ux(true, 1_000); assertEquals("no fix while restricted = tunnel → moving", State.MOVING, d.state)
    }

    @Test fun gps_movingNeeds2sAbove1_5_idlingNeeds3sBelow0_5() {
        val d = DriveContext()
        d.fix(fix(37.0, 127.0, 0, speed = 0.0), 0)
        d.ux(true, 0)
        d.fix(fix(37.0, 127.0, 1_000, speed = 0.0), 1_000)
        assertEquals(State.IDLING, d.state)
        d.fix(fix(37.0, 127.0, 2_000, speed = 2.0), 2_000)
        assertEquals("1 s above is not enough", State.IDLING, d.state)
        d.fix(fix(37.0, 127.0, 3_000, speed = 2.0), 3_000)
        assertEquals(State.IDLING, d.state)
        d.fix(fix(37.0, 127.0, 4_000, speed = 2.0), 4_000)
        assertEquals("2 s above 1.5 m/s", State.MOVING, d.state)
        assertEquals("ux+gps", d.basis)
        d.fix(fix(37.0, 127.0, 5_000, speed = 0.2), 5_000)
        d.fix(fix(37.0, 127.0, 7_000, speed = 0.2), 7_000)
        assertEquals("2 s below is not enough", State.MOVING, d.state)
        d.fix(fix(37.0, 127.0, 8_000, speed = 0.2), 8_000)
        assertEquals("3 s below 0.5 m/s", State.IDLING, d.state)
    }

    @Test fun gps_15mFromIdleAnchor_isMoving() {
        val d = DriveContext()
        d.ux(true, 0)
        d.fix(fix(37.0, 127.0, 0), 0)
        d.fix(fix(37.0, 127.0, 1_000), 1_000)
        assertEquals(State.IDLING, d.state)
        d.fix(fix(37.0 + 16 / 110_574.0, 127.0, 20_000), 20_000) // 16 m north, slowly (no speed field, 0.8 m/s)
        assertEquals(State.MOVING, d.state)
    }

    @Test fun gps_staleFix30s_isMoving() {
        val d = DriveContext()
        d.ux(true, 0)
        d.fix(fix(37.0, 127.0, 0, speed = 0.0), 0)
        d.tick(29_000); assertEquals(State.IDLING, d.state)
        d.tick(31_000); assertEquals(State.MOVING, d.state)
    }

    @Test fun vehicleGear_winsOverUx() {
        val d = DriveContext()
        d.gear(Gear.PARK, 0)
        d.ux(true, 1) // e.g. the restriction callback lags behind the gear property
        assertEquals("gear P is parked even while the restriction says otherwise", State.BEFORE_START, d.state)
        assertEquals("vehicle", d.basis)
    }

    @Test fun background_isAway_return_restoresStayingAfterRecentTrip() {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(10.0, 0); d.speed(0.0, 40_000); d.gear(Gear.PARK, 41_000)
        d.background(50_000); assertEquals(State.AWAY, d.state)
        // spec §1.3 "S6 → 복귀: 같은 자리면 이전 상태": the 400 m trip ended in a stop event at 41 s → back at 60 s = S4a again
        // (was STAYING before 2026-11-10 §15.4, which dropped 방금 지나온 곳 whenever another app covered us).
        d.foreground(60_000); assertEquals(State.JUST_STOPPED, d.state)
        d.tick(41_000 + DriveContext.JUST_STOPPED_MS); assertEquals(State.STAYING, d.state)
        d.background(70_000); d.foreground(40_000 + 31 * 60_000L)
        assertEquals("30 min+ since moving = fresh start", State.BEFORE_START, d.state)
    }

    @Test fun vehicleMapping_publicConstants() {
        assertEquals(Gear.PARK, VehicleMapping.gear(4))
        assertEquals(Gear.DRIVE, VehicleMapping.gear(8))
        assertEquals("GEAR_1 = 16 is a forward gear", Gear.DRIVE, VehicleMapping.gear(16))
        assertEquals(Gear.REVERSE, VehicleMapping.gear(2))
        assertEquals(Gear.NEUTRAL, VehicleMapping.gear(1))
        assertNull(VehicleMapping.gear(null))
        assertEquals(Ignition.OFF, VehicleMapping.ignition(2))
        assertEquals(Ignition.ACC, VehicleMapping.ignition(3))
        assertEquals(Ignition.ON, VehicleMapping.ignition(4))
        assertFalse(VehicleMapping.ignition(9) == Ignition.ON)
    }

    // ── §15.4 leaving and returning (another app over us / pushed off-screen; Pleos does not kill the app) ──

    private fun stoppedCar(): DriveContext {
        val d = DriveContext()
        d.gear(Gear.DRIVE, 0); d.speed(20 / 3.6, 1_000); d.tick(62_000); d.speed(0.0, 62_000)
        assertTrue(stopped(d.gear(Gear.PARK, 63_000)) != null)
        assertEquals(State.JUST_STOPPED, d.state)
        return d
    }

    @Test fun backgroundAndBack_withinJustStopped_resumesS4a_withoutAnotherStopEvent() {
        val d = stoppedCar()
        val away = d.background(70_000)
        assertEquals(State.AWAY, d.state)
        assertNull(stopped(away))
        val back = d.foreground(100_000)
        assertEquals("S4a resumes (방금 지나온 곳 comes back)", State.JUST_STOPPED, d.state)
        assertNull("no second stop event → no second request", stopped(back))
        // and it still ends 3 min after the stop, not 3 min after the return
        d.tick(63_000 + DriveContext.JUST_STOPPED_MS)
        assertEquals(State.STAYING, d.state)
    }

    @Test fun backgroundAndBack_afterJustStoppedWindow_isStaying_neverAStop() {
        val d = stoppedCar()
        d.background(70_000)
        val back = d.foreground(63_000 + DriveContext.JUST_STOPPED_MS + 1)
        assertEquals(State.STAYING, d.state)
        assertNull(stopped(back))
    }

    @Test fun manyResumeCycles_whileParked_emitNoStopEvents() {
        val d = DriveContext()
        d.gear(Gear.PARK, 0)
        var t = 1_000L
        repeat(20) {
            assertNull(stopped(d.background(t))); t += 5_000
            assertNull(stopped(d.foreground(t))); t += 5_000
        }
        assertEquals(State.BEFORE_START, d.state)
    }
}
