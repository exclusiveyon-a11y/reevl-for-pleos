package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.SearchCond
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.nearby.NearbyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA loop 2026-11-10 round 1: what the AI 검색 screen says about how its list was made. */
class QaLoopSearchTest {
    private val fix = NearbyEngine.Fix(37.5, 127.0, timeMs = 0, accuracyM = 10.0)
    private val east = SearchItem(cid = "east", name = "east", lat = 37.5, lng = 127.0034, distanceM = 300)
    private val west = SearchItem(cid = "west", name = "west", lat = 37.5, lng = 126.9966, distanceM = 300)
    private val right = SearchCond("right", "오른쪽", "car")
    private val price = SearchCond("price_max", "8억 이하", "applied")

    @Test fun directionAskedWithoutHeading_isSaid_andItsChipIsMuted() {
        // Parked on the emulator: no heading → both sides listed; before the fix the screen showed an `오른쪽` chip as applied
        // and said nothing (Shown.note was computed but never drawn).
        val r = SearchResponse(conds = listOf(price, right), scanned = 42, matched = 2, items = listOf(east, west))
        val s = AiSearch.shown(r.items, r.conds, fix, headingDeg = null)
        assertEquals(2, s.rows.size)
        assertEquals(AiSearch.NO_DIRECTION_NOTE, AiSearch.noteLine(r, s))
        assertTrue(AiSearch.directionUnchecked(right, s))
        assertFalse(AiSearch.directionUnchecked(price, s))
    }

    @Test fun directionApplied_needsNoNote() {
        val r = SearchResponse(conds = listOf(right), scanned = 42, matched = 2, items = listOf(east, west))
        val s = AiSearch.shown(r.items, r.conds, fix, headingDeg = 0.0)
        assertNull(AiSearch.noteLine(r, s))
        assertFalse(AiSearch.directionUnchecked(right, s))
    }

    @Test fun noCondition_saysTheListIsJustNearby() {
        val r = SearchResponse(conds = emptyList(), scanned = 10, matched = 1, items = listOf(east), emptyReason = "no_condition")
        val s = AiSearch.shown(r.items, r.conds, fix, 0.0)
        assertEquals(AiSearch.NO_CONDITION_NOTE, AiSearch.noteLine(r, s))
    }

    @Test fun unsupportedCondition_isSaid() {
        val r = SearchResponse(conds = listOf(SearchCond("school", "학군 좋은", "unsupported")), scanned = 42, matched = 2, items = listOf(east, west))
        val s = AiSearch.shown(r.items, r.conds, fix, 0.0)
        assertEquals("학군 좋은 조건은 확인 못 했어요", AiSearch.noteLine(r, s))
    }

    @Test fun emptyRadius_isNotAConditionProblem() {
        // `사막`: nothing in 1 km → the action is to widen, not `조건 빼기`.
        val r = SearchResponse(conds = listOf(price), radiusM = 1000, scanned = 0, items = emptyList(), emptyReason = "no_candidates")
        val e = AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0))
        assertEquals(AiSearch.Empty.NoCandidates(1000, canWiden = true), e)
        val wide = r.copy(radiusM = AiSearch.WIDE_RADIUS_M)
        assertEquals(false, (AiSearch.empty(wide, AiSearch.shown(wide.items, wide.conds, fix, 0.0)) as AiSearch.Empty.NoCandidates).canWiden)
    }

    @Test fun objectParticle_followsTheLastSyllable() {
        assertEquals("을", AiSearch.objParticle("오른쪽"))   // was "오른쪽를"
        assertEquals("를", AiSearch.objParticle("30평대"))
        assertEquals("을", AiSearch.objParticle("학군 좋은"))
        assertEquals("를", AiSearch.objParticle("8억 이하"))
        assertEquals("을", AiSearch.objParticle("1000"))
        assertEquals("를", AiSearch.objParticle("2"))
    }
}

class QaLoopHomeTest {
    private fun row(cid: String, m: Int) = kr.co.reevl.ontheway.NearbyRow(
        kr.co.reevl.ontheway.data.NearbyComplex(cid = cid, name = cid, lat = 0.0, lng = 0.0, distanceM = m), NearbyEngine.Side.UNKNOWN, false)

