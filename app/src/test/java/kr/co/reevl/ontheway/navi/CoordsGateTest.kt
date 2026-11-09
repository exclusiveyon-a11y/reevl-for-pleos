package kr.co.reevl.ontheway.navi

import kr.co.reevl.ontheway.data.ReevlCoords
import kr.co.reevl.ontheway.spike.SpikeStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordsGateTest {

    private fun stop(lat: Double?, lng: Double?, verified: Boolean) = Stop(
        id = "s1", position = 1, kind = "complex", name = "n", address = "a",
        lat = lat, lng = lng, locationVerified = verified, visitStatus = "planned",
    )

    @Test fun nullLat_isBlocked() {
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, CoordsGate.check(stop(null, 126.9, true))?.code)
    }

    @Test fun nullLng_isBlocked() {
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, CoordsGate.check(stop(37.5, null, true))?.code)
    }

    @Test fun unverified_isBlocked() {
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, CoordsGate.check(stop(37.5, 126.9, false))?.code)
    }

    @Test fun outOfRange_isBlocked() {
        // A swapped Reevl pair (lat=126.9) must never pass.
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, CoordsGate.check(stop(126.9, 37.5, true))?.code)
    }

    @Test fun verified_passes() {
        assertNull(CoordsGate.check(stop(37.5, 126.9, true)))
    }

    @Test fun reevlPair_isLonFirst() {
        val p = ReevlCoords.fromReevlPair(doubleArrayOf(126.964152, 37.569205))
        assertEquals(37.569205, p.lat, 0.0)
        assertEquals(126.964152, p.lng, 0.0)
    }

    @Test fun spikeStop_usesConvertedCoords() {
        val s = SpikeStops.FIRST
        assertEquals(37.569205, s.lat!!, 0.0)
        assertEquals(126.964152, s.lng!!, 0.0)
        assertTrue(s.locationVerified)
        assertNull(CoordsGate.check(s))
    }
}
