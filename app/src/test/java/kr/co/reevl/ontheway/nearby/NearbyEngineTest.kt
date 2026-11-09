package kr.co.reevl.ontheway.nearby

import kr.co.reevl.ontheway.nearby.NearbyEngine.Fix
import kr.co.reevl.ontheway.nearby.NearbyEngine.HeadingSource
import kr.co.reevl.ontheway.nearby.NearbyEngine.Place
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7 master §3-A 수용 조건: "차량 진행 방향 0°/90°/180°/270°에서 좌·우 구분 단위 테스트", "0/360° 경계, 저속·정차·GPS
 * 정확도 저하·유턴·후방 후보 테스트". The car sits at 경희궁자이(2단지)'s representative point; four places lie ~100 m
 * due north/east/south/west of it.
 */
class NearbyEngineTest {

    private val lat0 = 37.569205
    private val lng0 = 126.964152
    private val dLat = 0.0009 // ≈ 100 m
    private val dLng = 0.00114 // ≈ 100 m at 37.57° N

    private val north = Place("N", lat0 + dLat, lng0)
    private val east = Place("E", lat0, lng0 + dLng)
    private val south = Place("S", lat0 - dLat, lng0)
    private val west = Place("W", lat0, lng0 - dLng)
    private val four = listOf(north, east, south, west)

    private fun car(acc: Double? = 8.0) = Fix(lat0, lng0, timeMs = 0, accuracyM = acc, speedMps = 0.0)

    private fun sides(heading: Double?, acc: Double? = 8.0) =
        NearbyEngine.classify(car(acc), heading, four).associate { it.id to it.side }

    @Test fun heading0_north() {
        assertEquals(mapOf("N" to Side.AHEAD, "E" to Side.RIGHT, "S" to Side.BEHIND, "W" to Side.LEFT), sides(0.0))
    }

    @Test fun heading90_east() {
        assertEquals(mapOf("N" to Side.LEFT, "E" to Side.AHEAD, "S" to Side.RIGHT, "W" to Side.BEHIND), sides(90.0))
    }

    @Test fun heading180_south() {
        assertEquals(mapOf("N" to Side.BEHIND, "E" to Side.LEFT, "S" to Side.AHEAD, "W" to Side.RIGHT), sides(180.0))
    }

    @Test fun heading270_west() {
        assertEquals(mapOf("N" to Side.RIGHT, "E" to Side.BEHIND, "S" to Side.LEFT, "W" to Side.AHEAD), sides(270.0))
    }

    @Test fun heading360_isTheSameAs0() {
        assertEquals(sides(0.0), sides(360.0))
        assertEquals(sides(0.0), sides(720.0))
        assertEquals(sides(270.0), sides(-90.0))
    }

    @Test fun zero360Boundary_placeJustEastOfNorth_whileHeading350_isAhead() {
        // place bearing ≈ 10°, heading 350° → relative +20 (not −340)
        val p = Place("p", lat0 + dLat, lng0 + dLng * 0.176) // tan(10°) ≈ 0.176
        val c = NearbyEngine.classify(car(), 350.0, listOf(p)).single()
        assertEquals(Side.AHEAD, c.side)
        assertEquals(20.0, c.relativeDeg!!, 0.5)
        // and the mirror: place bearing ≈ 350°, heading 10° → relative −20
        val q = Place("q", lat0 + dLat, lng0 - dLng * 0.176)
        val d = NearbyEngine.classify(car(), 10.0, listOf(q)).single()
        assertEquals(Side.AHEAD, d.side)
        assertEquals(-20.0, d.relativeDeg!!, 0.5)
    }

