package kr.co.reevl.ontheway.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Why a repository call could not give fresh server data. Turned into a sentence by [UserMessages]. */
sealed interface RepoError {
    /** No answer from the server. [lastSyncAt] = when the data we still show was last fetched (null = never). */
    data class Offline(val lastSyncAt: Long?) : RepoError
    /**
     * Definitive 401 from the server's auth check (`{"error":"unauthorized"}` for the token that is still stored): the link
     * was revoked (account screen 해제 / car logout) or idle 180 d. Token + cache are cleared. A 401 without that body
     * (a proxy, a captive portal) is [Server], and the token is kept.
     */
    data object Expired : RepoError
    /** 404 on a plan route: the plan was deleted or belongs to someone else. */
    data object PlanNotFound : RepoError
    /** 400/404/410 on `POST /api/car/link`: unknown, used, expired or malformed code. */
    data object CodeInvalid : RepoError
    /** 429 */
    data object RateLimited : RepoError
    /** Any other non-2xx, or a 2xx body we could not read. */
    data class Server(val status: Int) : RepoError
    /** No token on this car. */
    data object NotLinked : RepoError
    /** 404 on `/api/car/complex/{cid}`: Reevl has no such complex (custom stop or a removed cid). */
    data object ComplexNotFound : RepoError
    /** 409 on `POST /api/car/favs`: the account already keeps the maximum number of saved complexes. */
    data object FavLimit : RepoError
    /** M-3 (D2 review): the Keystore could not store the token (key generation/encryption threw). Nothing is saved. */
    data object Storage : RepoError
    /**
     * I-2 (D2 review): the answer belongs to a session that has ended (logout or a new link happened while the call
     * was in flight). Nothing was written to the cache; the screen must drop the result silently.
     */
    data object Stale : RepoError
}

/** QR link poll outcome (errors other than expiry are [RepoError]s). */
sealed interface QrPoll {
    data object Pending : QrPoll
    /** The token is stored; the screen goes to HOME. */
    data object Linked : QrPoll
    /** 410 / unknown request: stop polling, offer 새 QR. */
    data object Expired : QrPoll
}

sealed interface RepoResult<out T> {
    data class Ok<T>(val value: T) : RepoResult<T>
    data class Err(val error: RepoError) : RepoResult<Nothing>
}

/**
 * Data plus where it came from. [fromCache] = true means it is NOT fresh: the screen must show
 * `마지막 동기화 HH:MM` ([syncedAt]). [notice] = why fresh data could not be fetched (null while just loading).
 */
data class Synced<T>(val value: T, val syncedAt: Long, val fromCache: Boolean, val notice: RepoError? = null)

/** User sentences (screen copy, D2 brief). Times are shown in the device time zone as HH:MM. */
object UserMessages {
    const val EXPIRED = "차량 연결이 끊겼어요. 휴대폰으로 다시 연결해 주세요."
    const val PLAN_NOT_FOUND = "계획을 찾을 수 없어요"
    const val CODE_INVALID = "코드가 만료됐거나 맞지 않아요"
    const val OFFLINE = "인터넷 연결을 확인해 주세요."
    const val RATE_LIMITED = "요청이 많아요. 잠시 후 다시 시도해 주세요."
    const val SERVER = "서버에서 처리하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val NOT_LINKED = "휴대폰으로 QR을 찍거나 6자리 코드로 차량을 연결해 주세요."
    const val COMPLEX_NOT_FOUND = "리블에 이 단지 정보가 없어요"
    const val STORAGE = "이 차에 연결 정보를 저장하지 못했어요. 다시 시도해 주세요."
    const val FAV_LIMIT = "저장한 집이 너무 많아요. 휴대폰·웹 리블에서 몇 곳을 정리해 주세요."

