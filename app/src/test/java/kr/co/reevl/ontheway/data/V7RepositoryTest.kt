package kr.co.reevl.ontheway.data

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
 * v7 routes (V7-contract.md §1–3) through the production OkHttp transport against MockWebServer: nearby complexes,
 * nearby offices, insert a stop (`{cid}` / `{officeId}` + insertAfterStopId) and undo (DELETE the stop).
 */
class V7RepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var cache: MemoryPlanCache
    private val logs = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun repo() = PlanRepository(Api(OkHttpTransport(server.url("/").toString())), tokens, cache, deviceName = "test car",
        clock = { 1_793_000_000_000L }, log = { e, f -> logs += e to f })

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("content-type", "application/json").setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = InMemoryTokenStore("T".repeat(43))
        cache = MemoryPlanCache()
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private val nearbyJson = """
        {"center":{"lat":37.5695,"lng":126.9638},"radiusM":500,
         "items":[
          {"cid":"11110|경희궁자이(2단지)|홍파동|199","name":"경희궁자이(2단지)","address":"서울 종로구 홍파동 199","lat":37.569205,"lng":126.964152,
           "distanceM":47,"households":1148,"completionYear":2017,
           "brand":{"name":"자이","logoUrl":"http://10.0.2.2:3300/brands/자이.png","avatar":{"kind":"logo","initials":"경","color":"#ff7a45"}},
           "latestTrade":{"date":"2026-09-28","areaM2":59.9,"pyeong":24,"price":236500,"priceUnit":"만원"}},
          {"cid":"x2","name":"이름없는단지","lat":37.57,"lng":126.96,"distanceM":140,"households":null,"completionYear":null,
           "brand":{"name":null,"logoUrl":null,"avatar":{"kind":"initial","initials":"이","color":"#3182f6"}},"latestTrade":null,"newField":true}
         ],
         "asOf":"2026-10-26","source":"reevl_property_dataset","notice":"대표 좌표 기준 후보 · 실제 보이는 건물과 다를 수 있어요"}
    """.trimIndent()

    @Test fun nearbyComplexes_query_bearer_andDecode() = runTest {
        server.enqueue(json(200, nearbyJson))
        val r = repo().nearbyComplexes(37.56951234, 126.96381234) as RepoResult.Ok
        assertEquals(2, r.value.items.size)
        val a = r.value.items[0]
        assertEquals(47, a.distanceM)
        assertEquals("자이", a.brand.name)
        assertEquals(236500L, a.latestTrade!!.price)
        assertNull(r.value.items[1].latestTrade)
        assertNull(r.value.items[1].households)
        assertEquals("대표 좌표 기준 후보 · 실제 보이는 건물과 다를 수 있어요", r.value.notice)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/car/complexes/nearby?lat=37.569512&lng=126.963812&radiusM=500&limit=10", req.path)
        assertEquals("Bearer " + "T".repeat(43), req.getHeader("authorization"))
        // the app log never carries the coordinates
        assertFalse(logs.toString().contains("37.56"))
    }

    @Test fun nearbyComplexes_401_expiresTheToken_404_isServerError_offlineIsOffline() = runTest {
        server.enqueue(json(404, """{"error":"not found"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(404)), repo().nearbyComplexes(37.5, 126.9))
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        assertEquals(RepoResult.Err(RepoError.Expired), repo().nearbyComplexes(37.5, 126.9))
        assertNull(tokens.get())
        tokens.set("T".repeat(43))
        server.shutdown()
        assertEquals(RepoResult.Err(RepoError.Offline(null)), repo().nearbyComplexes(37.5, 126.9))
    }

    @Test fun nearbyOffices_available_andEmptyDataset() = runTest {
        server.enqueue(json(200, """
            {"available":true,"items":[{"id":"o1","name":"데모공인중개사사무소","address":"서울 종로구 송월길 1","lat":37.5694,"lng":126.9638,
              "distanceM":120,"phone":"02-000-0000","registrationNo":null,"registrationStatus":null,"source":"서울 열린데이터광장","checkedAt":"2026-10-28"}],
             "source":"서울 열린데이터광장","checkedAt":"2026-10-28","notice":"출처 기준일 기준 정보 · 실제 영업·상담 가능 여부는 전화로 확인해 주세요"}
        """.trimIndent()))
        val r = repo().nearbyOffices(37.5694, 126.9638) as RepoResult.Ok
        assertTrue(r.value.available)
        assertEquals("o1", r.value.items.single().id)
        assertEquals("/api/car/places/nearby-offices?lat=37.569400&lng=126.963800&radiusM=1000&limit=10", server.takeRequest().path)
        server.enqueue(json(200, """{"available":false,"items":[],"source":null,"checkedAt":null,"notice":"출처 기준일 기준 정보 · 실제 영업·상담 가능 여부는 전화로 확인해 주세요"}"""))
        val e = repo().nearbyOffices(37.5, 126.9) as RepoResult.Ok
        assertFalse(e.value.available)
        assertTrue(e.value.items.isEmpty())
    }

    private val planAfterInsert = """
        {"id":"p1","title":"토요일 임장","plannedDate":"2026-11-07","status":"planned","version":7,"updatedAt":1,"mode":"tour","clientLabel":"고객 A",
         "stops":[
          {"id":"s1","position":1,"kind":"apartment_complex","name":"경희궁자이(2단지)","lat":37.569205,"lng":126.964152,"locationVerified":true,"visitStatus":"visited"},
          {"id":"n1","position":2,"kind":"apartment_complex","name":"경희궁자이(4단지)","lat":37.5701,"lng":126.9625,"locationVerified":true,"visitStatus":"planned"},
          {"id":"s2","position":3,"kind":"apartment_complex","name":"경희궁자이(3단지)","lat":37.568855,"lng":126.965171,"locationVerified":true,"visitStatus":"planned"}]}
    """.trimIndent()

    @Test fun addStop_cid_afterTheVisitedStop_postsTheContractBody_andCachesTheServerPlan() = runTest {
        server.enqueue(json(200, planAfterInsert))
        val r = repo().addStop("p1", AddStopRequest(cid = "11110|경희궁자이(4단지)|교남동|1", insertAfterStopId = "s1")) as RepoResult.Ok
        assertEquals(listOf("s1", "n1", "s2"), r.value.stops.sortedBy { it.position }.map { it.id })
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/car/plans/p1/stops", req.path)
        assertEquals("""{"cid":"11110|경희궁자이(4단지)|교남동|1","insertAfterStopId":"s1"}""", req.body.readUtf8())
        assertEquals(7, cache.plan("p1")!!.value.version)
    }

    @Test fun addStop_office_sendsOfficeIdOnly() = runTest {
        server.enqueue(json(200, planAfterInsert))
        repo().addStop("p1", AddStopRequest(officeId = "o1", insertAfterStopId = "s1"))
        assertEquals("""{"officeId":"o1","insertAfterStopId":"s1"}""", server.takeRequest().body.readUtf8())
    }

    @Test fun addStop_400_isServerError_planUntouched() = runTest {
        server.enqueue(json(400, """{"error":"unknown cid"}"""))
        assertEquals(RepoResult.Err(RepoError.Server(400)), repo().addStop("p1", AddStopRequest(cid = "nope")))
        assertNull(cache.plan("p1"))
    }

    @Test fun undo_deletesThatStop() = runTest {
        server.enqueue(json(200, planAfterInsert.replace("\"version\":7", "\"version\":8")))
        val r = repo().deleteStop("p1", "n1")
        assertTrue(r is RepoResult.Ok)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/car/plans/p1/stops/n1", req.path)
    }
}