    @Test fun homeNearest3_isNearestFirst_withoutPlanStops() {
        val got = kr.co.reevl.ontheway.ui.state.nearest3(listOf(row("a", 286), row("b", 439), row("c", 303), row("d", 120), row("e", 469)), setOf("d"))
        assertEquals(listOf("a", "c", "b"), got.map { it.c.cid })
    }
}

class QaLoopPlanTest {
    private fun s(id: String, d: String?) = kr.co.reevl.ontheway.data.PlanSummary(id, id, d, "planned", 1, 0, 1)
    private val today = java.time.LocalDate.parse("2026-11-10")

    @Test fun add_neverGoesIntoAPastPlan() {
        assertNull(kr.co.reevl.ontheway.plan.PlanLogic.addTarget(listOf(s("yesterday", "2026-11-09")), today))
        assertEquals("today", kr.co.reevl.ontheway.plan.PlanLogic.addTarget(listOf(s("yesterday", "2026-11-09"), s("today", "2026-11-10")), today)?.id)
        assertEquals("undated", kr.co.reevl.ontheway.plan.PlanLogic.addTarget(listOf(s("yesterday", "2026-11-09"), s("undated", null)), today)?.id)
    }
}

class QaLoopPlansRefreshTest {
    @Test fun askedRefresh_isDebounced30s() {
        val P = kr.co.reevl.ontheway.plan.PlanLogic
        assertTrue(P.plansStale(0L, 5_000L))
        assertFalse(P.plansStale(100_000L, 129_999L))
        assertTrue(P.plansStale(100_000L, 130_000L))
        val main = java.io.File("src/main/java/kr/co/reevl/ontheway/MainActivity.kt").readText()
        assertTrue(main.contains("nav { vm.refreshPlansOnAsk() }"))
        // never on a return from the background (§15.4: 0 requests)
        assertFalse(main.substringAfter("override fun onResume()").substringBefore("override fun onPause()").contains("Plans"))
    }
}

class QaLoopArrivalTest {
    private val stop = kr.co.reevl.ontheway.drive.Arrival.Stop("s1", 37.4140, 127.0960, planned = true)
    private val here = NearbyEngine.Fix(37.4140, 127.0960, timeMs = 0, accuracyM = 10.0)

    @Test fun undoOfDone_atTheStop_isArrivedAgainAtOnce() {
        val a = kr.co.reevl.ontheway.drive.Arrival()
        a.onTick(true, listOf(stop), here, 0); a.onTick(true, listOf(stop), here, 31_000)
        assertEquals("s1", a.stopId)
        a.onTick(true, listOf(stop.copy(planned = false)), here, 32_000) // 완료
        assertNull(a.stopId)
        a.restore("s1", "gps")                                           // 되돌리기
        a.onTick(true, listOf(stop), here, 33_000)
        assertEquals("s1", a.stopId)
        // left the place → cleared as before
        a.onTick(false, listOf(stop), NearbyEngine.Fix(37.4180, 127.0960, timeMs = 0, accuracyM = 10.0), 34_000)
        assertNull(a.stopId)
    }
}

class QaLoopPlanCidsTest {
    private fun st(id: String, cid: String?) = kr.co.reevl.ontheway.data.PlanStop(id = id, position = 1, kind = "apartment_complex", name = id,
        address = "서울", lat = 37.5, lng = 126.9, cid = cid, locationVerified = true, visitStatus = "planned")
    private fun plan(vararg s: kr.co.reevl.ontheway.data.PlanStop) = kr.co.reevl.ontheway.data.Plan("p", "t", "2026-11-10", "planned", 1, 0, s.toList())

    @Test fun inPlan_usesHomesPlanWhenNoneIsOpen() {
        val P = kr.co.reevl.ontheway.plan.PlanLogic
        assertEquals(setOf("fake|한빛"), P.planCids(null, plan(st("s1", "fake|한빛"))))
        assertEquals(setOf("fake|a"), P.planCids(plan(st("s1", "fake|a")), plan(st("s2", "fake|b"))))
        assertTrue(P.planCids(null, null).isEmpty())
    }
}
