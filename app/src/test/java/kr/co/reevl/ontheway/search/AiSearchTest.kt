package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.SearchCheck
import kr.co.reevl.ontheway.data.SearchCond
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSearchTest {

    // Car at 37.5/127.0 heading north (0°), good accuracy. ~300 m away in each direction.
    private val fix = NearbyEngine.Fix(37.5, 127.0, timeMs = 0, accuracyM = 10.0)
    private fun item(cid: String, lat: Double, lng: Double, checks: List<SearchCheck> = emptyList()) =
        SearchItem(cid = cid, name = cid, lat = lat, lng = lng, distanceM = 300, checks = checks)
    private val east = item("east", 37.5, 127.0034)
    private val west = item("west", 37.5, 126.9966)
    private val north = item("north", 37.5027, 127.0)
    private val south = item("south", 37.4973, 127.0)
    private val all = listOf(east, west, north, south)
    private fun car(label: String, key: String = "") = SearchCond(key, label, "car")
    private val price = SearchCond("price_max", "8억 이하", "applied")

    // ── cond chips ──

    @Test fun chips_mapStatus_appliedCarUnsupported_unknownIsMuted_blankDropped() {
        val chips = AiSearch.chips(listOf(price, car("오른쪽", "right"), SearchCond("school", "학군 좋은", "unsupported"),
            SearchCond("x", "새 상태", "future"), SearchCond("y", " ", "applied")))
        assertEquals(listOf(AiSearch.ChipKind.Applied, AiSearch.ChipKind.Direction, AiSearch.ChipKind.Unsupported, AiSearch.ChipKind.Unsupported),
            chips.map { it.kind })
        assertEquals(listOf("8억 이하", "오른쪽", "학군 좋은", "새 상태"), chips.map { it.label })
        assertTrue(AiSearch.hasUnsupported(listOf(price, SearchCond("s", "학군", "unsupported"))))
        assertFalse(AiSearch.hasUnsupported(listOf(price, car("왼쪽"))))
    }

    @Test fun sideOf_directionWords_byKeyOrLabel_onlyForCarStatus() {
        assertEquals(Side.RIGHT, AiSearch.sideOf(car("오른쪽")))
        assertEquals(Side.LEFT, AiSearch.sideOf(car("왼쪽")))
        assertEquals(Side.AHEAD, AiSearch.sideOf(car("앞")))
        assertEquals(Side.BEHIND, AiSearch.sideOf(car("방금 지나온")))
        assertEquals(Side.RIGHT, AiSearch.sideOf(car("R", key = "right")))
        assertEquals(Side.BEHIND, AiSearch.sideOf(car("passed", key = "passed")))
        assertNull(AiSearch.sideOf(SearchCond("right", "오른쪽", "applied")))
        assertNull(AiSearch.sideOf(car("근처")))
    }

    // ── side filtering ──

    @Test fun withHeading_rightCond_keepsOnlyRightCandidates() {
        val s = AiSearch.shown(all, listOf(price, car("오른쪽")), fix, headingDeg = 0.0)
        assertTrue(s.filtered)
        assertEquals(listOf("east"), s.rows.map { it.item.cid })
        assertEquals(Side.RIGHT, s.rows[0].side)
        assertEquals("현재 진행 방향 기준", s.note)
    }

    @Test fun withHeading_justPassed_meansBehind_andHeadingEastFlipsSides() {
        assertEquals(listOf("south"), AiSearch.shown(all, listOf(car("방금 지나온")), fix, 0.0).rows.map { it.item.cid })
        // heading east (90°): north is now on the left, south on the right
        assertEquals(listOf("south"), AiSearch.shown(all, listOf(car("오른쪽")), fix, 90.0).rows.map { it.item.cid })
        assertEquals(listOf("north"), AiSearch.shown(all, listOf(car("왼쪽")), fix, 90.0, headingEstimated = true).rows.map { it.item.cid })
        assertEquals("멈추기 직전 진행 방향 기준(방향 추정)", AiSearch.shown(all, listOf(car("왼쪽")), fix, 90.0, headingEstimated = true).note)
    }

    @Test fun withoutHeading_noFilter_allShown_andSaysSo() {
        val s = AiSearch.shown(all, listOf(car("오른쪽")), fix, headingDeg = null)
        assertFalse(s.filtered)
        assertEquals(4, s.rows.size)
        assertTrue(s.rows.all { it.side == Side.UNKNOWN })
        assertEquals("방향을 몰라 좌우 구분 없이 보여요", s.note)
    }

    @Test fun badAccuracy_orNoFix_isUnknownDirection_noFilter() {
        val poor = fix.copy(accuracyM = 80.0)
        assertEquals(AiSearch.NO_DIRECTION_NOTE, AiSearch.shown(all, listOf(car("오른쪽")), poor, 0.0).note)
        assertEquals(4, AiSearch.shown(all, listOf(car("오른쪽")), poor, 0.0).rows.size)
        val none = AiSearch.shown(all, listOf(car("왼쪽")), null, null)
        assertEquals(4, none.rows.size)
        assertFalse(none.filtered)
        assertEquals(AiSearch.NO_DIRECTION_NOTE, none.note)
    }

    @Test fun noDirectionCond_keepsEverything_withSides_andNoNote() {
        val s = AiSearch.shown(all, listOf(price), fix, 0.0)
        assertFalse(s.filtered)
        assertNull(s.note)
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.AHEAD, Side.BEHIND), s.rows.map { it.side })
    }

    // ── empty states ──

    private fun resp(items: List<SearchItem>, reason: String?, conds: List<SearchCond> = listOf(price), scanned: Int = 10, radiusM: Int = 1000) =
        SearchResponse(q = "q", conds = conds, radiusM = radiusM, scanned = scanned, matched = items.size, items = items, emptyReason = reason)

    @Test fun empty_noCandidates_offersWidenOnlyBelow3km() {
        val r = resp(emptyList(), "no_candidates", scanned = 0)
        assertEquals(AiSearch.Empty.NoCandidates(1000, canWiden = true), AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0)))
        val wide = resp(emptyList(), "no_candidates", scanned = 0, radiusM = 3000)
        assertEquals(AiSearch.Empty.NoCandidates(3000, canWiden = false), AiSearch.empty(wide, AiSearch.shown(wide.items, wide.conds, fix, 0.0)))
    }

    @Test fun empty_noMatch_namesTheMostFailedCheck_orNull() {
        val near = listOf(
            item("a", 37.5, 127.0034, listOf(SearchCheck("8억 이하", false), SearchCheck("1,000세대 이상", true))),
            item("b", 37.5, 127.0034, listOf(SearchCheck("8억 이하", false), SearchCheck("1,000세대 이상", false))),
        )
        val r = resp(near, "no_match")
        // near misses sent with no_match are never listed as results — they only name the failed check
        assertEquals(AiSearch.Empty.NoMatch("8억 이하"), AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0)))
        val bare = resp(emptyList(), "no_match")
        assertEquals(AiSearch.Empty.NoMatch(null), AiSearch.empty(bare, AiSearch.shown(bare.items, bare.conds, fix, 0.0)))
    }

    @Test fun empty_directionRemovedEveryMatch_isNoMatchOnTheDirection() {
        val conds = listOf(price, car("왼쪽"))
        val r = resp(listOf(east), null, conds)
        assertEquals(AiSearch.Empty.NoMatch("왼쪽"), AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0)))
    }

    @Test fun empty_noCondition_withRows_isAList_withoutRows_isNoCandidates() {
        val r = resp(listOf(east, west), "no_condition", conds = emptyList())
        assertNull(AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0)))
        val e = resp(emptyList(), "no_condition", conds = emptyList(), scanned = 0)
        assertTrue(AiSearch.empty(e, AiSearch.shown(e.items, e.conds, fix, 0.0)) is AiSearch.Empty.NoCandidates)
    }

    @Test fun empty_resultsPresent_isNull() {
        val r = resp(listOf(east), null)
        assertNull(AiSearch.empty(r, AiSearch.shown(r.items, r.conds, fix, 0.0)))
    }

    // ── lines ──

    @Test fun countAndNoticeLines() {
        val r = SearchResponse(q = "q", conds = listOf(car("오른쪽")), radiusM = 1000, scanned = 42, matched = 3, items = all,
            asOf = "2026-11-08", notice = "실거래 기준 · 매물 호가가 아니에요")
        val s = AiSearch.shown(r.items, r.conds, fix, 0.0)
        assertEquals("반경 1km · 후보 42곳 중 3곳 · 오른쪽 1곳", AiSearch.countLine(r, s))
        assertEquals("반경 1km · 후보 42곳 중 3곳", AiSearch.countLine(r, AiSearch.shown(r.items, r.conds, fix, null)))
        assertEquals("실거래 기준 · 매물 호가가 아니에요 · 2026-11-08 기준", AiSearch.noticeLine(r))
        assertEquals("반경 1km · 후보 42곳 중 3곳 · 자료 없어 확인 못 한 7곳", AiSearch.countLine(r.copy(unknown = 7), AiSearch.shown(r.items, r.conds, fix, null)))
        assertNull(AiSearch.noticeLine(r.copy(notice = null, asOf = null)))
        assertEquals("3km", AiSearch.radiusText(3000))
        assertEquals("1.5km", AiSearch.radiusText(1500))
        assertEquals("800m", AiSearch.radiusText(800))
    }

    @Test fun normalize_trimsCollapsesCaps_blankIsNull() {
        assertEquals("30평대 10억 이하", AiSearch.normalize("  30평대   10억\n이하 "))
        assertNull(AiSearch.normalize("   "))
        assertEquals(AiSearch.MAX_QUERY, AiSearch.normalize("가".repeat(200))!!.length)
    }

    @Test fun examples_areTheFiveAgreedSentences() {
        assertEquals(5, AiSearch.EXAMPLES.size)
        assertTrue("오른쪽에 8억 이하로 거래된 단지" in AiSearch.EXAMPLES)
        assertTrue("방금 지나온 신축" in AiSearch.EXAMPLES)
    }

    // ── driving lock ──

    @Test fun drivingLock_searchIsParkedOnly_guideStays() {
        assertFalse(DrivingPolicy.allows(ParkedAction.Search, driving = true))
        assertTrue(DrivingPolicy.allows(ParkedAction.Search, driving = false))
        assertTrue(DrivingPolicy.allows(ParkedAction.Guide, driving = true))
        assertFalse(DrivingPolicy.allows(ParkedAction.ChangeConfirm, driving = true))
        assertEquals("주행 중에는 쓸 수 없어요", kr.co.reevl.ontheway.ui.ds.comp.LOCK_TITLE)
        assertEquals("정차하면 이어서 보여요", kr.co.reevl.ontheway.ui.ds.comp.LOCK_SUB)
    }
}
