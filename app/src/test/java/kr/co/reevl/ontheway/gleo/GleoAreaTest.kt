package kr.co.reevl.ontheway.gleo

import kotlinx.coroutines.test.runTest
import kr.co.reevl.ontheway.data.AreaInfo
import kr.co.reevl.ontheway.data.AreaNews
import kr.co.reevl.ontheway.data.AreaResponse
import kr.co.reevl.ontheway.data.AreaRet
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.FavToggleResponse
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.data.Synced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "지금 지나는 동네 어때?" → ANALYZE_PROPERTY's area variant: the server's summary, spoken; still 3 functions. */
class GleoAreaTest {
    private class Fake(var area: RepoResult<AreaResponse>?) : GleoBackend {
        var pos: GleoPosition? = GleoPosition(37.44, 127.14, 0.0)
        var complexCalls = 0
        override fun position() = pos
        override suspend fun area(at: GleoPosition) = area
        override suspend fun search(q: String, at: GleoPosition): RepoResult<SearchResponse> = RepoResult.Err(RepoError.Offline(null))
        override suspend fun complex(cid: String): RepoResult<Synced<CarComplex>> { complexCalls++; return RepoResult.Err(RepoError.Offline(null)) }
        override suspend fun todayPlan(): RepoResult<Plan?> = RepoResult.Ok(null)
        override suspend fun addStop(planId: String, cid: String, afterStopId: String?): RepoResult<Plan> = RepoResult.Err(RepoError.Offline(null))
        override suspend fun deleteStop(planId: String, stopId: String): RepoResult<Plan> = RepoResult.Err(RepoError.Offline(null))
        override suspend fun setFav(cid: String, on: Boolean): RepoResult<FavToggleResponse> = RepoResult.Err(RepoError.Offline(null))
        override fun lastResults() = emptyList<GleoRef>()
        override fun rememberResults(refs: List<GleoRef>) {}
        override fun saved() = emptyList<GleoRef>()
        override fun pending(): PendingWrite? = null
        override fun setPending(p: PendingWrite?) {}
        override fun now() = 0L
    }

    private val ok = AreaResponse(area = AreaInfo("경기", "성남시 수정구", "신흥동", "경기 성남시 수정구 신흥동", ret = AreaRet(y1 = 0.2639)),
        news = listOf(AreaNews("경기 전체 소식", "A", "2026.11.09", "sido", "경기"), AreaNews("수정구 소식", "B", "2026.11.09", "gu", "수정구")),
        summary = "신흥동은 최근 1년 26.4% 올랐어요.")

    @Test fun areaWords_answerWithTheServerSummary() = runTest {
        val b = Fake(RepoResult.Ok(ok))
        val c = GleoActions(b).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "지금 지나는 동네"))
        assertEquals(GleoStatus.Success, c.status)
        assertEquals("Here is the latest summary for 신흥동.", c.message) // English sentence (action_callback.md §2.1)
        assertEquals("신흥동은 최근 1년 26.4% 올랐어요.", c.data.single()["summary"]) // the Korean summary rides in data
        assertEquals("+26.4%", c.data.single()["change1y"])
        assertEquals("수정구 소식", c.data.single()["news"]) // never the 시도-wide one as "this area's" news
        assertEquals(0, b.complexCalls)
        assertTrue(c.message.length <= 120)
    }

    @Test fun queryOnly_noComplex_alsoMeansTheArea() = runTest {
        val c = GleoActions(Fake(RepoResult.Ok(ok))).handle("ANALYZE_PROPERTY", mapOf("query" to "여기 동네 분위기 어때"))
        assertEquals(GleoStatus.Success, c.status)
    }

    @Test fun noPosition_asks_noArea_saysSo_errorFails() = runTest {
        val noPos = Fake(RepoResult.Ok(ok)).apply { pos = null }
        assertEquals(GleoStatus.Asking, GleoActions(noPos).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "이 동네")).status)
        val none = GleoActions(Fake(RepoResult.Ok(AreaResponse(summary = "이 근처에는 리블이 아는 아파트 단지가 없어요.")))).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "여기"))
        assertTrue(none.message, none.message.contains("no apartment complexes"))
        assertEquals(GleoStatus.Fail, GleoActions(Fake(RepoResult.Err(RepoError.Offline(null)))).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "동네")).status)
    }

    @Test fun complexNames_stillGoToTheComplex() = runTest {
        val b = Fake(RepoResult.Ok(ok))
        GleoActions(b).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "경희궁자이"))
        assertTrue(GleoProtocol.FUNCTIONS.size == 3)
        assertEquals(0, b.complexCalls) // unresolved name → asks, but never routed to the area
    }
}
