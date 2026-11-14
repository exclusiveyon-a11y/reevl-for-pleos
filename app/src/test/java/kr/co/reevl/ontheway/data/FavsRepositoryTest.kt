package kr.co.reevl.ontheway.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `GET`/`POST /api/car/favs` (server a2a0101d8e) through the production OkHttp transport against MockWebServer. */
class FavsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var favCache: MemoryFavCache
    private var now = 1_793_000_000_000L

    private fun repo() = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), tokens, MemoryPlanCache(), deviceName = "test car",
        clock = { now }, favCache = favCache)

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore("T".repeat(43))
        favCache = MemoryFavCache()
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private val list = """
        {"favs":[
          {"cid":"11110|경희궁자이(2단지)|홍파동|199","name":"경희궁자이(2단지)","address":"서울 종로구 홍파동 199","addressApprox":false,
           "lat":37.569205,"lng":126.964152,"households":1148,"completionYear":2017,
           "brand":{"name":"자이","logoUrl":null,"avatar":{"kind":"initial","initials":"경","color":"#ff7a45"}},
           "latestTrade":{"date":"2026-09-28","areaM2":59.9,"pyeong":24,"price":236500,"priceUnit":"만원"},"savedAt":1793000000000},
          {"cid":"11110|사라진단지|평동|1","name":null,"address":null,"addressApprox":false,"lat":null,"lng":null,"households":null,
           "completionYear":null,"brand":null,"latestTrade":null,"savedAt":1792000000000}
         ],"asOf":"2026-11-08","notice":"저장한 단지 · 최신 매매는 국토교통부 실거래 신고 기준이에요(현재 매물·호가 아님)"}
    """.trimIndent()

    @Test fun favs_get_bearer_decodesEveryField_incNullName() = runTest {
        server.enqueue(json(200, list))
        val r = (repo().favs() as RepoResult.Ok).value
        assertFalse(r.fromCache)
        val (a, gone) = r.value.favs
        assertEquals("경희궁자이(2단지)", a.name)
        assertEquals(1148, a.households)
        assertEquals(236500L, a.latestTrade!!.price)
        assertEquals("자이", a.brand!!.name)
        assertEquals(1793000000000L, a.savedAt)
        assertNull(gone.name)
        assertNull(gone.lat)
        assertNull(gone.brand)
        assertEquals("2026-11-08", r.value.asOf)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/car/favs", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        assertTrue(favCache.get() != null)
    }

    @Test fun favs_offline_withCache_isTheCache_markedWithItsTime_withoutCache_isAnError() = runTest {
        server.enqueue(json(200, list))
        repo().favs()
        now += 60_000
        server.enqueue(json(503, """{"error":"down"}"""))
        val c = (repo().favs() as RepoResult.Ok).value
        assertTrue(c.fromCache)
        assertEquals(1_793_000_000_000L, c.syncedAt)
        assertEquals(RepoError.Server(503), c.notice)
        assertEquals(2, c.value.favs.size)
        // no cache: never an invented list
        favCache.clear()
        server.shutdown()
        assertEquals(RepoResult.Err(RepoError.Offline(null)), repo().favs())
        assertNull(repo().cachedFavs())
    }

    @Test fun setFav_postsCidAndOn_answerIsTheTruth() = runTest {
        server.enqueue(json(200, """{"cid":"c1","on":true,"count":3}"""))
        val r = (repo().setFav("c1", true) as RepoResult.Ok).value
        assertEquals(FavToggleResponse("c1", true, 3), r)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/car/favs", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("c1", body["cid"]!!.jsonPrimitive.content)
        assertTrue(body["on"]!!.jsonPrimitive.boolean)
        server.enqueue(json(200, """{"cid":"c1","on":false,"count":2}"""))
        assertFalse((repo().setFav("c1", false) as RepoResult.Ok).value.on)
        assertFalse(Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["on"]!!.jsonPrimitive.boolean)
    }

    @Test fun setFav_errors_400_404_409_401_offline() = runTest {
        server.enqueue(json(400, """{"error":"bad"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(400)), repo().setFav("x", true))
        server.enqueue(json(404, """{"error":"not found"}"""))
        assertEquals(RepoResult.Err(RepoError.ComplexNotFound), repo().setFav("x", true))
        server.enqueue(json(409, """{"error":"at most 500 favorites"}"""))
        assertEquals(RepoResult.Err(RepoError.FavLimit), repo().setFav("x", true))
        favCache.put(FavsResponse(), 1)
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoResult.Err(RepoError.Expired), repo().setFav("x", true))
        assertNull(tokens.get())
        assertNull(favCache.get()) // the expired flow clears the saved list too
        tokens.set("T".repeat(43))
        server.shutdown()
        assertEquals(RepoResult.Err(RepoError.Offline(null)), repo().setFav("x", false))
    }

    @Test fun logout_clearsTheFavCache() = runTest {
        favCache.put(FavsResponse(), 1)
        server.enqueue(json(200, "{}"))
        repo().logout()
        assertNull(favCache.get())
    }
}
