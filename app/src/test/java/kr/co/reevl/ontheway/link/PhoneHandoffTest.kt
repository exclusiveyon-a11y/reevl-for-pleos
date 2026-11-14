package kr.co.reevl.ontheway.link

import java.time.LocalDate
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.link.PhoneHandoff.Change
import kr.co.reevl.ontheway.link.PhoneHandoff.Kind
import kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 휴대폰으로 넘기기: public URL only, refresh/stop rules (visible · foreground · parked · ≤ 10 min), change → close + action. */
class PhoneHandoffTest {

    private val today = LocalDate.of(2026, 11, 10)
    private fun sum(id: String, date: String?, updatedAt: Long = 1) = PlanSummary(id, "임장 $id", date, "planned", 1, updatedAt, 1)
    private fun stop(id: String, cid: String?, status: String = "planned", verified: Boolean = true) =
        PlanStop(id = id, position = id.last().digitToInt(), kind = "apartment_complex", cid = cid, name = "단지$id", lat = 37.4, lng = 127.1,
            locationVerified = verified, visitStatus = status)
    private fun plan(vararg s: PlanStop) = Plan("p", "토요일 임장", "2026-11-10", "planned", 1, 2, s.toList())

    // ── URL: public route + source tag, nothing else ──

    @Test fun url_isPublicRoute_withSourceTagOnly() {
        assertEquals("https://reevl.co.kr/plan?from=car", PhoneHandoff.url(Kind.PLAN))
        assertEquals("https://reevl.co.kr/ai?from=car", PhoneHandoff.url(Kind.SEARCH))
        for (k in Kind.values()) {
            val u = PhoneHandoff.url(k)
            assertTrue(u.startsWith("https://reevl.co.kr/"))
            assertFalse("no fragment: $u", u.contains('#'))
            assertEquals("only from=car in the query: $u", "from=car", u.substringAfter('?'))
            for (bad in listOf("token", "cid", "r=", "secret", "|")) assertFalse("$bad in $u", u.contains(bad))
        }
    }

    @Test fun url_plan_carriesTheWantedComplex_encoded() {
        val cid = "11110|경희궁자이(2단지)|홍파동|199"
        val u = PhoneHandoff.url(Kind.PLAN, cid)
        assertEquals("https://reevl.co.kr/plan?from=car&add=11110%7C%EA%B2%BD%ED%9D%AC%EA%B6%81%EC%9E%90%EC%9D%B4%282%EB%8B%A8%EC%A7%80%29%7C%ED%99%8D%ED%8C%8C%EB%8F%99%7C199", u)
        // Round trip — what the web reads with URLSearchParams.get('add').
        assertEquals(cid, java.net.URLDecoder.decode(u.substringAfter("&add="), "UTF-8"))
        for (bad in listOf("|", " ", "#", "token", "secret")) assertFalse("$bad in $u", u.contains(bad))
        assertEquals("space → %20, never +", "a%20b", PhoneHandoff.encode("a b"))
    }

    @Test fun url_ignoresBlankCid_andSearchNeverCarriesOne() {
        assertEquals("https://reevl.co.kr/plan?from=car", PhoneHandoff.url(Kind.PLAN, ""))
        assertEquals("https://reevl.co.kr/plan?from=car", PhoneHandoff.url(Kind.PLAN, "  "))
        assertEquals("https://reevl.co.kr/ai?from=car", PhoneHandoff.url(Kind.SEARCH, "11110|단지|동|1"))
    }

    @Test fun url_decodesFromTheDrawnQr() {
        // The same ZXing matrix the screen draws; QrLinkTest decodes the link QR — here only that it is made at all.
        for (k in Kind.values()) assertTrue(QrLink.matrix(PhoneHandoff.url(k)).size >= 21)
        assertTrue(QrLink.matrix(PhoneHandoff.url(Kind.PLAN, "11110|경희궁자이(2단지)|홍파동|199")).size >= 21)
    }

    // ── refresh / stop ──

    @Test fun shouldPoll_onlyOnScreen_inFront_parked_notFound_within10Min() {
        val t0 = 1_000L
        fun p(on: Boolean = true, fg: Boolean = true, driving: Boolean = false, found: Boolean = false, now: Long = t0 + 5_000) =
            PhoneHandoff.shouldPoll(on, fg, driving, found, t0, now)
        assertTrue(p())
        assertFalse("another screen", p(on = false))
        assertFalse("app in background", p(fg = false))
        assertFalse("driving-locked = 0 calls", p(driving = true))
        assertFalse("change already arrived", p(found = true))
        assertTrue("just before 10 min", p(now = t0 + PhoneHandoff.MAX_MS - 1))
        assertFalse("10 min reached", p(now = t0 + PhoneHandoff.MAX_MS))
        assertEquals(10_000L, PhoneHandoff.POLL_MS)
        assertEquals(600_000L, PhoneHandoff.MAX_MS)
        assertEquals("≤ 60 checks per QR", 60L, PhoneHandoff.MAX_MS / PhoneHandoff.POLL_MS)
    }