    @Test fun plusMinus180_isBehind_andNormalizeKeepsTheRange() {
        assertEquals(180.0, NearbyEngine.normalizeAngle(-180.0), 0.0)
        assertEquals(180.0, NearbyEngine.normalizeAngle(180.0), 0.0)
        assertEquals(-179.0, NearbyEngine.normalizeAngle(181.0), 1e-9)
        assertEquals(179.0, NearbyEngine.normalizeAngle(-181.0), 1e-9)
        assertEquals(0.0, NearbyEngine.normalizeAngle(360.0), 0.0)
        assertEquals(-90.0, NearbyEngine.normalizeAngle(270.0), 0.0)
        assertEquals(90.0, NearbyEngine.normalizeAngle(-270.0), 0.0)
        assertEquals(Side.BEHIND, NearbyEngine.sideOf(180.0))
        assertEquals(Side.BEHIND, NearbyEngine.sideOf(NearbyEngine.normalizeAngle(-180.0)))
    }

    @Test fun bucketEdges_followThePseudocodeExactly() {
        assertEquals(Side.AHEAD, NearbyEngine.sideOf(35.0))
        assertEquals(Side.AHEAD, NearbyEngine.sideOf(-35.0))
        assertEquals(Side.RIGHT, NearbyEngine.sideOf(35.0001))
        assertEquals(Side.RIGHT, NearbyEngine.sideOf(145.0))
        assertEquals(Side.BEHIND, NearbyEngine.sideOf(145.0001))
        assertEquals(Side.LEFT, NearbyEngine.sideOf(-35.0001))
        assertEquals(Side.LEFT, NearbyEngine.sideOf(-145.0))
        assertEquals(Side.BEHIND, NearbyEngine.sideOf(-145.0001))
    }

    @Test fun noHeading_isUnknown_forEveryPlace() {
        assertTrue(sides(null).values.all { it == Side.UNKNOWN })
        assertTrue(NearbyEngine.classify(car(), null, four).all { it.relativeDeg == null })
    }

    @Test fun badAccuracy_orNoAccuracy_isUnknown() {
        assertTrue(sides(90.0, acc = 51.0).values.all { it == Side.UNKNOWN })
        assertTrue(sides(90.0, acc = null).values.all { it == Side.UNKNOWN })
        assertEquals(Side.RIGHT, sides(0.0, acc = 50.0)["E"]) // exactly the limit still counts
    }

    @Test fun lowSpeed_givesNoLiveHeading_evenWithAPlatformBearing() {
        val slow = Fix(lat0, lng0, 1_000, 8.0, speedMps = 1.0, bearingDeg = 90.0)
        val prev = Fix(lat0 - 0.000005, lng0, 0, 8.0, speedMps = 1.0) // 0.5 m in 1 s
        assertNull(NearbyEngine.liveHeading(slow, prev))
        assertNull(NearbyEngine.liveHeading(slow.copy(speedMps = 0.0), null))
    }

    @Test fun platformBearing_isUsed_whenMovingFastEnough() {
        val h = NearbyEngine.liveHeading(Fix(lat0, lng0, 1_000, 8.0, speedMps = 8.0, bearingDeg = 450.0), null)
        assertNotNull(h)
        assertEquals(90.0, h!!.deg, 0.0)
        assertEquals(HeadingSource.PLATFORM_BEARING, h.source)
    }

    @Test fun courseOverGround_fromTheLastTwoFixes_whenThePlatformGivesNoBearing() {
        val prev = Fix(lat0 - dLat, lng0, 0, 8.0)
        val cur = Fix(lat0, lng0, 10_000, 8.0) // 100 m north in 10 s = 10 m/s
        val h = NearbyEngine.liveHeading(cur, prev)!!
        assertEquals(HeadingSource.COURSE_OVER_GROUND, h.source)
        assertEquals(0.0, NearbyEngine.normalizeAngle(h.deg), 0.5)
        // too slow between the fixes (100 m in 200 s = 0.5 m/s) → none
        assertNull(NearbyEngine.liveHeading(cur.copy(timeMs = 200_000), prev))
    }

