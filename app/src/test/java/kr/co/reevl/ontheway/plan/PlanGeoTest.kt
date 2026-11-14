package kr.co.reevl.ontheway.plan

import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.plan.PlanGeo.LL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors reevl/frontend/tests/planGeo.test.mjs (2026-11-11) + the car's mid-trip rule and the map projection. */
class PlanGeoTest {
    private fun P(lat: Double, lng: Double) = LL(lat, lng)
    private fun near(a: Double, b: Double, tol: Double) = assertTrue("$a ≉ $b (±$tol)", kotlin.math.abs(a - b) <= tol)

    @Test fun haversine_oneDegreeLat_andSamePoint() {
        near(PlanGeo.haversineM(P(37.0, 127.0), P(38.0, 127.0)), 111195.0, 5.0)
        assertEquals(0.0, PlanGeo.haversineM(P(37.5, 127.0), P(37.5, 127.0)), 0.0)
    }

    @Test fun realCoords_gyeonghuigungXi2to3_within1km() {
        // web/data/coords/11110.json [lon, lat]: 경희궁자이(2단지) [126.964152, 37.569205] · (3단지) [126.965171, 37.568855]
        val d = PlanGeo.haversineM(P(37.569205, 126.964152), P(37.568855, 126.965171))
        assertTrue("distance $d", d > 0 && d < 1000)
    }

    @Test fun legsAndTotal_missingSideIsNull_andCountedAsGap() {
        val pts = listOf(P(37.0, 127.0), P(37.01, 127.0), null, P(37.03, 127.0))
        val legs = PlanGeo.legsM(pts)
        assertEquals(3, legs.size)
        near(legs[0]!!, 1112.0, 2.0)
        assertNull(legs[1]); assertNull(legs[2])
        val t = PlanGeo.totalM(pts)
        near(t.m, 1112.0, 2.0)
        assertEquals(2, t.gaps)
        assertEquals(emptyList<Double?>(), PlanGeo.legsM(listOf(P(37.0, 127.0))))
        assertEquals(PlanGeo.Total(0.0, 0), PlanGeo.totalM(emptyList()))
    }

    @Test fun hasLL_outsideKoreaNanNullAreNotCoordinates() {
        assertTrue(PlanGeo.hasLL(37.5, 127.0))
        assertFalse(PlanGeo.hasLL(127.0, 37.5)) // flipped [lon, lat]
        assertFalse(PlanGeo.hasLL(null, 127.0))
        assertFalse(PlanGeo.hasLL(Double.NaN, 127.0))
    }

    @Test fun fmtDist_sameAsWeb() {
        assertEquals("50m 이내", PlanGeo.fmtDist(12.0))
        assertEquals("850m", PlanGeo.fmtDist(846.0))
        assertEquals("1000m", PlanGeo.fmtDist(999.0))
        assertEquals("1.2km", PlanGeo.fmtDist(1234.0))
        assertEquals("12.4km", PlanGeo.fmtDist(12_449.0))
        assertEquals("123km", PlanGeo.fmtDist(123_456.0))
        assertEquals("", PlanGeo.fmtDist(-1.0))
    }

    private fun line(vararg ks: Double) = ks.map { P(37 + it * 0.01, 127.0) }

    @Test fun optimizeOrder_startFixed_lineInDistanceOrder() {
        assertEquals(listOf(0, 2, 3, 1), PlanGeo.optimizeOrder(line(0.0, 3.0, 1.0, 2.0)))
        assertEquals(listOf(0, 1), PlanGeo.optimizeOrder(line(0.0, 1.0)))
        assertEquals(emptyList<Int>(), PlanGeo.optimizeOrder(emptyList()))
    }

    @Test fun optimizeOrder_fixEndKeepsTheLastPoint() {
        val o = PlanGeo.optimizeOrder(line(0.0, 1.0, 2.0, 0.5), fixEnd = true)
        assertEquals(0, o.first()); assertEquals(3, o.last())
        assertEquals(listOf(0, 1, 2, 3), o.sorted())
    }

    @Test fun optimizeOrder_untanglesNearestNeighbour() {
        val pts = listOf(P(37.0, 127.0), P(37.0, 127.011), P(37.0, 126.99), P(37.02, 126.99), P(37.02, 127.011))
        val o = PlanGeo.optimizeOrder(pts)
        assertEquals(0, o[0])
        assertTrue(PlanGeo.pathM(pts, o) <= PlanGeo.pathM(pts, listOf(0, 1, 2, 3, 4)) + 1e-6)
    }

