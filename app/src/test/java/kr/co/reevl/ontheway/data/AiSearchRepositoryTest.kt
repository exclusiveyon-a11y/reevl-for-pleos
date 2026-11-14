package kr.co.reevl.ontheway.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `POST /api/car/search` (contract 2026-11-09) through the production OkHttp transport against MockWebServer. */
class AiSearchRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private val logs = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun repo() = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), tokens, MemoryPlanCache(), deviceName = "test car",
        clock = { 1_793_000_000_000L }, log = { e, f -> logs += e to f })

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("content-type", "application/json").setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore("T".repeat(43))
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private val full = """
        {"q":"오른쪽에 8억 이하로 거래된 단지",
         "conds":[{"key":"price_max","label":"8억 이하","status":"applied"},
                  {"key":"right","label":"오른쪽","status":"car"},
                  {"key":"school","label":"학군 좋은","status":"unsupported"}],
         "radiusM":1000,"scanned":42,"matched":2,"unknown":5,
         "items":[
          {"cid":"41135|정자동한솔|정자동|110","name":"한솔마을5단지","address":"경기 성남시 분당구 정자동 110","addressApprox":false,
           "lat":37.3671,"lng":127.1121,"distanceM":320,"households":1156,"completionYear":1994,
           "brand":{"name":null,"logoUrl":null,"avatar":{"kind":"initial","initials":"한","color":"#3182f6"}},
           "latestTrade":{"date":"2026-10-02","areaM2":59.8,"pyeong":24,"price":79000,"priceUnit":"만원"},
           "checks":[{"label":"8억 이하","ok":true,"evidence":"7억 9,000만 · 26.10.02"},{"label":"학군 좋은","ok":null,"evidence":null}],
           "evidenceTrade":{"date":"2026-10-02","areaM2":59.8,"pyeong":24,"price":79000,"priceUnit":"만원"},
           "futureField":1},
          {"cid":"x2","name":"근사주소단지","address":"경기 성남시 분당구 정자동 1","addressApprox":true,
           "lat":37.368,"lng":127.113,"distanceM":610,"households":null,"completionYear":null,
           "brand":{"name":null,"logoUrl":null,"avatar":{"kind":"initial","initials":"근","color":"#3182f6"}},
           "latestTrade":null,"checks":[],"evidenceTrade":null}
         ],
         "emptyReason":null,"asOf":"2026-11-08","source":"reevl_property_dataset","notice":"실거래 기준 · 매물 호가가 아니에요"}
    """.trimIndent()

    @Test fun search_postsBody_withBearer_andDecodesEveryField() = runTest {
        server.enqueue(json(200, full))
        val r = repo().search("오른쪽에 8억 이하로 거래된 단지", 37.36712345, 127.11198765) as RepoResult.Ok
        val v = r.value
        assertEquals(3, v.conds.size)
        assertEquals("car", v.conds[1].status)
        assertEquals(42, v.scanned)
        assertEquals(2, v.matched)
        assertEquals(5, v.unknown)
        assertEquals(1000, v.radiusM)
        assertNull(v.emptyReason)
        assertEquals("2026-11-08", v.asOf)
        assertEquals("실거래 기준 · 매물 호가가 아니에요", v.notice)
        val a = v.items[0]
        assertEquals(320, a.distanceM)
        assertEquals(1156, a.households)
        assertEquals(79000L, a.latestTrade!!.price)
        assertEquals(true, a.checks[0].ok)
        assertEquals("7억 9,000만 · 26.10.02", a.checks[0].evidence)
        assertNull(a.checks[1].ok)
        assertNull(a.checks[1].evidence)
        assertEquals(79000L, a.evidenceTrade!!.price)
        assertFalse(a.addressApprox)
        val b = v.items[1]
        assertTrue(b.addressApprox)
        assertNull(b.households)
        assertNull(b.completionYear)
        assertNull(b.latestTrade)
        assertNull(b.evidenceTrade)
        assertTrue(b.checks.isEmpty())
        // same fields as a nearby item
        assertEquals(b.cid, b.asNearby().cid)
        assertTrue(b.asNearby().addressApprox)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/car/search", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("오른쪽에 8억 이하로 거래된 단지", body["q"]!!.jsonPrimitive.content)
        assertEquals(37.36712345, body["lat"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(127.11198765, body["lng"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(1000, body["radiusM"]!!.jsonPrimitive.int)
        assertEquals(10, body["limit"]!!.jsonPrimitive.int)
        // AppLog rule: no coordinates, no token, no query text in the repository log
        val logText = logs.toString()
        assertFalse(logText.contains("37.36"))
        assertFalse(logText.contains("TTTT"))
        assertFalse(logText.contains("8억"))
    }

    @Test fun search_minimalBody_nullsAndMissingFields_useDefaults() = runTest {
        server.enqueue(json(200, """{"q":"주변","conds":null,"items":[{"cid":"c","name":"n","lat":1.0,"lng":2.0,"distanceM":5,
            "brand":null,"checks":null,"latestTrade":null}],"emptyReason":"no_condition","asOf":null,"source":null,"notice":null}"""))
        val v = (repo().search("주변", 1.0, 2.0) as RepoResult.Ok).value
        assertTrue(v.conds.isEmpty())
        assertEquals(1000, v.radiusM)
        assertEquals(0, v.scanned)
        assertEquals(0, v.unknown)
        assertEquals("no_condition", v.emptyReason)
        assertNull(v.asOf)
        assertNull(v.notice)
        assertTrue(v.items[0].checks.isEmpty())
        assertEquals("아", v.items[0].brand.avatar.initials)
    }

    @Test fun search_widerRadius_isSent() = runTest {
        server.enqueue(json(200, """{"q":"x","conds":[],"radiusM":3000,"scanned":0,"matched":0,"items":[],"emptyReason":"no_candidates","notice":"n"}"""))
        val v = (repo().search("x", 1.0, 2.0, radiusM = 3000) as RepoResult.Ok).value
        assertEquals(3000, v.radiusM)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(3000, body["radiusM"]!!.jsonPrimitive.int)
    }

    @Test fun search_errors_mapLikeTheOtherCarRoutes() = runTest {
        server.enqueue(json(500, """{"error":"boom"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(500)), repo().search("q", 1.0, 2.0))
        server.enqueue(json(429, """{"error":"slow down"}"""))
        assertEquals(RepoResult.Err(RepoError.RateLimited), repo().search("q", 1.0, 2.0))
        server.enqueue(json(200, "not json"))
        assertEquals(RepoResult.Err(RepoError.Server(200)), repo().search("q", 1.0, 2.0))
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoResult.Err(RepoError.Expired), repo().search("q", 1.0, 2.0))
        assertNull(tokens.get()) // the existing expired flow: token cleared
        assertEquals(RepoResult.Err(RepoError.NotLinked), repo().search("q", 1.0, 2.0))
        tokens.set("T".repeat(43))
        server.shutdown()
        assertEquals(RepoResult.Err(RepoError.Offline(null)), repo().search("q", 1.0, 2.0))
    }
}