    @Test fun leave_refreshesOnce_onlyWithoutChange_andNeverWhileLocked() {
        assertTrue(PhoneHandoff.refreshOnLeave(found = false, driving = false))
        assertFalse("the change already refreshed plans + saved", PhoneHandoff.refreshOnLeave(found = true, driving = false))
        assertFalse("locked: 0 calls", PhoneHandoff.refreshOnLeave(found = false, driving = true))
    }

    // ── change detection ──

    @Test fun plan_noTargetBefore_thenTodaysPlanAppears_isAChange() {
        val base = PhoneHandoff.baseline(listOf(sum("old", "2026-11-09")), emptyList())
        assertNull("only yesterday's plan: nothing to add to yet", PhoneHandoff.detect(Kind.PLAN, base, listOf(sum("old", "2026-11-09")), emptyList(), today))
        assertEquals(Change.PlanMade("new"),
            PhoneHandoff.detect(Kind.PLAN, base, listOf(sum("old", "2026-11-09"), sum("new", "2026-11-10")), emptyList(), today))
    }

    @Test fun plan_pastPlanMovedToToday_isAChange() {
        val base = PhoneHandoff.baseline(listOf(sum("p", "2026-11-09", updatedAt = 10)), emptyList())
        assertEquals(Change.PlanMade("p"), PhoneHandoff.detect(Kind.PLAN, base, listOf(sum("p", "2026-11-10", updatedAt = 20)), emptyList(), today))
    }

    @Test fun plan_sameTargetUnchanged_isNotAChange_butAnEditIs() {
        val base = PhoneHandoff.baseline(listOf(sum("p", "2026-11-10", updatedAt = 10)), emptyList())
        assertNull(PhoneHandoff.detect(Kind.SEARCH, base, listOf(sum("p", "2026-11-10", updatedAt = 10)), emptyList(), today))
        assertEquals(Change.PlanMade("p"), PhoneHandoff.detect(Kind.SEARCH, base, listOf(sum("p", "2026-11-10", updatedAt = 11)), emptyList(), today))
    }

    @Test fun saved_closesOnlyTheSearchHandoff() {
        val base = PhoneHandoff.baseline(emptyList(), listOf("a"))
        assertEquals(Change.Saved("b"), PhoneHandoff.detect(Kind.SEARCH, base, emptyList(), listOf("b", "a"), today))
        assertNull("starring on the way to a plan is not the plan", PhoneHandoff.detect(Kind.PLAN, base, emptyList(), listOf("b", "a"), today))
        assertNull("unsave is not news", PhoneHandoff.detect(Kind.SEARCH, base, emptyList(), emptyList(), today))
    }

    @Test fun planBeatsSaved_andNoFreshAnswerMeansNoChange() {
        val base = PhoneHandoff.baseline(emptyList(), emptyList())
        assertEquals(Change.PlanMade("n"), PhoneHandoff.detect(Kind.SEARCH, base, listOf(sum("n", null)), listOf("x"), today))
        assertNull(PhoneHandoff.detect(Kind.SEARCH, base, null, null, today))
    }

    // ── result action ──

    @Test fun action_add_whenTheWantedComplexIsNotInThePlanYet() {
        val p = plan(stop("s1", "c1"))
        assertEquals(PlanAction.ADD, PhoneHandoff.planAction(p, "wanted") { true })
    }

    @Test fun action_guide_toTheWantedComplex_orTheNextStop() {
        val p = plan(stop("s1", "c1", status = "visited"), stop("s2", "c2"), stop("s3", "wanted"))
        assertEquals(PlanAction.GUIDE, PhoneHandoff.planAction(p, "wanted") { true })
        assertEquals("s3", PhoneHandoff.planStop(p, "wanted")!!.id)
        assertEquals("next planned stop without a wanted complex", "s2", PhoneHandoff.planStop(p, null)!!.id)
    }

    @Test fun action_open_whenNothingCanBeGuided() {
        assertEquals(PlanAction.OPEN, PhoneHandoff.planAction(plan(stop("s1", "c1")), null) { false })
        assertEquals(PlanAction.OPEN, PhoneHandoff.planAction(plan(), null) { true })
    }
}