    /** Same LCG as the web test → the same 80 layouts. */
    private class Lcg(seed: Long) {
        private var s = seed and 0xFFFFFFFFL
        fun next(): Double { s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL; return s / 4294967296.0 }
    }

    private fun bruteBest(pts: List<LL>, fixEnd: Boolean): Double {
        val n = pts.size
        val mid = (1 until (if (fixEnd) n - 1 else n)).toMutableList()
        var best = Double.POSITIVE_INFINITY
        fun perm(l: Int) {
            if (l == mid.size) { best = minOf(best, PlanGeo.pathM(pts, listOf(0) + mid + (if (fixEnd) listOf(n - 1) else emptyList()))); return }
            for (i in l until mid.size) { java.util.Collections.swap(mid, l, i); perm(l + 1); java.util.Collections.swap(mid, l, i) }
        }
        perm(0)
        return best
    }

    @Test fun optimizeOrder_random80_permutationStartFixedNeverLongerAndShortest() {
        val r = Lcg(20261111)
        for (k in 0 until 80) {
            val n = 3 + (k % 8)
            val pts = List(n) { P(37.45 + r.next() * 0.15, 126.9 + r.next() * 0.2) }
            for (fixEnd in listOf(false, true)) {
                val o = PlanGeo.optimizeOrder(pts, fixEnd)
                assertEquals("permutation", pts.indices.toList(), o.sorted())
                assertEquals("start fixed", 0, o[0])
                if (fixEnd) assertEquals("end fixed", n - 1, o[n - 1])
                assertTrue("never longer than input", PlanGeo.pathM(pts, o) <= PlanGeo.pathM(pts, pts.indices.toList()) + 1e-6)
                assertTrue("shortest (n=$n)", PlanGeo.pathM(pts, o) <= bruteBest(pts, fixEnd) + 1e-6)
                assertEquals("deterministic", o, PlanGeo.optimizeOrder(pts, fixEnd))
            }
        }
    }

    private data class St(val id: String, val lat: Double?, val lng: Double?)
    private fun llOf(s: St) = if (PlanGeo.hasLL(s.lat, s.lng)) LL(s.lat!!, s.lng!!) else null

    @Test fun optimizeStops_proposalOnly_withoutCoordsGoLast() {
        val stops = listOf(St("a", 37.00, 127.0), St("x", null, null), St("d", 37.03, 127.0), St("b", 37.01, 127.0), St("c", 37.02, 127.0))
        val r = PlanGeo.optimizeStops(stops, ::llOf)!!
        assertEquals(listOf("a", "b", "c", "d", "x"), r.items.map { it.id })
        assertTrue(r.changed)
        assertEquals(1, r.skipped)
        assertTrue(r.afterM < r.beforeM)
        assertEquals(listOf("a", "x", "d", "b", "c"), stops.map { it.id })
    }

    @Test fun optimizeStops_alreadyShortest_orUnknownStart() {
        val ok = listOf(St("a", 37.00, 127.0), St("b", 37.01, 127.0), St("c", 37.02, 127.0))
        val r = PlanGeo.optimizeStops(ok, ::llOf)!!
        assertFalse(r.changed)
        assertEquals(listOf("a", "b", "c"), r.items.map { it.id })
        assertEquals(r.beforeM, r.afterM, 0.0)
        assertNull(PlanGeo.optimizeStops(listOf(St("x", null, null)) + ok, ::llOf))
        assertNull(PlanGeo.optimizeStops(emptyList<St>(), ::llOf))
    }

    @Test fun optimizeStops_fixEndKeepsTheLastStop() {
        val stops = listOf(St("a", 37.00, 127.0), St("c", 37.02, 127.0), St("b", 37.01, 127.0), St("z", 37.005, 127.0))
        assertEquals("z", PlanGeo.optimizeStops(stops, ::llOf, fixEnd = true)!!.items.last().id)
        assertTrue(PlanGeo.optimizeStops(stops, ::llOf)!!.items.last().id != "z")
    }

    // ── car rules ──

