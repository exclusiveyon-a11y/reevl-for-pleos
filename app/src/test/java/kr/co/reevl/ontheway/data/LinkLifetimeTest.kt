package kr.co.reevl.ontheway.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 2026-11-10 "stay linked once linked" + QR 연결, against MockWebServer through the production OkHttp transport.
 * Server contract: reevl lib/carAuth.ts (`x-car-token` rotation header; /api/car/link/start · /poll).
 */
class LinkLifetimeTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var cache: MemoryPlanCache
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    private val old = "O".repeat(43)
    private val fresh = "N".repeat(43)

    private fun repo(transport: HttpTransport = OkHttpTransport(server.url("/").toString())) =
        PlanRepository(Api(transport), tokens, cache, deviceName = "test car", clock = { 1_793_000_000_000L },
            log = { e, f -> events += e to f })

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("content-type", "application/json").setHeader("cache-control", "no-store, private").setBody(body)

    private val plansBody = """{"plans":[{"id":"p1","title":"t","plannedDate":null,"status":"planned","version":1,"updatedAt":1,"stopCount":0}]}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore(old)
        cache = MemoryPlanCache()
        events.clear()
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    // ── rotation ──

    @Test fun rotationHeader_swapsTheStoredToken_sameSession_answerIsFresh_nextCallUsesIt() = runTest {
        val s0 = tokens.session()
        server.enqueue(json(200, plansBody).setHeader("x-car-token", fresh))
        val r = repo().plans()
        assertTrue("rotation must not make the answer stale", r is RepoResult.Ok && !r.value.fromCache)
        assertEquals(fresh, tokens.get())
        assertEquals("a rotation is the same link", s0, tokens.session())
        assertEquals("Bearer $old", server.takeRequest().getHeader("authorization"))

        server.enqueue(json(200, plansBody))
        repo().plans()
        assertEquals("Bearer $fresh", server.takeRequest().getHeader("authorization"))
        // never the token in the log
        assertFalse(events.toString().contains(fresh))
        assertFalse(events.toString().contains(old))
        assertTrue(events.any { it.first == "token.rotate" && it.second["swapped"] == true })
    }

    @Test fun rotationHeader_onAnErrorAnswer_isStillApplied() = runTest {
        server.enqueue(json(404, """{"error":"plan not found"}""").setHeader("x-car-token", fresh))
        assertEquals(RepoResult.Err(RepoError.PlanNotFound), repo().plan("p1"))
        assertEquals(fresh, tokens.get())
    }

    @Test fun rotationHeader_malformed_isIgnored() = runTest {
        for (bad in listOf("short", "x".repeat(44), "a/b".padEnd(43, 'c'), "")) {
            server.enqueue(json(200, plansBody).setHeader("x-car-token", bad))
            repo().plans()
            assertEquals(bad, old, tokens.get())
        }
    }

    @Test fun rotationHeader_afterLogoutInFlight_doesNotResurrectTheToken() = runTest {
        val real = OkHttpTransport(server.url("/").toString())
        server.enqueue(json(200, plansBody).setHeader("x-car-token", fresh))
        val r = repo(HttpTransport { c -> real.execute(c).also { tokens.clear() } }).plans()
        assertEquals(RepoResult.Err(RepoError.Stale), r)
        assertNull("compare-and-swap: a logged-out car stays logged out", tokens.get())
    }

    @Test fun inMemoryStore_rotate_isCompareAndSwap() {
        assertFalse(tokens.rotate("X".repeat(43), fresh))
        assertEquals(old, tokens.get())
        val s = tokens.session()
        assertTrue(tokens.rotate(old, fresh))
        assertEquals(fresh, tokens.get())
        assertEquals(s, tokens.session())
        tokens.set("B".repeat(43))
        assertTrue(tokens.session() > s)
    }

    // ── a 401 for the token we just replaced is retried, not a logout ──

    @Test fun http401_forARotatedToken_isRetriedOnceWithTheNewToken() = runTest {
        val real = OkHttpTransport(server.url("/").toString())
        var first = true
        // Another answer rotated the token while this call was in flight; the server then refused the old one.
        val t = HttpTransport { c -> real.execute(c).also { if (first) { first = false; tokens.rotate(old, fresh) } } }
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        server.enqueue(json(200, plansBody))
        val r = repo(t).plans()
        assertTrue(r is RepoResult.Ok)
        assertEquals(fresh, tokens.get())
        assertEquals("Bearer $old", server.takeRequest().getHeader("authorization"))
        assertEquals("Bearer $fresh", server.takeRequest().getHeader("authorization"))
    }

    // ── only a definitive 401 ends the link ──

    @Test fun definitive401_clearsTokenAndCache() = runTest {
        cache.putPlans(emptyList(), 1)
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoResult.Err(RepoError.Expired), repo().plans())
        assertNull(tokens.get())
        assertNull(cache.plans())
    }

    @Test fun nonDefinitive401_proxyOrPortal_keepsTheToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("content-type", "text/html").setBody("<html>login to wifi</html>"))
        assertEquals(RepoResult.Err(RepoError.Server(401)), repo().plans())
        server.enqueue(json(401, """{"error":"something else"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(401)), repo().nearbyComplexes(37.5, 127.0))
        assertEquals(old, tokens.get())
    }

    @Test fun transientErrors_neverWipeTheToken() = runTest {
        cache.putPlans(emptyList(), 1)
        for (code in listOf(500, 502, 503, 429, 403)) {
            server.enqueue(json(code, """{"error":"x"}"""))
            repo().plans()
            assertEquals("HTTP $code", old, tokens.get())
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val r = repo().plans()
        assertTrue(r is RepoResult.Ok && r.value.fromCache && r.value.notice is RepoError.Offline)
        assertEquals(old, tokens.get())
        server.enqueue(json(200, "<html>portal</html>"))
        repo().plans()
        assertEquals("unreadable 200 body", old, tokens.get())
    }

    // ── QR 연결 ──

    @Test fun qrStart_sendsDeviceName_withoutBearer_andReturnsTheRequest() = runTest {
        tokens.clear()
        server.enqueue(json(200, """{"requestId":"${"r".repeat(22)}","pollSecret":"${"s".repeat(43)}","match":"07","expiresAt":1793000600000}"""))
        val r = repo().qrStart() as RepoResult.Ok
        assertEquals("07", r.value.match)
        assertEquals("r".repeat(22), r.value.requestId)
        val req = server.takeRequest()
        assertEquals("/api/car/link/start", req.path)
        assertEquals("POST", req.method)
        assertEquals("""{"deviceName":"test car"}""", req.body.readUtf8())
        assertNull(req.getHeader("authorization"))
        assertFalse("the poll secret is never logged", events.toString().contains("s".repeat(43)))
    }

    @Test fun qrPoll_pending_linked_expired_errors() = runTest {
        tokens.clear()
        val rid = "r".repeat(22)
        val sec = "s".repeat(43)
        server.enqueue(json(200, """{"status":"pending"}"""))
        assertEquals(RepoResult.Ok(QrPoll.Pending), repo().qrPoll(rid, sec))
        val req = server.takeRequest()
        assertEquals("/api/car/link/poll", req.path)
        assertEquals("""{"requestId":"$rid","pollSecret":"$sec"}""", req.body.readUtf8())
        assertNull(tokens.get())

        cache.putPlans(emptyList(), 1)
        val s0 = tokens.session()
        server.enqueue(json(200, """{"status":"linked","token":"$fresh"}"""))
        assertEquals(RepoResult.Ok(QrPoll.Linked), repo().qrPoll(rid, sec))
        assertEquals("stored like a 6-digit link", fresh, tokens.get())
        assertTrue("a new session", tokens.session() != s0)
        assertNull("previous account's cache is dropped", cache.plans())
        assertFalse(events.toString().contains(fresh))

        for (code in listOf(410, 404, 400)) {
            server.enqueue(json(code, """{"error":"request expired"}"""))
            assertEquals("HTTP $code", RepoResult.Ok(QrPoll.Expired), repo().qrPoll(rid, sec))
        }
        server.enqueue(json(429, """{"error":"rate limited"}"""))
        assertEquals(RepoResult.Err(RepoError.RateLimited), repo().qrPoll(rid, sec))
        server.enqueue(json(503, """{"error":"x"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(503)), repo().qrPoll(rid, sec))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(RepoResult.Err(RepoError.Offline(null)), repo().qrPoll(rid, sec))
        // "linked" without a usable token is not a link
        server.enqueue(json(200, """{"status":"linked","token":"short"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(200)), repo().qrPoll(rid, sec))
        assertEquals(fresh, tokens.get())
    }

    @Test fun qrPollResponse_toString_hidesTheToken() {
        val s = QrPollResponse("linked", fresh).toString()
        assertFalse(s.contains(fresh))
        assertNotNull(s)
    }
}
