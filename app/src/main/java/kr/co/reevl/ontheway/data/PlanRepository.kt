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
    /** 401: the car token is unknown, revoked or expired (idle 30 d / absolute 90 d). Token + cache are cleared. */
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
    /** M-3 (D2 review): the Keystore could not store the token (key generation/encryption threw). Nothing is saved. */
    data object Storage : RepoError
    /**
     * I-2 (D2 review): the answer belongs to a session that has ended (logout or a new link happened while the call
     * was in flight). Nothing was written to the cache; the screen must drop the result silently.
     */
    data object Stale : RepoError
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
    const val EXPIRED = "차량 연결이 만료됐어요. 휴대폰에서 새 코드를 받아 주세요."
    const val PLAN_NOT_FOUND = "계획을 찾을 수 없어요"
    const val CODE_INVALID = "코드가 만료됐거나 맞지 않아요"
    const val OFFLINE = "인터넷 연결을 확인해 주세요."
    const val RATE_LIMITED = "요청이 많아요. 잠시 후 다시 시도해 주세요."
    const val SERVER = "서버에서 처리하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val NOT_LINKED = "휴대폰에서 받은 6자리 코드로 차량을 연결해 주세요."
    const val COMPLEX_NOT_FOUND = "리블에 이 단지 정보가 없어요"
    const val STORAGE = "이 차에 연결 정보를 저장하지 못했어요. 다시 시도해 주세요."

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
 * server returns (v5 §9-4). 401 anywhere → token and cache are cleared ([RepoError.Expired]). Network failure on a
 * read → the cached copy, marked as cache with its sync time; never presented as fresh.
 */
class PlanRepository(
    private val api: Api,
    private val tokens: TokenStore,
    private val cache: PlanCache,
    private val deviceName: String = "Pleos car",
    /** Car complex summaries (`/api/car/complex/{cid}`, task A7) — same cache rules as plans. */
    private val complexCache: ComplexCache = MemoryComplexCache(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (event: String, fields: Map<String, Any?>) -> Unit = { _, _ -> },
) {
    fun isLinked(): Boolean = tokens.get() != null

    suspend fun link(code: String): RepoResult<Unit> {
        if (!CODE.matches(code)) return RepoResult.Err(RepoError.CodeInvalid)
        return try {
            val token = api.link(code, deviceName)
            try {
                tokens.set(token)
            } catch (e: Exception) {
                // M-3: Keystore failure (KeyStoreException, ProviderException, …) must not crash the app.
                runCatching { tokens.clear() }
                log("link", mapOf("ok" to false, "error" to "token store: ${e.javaClass.simpleName}"))
                return RepoResult.Err(RepoError.Storage)
            }
            cache.clear() // a new account may be behind this code
            complexCache.clear()
            log("link", mapOf("ok" to true))
            RepoResult.Ok(Unit)
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

    /** `GET /api/car/complex/{cid}` — facts for the car complex detail (A7). Cache on network/server failure. */
    suspend fun complex(cid: String): RepoResult<Synced<CarComplex>> = read(
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

    private suspend fun <T> live(name: String, fetch: suspend (token: String) -> T): RepoResult<T> {
        val token = tokens.get() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val v = fetch(token)
            if (tokens.get() != token) return RepoResult.Err(RepoError.Stale)
            log(name, mapOf("ok" to true)) // never the coordinates
            RepoResult.Ok(v)
        } catch (f: ApiFailure) {
            if (tokens.get() != token) return RepoResult.Err(RepoError.Stale)
            val e = classify(f) { RepoError.Server(404) }
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
        log("expired", mapOf("tokenCleared" to true))
    }

    private suspend fun <T> read(
        name: String,
        fetch: suspend (token: String) -> T,
        store: (T, Long) -> Unit,
        cached: () -> Stamped<T>?,
        onNotFound: () -> RepoError,
    ): RepoResult<Synced<T>> {
        val token = tokens.get() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val v = fetch(token)
            val at = clock()
            // I-2: a logout/new link happened while the call was in flight → this answer belongs to the old session.
            // Never write it to the (already cleared) cache and never hand it to the screen as fresh.
            if (tokens.get() != token) {
                log(name, mapOf("stale" to true))
                return RepoResult.Err(RepoError.Stale)
            }
            store(v, at)
            log(name, mapOf("fromCache" to false))
            RepoResult.Ok(Synced(v, at, fromCache = false))
        } catch (f: ApiFailure) {
            // I-2: answer for an ended session. Checked BEFORE classify(): an old token's 401 must not expire() the
            // token of the account that linked in the meantime.
            if (tokens.get() != token) return RepoResult.Err(RepoError.Stale)
            val e = classify(f, onNotFound)
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
        val token = tokens.get() ?: return RepoResult.Err(RepoError.NotLinked)
        return try {
            val p = call(token)
            if (tokens.get() != token) { // I-2: same rule as read()
                log(name, mapOf("stale" to true))
                return RepoResult.Err(RepoError.Stale)
            }
            putPlanIfNotOlder(p, clock())
            log(name, mapOf("ok" to true, "version" to p.version))
            RepoResult.Ok(p)
        } catch (f: ApiFailure) {
            if (tokens.get() != token) return RepoResult.Err(RepoError.Stale) // I-2 (see read())
            val e = classify(f) { cache.removePlan(planId); RepoError.PlanNotFound }
            val out = if (e is RepoError.Offline) RepoError.Offline(cache.plan(planId)?.syncedAt) else e
            log(name, mapOf("ok" to false, "error" to f.message))
            RepoResult.Err(out)
        }
    }

    private fun classify(f: ApiFailure, onNotFound: () -> RepoError): RepoError = when (f) {
        is ApiFailure.Network -> RepoError.Offline(null)
        is ApiFailure.Http -> when (f.status) {
            401 -> { expire(); RepoError.Expired }
            404 -> onNotFound()
            429 -> RepoError.RateLimited
            else -> RepoError.Server(f.status)
        }
        is ApiFailure.BadBody -> RepoError.Server(200)
    }

    companion object {
        val CODE = Regex("^[0-9]{6}$")
    }
}
