package kr.co.reevl.ontheway.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 휴대폰 ↔ 차 동기화 (reevl lib/recentOps.ts, 2026-11-10) through the production OkHttp transport: `GET/PUT /api/car/recent`,
 * `GET /api/car/memos`, `GET /api/car/screeners` — pending car views survive a failure, go with the next call, and are dropped
 * once the server took them; a logout / 401 forgets everything (it belongs to the account).
 */
class SyncRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var store: MemorySyncStore
    private var now = 1_793_000_000_000L

    private fun repo() = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), tokens, MemoryPlanCache(), deviceName = "test car",
        clock = { now }, syncStore = store)

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore("T".repeat(43))
        store = MemorySyncStore()
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private val C2 = "11110|경희궁자이(2단지)|홍파동|199"
    private val LOTTE = "11110|경희궁롯데캐슬|무악동|89"
    private val list = """{"items":[
        {"cid":"$C2","name":"경희궁자이(2단지)","households":1148,"latestTrade":{"date":"2026-09-28","areaM2":59.9,"price":236500},"viewedAt":1792999000000,"source":"web"},
        {"cid":"$LOTTE","name":"경희궁롯데캐슬","viewedAt":1792000000000,"source":"web","approx":true}],"asOf":"2026-11-08","notice":"n"}"""

    @Test fun recent_get_whenNothingPending_decodes_andCaches() = runTest {
        server.enqueue(json(200, list))
        val r = (repo().recent() as RepoResult.Ok).value
        assertFalse(r.fromCache)
        assertEquals(listOf(C2, LOTTE), r.value.items.map { it.cid })
        assertEquals("web", r.value.items[0].source)
        assertTrue(r.value.items[1].approx)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/car/recent", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        assertTrue(store.recent.get() != null)
    }

    @Test fun recent_pending_isPut_thenDropped_butAFailureKeepsIt_andServesTheCache() = runTest {
        val repo = repo()
        server.enqueue(json(200, list)); repo.recent(); server.takeRequest()
        repo.noteRecent(RecentItem(LOTTE, "경희궁롯데캐슬"))
        repo.noteRecent(RecentItem("fake|없는", null))           // not a known complex → not noted
        assertEquals(listOf(LOTTE), repo.pendingRecent().map { it.cid })
        assertEquals("car", repo.pendingRecent()[0].source)

        now += 60_000
        server.enqueue(json(503, """{"error":"down"}"""))
        val c = (repo.recent() as RepoResult.Ok).value
        assertTrue("offline/5xx → last list as cache", c.fromCache)
        assertEquals(1, repo.pendingRecent().size)
        val failed = server.takeRequest()
        assertEquals("PUT", failed.method)

        server.enqueue(json(200, list.replace("1792000000000,\"source\":\"web\",\"approx\":true", "$now,\"source\":\"car\"")))
        (repo.recent() as RepoResult.Ok).value
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/car/recent", put.path)
        val items = Json.parseToJsonElement(put.body.readUtf8()).jsonObject["items"]!!.jsonArray
        assertEquals(LOTTE, items[0].jsonObject["cid"]!!.jsonPrimitive.content)
        assertEquals(1_793_000_000_000L, items[0].jsonObject["viewedAt"]!!.jsonPrimitive.long)
        assertTrue("the server took it", repo.pendingRecent().isEmpty())
    }

    @Test fun recent_aViewDuringThePut_staysPending() {
        val sent = listOf(RecentPutItem(C2, 100))
        val pending = listOf(RecentItem(C2, "a", viewedAt = 200, source = "car"), RecentItem(LOTTE, "b", viewedAt = 90, source = "car"))
        assertEquals(listOf(C2), kr.co.reevl.ontheway.search.Recent.afterPush(pending, sent).map { it.cid }.filter { it == C2 })
        assertEquals(2, kr.co.reevl.ontheway.search.Recent.afterPush(pending, sent).size)
    }

    @Test fun memos_and_screeners_decode_andCache() = runTest {
        server.enqueue(json(200, """{"memos":[{"cid":"$C2","text":"로열동 12억 호가","updatedAt":1793000000000}]}"""))
        server.enqueue(json(200, """{"items":[{"id":"s1","name":"역세권 10억","q":"10억 이하","skipped":["역세권"]},{"id":"s2","name":"학군","q":null,"skipped":["학군"]}]}"""))
        val repo = repo()
        val m = (repo.memos() as RepoResult.Ok).value.value
        assertEquals("로열동 12억 호가", m.memos.single().text)
        val s = (repo.screeners() as RepoResult.Ok).value.value
        assertEquals("10억 이하", s.items[0].q)
        assertNull(s.items[1].q)
        assertEquals("/api/car/memos", server.takeRequest().path)
        assertEquals("/api/car/screeners", server.takeRequest().path)
        assertTrue(repo.cachedMemos() != null && repo.cachedScreeners() != null)
    }

    @Test fun a401_forgetsEverything_ofTheAccount() = runTest {
        val repo = repo()
        server.enqueue(json(200, list)); repo.recent()
        repo.noteRecent(RecentItem(LOTTE, "경희궁롯데캐슬"))
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoError.Expired, (repo.memos() as RepoResult.Err).error)
        assertNull(store.recent.get())
        assertNull(store.pending.get())
        assertNull(tokens.get())
    }
}