    fun of(e: RepoError, zone: ZoneId = ZoneId.systemDefault()): String = when (e) {
        is RepoError.Offline -> if (e.lastSyncAt != null) "$OFFLINE ${lastSync(e.lastSyncAt, zone)}" else OFFLINE
        RepoError.Expired -> EXPIRED
        RepoError.PlanNotFound -> PLAN_NOT_FOUND
        RepoError.CodeInvalid -> CODE_INVALID
        RepoError.RateLimited -> RATE_LIMITED
        is RepoError.Server -> SERVER
        RepoError.NotLinked -> NOT_LINKED
        RepoError.ComplexNotFound -> COMPLEX_NOT_FOUND
        RepoError.Storage -> STORAGE
        RepoError.FavLimit -> FAV_LIMIT
        RepoError.Stale -> SERVER // never shown: screens drop stale results
    }

    /**
     * I-1 (D2 review): the one sentence under the header for data on screen. Data served from cache **always**
     * carries `마지막 동기화 HH:MM` — also when the reason is a 5xx/429/unreadable body, not only a network error
     * (behind Cloudflare an origin outage arrives as HTTP 5xx). Fresh data with no notice → null.
     */
    fun syncNotice(s: Synced<*>, zone: ZoneId = ZoneId.systemDefault()): String? {
        val base = s.notice?.let { of(it, zone) }
        if (!s.fromCache) return base
        val sync = lastSync(s.syncedAt, zone)
        return when {
            base == null -> sync
            base.contains("마지막 동기화") -> base
            else -> "$base $sync"
        }
    }

    /**
     * The one line a screen shows under its header: an action/error [message] if there is one, else the data's own
     * [syncNotice]. Either way, if what is on screen came from the cache, the line ends with `마지막 동기화 HH:MM` (I-1).
     */
    fun screenLine(data: Synced<*>?, message: String?, zone: ZoneId = ZoneId.systemDefault()): String? = when {
        message == null -> data?.let { syncNotice(it, zone) }
        data != null && data.fromCache && !message.contains("마지막 동기화") -> "$message ${lastSync(data.syncedAt, zone)}"
        else -> message
    }

    fun lastSync(at: Long, zone: ZoneId = ZoneId.systemDefault()): String = "마지막 동기화 ${hhmm(at, zone)}"

    fun hhmm(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(at))
}

@Serializable
data class Stamped<T>(val syncedAt: Long, val value: T)

/** Last plan list / plans this car saw. Memory first, disk behind it. Cleared on logout and on 401. */
interface PlanCache {
    fun plans(): Stamped<List<PlanSummary>>?
    fun putPlans(value: List<PlanSummary>, at: Long)
    fun plan(id: String): Stamped<Plan>?
    fun putPlan(value: Plan, at: Long)
    fun removePlan(id: String)
    fun clear()
}

class MemoryPlanCache : PlanCache {
    private var list: Stamped<List<PlanSummary>>? = null
    private val byId = mutableMapOf<String, Stamped<Plan>>()
    @Synchronized override fun plans() = list
    @Synchronized override fun putPlans(value: List<PlanSummary>, at: Long) { list = Stamped(at, value) }
    @Synchronized override fun plan(id: String) = byId[id]
    @Synchronized override fun putPlan(value: Plan, at: Long) { byId[value.id] = Stamped(at, value) }
    @Synchronized override fun removePlan(id: String) { byId.remove(id) }
    @Synchronized override fun clear() { list = null; byId.clear() }
}

/** Memory + JSON files under [dir] (app-private, no backup). Corrupt files are ignored and deleted. */
class DiskPlanCache(private val dir: File) : PlanCache {
    private val mem = MemoryPlanCache()
    private val listSer = Stamped.serializer(ListSerializer(PlanSummary.serializer()))
    private val planSer = Stamped.serializer(Plan.serializer())

    @Synchronized override fun plans(): Stamped<List<PlanSummary>>? = mem.plans() ?: read(listFile()) {
        Api.json.decodeFromString(listSer, it)
    }?.also { mem.putPlans(it.value, it.syncedAt) }

