package kr.co.reevl.ontheway.gleo

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.co.reevl.ontheway.BuildConfig
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.data.FavToggleResponse
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.data.SearchCond
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.data.Synced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoTest {

    private class Fake : GleoBackend {
        var pos: GleoPosition? = GleoPosition(37.5692, 126.9641, null)
        var searchResult: RepoResult<SearchResponse> = RepoResult.Ok(SearchResponse(q = "q", radiusM = 1000, scanned = 60, items = listOf(
            SearchItem(cid = "11110|경희궁자이(2단지)|홍파동|199", name = "경희궁자이(2단지)", lat = 1.0, lng = 2.0, distanceM = 50, side = "right",
                latestTrade = LatestTrade("2026-09-28", 59.9, price = 236500)),
            SearchItem(cid = "11110|산경|x|1", name = "산경", lat = 1.0, lng = 2.0, distanceM = 107))))
        var plan: Plan? = Plan("p", "토요일 임장", status = "active", version = 1, updatedAt = 0, stops = listOf(
            PlanStop(id = "s1", position = 1, kind = "apartment_complex", cid = "11110|경희궁자이(3단지)|평동|233", name = "경희궁자이(3단지)", visitStatus = "visited")))
        var favAnswer: RepoResult<FavToggleResponse> = RepoResult.Ok(FavToggleResponse("c", true, 4))
        var favCalls = 0
        var added: Triple<String, String, String?>? = null
        var last = listOf<GleoRef>()
        var slow = false
        override fun position() = pos
        override suspend fun search(q: String, at: GleoPosition): RepoResult<SearchResponse> { if (slow) delay(10_000); return searchResult }
        override suspend fun complex(cid: String): RepoResult<Synced<CarComplex>> = RepoResult.Ok(Synced(CarComplex(cid = cid, name = "산경", households = 120,
            completionYear = 2003, recentTrades = listOf(CarTrade("2026-09-01", 84.9, price = 88000))), 0, false))
        override suspend fun todayPlan(): RepoResult<Plan?> = RepoResult.Ok(plan)
        override suspend fun addStop(planId: String, cid: String, afterStopId: String?): RepoResult<Plan> { added = Triple(planId, cid, afterStopId); return RepoResult.Ok(plan!!) }
        override suspend fun deleteStop(planId: String, stopId: String): RepoResult<Plan> = RepoResult.Ok(plan!!)
        var reordered: List<String>? = null
        override suspend fun reorder(planId: String, order: List<String>): RepoResult<Plan> {
            reordered = order
            val p = plan!!
            return RepoResult.Ok(p.copy(version = p.version + 1, stops = order.mapIndexed { i, id -> p.stops.first { it.id == id }.copy(position = i + 1) }))
        }
        override suspend fun setFav(cid: String, on: Boolean): RepoResult<FavToggleResponse> { favCalls++; return favAnswer }
        override fun lastResults() = last
        override fun rememberResults(refs: List<GleoRef>) { last = refs }
        override fun saved() = emptyList<GleoRef>()
        var pend: PendingWrite? = null
        var clock = 1_000_000L
        override fun pending() = pend
        override fun setPending(p: PendingWrite?) { pend = p }
        override fun now() = clock
    }

    private suspend fun GleoActions.confirm(action: String, ref: String? = null) =
        handle("MANAGE_VISIT_PLAN", buildMap { put("action", action); put("confirmed", "true"); ref?.let { put("complexIdOrReference", it) } })

    @Test fun parseParams_allThreeShapesFromTheGuide() {
        assertEquals("오른쪽 8억", GleoProtocol.parseParams("{\"query\":\"오른쪽 8억\"}")["query"])
        assertEquals("오른쪽 8억", GleoProtocol.parseParams("\"{\\\"query\\\":\\\"오른쪽 8억\\\"}\"")["query"])
        assertEquals("add", GleoProtocol.parseParams("{\"packageName\":\"x\",\"action\":\"MANAGE_VISIT_PLAN\",\"actionParameters\":{\"action\":\"add\"}}")["action"])
        assertEquals("산경", GleoProtocol.parseParams(null, mapOf("complexIdOrReference" to "산경"))["complexIdOrReference"])
        assertTrue(GleoProtocol.parseParams("not json").isEmpty())
    }

    @Test fun callbackJson_hasTheGuideFields_dataAsJsonArrayString() {
        val j = Json.parseToJsonElement(GleoCallback("SEARCH_PROPERTIES", GleoStatus.Success, "Found 1.", listOf(mapOf("name" to "산경"))).toJson()).jsonObject
        assertEquals("SEARCH_PROPERTIES", j["functionName"]!!.jsonPrimitive.content)
        assertEquals("success", j["status"]!!.jsonPrimitive.content)
        assertEquals("산경", Json.parseToJsonElement(j["data"]!!.jsonPrimitive.content).jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertFalse(Json.parseToJsonElement(GleoCallback("X", GleoStatus.Fail, "m").toJson()).jsonObject.containsKey("data"))
    }

    @Test fun search_noQuery_or_noLocation_isAsking_ok_isSuccessWithTop3_andRemembers() = runTest {
        val f = Fake()
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("SEARCH_PROPERTIES", emptyMap()).status)
        f.pos = null
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "8억 이하")).status)
        f.pos = GleoPosition(37.5, 127.0, 10.0)
        val cb = GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "오른쪽 8억 이하"))
        assertEquals(GleoStatus.Success, cb.status)
        assertEquals("경희궁자이(2단지)", cb.data[0]["name"])
        assertEquals("오른쪽", cb.data[0]["side"])
        assertEquals(2, f.last.size)
    }

    @Test fun search_errors_areFail_andTimeoutAnswersBeforeGleoGivesUp() = runTest {
        val f = Fake()
        f.searchResult = RepoResult.Err(RepoError.Offline(null))
        assertEquals(GleoStatus.Fail, GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q")).status)
        f.slow = true
        val cb = GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        assertEquals(GleoStatus.Fail, cb.status)
        assertEquals("A timeout occurred while processing the request.", cb.message)
    }

    @Test fun analyze_byRank_afterSearch_ambiguous_missing() = runTest {
        val f = Fake()
        GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        val cb = GleoActions(f).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "두 번째"))
        assertEquals(GleoStatus.Success, cb.status)
        assertEquals("120", cb.data[0]["households"])
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("ANALYZE_PROPERTY", emptyMap()).status)
        assertEquals(GleoStatus.Success, GleoActions(f).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "경희궁")).status) // one match
        f.last = listOf(GleoRef("a", "경희궁자이(2단지)"), GleoRef("b", "경희궁자이(3단지)"))
        val many = GleoActions(f).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "경희궁자이"))
        assertEquals(GleoStatus.Asking, many.status)
        assertEquals(2, many.data.size)
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "없는단지")).status)
    }

    @Test fun save_isSuccessOnlyAfterTheServerSaidOk() = runTest {
        val f = Fake()
        GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        val a = GleoActions(f)
        // first turn: only a question, nothing written
        assertEquals(GleoStatus.Asking, a.handle("MANAGE_VISIT_PLAN", mapOf("action" to "save", "complexIdOrReference" to "산경")).status)
        assertEquals(0, f.favCalls)
        f.favAnswer = RepoResult.Err(RepoError.Offline(null))
        assertEquals(GleoStatus.Fail, a.confirm("save").status)
        assertEquals(1, f.favCalls)
        a.handle("MANAGE_VISIT_PLAN", mapOf("action" to "save", "complexIdOrReference" to "산경"))
        f.favAnswer = RepoResult.Ok(FavToggleResponse("11110|산경|x|1", true, 4))
        assertEquals(GleoStatus.Success, a.confirm("save", "산경").status)
        assertEquals(2, f.favCalls)
    }

    @Test fun plan_add_afterAnchor_noPlan_fail_list_and_unknownAction_asking() = runTest {
        val f = Fake()
        GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "add", "complexIdOrReference" to "첫 번째")).status)
        assertEquals(null, f.added)
        assertEquals(GleoStatus.Success, GleoActions(f).confirm("add").status)
        assertEquals(Triple("p", "11110|경희궁자이(2단지)|홍파동|199", "s1"), f.added)
        assertEquals(GleoStatus.Success, GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "list")).status)
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "rename")).status)
        assertEquals(GleoStatus.Asking, GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "add")).status)
        f.plan = null
        GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "add", "complexIdOrReference" to "산경"))
        assertEquals(GleoStatus.Fail, GleoActions(f).confirm("add").status)
    }

    @Test fun writes_needAFreshMatchingConfirmation() = runTest {
        val f = Fake()
        GleoActions(f).handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        val a = GleoActions(f)
        // a lone "confirmed=true" without an earlier question does nothing but ask
        assertEquals(GleoStatus.Asking, a.confirm("save", "산경").status)
        assertEquals(0, f.favCalls)
        // expired
        f.clock += GleoActions.CONFIRM_TTL_MS + 1
        assertEquals(GleoStatus.Asking, a.confirm("save", "산경").status)
        assertEquals(0, f.favCalls)
        // other action or other target does not confirm it
        a.handle("MANAGE_VISIT_PLAN", mapOf("action" to "save", "complexIdOrReference" to "산경"))
        assertEquals(GleoStatus.Asking, a.confirm("add", "산경").status)
        a.handle("MANAGE_VISIT_PLAN", mapOf("action" to "save", "complexIdOrReference" to "산경"))
        assertEquals(GleoStatus.Asking, a.confirm("save", "첫 번째").status)
        assertEquals(0, f.favCalls)
        // remove also asks first
        f.plan = f.plan!!.copy(stops = f.plan!!.stops)
        assertEquals(GleoStatus.Asking, a.handle("MANAGE_VISIT_PLAN", mapOf("action" to "remove", "complexIdOrReference" to "경희궁자이(3단지)")).status)
        assertEquals(GleoStatus.Success, a.confirm("remove").status)
    }

    @Test fun sender_onlyGleoOrUnknown() {
        assertTrue(GleoProtocol.senderAllowed(null))
        assertTrue(GleoProtocol.senderAllowed("ai.umos.ambientai"))
        assertFalse(GleoProtocol.senderAllowed("com.evil.app"))
    }

    @Test fun releaseManifest_requiresGleosSignaturePermission_debugRemovesIt() {
        val main = File("src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("GleoReceiver\"[^>]*android:permission=\"ai.umos.gleo.permission.APP_ACTION\"", RegexOption.DOT_MATCHES_ALL).containsMatchIn(main))
        assertTrue(File("src/debug/AndroidManifest.xml").readText().contains("tools:remove=\"android:permission\""))
    }

    @Test fun schemaJson_followsTheGuide() {
        val s = Json.parseToJsonElement(File("src/main/assets/schema.json").readText()).jsonObject
        assertEquals("kr.co.reevl.ontheway", s["packageName"]!!.jsonPrimitive.content)
        assertEquals(BuildConfig.VERSION_NAME.removeSuffix("-mock"), s["version"]!!.jsonPrimitive.content)
        val calls = s["actionCalls"]!!.jsonArray
        assertTrue(calls.size <= 3)
        val names = calls.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(GleoProtocol.FUNCTIONS, names)
        names.forEach { assertTrue(it, Regex("^[A-Z]+(_[A-Z]+)+$").matches(it)) }
        fun ascii(t: String) = t.all { it.code < 128 }
        assertTrue(ascii(s["appDescription"]!!.jsonPrimitive.content))
        calls.forEach { c ->
            val fn = c.jsonObject["function"]!!.jsonObject
            assertTrue(ascii(fn["description"]!!.jsonPrimitive.content))
            assertEquals("service", fn["component"]!!.jsonPrimitive.content)
            val props = fn["parameters"]!!.jsonObject["properties"]!!.jsonObject
            props.values.forEach { p -> assertTrue(ascii((p as JsonObject)["description"]!!.jsonPrimitive.content)); assertTrue(p["type"]!!.jsonPrimitive.content in setOf("string", "number", "boolean", "array")) }
            fn["parameters"]!!.jsonObject["required"]!!.jsonArray.forEach { assertTrue(it.jsonPrimitive.content in props.keys) }
        }
    }

    // ── 순서 최적화 by voice ("동선 줄여줘") ──

    private fun st(id: String, pos: Int, lat: Double, status: String = "planned") =
        PlanStop(id = id, position = pos, kind = "apartment_complex", cid = "c|$id", name = "단지$id", lat = lat, lng = 127.0, locationVerified = true, visitStatus = status)

    @Test fun reorder_asksWithStraightLineNumbers_thenAppliesOnlyOnConfirm() = runTest {
        val f = Fake()
        f.plan = Plan("p", "토요일 임장", status = "active", version = 4, updatedAt = 0,
            stops = listOf(st("a", 1, 37.00), st("d", 2, 37.03), st("b", 3, 37.01), st("c", 4, 37.02)))
        val g = GleoActions(f)
        val ask = g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "reorder"))
        assertEquals(GleoStatus.Asking, ask.status)
        assertTrue(ask.message, ask.message.startsWith("Confirmation is required to reorder the stops") && ask.message.contains(" km to "))
        assertFalse(ask.message, ask.message.contains(". ") || ask.message.contains("? ")) // one sentence
        assertEquals("단지a, 단지b, 단지c, 단지d", ask.data.single()["order"])
        assertFalse(ask.message.contains("min")) // never a drive time
        assertEquals(null, f.reordered)
        val done = g.confirm("reorder")
        assertEquals(GleoStatus.Success, done.status)
        assertEquals(listOf("a", "b", "c", "d"), f.reordered)
        assertEquals("단지b", done.data[1]["name"])
    }

    @Test fun reorder_planChangedSinceTheQuestion_asksAgain() = runTest {
        val f = Fake()
        f.plan = Plan("p", "토요일 임장", status = "active", version = 4, updatedAt = 0,
            stops = listOf(st("a", 1, 37.00), st("d", 2, 37.03), st("b", 3, 37.01), st("c", 4, 37.02)))
        val g = GleoActions(f)
        g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "reorder"))
        f.plan = f.plan!!.copy(version = 5) // the phone edited the plan in between
        assertEquals(GleoStatus.Asking, g.confirm("reorder").status)
        assertEquals(null, f.reordered)
    }

    @Test fun reorder_alreadyShortest_orTooFew_saysSo_andSendsNothing() = runTest {
        val f = Fake()
        f.plan = Plan("p", "t", status = "active", version = 1, updatedAt = 0, stops = listOf(st("a", 1, 37.00), st("b", 2, 37.01), st("c", 3, 37.02)))
        assertEquals("The current order is already the shortest.", GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "reorder")).message)
        f.plan = Plan("p", "t", status = "active", version = 1, updatedAt = 0, stops = listOf(st("a", 1, 37.00), st("b", 2, 37.01)))
        assertTrue(GleoActions(f).handle("MANAGE_VISIT_PLAN", mapOf("action" to "reorder")).message.startsWith("At least three stops"))
        assertEquals(null, f.reordered)
    }

    @Test fun schema_offersReorder() {
        val s = File("src/main/assets/schema.json").readText()
        assertTrue(s.contains("\"reorder\""))
    }

    // ── Callback message language (action_callback.md §2.1: English sentence, Korean only for names/places/terms) ──

    /** Hangul allowed only inside the names/terms we passed in; everything else must be English. */
    private fun assertEnglish(cb: GleoCallback, vararg allowed: String) {
        var m = cb.message
        allowed.forEach { m = m.replace(it, "") }
        assertFalse("not English: ${cb.message}", m.any { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' })
        assertTrue("not a sentence: ${cb.message}", (cb.message.first().isUpperCase() || cb.message.first() in '\uAC00'..'\uD7A3') && cb.message.trimEnd().last() in ".?!")
    }

    @Test fun everyCallbackMessage_isAnEnglishSentence() = runTest {
        val names = arrayOf("경희궁자이(2단지)", "경희궁자이(3단지)", "산경", "토요일 임장", "단지a", "단지b", "단지c", "단지d")
        val f = Fake()
        val g = GleoActions(f)
        val all = mutableListOf<GleoCallback>()
        all += g.handle("SEARCH_PROPERTIES", emptyMap())
        all += g.handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        all += g.handle("ANALYZE_PROPERTY", emptyMap())
        all += g.handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "산경"))
        all += g.handle("ANALYZE_PROPERTY", mapOf("complexIdOrReference" to "없는단지"))
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "list"))
        all += g.handle("MANAGE_VISIT_PLAN", emptyMap())
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "add", "complexIdOrReference" to "산경"))
        all += g.confirm("add")
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "save", "complexIdOrReference" to "산경"))
        all += g.confirm("save")
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "remove", "complexIdOrReference" to "경희궁자이(3단지)"))
        all += g.confirm("remove")
        all += g.handle("UNKNOWN_FN", emptyMap())
        f.plan = Plan("p", "토요일 임장", status = "active", version = 4, updatedAt = 0,
            stops = listOf(st("a", 1, 37.00), st("d", 2, 37.03), st("b", 3, 37.01), st("c", 4, 37.02)))
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "reorder"))
        all += g.confirm("reorder")
        f.plan = null
        all += g.handle("MANAGE_VISIT_PLAN", mapOf("action" to "list"))
        f.pos = null
        all += g.handle("SEARCH_PROPERTIES", mapOf("query" to "q"))
        all.forEach { assertEnglish(it, *names) }
        listOf(RepoError.Expired, RepoError.NotLinked, RepoError.Offline(null), RepoError.RateLimited, RepoError.ComplexNotFound,
            RepoError.PlanNotFound, RepoError.FavLimit).forEach { assertEnglish(GleoCallback("X", GleoStatus.Fail, GleoActions.failText(it))) }
    }

    @Test fun distEn_spokenUnits() {
        assertEquals("less than 50 m", GleoActions.distEn(10.0))
        assertEquals("850 m", GleoActions.distEn(853.0))
        assertEquals("1 km", GleoActions.distEn(1000.0))
        assertEquals("1.2 km", GleoActions.distEn(1210.0))
    }
}
