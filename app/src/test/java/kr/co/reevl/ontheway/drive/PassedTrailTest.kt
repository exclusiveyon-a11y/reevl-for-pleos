package kr.co.reevl.ontheway.drive

import kotlinx.coroutines.test.runTest
import kr.co.reevl.ontheway.data.NearbyComplexes
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.nearby.CachedNearbySource
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.nearby.NearbySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trail (§2.1), PassedAlong (§2.3), Arrival (§2.5), StopBudget, nearby cell cache (§2.2), 동 parsing (§2.4). */
class PassedTrailTest {
    private val north = 1 / 110_574.0
    private val east = 1 / (111_320.0 * Math.cos(Math.toRadians(37.0)))
    private fun at(n: Double, e: Double) = Pair(37.0 + n * north, 127.0 + e * east)
    private fun fix(n: Double, e: Double, t: Long, acc: Double = 5.0) = at(n, e).let { NearbyEngine.Fix(it.first, it.second, t, acc, 10.0, 0.0) }
    private fun place(id: String, n: Double, e: Double) = at(n, e).let { NearbyEngine.Place(id, it.first, it.second) }

    /** Drive north 600 m at 10 m/s, offering a fix every second. */
    private fun northTrail(t0: Long = 0): Trail {
        val tr = Trail()
        for (i in 0..60) tr.offer(fix(i * 10.0, 0.0, t0 + i * 1000L), moving = true, now = t0 + i * 1000L)
        return tr
    }

    @Test fun trail_samples25m_notWhileIdling_dropsInaccurate() {
        val tr = northTrail()
        assertTrue("10 m fixes, ≥ 25 m step → every 3rd fix (30 m): 21 points, got ${tr.points.size}", tr.points.size in 20..22)
        assertFalse(tr.offer(fix(700.0, 0.0, 70_000), moving = false, now = 70_000))
        assertFalse(tr.offer(fix(800.0, 0.0, 71_000, acc = 80.0), moving = true, now = 71_000))
    }

    @Test fun trail_keepsOnly10Minutes_and5km_andOffClears() {
        val tr = northTrail()
        tr.prune(60_000 + 10 * 60_000L + 1)
        assertTrue(tr.points.size < 25)
        val long = Trail(maxLengthM = 300.0)
        for (i in 0..60) long.offer(fix(i * 10.0, 0.0, i * 1000L), true, i * 1000L)
        val pts = long.points
        val len = pts.zipWithNext { a, b -> NearbyEngine.haversine(a.lat, a.lng, b.lat, b.lng) }.sum()
        assertTrue("length capped at 300 m, got $len", len <= 300.0)
        long.enabled = false
        assertTrue(long.points.isEmpty())
        assertFalse(long.offer(fix(0.0, 0.0, 100_000), true, 100_000))
    }

    @Test fun passed_sideByCrossProduct_within150m_newestFirst_excludesWhereItStopped() {
        val tr = northTrail()
        val places = listOf(
            place("eastEarly", 100.0, 60.0),   // right of a northbound car, passed at ~10 s
            place("westLate", 450.0, -80.0),   // left, passed at ~45 s
            place("far", 300.0, 400.0),        // 400 m off the line
            place("ahead", 700.0, 10.0),       // beyond the stop = "here/ahead", not passed
        )
        val got = PassedAlong.compute(tr.points, places, now = 60_000)
        assertEquals(listOf("westLate", "eastEarly"), got.map { it.id })
        assertEquals(Side.LEFT, got[0].side)
        assertEquals(Side.RIGHT, got[1].side)
        assertEquals(80.0, got[0].lateralM, 2.0)
    }

    @Test fun passed_tooOld_orOnTheLine_hasNoSideOrIsDropped() {
        val tr = northTrail()
        val onLine = PassedAlong.compute(tr.points, listOf(place("onLine", 200.0, 3.0)), now = 60_000)
        assertEquals(Side.UNKNOWN, onLine.single().side)
        val old = PassedAlong.compute(tr.points, listOf(place("p", 100.0, 60.0)), now = 10_000 + 10 * 60_000L + 1)
        assertTrue(old.isEmpty())
        assertTrue("a single point is no trail", PassedAlong.compute(tr.points.take(1), listOf(place("p", 0.0, 10.0)), 0).isEmpty())
    }

    @Test fun ago_words() {
        assertEquals("방금", PassedAlong.ago(0, 59_000))
        assertEquals("2분 전", PassedAlong.ago(0, 125_000))
    }

    // ── arrival ──

    private val stopA = at(0.0, 0.0).let { Arrival.Stop("a", it.first, it.second, planned = true) }

