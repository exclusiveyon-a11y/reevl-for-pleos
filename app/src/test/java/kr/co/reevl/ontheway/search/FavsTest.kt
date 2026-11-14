package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.FavItem
import kr.co.reevl.ontheway.data.FavsResponse
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavsTest {
    private fun fav(cid: String, name: String? = cid, lat: Double? = 37.5, lng: Double? = 127.0) =
        FavItem(cid = cid, name = name, lat = lat, lng = lng, savedAt = 1_793_000_000_000L)

    @Test fun state_onlyFromTheServer_confirmedWins_unknownWithoutList() {
        val l = FavsResponse(listOf(fav("a"), fav("b")))
        assertNull(Favs.state("a", null, emptyMap()))
        assertEquals(true, Favs.state("a", l, emptyMap()))
        assertEquals(false, Favs.state("z", l, emptyMap()))
        assertEquals(false, Favs.state("a", l, mapOf("a" to false)))
        assertEquals(true, Favs.state("z", l, mapOf("z" to true)))
        // a full (capped) list cannot prove "not saved"
        val full = FavsResponse((1..Favs.LIST_MAX).map { fav("c$it") })
        assertNull(Favs.state("z", full, emptyMap()))
        assertEquals(true, Favs.state("c7", full, emptyMap()))
    }

    @Test fun countLabel_serverCount_listSize_capped_unknown() {
        assertNull(Favs.countLabel(null, null))
        assertEquals("2곳", Favs.countLabel(null, FavsResponse(listOf(fav("a"), fav("b")))))
        assertEquals("0곳", Favs.countLabel(null, FavsResponse()))
        assertEquals("100곳+", Favs.countLabel(null, FavsResponse((1..100).map { fav("c$it") })))
        assertEquals("140곳", Favs.countLabel(140, FavsResponse((1..100).map { fav("c$it") })))
    }

    @Test fun failText_saysWhatFailed_andWhy() {
        assertEquals("저장하지 못했어요. 리블에 이 단지 정보가 없어요.", Favs.failText(true, RepoError.ComplexNotFound))
        assertTrue(Favs.failText(false, RepoError.Offline(null)).startsWith("저장을 해제하지 못했어요. 인터넷 연결을 확인해 주세요."))
        assertTrue(Favs.failText(true, RepoError.FavLimit).contains("너무 많아요"))
    }

    @Test fun savedLabel_kst() {
        assertEquals("저장 26.10.26", Favs.savedLabel(1_793_000_000_000L)) // 2026-10-26T08:53Z
        assertNull(Favs.savedLabel(0))
    }

    @Test fun pick_fromFav_unknownCid_isInfoMissing_andNotGuidable() {
        val p = ComplexPick.fromFav(fav("gone", name = null, lat = null, lng = null))
        assertFalse(p.known)
        assertEquals("정보 없음", p.displayName)
        assertFalse(PlanLogic.canGuide(p.stop()))
        assertEquals(PickSource.SAVED, p.source)
        assertTrue(PlanLogic.canGuide(ComplexPick.fromFav(fav("a")).stop()))
    }

    @Test fun pick_fromSearch_keepsSideChecksAndServerCoords() {
        val item = SearchItem(cid = "c", name = "n", lat = 37.5, lng = 127.0, distanceM = 300)
        val p = ComplexPick.fromSearch(AiSearch.Row(item, Side.RIGHT))
        assertEquals(Side.RIGHT, p.side)
        assertEquals(300, p.distanceM)
        assertTrue(p.stop().locationVerified)
        assertEquals("c", p.stop().cid)
    }

    @Test fun saveToggle_isParkedOnly() {
        assertFalse(DrivingPolicy.allows(ParkedAction.Save, driving = true))
        assertTrue(DrivingPolicy.allows(ParkedAction.Save, driving = false))
    }
}
