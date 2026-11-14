package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.SearchCond
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.data.SearchRequest
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.data.FavsResponse
import kr.co.reevl.ontheway.data.FavStatus
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.plan.Insert
import kr.co.reevl.ontheway.ui.state.NoticeKind
import kr.co.reevl.ontheway.ui.state.Notices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server fields of 546c8b233b + car-ds-v2 copy rules that live in pure code. */
class DsV2LogicTest {
    private val fix = NearbyEngine.Fix(37.5, 127.0, 0, accuracyM = 10.0)
    private fun item(cid: String, side: String?, lat: Double = 37.5, lng: Double = 127.0034) = SearchItem(cid = cid, name = cid, lat = lat, lng = lng, distanceM = 300, side = side)

    @Test fun serverSides_areShownAsIs_neverFilteredAgain() {
        val conds = listOf(SearchCond("right", "오른쪽", "applied"))
        // the server already kept only right-hand ones; the car must not drop the 'left' it labelled differently
        val s = AiSearch.shown(listOf(item("a", "right"), item("b", "behind")), conds, fix, headingDeg = 0.0)
        assertEquals(listOf(Side.RIGHT, Side.BEHIND), s.rows.map { it.side })
        assertFalse(s.filtered)
        assertNull(s.note)
    }

    @Test fun withoutServerSides_carSideRuleStays() {
        val conds = listOf(SearchCond("right", "오른쪽", "car"))
        val s = AiSearch.shown(listOf(item("east", null), item("west", null, lng = 126.9966)), conds, fix, headingDeg = 0.0)
        assertEquals(listOf("east"), s.rows.map { it.item.cid })
    }

    @Test fun summary_usesTheRadiusActuallyScanned() {
        val r = SearchResponse(q = "q", radiusM = 3000, scanned = 200, matched = 12, poolCapped = true, scannedRadiusM = 2400,
            items = (1..12).map { item("c$it", null) })
        val s = AiSearch.shown(r.items, emptyList(), fix, null)
        assertEquals("2.4km 안 200곳 중 12곳", AiSearch.summary(r, s))
        assertEquals("1km 안 60곳 중 12곳", AiSearch.summary(r.copy(poolCapped = false, scannedRadiusM = null, radiusM = 1000, scanned = 60), s))
    }

    @Test fun wire_headingDeg_poolCapped_side_count_favStatus() {
        val body = Api.json.encodeToString(SearchRequest.serializer(), SearchRequest("q", 1.0, 2.0, 1000, 10, 87.5))
        assertTrue(body.contains("\"headingDeg\":87.5"))
        assertFalse(Api.json.encodeToString(SearchRequest.serializer(), SearchRequest("q", 1.0, 2.0)).contains("headingDeg"))
        val r = Api.json.decodeFromString(SearchResponse.serializer(),
            """{"q":"q","poolCapped":true,"scannedRadiusM":2400,"items":[{"cid":"c","name":"n","lat":1,"lng":2,"distanceM":3,"side":"left"}]}""")
        assertTrue(r.poolCapped); assertEquals(2400, r.scannedRadiusM); assertEquals("left", r.items[0].side)
        assertEquals(Side.LEFT, AiSearch.serverSide(r.items[0].side))
        assertEquals(140, Api.json.decodeFromString(FavsResponse.serializer(), """{"favs":[],"count":140}""").count)
        assertEquals(FavStatus("c", true), Api.json.decodeFromString(FavStatus.serializer(), """{"cid":"c","on":true}"""))
    }

    @Test fun insertOptions_afterEachStop_thenEnd_defaultAfterAnchor() {
        fun st(id: String, pos: Int, v: String) = PlanStop(id = id, position = pos, kind = "apartment_complex", name = id, visitStatus = v)
        val p = Plan("p", "t", status = "active", version = 1, updatedAt = 0, stops = listOf(st("a", 1, "visited"), st("b", 2, "planned"), st("c", 3, "planned")))
        val o = Insert.options(p)
        assertEquals(listOf("1 다음", "2 다음", "맨 끝"), o.map { it.label })
        assertEquals("c", o.last().afterStopId)
        assertEquals(0, Insert.defaultOption(p, o)) // after the visited one (old single choice)
    }

    @Test fun confirmOptions_areaWords_get3_else_drop() {
        assertEquals(listOf("전용 84㎡ 안팎", "공급 99~132㎡", "빼기"), AiSearch.confirmOptions("30평대"))
        assertEquals(listOf("빼기"), AiSearch.confirmOptions("학군 좋은"))
        assertEquals(4, AiSearch.SUGGESTIONS.size)
    }

    @Test fun notices_offlineWithTime_errorOnlyWithoutData() {
        val cached = Synced("x", 1_793_000_000_000L, fromCache = true, notice = RepoError.Offline(null))
        assertNull(Notices.of(Synced("x", 1L, fromCache = true), null)) // cache shown while loading
        val n = Notices.of(cached, "whatever")!!
        assertEquals(NoticeKind.Offline, n.kind)
        assertTrue(n.text.startsWith("오프라인 · ") && n.text.endsWith(" 기준"))
        assertNull(Notices.of(Synced("x", 1, fromCache = false), null))
        assertEquals(NoticeKind.Error, Notices.of(null, "불러오지 못했어요")!!.kind)
        assertEquals("오프라인이에요", Notices.failed(RepoError.Offline(null)))
    }
}