    @Test fun uTurn_flipsLeftAndRight() {
        val northbound = NearbyEngine.liveHeading(Fix(lat0, lng0, 10_000, 8.0), Fix(lat0 - dLat, lng0, 0, 8.0))!!.deg
        val southbound = NearbyEngine.liveHeading(Fix(lat0 - dLat, lng0, 20_000, 8.0), Fix(lat0, lng0, 10_000, 8.0))!!.deg
        val e = Place("E", lat0 - dLat / 2, lng0 + dLng)
        val car = Fix(lat0 - dLat / 2, lng0, 0, 8.0)
        assertEquals(Side.RIGHT, NearbyEngine.classify(car, northbound, listOf(e)).single().side)
        assertEquals(Side.LEFT, NearbyEngine.classify(car, southbound, listOf(e)).single().side)
    }

    @Test fun parked_reusesTheLastMovingHeading_asAnEstimate_onlyWhenRecentAndNearby() {
        val moving = NearbyEngine.liveHeading(Fix(lat0, lng0, 10_000, 8.0), Fix(lat0 - dLat, lng0, 0, 8.0))!!
        val parkedHere = Fix(lat0 + 0.0001, lng0, 70_000, 8.0, speedMps = 0.0) // 11 m further, a minute later
        val h = NearbyEngine.headingForParked(parkedHere, live = null, lastMoving = moving)!!
        assertTrue(h.estimated)
        assertEquals(moving.deg, h.deg, 0.0)
        // too old
        assertNull(NearbyEngine.headingForParked(parkedHere.copy(timeMs = 10_000 + NearbyEngine.PARKED_HEADING_MAX_AGE_MS + 1), null, moving))
        // drove away (≈ 200 m)
        assertNull(NearbyEngine.headingForParked(parkedHere.copy(lat = lat0 + 2 * dLat), null, moving))
        // nothing measured while moving
        assertNull(NearbyEngine.headingForParked(parkedHere, null, null))
        // a live heading wins and is not an estimate
        val live = moving.copy(source = HeadingSource.PLATFORM_BEARING)
        assertEquals(false, NearbyEngine.headingForParked(parkedHere, live, moving)!!.estimated)
    }

    @Test fun haversine_andBearing_onKnownPoints() {
        // 경희궁자이(2단지) → 경희궁자이(3단지) (A2 demo coordinates): ≈ 97 m, ESE
        val d = NearbyEngine.haversine(37.569205, 126.964152, 37.568855, 126.965171)
        assertEquals(97.0, d, 3.0)
        val b = NearbyEngine.initialBearing(37.569205, 126.964152, 37.568855, 126.965171)
        assertTrue("bearing $b", b in 105.0..120.0)
        assertEquals(0.0, NearbyEngine.initialBearing(lat0, lng0, lat0 + dLat, lng0), 0.01)
        assertEquals(180.0, NearbyEngine.initialBearing(lat0, lng0, lat0 - dLat, lng0), 0.01)
        assertEquals(100.0, NearbyEngine.haversine(lat0, lng0, lat0 + dLat, lng0), 1.0)
    }

    @Test fun straightLineCopy() {
        assertEquals("직선 약 140m", NearbyEngine.straightLine(137.2))
        // under 50 m (offices in the complex's own building come back as 0 m) → never `직선 약 0m`
        assertEquals("직선 50m 이내", NearbyEngine.straightLine(0.0))
        assertEquals("직선 50m 이내", NearbyEngine.straightLine(2.0))
        assertEquals("직선 50m 이내", NearbyEngine.straightLine(49.0))
        assertEquals("직선 약 50m", NearbyEngine.straightLine(50.0))
        assertEquals("직선 약 160m", NearbyEngine.straightLine(155.0))
        assertEquals("직선 약 1km", NearbyEngine.straightLine(1004.0))
        assertEquals("직선 약 1.2km", NearbyEngine.straightLine(1240.0))
        assertEquals("오른쪽 후보", NearbyEngine.sideLabel(Side.RIGHT))
        assertNull(NearbyEngine.sideLabel(Side.UNKNOWN))
    }
}