    @Synchronized override fun putPlans(value: List<PlanSummary>, at: Long) {
        mem.putPlans(value, at)
        write(listFile(), Api.json.encodeToString(listSer, Stamped(at, value)))
    }

    @Synchronized override fun plan(id: String): Stamped<Plan>? = mem.plan(id) ?: read(planFile(id)) {
        Api.json.decodeFromString(planSer, it)
    }?.also { mem.putPlan(it.value, it.syncedAt) }

    @Synchronized override fun putPlan(value: Plan, at: Long) {
        mem.putPlan(value, at)
        write(planFile(value.id), Api.json.encodeToString(planSer, Stamped(at, value)))
    }

    @Synchronized override fun removePlan(id: String) {
        mem.removePlan(id)
        planFile(id).delete()
    }

    @Synchronized override fun clear() {
        mem.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun listFile() = File(dir, "plans.json")
    private fun planFile(id: String) = File(dir, "plan_" + id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private fun <T> read(f: File, decode: (String) -> T): T? {
        if (!f.exists()) return null
        return runCatching { decode(f.readText()) }.getOrElse { f.delete(); null }
    }

    private fun write(f: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}

/**
 * The car's only way to the Reevl plan data: `/api/car/...` with the car token.
 *
 * Rules: every change (visit status, order) is saved on the server first and the screen renders the plan the
 * server returns (v5 §9-4). Only a definitive 401 (server `{"error":"unauthorized"}`, for the token still stored) clears
 * token and cache ([RepoError.Expired]); network errors, 5xx, other 401s never do (2026-11-10 "stay linked once linked").
 * Network failure on a read → the cached copy, marked as cache with its sync time; never presented as fresh.
 *
 * Token rotation (server, every 30 d): an answer may carry `x-car-token`; [Api.onTokenRotated] swaps the stored token with a
 * compare-and-swap ([TokenStore.rotate]). A rotation is the same session — answers in flight stay valid; a 401 for the
 * token we just replaced is retried once with the new one instead of logging out.
 */
/** A complex summary is reused this long without asking the server (daily data; DETAIL / COMPARE). */
const val COMPLEX_FRESH_MS = 24 * 60 * 60 * 1000L

class PlanRepository(
    private val api: Api,
    private val tokens: TokenStore,
    private val cache: PlanCache,
    private val deviceName: String = "Pleos car",
    /** Car complex summaries (`/api/car/complex/{cid}`, task A7) — same cache rules as plans. */
    private val complexCache: ComplexCache = MemoryComplexCache(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Last 저장한 집 list (offline: shown as cache with its sync time; never invented). */
    private val favCache: FavCache = MemoryFavCache(),
    /** 휴대폰 ↔ 차 동기화: 최근 본 단지 · 내 메모 · 저장한 조건 + not-yet-sent car views. */
    private val syncStore: SyncStore = MemorySyncStore(),
    private val log: (event: String, fields: Map<String, Any?>) -> Unit = { _, _ -> },
) {
    init {
        api.onTokenRotated = { used, fresh -> onRotated(used, fresh) }
    }

    fun isLinked(): Boolean = tokens.get() != null

    /** Server handed a replacement for [used]. Swap only if [used] is still ours (a logout / relink in between wins). */
    private fun onRotated(used: String, fresh: String) {
        val swapped = try {
            tokens.rotate(used, fresh)
        } catch (e: Exception) {
            // Keystore write failed: keep the old token — the server keeps it valid until the new one is used.
            log("token.rotate", mapOf("swapped" to false, "error" to e.javaClass.simpleName))
            return
        }
        log("token.rotate", mapOf("swapped" to swapped)) // never the token
    }

    /** A token from `POST /api/car/link` or a linked QR poll: store it (new session) and forget the previous account's data. */
    private fun storeNewToken(token: String, event: String): RepoResult<Unit> {
        try {
            tokens.set(token)
        } catch (e: Exception) {
            // M-3: Keystore failure (KeyStoreException, ProviderException, …) must not crash the app.
            runCatching { tokens.clear() }
            log(event, mapOf("ok" to false, "error" to "token store: ${e.javaClass.simpleName}"))
            return RepoResult.Err(RepoError.Storage)
        }
        cache.clear() // a new account may be behind this code
        complexCache.clear()
        favCache.clear()
        syncStore.clear()
        log(event, mapOf("ok" to true))
        return RepoResult.Ok(Unit)
    }

    suspend fun link(code: String): RepoResult<Unit> {
        if (!CODE.matches(code)) return RepoResult.Err(RepoError.CodeInvalid)
        return try {
            storeNewToken(api.link(code, deviceName), "link")
        } catch (f: ApiFailure) {
            val e = when (f) {
                is ApiFailure.Network -> RepoError.Offline(null)
                is ApiFailure.Http -> when (f.status) {
                    400, 404, 410 -> RepoError.CodeInvalid
                    429 -> RepoError.RateLimited
                    else -> RepoError.Server(f.status)
                }
                is ApiFailure.BadBody -> RepoError.Server(200)
            }
            log("link", mapOf("ok" to false, "error" to f.message))
            RepoResult.Err(e)
        }
    }

    // ── QR 연결 (device flow) ──

    /** QR ①: a new 10-minute request. The poll secret is returned to the caller only (memory; never logged). */
    suspend fun qrStart(): RepoResult<QrStart> = try {
        val r = api.qrStart(deviceName)
        log("qr.start", mapOf("ok" to true, "expiresAt" to r.expiresAt)) // never the id's secret
        RepoResult.Ok(r)
    } catch (f: ApiFailure) {
        log("qr.start", mapOf("ok" to false, "error" to f.message))
        RepoResult.Err(linkError(f))
    }

    /** QR ②: pending → keep polling · linked → token stored (like [link]) · expired (410/404/400) → show 새 QR. */
    suspend fun qrPoll(requestId: String, pollSecret: String): RepoResult<QrPoll> = try {
        val r = api.qrPoll(requestId, pollSecret)
        when {
            r.status == "linked" && r.token != null && Api.TOKEN_RE.matches(r.token) ->
                when (val stored = storeNewToken(r.token, "qr.linked")) {
                    is RepoResult.Ok -> RepoResult.Ok(QrPoll.Linked)
                    is RepoResult.Err -> stored
                }
            r.status == "pending" -> RepoResult.Ok(QrPoll.Pending)
            else -> { log("qr.poll", mapOf("ok" to false, "status" to r.status)); RepoResult.Err(RepoError.Server(200)) }
        }
    } catch (f: ApiFailure) {
        if (f is ApiFailure.Http && f.status in setOf(400, 404, 410)) {
            log("qr.poll", mapOf("expired" to true, "status" to f.status))
            RepoResult.Ok(QrPoll.Expired)
        } else {
            log("qr.poll", mapOf("ok" to false, "error" to f.message))
            RepoResult.Err(linkError(f))
        }
    }

    private fun linkError(f: ApiFailure): RepoError = when (f) {
        is ApiFailure.Network -> RepoError.Offline(null)
        is ApiFailure.Http -> if (f.status == 429) RepoError.RateLimited else RepoError.Server(f.status)
        is ApiFailure.BadBody -> RepoError.Server(200)
    }

    /** What the cache holds right now, for the first frame while the network call runs. */
    fun cachedPlans(): Synced<List<PlanSummary>>? = cache.plans()?.let { Synced(it.value, it.syncedAt, fromCache = true) }
    fun cachedPlan(id: String): Synced<Plan>? = cache.plan(id)?.let { Synced(it.value, it.syncedAt, fromCache = true) }

    suspend fun plans(): RepoResult<Synced<List<PlanSummary>>> = read(
        name = "plans",
        fetch = { api.plans(it) },
        store = { v, at -> cache.putPlans(v, at) },
        cached = { cache.plans() },
        onNotFound = { RepoError.Server(404) },
    )

    suspend fun plan(id: String): RepoResult<Synced<Plan>> = read(
        name = "plan",
        fetch = { api.plan(it, id) },
        store = { v, at -> putPlanIfNotOlder(v, at) },
        cached = { cache.plan(id) },
        onNotFound = { cache.removePlan(id); RepoError.PlanNotFound },
    )

    /** What the cache holds for this complex right now (first frame of STOP_DETAIL / list avatars). */
    fun cachedComplex(cid: String): Synced<CarComplex>? =
        complexCache.get(cid)?.let { Synced(it.value, it.syncedAt, fromCache = true) }

    /**
     * `GET /api/car/complex/{cid}` — facts for the car complex detail (A7) + every REEVL metric (`detail`). Cache on network/server
     * failure. Server cost (2026-11-10): the numbers change once a day (04:00 KST pipeline), so a copy younger than
     * [COMPLEX_FRESH_MS] that already carries `detail` is answered from the device without a request — DETAIL and COMPARE
     * (which loads up to 3 of these) share it.
     */
    suspend fun complex(cid: String): RepoResult<Synced<CarComplex>> {
        complexCache.get(cid)?.takeIf { it.value.detail != null && clock() - it.syncedAt in 0 until COMPLEX_FRESH_MS }?.let {
            log("complex.fresh", mapOf("ageMin" to (clock() - it.syncedAt) / 60_000))
            return RepoResult.Ok(Synced(it.value, it.syncedAt, fromCache = false))
        }
        return complexFetch(cid)
    }

    private suspend fun complexFetch(cid: String): RepoResult<Synced<CarComplex>> = read(
        name = "complex",
        fetch = { api.complex(it, cid) },
        store = { v, at -> complexCache.put(cid, v, at) },
        cached = { complexCache.get(cid) },
        onNotFound = { complexCache.remove(cid); RepoError.ComplexNotFound },
    )

    suspend fun setVisitStatus(planId: String, stopId: String, visitStatus: String): RepoResult<Plan> =
        write("visitStatus", planId) { api.patch(it, planId, PlanPatch(stops = listOf(StopStatusChange(stopId, visitStatus)))) }

    suspend fun reorder(planId: String, order: List<String>): RepoResult<Plan> =
        write("order", planId) { api.patch(it, planId, PlanPatch(order = order)) }

    /** v7 현장에서 추가: insert a complex (`cid`) or a verified office (`officeId`) after [insertAfterStopId]. */
    suspend fun addStop(planId: String, req: AddStopRequest): RepoResult<Plan> =
        write("addStop", planId) { api.addStop(it, planId, req) }

    /** `실행 취소` of an insert (or removing a stop): the server renumbers and returns the plan. */
    suspend fun deleteStop(planId: String, stopId: String): RepoResult<Plan> =
        write("deleteStop", planId) { api.deleteStop(it, planId, stopId) }

    /**
     * v7 NEARBY 단지 around a point. Never cached: a location answer is only true where it was asked (and we do not
     * keep a location history on disk — v7 §3-A step 8). Offline → [RepoError.Offline] with no time.
     */
    suspend fun nearbyComplexes(lat: Double, lng: Double, radiusM: Int = 500, limit: Int = 10): RepoResult<NearbyComplexes> =
        live("nearbyComplexes") { api.nearbyComplexes(it, lat, lng, radiusM, limit) }

    /** v7 NEARBY 중개사무소 (verified demo dataset only). */
    suspend fun nearbyOffices(lat: Double, lng: Double, radiusM: Int = 1000, limit: Int = 10): RepoResult<NearbyOffices> =
        live("nearbyOffices") { api.nearbyOffices(it, lat, lng, radiusM, limit) }

    /** 지나가는 동네. Never cached (a place answer is only true where it was asked). */
    suspend fun area(lat: Double, lng: Double, headingDeg: Double? = null): RepoResult<AreaResponse> =
        live("area") { api.area(it, lat, lng, headingDeg) }

    /** AI 검색 around a point. Never cached (same rule as nearby: a location answer is only true where it was asked). */
    suspend fun search(q: String, lat: Double, lng: Double, radiusM: Int = 1000, limit: Int = 10, headingDeg: Double? = null): RepoResult<SearchResponse> =
        live("search") { api.search(it, SearchRequest(q, lat, lng, radiusM, limit, headingDeg)) }

    /** Saved or not for one cid (exact). Never cached: the answer is only true now. */
    suspend fun favStatus(cid: String): RepoResult<FavStatus> = live("favStatus") { api.favStatus(it, cid) }

    /** What the 저장한 집 cache holds right now (first frame of SAVED). */
    fun cachedFavs(): Synced<FavsResponse>? = favCache.get()?.let { Synced(it.value, it.syncedAt, fromCache = true) }

    /** `GET /api/car/favs` — server first; on network/server failure the last list, marked as cache. No cache → error. */
    suspend fun favs(): RepoResult<Synced<FavsResponse>> = read(
        name = "favs",
        fetch = { api.favs(it) },
        store = { v, at -> favCache.put(v, at) },
        cached = { favCache.get() },
        onNotFound = { RepoError.Server(404) },
    )

    /**
     * `POST /api/car/favs {cid, on}`. The answer is the truth: the caller shows 저장됨 only after Ok. 404 = no such
     * complex, 409 = limit. The cached list is not edited here — the caller refetches it.
     */
    suspend fun setFav(cid: String, on: Boolean): RepoResult<FavToggleResponse> {
        val a = auth() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val r = authed(a) { api.setFav(it, cid, on) }
            if (ended(a)) return RepoResult.Err(RepoError.Stale)
            log("setFav", mapOf("ok" to true, "on" to r.on, "count" to r.count))
            RepoResult.Ok(r)
        } catch (f: ApiFailure) {
            if (ended(a)) return RepoResult.Err(RepoError.Stale)
            val e = if (f is ApiFailure.Http && f.status == 409) RepoError.FavLimit else classify(f, a) { RepoError.ComplexNotFound }
            log("setFav", mapOf("ok" to false, "on" to on, "error" to f.message))
            RepoResult.Err(e)
        }
    }

    // ── 휴대폰 ↔ 차 동기화 (2026-11-10) ──

    /** What this car opened and the server has not taken yet (newest first). */
    fun pendingRecent(): List<RecentItem> = syncStore.pending.get().orEmpty()

    /** Remember that the car opened [item] now (disk, no request — it goes with the next [recent] call). */
    fun noteRecent(item: RecentItem) {
        val cur = pendingRecent()
        val next = kr.co.reevl.ontheway.search.Recent.note(cur, item, clock())
        if (next !== cur) syncStore.pending.put(next)
    }

    fun cachedRecent(): Synced<RecentResponse>? = syncStore.recent.get()?.let { Synced(it.value, it.syncedAt, fromCache = true) }
    fun cachedMemos(): Synced<MemosResponse>? = syncStore.memos.get()?.let { Synced(it.value, it.syncedAt, fromCache = true) }
    fun cachedScreeners(): Synced<ScreenersResponse>? = syncStore.screeners.get()?.let { Synced(it.value, it.syncedAt, fromCache = true) }

    /**
     * 최근 본 단지: pending car views → `PUT /api/car/recent` (the answer is the merged list), else `GET`. Network/server failure →
     * the last list as cache, and the pending views stay on disk for the next try. Views opened while the PUT was in flight stay.
     */
    suspend fun recent(): RepoResult<Synced<RecentResponse>> {
        val sent = kr.co.reevl.ontheway.search.Recent.toPut(pendingRecent())
        return read(
            name = if (sent.isEmpty()) "recent" else "recent.put",
            fetch = { if (sent.isEmpty()) api.recent(it) else api.putRecent(it, sent) },
            store = { v, at ->
                syncStore.recent.put(Stamped(at, v))
                if (sent.isNotEmpty()) syncStore.pending.put(kr.co.reevl.ontheway.search.Recent.afterPush(pendingRecent(), sent))
            },
            cached = { syncStore.recent.get() },
            onNotFound = { RepoError.Server(404) },
        )
    }

    /** `GET /api/car/memos` — read-only in the car. Cache on failure. */
    suspend fun memos(): RepoResult<Synced<MemosResponse>> = read(
        name = "memos", fetch = { api.memos(it) }, store = { v, at -> syncStore.memos.put(Stamped(at, v)) },
        cached = { syncStore.memos.get() }, onNotFound = { RepoError.Server(404) },
    )

    /** `GET /api/car/screeners` — the phone's saved conditions as one-tap car searches. Cache on failure. */
    suspend fun screeners(): RepoResult<Synced<ScreenersResponse>> = read(
        name = "screeners", fetch = { api.screeners(it) }, store = { v, at -> syncStore.screeners.put(Stamped(at, v)) },
        cached = { syncStore.screeners.get() }, onNotFound = { RepoError.Server(404) },
    )

    private suspend fun <T> live(name: String, fetch: suspend (token: String) -> T): RepoResult<T> {
        val a = auth() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val v = authed(a, fetch)
            if (ended(a)) return RepoResult.Err(RepoError.Stale)
            log(name, mapOf("ok" to true)) // never the coordinates
            RepoResult.Ok(v)
        } catch (f: ApiFailure) {
            if (ended(a)) return RepoResult.Err(RepoError.Stale)
            val e = classify(f, a) { RepoError.Server(404) }
            log(name, mapOf("ok" to false, "error" to f.message))
            RepoResult.Err(e)
        }
    }

    /**
     * Car logout: revoke this car's token on the server, then forget token and cache whatever the server said
     * (a shared car must not keep the account). Returns true when the server confirmed (or already had no valid token).
     */
    suspend fun logout(): Boolean {
        val token = tokens.get()
        val serverOk = if (token == null) true else try {
            api.unlink(token)
            true
        } catch (f: ApiFailure) {
            f is ApiFailure.Http && f.status == 401
        }
        tokens.clear()
        cache.clear()
        complexCache.clear()
        favCache.clear()
        syncStore.clear()
        log("logout", mapOf("serverConfirmed" to serverOk))
        return serverOk
    }

    /**
     * D2 re-review Minor 1 (rest of I-2c): never overwrite a cached plan with an older version — a GET issued before a
     * PATCH that returns after it would otherwise put the pre-PATCH visit status back on disk.
     */
    private fun putPlanIfNotOlder(p: Plan, at: Long) {
        val have = cache.plan(p.id)?.value
        if (have != null && have.version > p.version) {
            log("cache", mapOf("kept" to have.version, "dropped" to p.version))
            return
        }
        cache.putPlan(p, at)
    }

    private fun expire() {
        tokens.clear()
        cache.clear()
        complexCache.clear()
        favCache.clear()
        syncStore.clear()
        log("expired", mapOf("tokenCleared" to true))
    }

    private suspend fun <T> read(
        name: String,
        fetch: suspend (token: String) -> T,
        store: (T, Long) -> Unit,
        cached: () -> Stamped<T>?,
        onNotFound: () -> RepoError,
    ): RepoResult<Synced<T>> {
        val a = auth() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val v = authed(a, fetch)
            val at = clock()
            // I-2: a logout/new link happened while the call was in flight → this answer belongs to the old session.
            // Never write it to the (already cleared) cache and never hand it to the screen as fresh.
            if (ended(a)) {
                log(name, mapOf("stale" to true))
                return RepoResult.Err(RepoError.Stale)
            }
            store(v, at)
            log(name, mapOf("fromCache" to false))
            RepoResult.Ok(Synced(v, at, fromCache = false))
        } catch (f: ApiFailure) {
            // I-2: answer for an ended session. Checked BEFORE classify(): an old token's 401 must not expire() the
            // token of the account that linked in the meantime.
            if (ended(a)) return RepoResult.Err(RepoError.Stale)
            val e = classify(f, a, onNotFound)
            if (e == RepoError.Expired || e == RepoError.PlanNotFound || e == RepoError.ComplexNotFound) {
                log(name, mapOf("error" to f.message))
                return RepoResult.Err(e)
            }
            val c = cached()
            val err = if (e is RepoError.Offline) RepoError.Offline(c?.syncedAt) else e
            log(name, mapOf("fromCache" to (c != null), "error" to f.message))
            if (c != null) RepoResult.Ok(Synced(c.value, c.syncedAt, fromCache = true, notice = err)) else RepoResult.Err(err)
        }
    }

    private suspend fun write(name: String, planId: String, call: suspend (token: String) -> Plan): RepoResult<Plan> {
        val a = auth() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val p = authed(a, call)
            if (ended(a)) { // I-2: same rule as read()
                log(name, mapOf("stale" to true))
                return RepoResult.Err(RepoError.Stale)
            }
            putPlanIfNotOlder(p, clock())
            log(name, mapOf("ok" to true, "version" to p.version))
            RepoResult.Ok(p)
        } catch (f: ApiFailure) {
            if (ended(a)) return RepoResult.Err(RepoError.Stale) // I-2 (see read())
            val e = classify(f, a) { cache.removePlan(planId); RepoError.PlanNotFound }
            val out = if (e is RepoError.Offline) RepoError.Offline(cache.plan(planId)?.syncedAt) else e
            log(name, mapOf("ok" to false, "error" to f.message))
            RepoResult.Err(out)
        }
    }