    private fun stop(id: String, pos: Int, lat: Double?, status: String = PlanLogic.PLANNED) =
        PlanStop(id = id, position = pos, kind = "apartment_complex", cid = "c|$id", name = id, lat = lat, lng = lat?.let { 127.0 },
            locationVerified = lat != null, visitStatus = status)
    private fun plan(vararg s: PlanStop) = Plan("p", "토요일 임장", null, "planned", 3, 0, s.toList())

    @Test fun proposal_nothingVisited_isTheWebs_stop1Fixed() {
        val p = plan(stop("a", 1, 37.00), stop("d", 2, 37.03), stop("b", 3, 37.01), stop("c", 4, 37.02))
        val r = PlanGeo.proposal(p)!!
        assertEquals(listOf("a", "b", "c", "d"), r.order)
        assertTrue(r.changed); assertEquals(1, r.fixed); assertEquals(3, r.version)
        near(r.beforeM, PlanGeo.planTotalM(p).m, 1e-6)
        assertTrue(r.savedM > 0)
    }

    @Test fun proposal_midTrip_keepsHistoryAndStartsAtTheLastVisited() {
        // a visited, then c (lat 37.02) … the planned ones after it get reordered from c; a stays first.
        val p = plan(stop("a", 1, 37.00, PlanLogic.VISITED), stop("c", 2, 37.02, PlanLogic.SKIPPED),
            stop("e", 3, 37.04), stop("d", 4, 37.03), stop("f", 5, 37.05))
        val r = PlanGeo.proposal(p)!!
        assertEquals(listOf("a", "c", "d", "e", "f"), r.order)
        assertEquals(2, r.fixed)
    }

    @Test fun proposal_tooFewMovableStops_isNull() {
        assertNull(PlanGeo.proposal(plan(stop("a", 1, 37.0), stop("b", 2, 37.01))))
        assertNull(PlanGeo.proposal(plan(stop("a", 1, 37.0, PlanLogic.VISITED), stop("b", 2, 37.01, PlanLogic.VISITED), stop("c", 3, 37.02))))
        assertNull(PlanGeo.proposal(plan(stop("a", 1, null), stop("b", 2, 37.01), stop("c", 3, 37.02), stop("d", 4, 37.03))))
        assertNotNull(PlanGeo.proposal(plan(stop("a", 1, 37.0), stop("b", 2, 37.02), stop("c", 3, 37.01))))
    }

    @Test fun fit_keepsAspect_padsAndCentres() {
        val pts = listOf(P(37.0, 127.0), P(37.01, 127.0), null)
        val f = PlanGeo.fit(pts, 400f, 200f, 20f)
        assertNull(f[2])
        val (x0, y0) = f[0]!!; val (x1, y1) = f[1]!!
        assertEquals(200f, x0, 0.01f); assertEquals(200f, x1, 0.01f) // same longitude → centred horizontally
        assertEquals(180f, y0, 0.01f); assertEquals(20f, y1, 0.01f) // north is up, padded
        assertEquals(listOf(200f to 100f), PlanGeo.fit(listOf(P(37.0, 127.0)), 400f, 200f, 20f))
        assertEquals(listOf<Pair<Float, Float>?>(null), PlanGeo.fit(listOf(null), 400f, 200f, 20f))
    }

    @Test fun remaining_fromTheLastVisitedStop() {
        val p = plan(stop("a", 1, 37.00, PlanLogic.VISITED), stop("b", 2, 37.01), stop("c", 3, 37.02))
        near(PlanGeo.remainingM(p).m, PlanGeo.haversineM(P(37.0, 127.0), P(37.02, 127.0)), 1e-6)
        val q = plan(stop("a", 1, 37.00), stop("b", 2, 37.01))
        near(PlanGeo.remainingM(q).m, PlanGeo.planTotalM(q).m, 1e-9)
    }

    @Test fun spread_separatesCoveringPins_keepsFarOnesAndNulls() {
        val out = PlanGeo.spread(listOf(100f to 100f, 110f to 100f, 400f to 400f, null, 100f to 100f), 60f)
        for (i in out.indices) for (j in i + 1 until out.size) {
            val a = out[i] ?: continue; val b = out[j] ?: continue
            val d = kotlin.math.hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())
            assertTrue("pins $i,$j still overlap: $d", d >= 59.0)
        }
        assertEquals(400f to 400f, out[2])
        assertNull(out[3])
        assertTrue(out[0]!!.first < out[1]!!.first) // order kept left → right
    }
}
