package kr.co.reevl.ontheway.data

import java.time.ZoneId
import kotlinx.coroutines.test.runTest
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
 * Repository error mapping against a real HTTP server (MockWebServer) through the production OkHttp transport.
 * Bodies are copied from the A2 contract (reevl A2-report.md, 2026-10-26).
 */
class PlanRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var cache: MemoryPlanCache
    private var now = 1_793_000_000_000L // 2026-10-26 ≈ 18:13 KST
    private val kst = ZoneId.of("Asia/Seoul")

    private fun repo(baseUrl: String = server.url("/").toString()) =
        PlanRepository(Api(OkHttpTransport(baseUrl)), tokens, cache, deviceName = "test car", clock = { now })

    private val planJson = """
        {"id":"p1","title":"토요일 임장","plannedDate":"2026-11-07","status":"planned","version":3,"updatedAt":1793000000000,
         "stops":[
          {"id":"s1","position":1,"kind":"apartment_complex","cid":"11110|경희궁자이(2단지)|홍파동|199","name":"경희궁자이(2단지)",
           "address":"서울 종로구 홍파동 199","lat":37.569205,"lng":126.964152,"locationVerified":true,"phone":null,"source":null,
           "visitStatus":"planned","note":null},
          {"id":"s2","position":2,"kind":"real_estate_office","name":"데모 공인중개사","address":"서울 종로구",
           "lat":37.5695,"lng":126.9645,"locationVerified":false,"visitStatus":"planned","extraFieldFromNewerServer":1}
         ]}
    """.trimIndent()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("content-type", "application/json").setHeader("cache-control", "no-store, private").setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore("T".repeat(43))
        cache = MemoryPlanCache()
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    // ── link ──

    @Test fun link_ok_storesToken_andSendsCodeAndDeviceName() = runTest {
        tokens.clear()
        server.enqueue(json(200, """{"token":"${"a".repeat(43)}"}"""))
        assertEquals(RepoResult.Ok(Unit), repo().link("123456"))
        assertEquals("a".repeat(43), tokens.get())
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/car/link", req.path)
        assertEquals("""{"code":"123456","deviceName":"test car"}""", req.body.readUtf8())
        assertNull(req.getHeader("authorization"))
    }

    @Test fun link_404_410_400_areCodeInvalid_withTheBriefSentence() = runTest {
        for (code in listOf(404, 410, 400)) {
            tokens.clear()
            server.enqueue(json(code, """{"error":"x"}"""))
            val r = repo().link("123456") as RepoResult.Err
            assertEquals("HTTP $code", RepoError.CodeInvalid, r.error)
            assertNull(tokens.get())
        }
        assertEquals("코드가 만료됐거나 맞지 않아요", UserMessages.of(RepoError.CodeInvalid))
    }

    @Test fun link_notSixDigits_isRejectedLocally_withoutACall() = runTest {
        assertEquals(RepoResult.Err(RepoError.CodeInvalid), repo().link("12345"))
        assertEquals(RepoResult.Err(RepoError.CodeInvalid), repo().link("12a456"))
        assertEquals(0, server.requestCount)
    }

    @Test fun link_429_isRateLimited() = runTest {
        server.enqueue(json(429, """{"error":"too many"}"""))
        assertEquals(RepoResult.Err(RepoError.RateLimited), repo().link("123456"))
    }

    // ── reads ──

    @Test fun plan_ok_decodesContract_bearerSent_cacheFilled() = runTest {
        server.enqueue(json(200, planJson))
        val r = repo().plan("p1") as RepoResult.Ok
        assertFalse(r.value.fromCache)
        assertNull(r.value.notice)
        val p = r.value.value
        assertEquals("토요일 임장", p.title)
        assertEquals(37.569205, p.stops[0].lat!!, 0.0)
        assertEquals(126.964152, p.stops[0].lng!!, 0.0)
        assertTrue(p.stops[0].locationVerified)
        assertFalse(p.stops[1].locationVerified)
        val req = server.takeRequest()
        assertEquals("/api/car/plans/p1", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        assertEquals(now, cache.plan("p1")!!.syncedAt)
    }

    @Test fun plans_ok_list() = runTest {
        server.enqueue(json(200, """{"plans":[{"id":"p1","title":"토요일 임장","plannedDate":"2026-11-07","status":"planned","version":1,"updatedAt":1,"stopCount":3}]}"""))
        val r = repo().plans() as RepoResult.Ok
        assertEquals(3, r.value.value.single().stopCount)
        assertEquals("/api/car/plans", server.takeRequest().path)
    }

    @Test fun http401_onRead_clearsTokenAndCache_isExpired() = runTest {
        cache.putPlans(emptyList(), now - 1)
        cache.putPlan(Api.json.decodeFromString(Plan.serializer(), planJson), now - 1)
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        val r = repo().plans()
        assertEquals(RepoResult.Err(RepoError.Expired), r)
        assertNull("token cleared", tokens.get())
        assertNull("cache cleared", cache.plans())
        assertNull("cache cleared", cache.plan("p1"))
        assertEquals("차량 연결이 끊겼어요. 휴대폰으로 다시 연결해 주세요.", UserMessages.of(RepoError.Expired))
    }

    @Test fun http401_onPatch_clearsToken_isExpired() = runTest {
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoResult.Err(RepoError.Expired), repo().setVisitStatus("p1", "s1", "visited"))
        assertNull(tokens.get())
    }

    @Test fun http404_onPlan_isPlanNotFound_andDropsCachedCopy() = runTest {
        cache.putPlan(Api.json.decodeFromString(Plan.serializer(), planJson), now - 1)
        server.enqueue(json(404, """{"error":"not found"}"""))
        assertEquals(RepoResult.Err(RepoError.PlanNotFound), repo().plan("p1"))
        assertNull(cache.plan("p1"))
        assertEquals("계획을 찾을 수 없어요", UserMessages.of(RepoError.PlanNotFound))
    }

    @Test fun network_withCache_servesCacheMarkedStale_withLastSyncTime() = runTest {
        server.enqueue(json(200, planJson))
        val r0 = repo().plan("p1") as RepoResult.Ok
        val syncedAt = r0.value.syncedAt
        val base = server.url("/").toString()
        server.shutdown() // the server is gone
        now += 60_000
        val r = repo(baseUrl = base).plan("p1") as RepoResult.Ok
        assertTrue("served from cache", r.value.fromCache)
        assertEquals(syncedAt, r.value.syncedAt)
        assertEquals(RepoError.Offline(syncedAt), r.value.notice)
        val sentence = UserMessages.of(r.value.notice!!, kst)
        assertEquals("인터넷 연결을 확인해 주세요. 마지막 동기화 ${UserMessages.hhmm(syncedAt, kst)}", sentence)
        assertTrue(sentence, Regex("""^인터넷 연결을 확인해 주세요\. 마지막 동기화 \d\d:\d\d$""").matches(sentence))
    }

    @Test fun network_withoutCache_isOffline_noTime() = runTest {
        val base = server.url("/").toString()
        server.shutdown()
        val r = repo(baseUrl = base).plans() as RepoResult.Err
        assertEquals(RepoError.Offline(null), r.error)
        assertEquals("인터넷 연결을 확인해 주세요.", UserMessages.of(r.error))
    }

    @Test fun network_onPatch_isOffline_andDoesNotTouchCache() = runTest {
        server.enqueue(json(200, planJson))
        repo().plan("p1")
        val before = cache.plan("p1")
        val base = server.url("/").toString()
        server.shutdown()
        val r = repo(baseUrl = base).setVisitStatus("p1", "s1", "visited") as RepoResult.Err
        assertEquals(RepoError.Offline(before!!.syncedAt), r.error)
        assertEquals(before, cache.plan("p1"))
    }

    // ── writes ──

    @Test fun setVisitStatus_sendsPatchBody_andReturnsServerPlan() = runTest {
        server.enqueue(json(200, planJson.replace("\"version\":3", "\"version\":4")))
        val r = repo().setVisitStatus("p1", "s1", "visited") as RepoResult.Ok
        assertEquals(4, r.value.version)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/api/car/plans/p1", req.path)
        assertEquals("""{"stops":[{"id":"s1","visitStatus":"visited"}]}""", req.body.readUtf8())
        assertEquals(4, cache.plan("p1")!!.value.version)
    }

    @Test fun reorder_sendsOrderOnly() = runTest {
        server.enqueue(json(200, planJson))
        repo().reorder("p1", listOf("s2", "s1"))
        assertEquals("""{"order":["s2","s1"]}""", server.takeRequest().body.readUtf8())
    }

    @Test fun http500_isServerError_sentence() = runTest {
        server.enqueue(json(500, """{"error":"internal error"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(500)), repo().setVisitStatus("p1", "s1", "visited"))
    }

    // ── logout ──

    @Test fun logout_callsDelete_withBearer_thenClearsTokenAndCache() = runTest {
        cache.putPlans(emptyList(), now)
        server.enqueue(json(200, """{"ok":true}"""))
        assertTrue(repo().logout())
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/car/link", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        assertNull(tokens.get())
        assertNull(cache.plans())
    }

    @Test fun logout_whenServerUnreachable_stillClearsLocally_andSaysNotConfirmed() = runTest {
        cache.putPlans(emptyList(), now)
        val base = server.url("/").toString()
        server.shutdown()
        assertFalse(repo(baseUrl = base).logout())
        assertNull(tokens.get())
        assertNull(cache.plans())
    }

    @Test fun notLinked_readsWithoutToken_doNotCallServer() = runTest {
        tokens.clear()
        assertEquals(RepoResult.Err(RepoError.NotLinked), repo().plans())
        assertEquals(0, server.requestCount)
    }

    // ── D2 review fixes ──

    // I-1: an origin outage behind Cloudflare is HTTP 5xx, not an IOException. The cached plan must still say when it
    // was last synced.
    @Test fun i1_http500_withCache_servesCache_andTheSentenceCarriesLastSync() = runTest {
        server.enqueue(json(200, planJson))
        repo().plan("p1")
        val syncedAt = now
        now += 15 * 60_000
        server.enqueue(json(500, """{"error":"internal error"}"""))
        val r = repo().plan("p1") as RepoResult.Ok
        assertTrue(r.value.fromCache)
        assertEquals(RepoError.Server(500), r.value.notice)
        val line = UserMessages.syncNotice(r.value, kst)!!
        assertTrue(line, line.contains(UserMessages.lastSync(syncedAt, kst)))
        assertTrue(line, line.startsWith(UserMessages.SERVER))
    }

    @Test fun i1_429_andBadBody_withCache_alsoCarryLastSync() = runTest {
        server.enqueue(json(200, planJson))
        repo().plan("p1")
        server.enqueue(json(429, """{"error":"slow down"}"""))
        val rl = repo().plan("p1") as RepoResult.Ok
        assertTrue(UserMessages.syncNotice(rl.value, kst)!!.contains("마지막 동기화"))
        server.enqueue(json(200, "<html>not json</html>"))
        val bad = repo().plan("p1") as RepoResult.Ok
        assertTrue(UserMessages.syncNotice(bad.value, kst)!!.contains("마지막 동기화"))
    }

    @Test fun i1_freshData_hasNoNotice_offlineSentenceIsNotDoubled() = runTest {
        server.enqueue(json(200, planJson))
        val fresh = repo().plan("p1") as RepoResult.Ok
        assertNull(UserMessages.syncNotice(fresh.value, kst))
        val base = server.url("/").toString()
        server.shutdown()
        val off = repo(baseUrl = base).plan("p1") as RepoResult.Ok
        val line = UserMessages.syncNotice(off.value, kst)!!
        assertEquals(1, Regex("마지막 동기화").findAll(line).count())
    }

    // I-1: a PATCH error shown over a cached plan still carries the cached plan's sync time.
    @Test fun i1_screenLine_actionErrorOverCache_appendsLastSync_overFresh_doesNot() {
        val cached = Synced(Unit, now, fromCache = true)
        val fresh = Synced(Unit, now, fromCache = false)
        val overCache = UserMessages.screenLine(cached, UserMessages.SERVER, kst)!!
        assertEquals("${UserMessages.SERVER} ${UserMessages.lastSync(now, kst)}", overCache)
        assertEquals(UserMessages.SERVER, UserMessages.screenLine(fresh, UserMessages.SERVER, kst))
        assertEquals(UserMessages.lastSync(now, kst), UserMessages.screenLine(cached, null, kst))
        assertNull(UserMessages.screenLine(fresh, null, kst))
    }

    /** Transport that runs [during] after the real HTTP answer arrived but before the repository sees it. */
    private fun racingRepo(during: () -> Unit): PlanRepository {
        val real = OkHttpTransport(server.url("/").toString())
        return PlanRepository(Api { call -> real.execute(call).also { during() } }, tokens, cache, deviceName = "test car", clock = { now })
    }

    // I-2(a): logout lands while account A's plan list is in flight → A's list must not be written back or shown.
    @Test fun i2_logoutDuringRead_dropsTheAnswer_andLeavesCacheEmpty() = runTest {
        server.enqueue(json(200, """{"plans":[{"id":"pA","title":"A의 계획","plannedDate":null,"status":"planned","version":1,"updatedAt":1,"stopCount":2}]}"""))
        val r = racingRepo { tokens.clear(); cache.clear() }.plans()
        assertEquals(RepoResult.Err(RepoError.Stale), r)
        assertNull(cache.plans())
    }

    // I-2(a'): account B linked while A's request was in flight → A's 401 must not expire B's token.
    @Test fun i2_oldTokens401_afterRelink_doesNotClearTheNewToken() = runTest {
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        val r = racingRepo { tokens.set("B".repeat(43)) }.plan("p1")
        assertEquals(RepoResult.Err(RepoError.Stale), r)
        assertEquals("B".repeat(43), tokens.get())
    }

    @Test fun i2_logoutDuringPatch_doesNotWriteTheCache() = runTest {
        server.enqueue(json(200, planJson))
        val r = racingRepo { tokens.clear(); cache.clear() }.setVisitStatus("p1", "s1", "visited")
        assertEquals(RepoResult.Err(RepoError.Stale), r)
        assertNull(cache.plan("p1"))
    }

    // D2 re-review Minor 1: a late GET (v3) must not overwrite the cached PATCH result (v4).
    @Test fun cache_isNeverOverwrittenByAnOlderVersion() = runTest {
        server.enqueue(json(200, planJson.replace("\"version\":3", "\"version\":4")))
        repo().setVisitStatus("p1", "s1", "visited")
        assertEquals(4, cache.plan("p1")!!.value.version)
        server.enqueue(json(200, planJson)) // version 3, issued before the PATCH, answered after it
        repo().plan("p1")
        assertEquals(4, cache.plan("p1")!!.value.version)
        server.enqueue(json(200, planJson.replace("\"version\":3", "\"version\":5")))
        repo().plan("p1")
        assertEquals(5, cache.plan("p1")!!.value.version)
    }

    // A7 fix round 1 contract (A7-report.md "Fix round 1" sample, 경희궁자이(3단지)).
    @Test fun complex_fixRound1Fields_areDecoded() = runTest {
        server.enqueue(json(200, """
            {"cid":"11110|경희궁자이(3단지)|평동|233","name":"경희궁자이(3단지)",
             "brand":{"name":"자이","names":["자이"],"logoUrl":"http://localhost:3300/brands/%EC%9E%90%EC%9D%B4.png",
                      "logoUrls":["http://localhost:3300/brands/%EC%9E%90%EC%9D%B4.png"],
                      "avatar":{"kind":"logo","initials":"경","color":"#4c6ef5","bgAlpha":0.14,"fontScale":0.42}},
             "builder":null,"builders":[],"completionYear":2017,"completionYearSource":"approve","approveDate":"2017-02-24",
             "buildAgeYears":9,"completionText":"2017.02 · 9년차","tradeAsOf":"2026-10-26"}
        """.trimIndent()))
        val c = (repo().complex("11110|경희궁자이(3단지)|평동|233") as RepoResult.Ok).value.value
        assertEquals("2017.02 · 9년차", c.completionText)
        assertEquals("approve", c.completionYearSource)
        assertEquals(9, c.buildAgeYears)
        assertEquals(listOf("자이"), c.brand.names)
        assertEquals(emptyList<String>(), c.builders)
        // no approval date (841 complexes): the web cell is 'N년차' only, no year
        server.enqueue(json(200, """{"cid":"x","name":"y","completionYear":null,"completionYearSource":null,"buildAgeYears":7,"completionText":"7년차",
            "brand":{"name":"래미안","names":["래미안","아이파크"]},"builder":"삼성물산 · HDC현대산업개발","builders":["삼성물산","HDC현대산업개발"]}"""))
        val jv = (repo().complex("x") as RepoResult.Ok).value.value
        assertNull(jv.completionYear)
        assertEquals("7년차", jv.completionText)
        assertEquals(listOf("래미안", "아이파크"), jv.brand.names)
        assertEquals(2, jv.builders.size)
    }

    // M-3: a Keystore failure while saving the token is a sentence, not a crash.
    @Test fun m3_tokenStoreThrows_onLink_isStorageError_andNothingIsSaved() = runTest {
        val broken = object : TokenStore {
            override fun get(): String? = null
            override fun set(token: String) = throw java.security.ProviderException("keystore unavailable")
            override fun clear() = Unit
            override fun rotate(old: String, new: String) = false
            override fun session() = 0L
        }
        server.enqueue(json(200, """{"token":"${"a".repeat(43)}"}"""))
        val r = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), broken, cache, clock = { now }).link("123456")
        assertEquals(RepoResult.Err(RepoError.Storage), r)
        assertEquals("이 차에 연결 정보를 저장하지 못했어요. 다시 시도해 주세요.", UserMessages.of(RepoError.Storage))
    }

    // ── /api/car/complex/{cid} (A7) ──

    private val complexJson = """
        {"cid":"11110|경희궁자이(2단지)|홍파동|199","name":"경희궁자이(2단지)","address":"서울 종로구 홍파동 199","addressApprox":true,
         "sido":"서울","gu":"종로구","dong":"홍파동",
         "brand":{"name":"자이","logoUrl":"http://10.0.2.2:3300/brands/%EC%9E%90%EC%9D%B4.png","logoUrls":["http://10.0.2.2:3300/brands/%EC%9E%90%EC%9D%B4.png"],
                  "avatar":{"kind":"logo","initials":"경","color":"#ff7a45","bgAlpha":0.14,"fontScale":0.42}},
         "builder":"GS건설","households":1148,"completionYear":2017,"approveDate":"2017-02-24","dongCount":14,
         "nearestStation":{"name":"서대문역","lines":["수도권 5호선"],"distanceM":436,"walkMin":8},
         "jeonseRatio":{"value":48.1,"unit":"%","asOf":"2026-10-26","jeonseCount":248},
         "recentTrades":[{"date":"2026-09-28","areaM2":59.9,"pyeong":24,"price":236500,"priceUnit":"만원","priceText":"23억 6,500만","floor":18,"kind":"매매"}],
         "tradeAsOf":"2026-10-26","dataUpdatedAt":"2026-10-26 04:54","units":{"households":"세대"},
         "sources":[{"item":"최근 실거래","source":"국토교통부 실거래가"}],"notice":"참고용 통계 · 실거래 신고 기준"}
    """.trimIndent()

    @Test fun complex_ok_decodesA7Contract_encodesCid_andCaches() = runTest {
        server.enqueue(json(200, complexJson))
        val r = repo().complex("11110|경희궁자이(2단지)|홍파동|199") as RepoResult.Ok
        val c = r.value.value
        assertEquals("자이", c.brand.name)
        assertEquals("#ff7a45", c.brand.avatar.color)
        assertEquals(1148, c.households)
        assertEquals("서대문역", c.nearestStation!!.name)
        assertEquals(48.1, c.jeonseRatio!!.value, 0.0)
        assertEquals(236500L, c.recentTrades!!.single().price)
        val req = server.takeRequest()
        assertEquals("/api/car/complex/11110%7C%EA%B2%BD%ED%9D%AC%EA%B6%81%EC%9E%90%EC%9D%B4%282%EB%8B%A8%EC%A7%80%29%7C%ED%99%8D%ED%8C%8C%EB%8F%99%7C199", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
    }

    @Test fun complex_withDetail_isReusedOnDevice_for24h_thenAskedAgain() = runTest {
        // Server cost (2026-11-10): daily data → DETAIL / COMPARE reuse a copy younger than a day instead of a new request.
        val body = """{"cid":"x","name":"이름","detail":{"v":1,"metrics":{"hh":{"label":"세대수","v":"644세대","n":644}},"tabs":[]}}"""
        server.enqueue(json(200, body)); server.enqueue(json(200, body))
        val r = repo()
        assertTrue(r.complex("x") is RepoResult.Ok)
        now += COMPLEX_FRESH_MS - 1
        val again = r.complex("x") as RepoResult.Ok
        assertEquals("644세대", again.value.value.detail!!.metrics["hh"]!!.v)
        assertEquals(1, server.requestCount)
        now += 2
        assertTrue(r.complex("x") is RepoResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test fun complex_withoutDetail_isAlwaysAskedAgain() = runTest {
        server.enqueue(json(200, """{"cid":"x","name":"이름"}""")); server.enqueue(json(200, """{"cid":"x","name":"이름"}"""))
        val r = repo()
        r.complex("x"); r.complex("x")
        assertEquals(2, server.requestCount)
    }

    @Test fun complex_nullTrades_staysNull_notEmpty() = runTest {
        server.enqueue(json(200, """{"cid":"x","name":"이름","recentTrades":null,"households":null}"""))
        val c = (repo().complex("x") as RepoResult.Ok).value.value
        assertNull(c.recentTrades)
        assertNull(c.households)
    }

    @Test fun complex_404_isComplexNotFound() = runTest {
        server.enqueue(json(404, """{"error":"not found"}"""))
        assertEquals(RepoResult.Err(RepoError.ComplexNotFound), repo().complex("nope"))
    }

    @Test fun complex_offline_servesCache_withLastSync_andLogoutClearsIt() = runTest {
        val cc = MemoryComplexCache()
        val r1 = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), tokens, cache, complexCache = cc, clock = { now })
        server.enqueue(json(200, complexJson))
        r1.complex("11110|경희궁자이(2단지)|홍파동|199")
        val base = server.url("/").toString()
        server.shutdown()
        val r2 = PlanRepository(Api(OkHttpTransport(base)), tokens, cache, complexCache = cc, clock = { now })
        val off = r2.complex("11110|경희궁자이(2단지)|홍파동|199") as RepoResult.Ok
        assertTrue(off.value.fromCache)
        assertTrue(UserMessages.syncNotice(off.value, kst)!!.contains("마지막 동기화"))
        r2.logout()
        assertNull(cc.get("11110|경희궁자이(2단지)|홍파동|199"))
    }
}