    @Test fun arrival_gps_150m_for30s_whileParked_thenLeave300m() {
        val a = Arrival()
        val near = fix(100.0, 0.0, 0)
        assertNull(a.onTick(parked = true, listOf(stopA), near, 0))
        assertNull(a.onTick(true, listOf(stopA), near, 29_000))
        assertEquals("a", a.onTick(true, listOf(stopA), near, 30_000))
        assertEquals("gps", a.via)
        a.onTick(true, listOf(stopA), fix(280.0, 0.0, 0), 40_000); assertEquals("a", a.stopId)
        a.onTick(true, listOf(stopA), fix(320.0, 0.0, 0), 41_000); assertNull(a.stopId)
    }

    @Test fun arrival_gps_notWhileMoving_notOutside150m_notDoneStops() {
        val a = Arrival()
        a.onTick(false, listOf(stopA), fix(10.0, 0.0, 0), 0); a.onTick(false, listOf(stopA), fix(10.0, 0.0, 0), 40_000)
        assertNull(a.stopId)
        a.onTick(true, listOf(stopA), fix(160.0, 0.0, 0), 0); a.onTick(true, listOf(stopA), fix(160.0, 0.0, 0), 40_000)
        assertNull(a.stopId)
        val done = stopA.copy(planned = false)
        a.onTick(true, listOf(done), fix(0.0, 0.0, 0), 0); a.onTick(true, listOf(done), fix(0.0, 0.0, 0), 40_000)
        assertNull(a.stopId)
    }

    @Test fun arrival_navi_onlyForTheGuidedStop_within200m() {
        val a = Arrival()
        assertNull("not guiding one of ours", a.onNavi(null, listOf(stopA), null))
        assertNull("fix 250 m away", a.onNavi("a", listOf(stopA), fix(250.0, 0.0, 0)))
        assertEquals("a", a.onNavi("a", listOf(stopA), fix(150.0, 0.0, 0)))
        assertEquals("navi", a.via)
        val b = Arrival()
        assertEquals("no fix: trust the navigation", "a", b.onNavi("a", listOf(stopA), null))
        b.onTick(true, listOf(stopA.copy(planned = false)), null, 0)
        assertNull("완료 clears it", b.stopId)
    }

    @Test fun stopBudget_12perHour() {
        val b = StopBudget()
        repeat(12) { assertTrue(b.take(it * 1000L)) }
        assertFalse(b.take(13_000))
        assertTrue(b.take(3_600_001L))
    }

    // ── nearby cell cache ──

    private class Counting : NearbySource {
        var calls = 0
        override suspend fun complexes(lat: Double, lng: Double, radiusM: Int): RepoResult<NearbyComplexes> {
            calls++; return RepoResult.Ok(NearbyComplexes())
        }
    }

    @Test fun cellCache_10min_perCell_forceDebounced10s() = runTest {
        val src = Counting()
        val c = CachedNearbySource(src)
        val (lat, lng) = at(10.0, 10.0)
        assertFalse(c.get(lat, lng, 500, 0).cached)
        val (lat2, lng2) = at(30.0, 30.0) // same ~100 m cell
        assertEquals(CachedNearbySource.cellOf(lat, lng), CachedNearbySource.cellOf(lat2, lng2))
        assertTrue(c.get(lat2, lng2, 500, 60_000).cached)
        assertTrue("⟳ within 10 s of the answer reuses it", c.get(lat, lng, 500, 5_000, force = true).cached)
        assertFalse("⟳ after 10 s asks", c.get(lat, lng, 500, 11_000, force = true).cached)
        assertTrue(c.get(lat, lng, 500, 11_000 + 9 * 60_000L).cached)
        assertFalse("10 min later asks again", c.get(lat, lng, 500, 11_000 + 10 * 60_000L + 1).cached)
        val (lat3, lng3) = at(250.0, 10.0)
        assertNotEquals(CachedNearbySource.cellOf(lat, lng), CachedNearbySource.cellOf(lat3, lng3))
        assertEquals(3, src.calls)
    }

    // ── 동 ──

    @Test fun dong_parse_naviAddress_andMatchAreaKey() {
        val d = DongLogic.parse("수정구 시흥동", "navi")!!
        assertEquals("시흥동", d.name); assertEquals("수정구", d.gu); assertEquals("수정구 시흥동", d.guDong)
        assertEquals("신흥2동", DongLogic.parse("경기 성남시 수정구 신흥2동 123-4", "navi")!!.name)
        assertEquals("송월동", DongLogic.parse("종로구 송월동", "navi")!!.name)
        assertNull(DongLogic.parse("", "navi")); assertNull(DongLogic.parse(null, "navi"))
        assertTrue(DongLogic.matchesKey(d, "경기 성남시 수정구 시흥동"))
        assertFalse(DongLogic.matchesKey(d, "경기 성남시 중원구 시흥동"))
        val fromArea = Dong("시흥동", "성남시 수정구", "area-cache")
        assertTrue("area gu `성남시 수정구` = navigation `수정구`", DongLogic.same(d, fromArea))
        assertFalse(DongLogic.same(d, Dong("신흥동", "수정구", "navi")))
        assertFalse(DongLogic.same(d, null))
    }
}