    /** The token one call uses + the session it belongs to. [token] changes only when [authed] retries after a rotation. */
    private class Auth(@Volatile var token: String, val session: Long)

    private fun auth(): Auth? {
        val s = tokens.session()
        val t = tokens.get() ?: return null
        return Auth(t, s)
    }

    /** Logout or a new link happened since [a] was taken (a rotation is NOT an end). */
    private fun ended(a: Auth) = tokens.session() != a.session

    /**
     * Runs [fetch] with the call's token. If it answers 401 but the stored token was rotated meanwhile (another answer
     * carried `x-car-token`), the 401 was for the token we just replaced — retry once with the current one.
     */
    private suspend fun <T> authed(a: Auth, fetch: suspend (token: String) -> T): T {
        try {
            return fetch(a.token)
        } catch (f: ApiFailure.Http) {
            if (f.status != 401 || ended(a)) throw f
            val cur = tokens.get()
            if (cur == null || cur == a.token) throw f
            a.token = cur
            log("auth.retry", mapOf("reason" to "rotated"))
            return fetch(cur)
        }
    }

    /** A 401 is definitive only when our server's auth check said so (`{"error":"unauthorized"}`) for the token we still hold. */
    private fun definitive401(f: ApiFailure.Http, a: Auth) =
        f.status == 401 && f.error == UNAUTHORIZED && !ended(a) && tokens.get() == a.token

    private fun classify(f: ApiFailure, a: Auth, onNotFound: () -> RepoError): RepoError = when (f) {
        is ApiFailure.Network -> RepoError.Offline(null)
        is ApiFailure.Http -> when (f.status) {
            401 -> if (definitive401(f, a)) { expire(); RepoError.Expired } else {
                log("auth.401", mapOf("definitive" to false)) // proxy / portal / rotated meanwhile — keep the token
                RepoError.Server(401)
            }
            404 -> onNotFound()
            429 -> RepoError.RateLimited
            else -> RepoError.Server(f.status)
        }
        is ApiFailure.BadBody -> RepoError.Server(200)
    }

    companion object {
        val CODE = Regex("^[0-9]{6}$")
        /** `lib/visitPlanDb.ts runCar()` body for a missing/revoked/expired car token. */
        const val UNAUTHORIZED = "unauthorized"
    }
}
