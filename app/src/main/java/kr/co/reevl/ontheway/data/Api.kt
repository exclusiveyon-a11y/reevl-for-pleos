package kr.co.reevl.ontheway.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// ── Wire models: the Reevl car routes as built in A2 (reevl frontend/app/api/car/*, 2026-10-26) ──────────

/** One stop as the server returns it. lat/lng are already latitude/longitude (the server converts Reevl `[lon, lat]`). */
@Serializable
data class PlanStop(
    val id: String,
    val position: Int,
    val kind: String,
    val cid: String? = null,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** JSON boolean in responses (the DB keeps 0/1). Missing → not verified. */
    val locationVerified: Boolean = false,
    val phone: String? = null,
    val source: String? = null,
    /** `planned` | `visited` | `skipped` */
    val visitStatus: String,
    val note: String? = null,
    /** v7 (V7-contract §1): `YYYY-MM-DDTHH:mm:00+09:00`, entered on the phone. Absent on older servers → null. */
    val appointmentAt: String? = null,
    /** v7: where the broker meets the client for this stop. Absent → null. Only `verified` points may be routed. */
    val meetingPoint: MeetingPoint? = null,
)

/** V7-contract §1 `placeRef`: `{kind:"complex", cid}` or `{kind:"office", id}`. */
@Serializable
data class PlaceRef(val kind: String, val cid: String? = null, val id: String? = null)

/**
 * V7-contract §1. `verified` is true ONLY when the server filled the point from its own data (placeRef); a point
 * typed on the phone (`source:"client"`) is never routed by the car (CoordsGate).
 */
@Serializable
data class MeetingPoint(
    val label: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val verified: Boolean = false,
    val source: String? = null,
    val placeRef: PlaceRef? = null,
)

@Serializable
data class Plan(
    val id: String,
    val title: String,
    val plannedDate: String? = null,
    val status: String,
    val version: Int,
    val updatedAt: Long,
    val stops: List<PlanStop>,
    /** v7: `basic` | `tour` (고객 투어 모드). Absent on older servers → basic. */
    val mode: String = "basic",
    /** v7: client pseudonym (e.g. `고객 A`), only meaningful in tour mode. */
    val clientLabel: String? = null,
)

@Serializable
data class PlanSummary(
    val id: String,
    val title: String,
    val plannedDate: String? = null,
    val status: String,
    val version: Int,
    val updatedAt: Long,
    val stopCount: Int,
    val mode: String = "basic",
    val clientLabel: String? = null,
)

// ── v7 nearby (V7-contract §2–3). Side (left/right) is computed on the car, never by the server. ────────────

@Serializable data class LatLng(val lat: Double, val lng: Double)

/** Newest 매매 of a nearby complex — same rules as the A7 `recentTrades[0]`. */
@Serializable
data class LatestTrade(
    val date: String,
    val areaM2: Double,
    val pyeong: Int? = null,
    /** 만원 */
    val price: Long,
    val priceUnit: String = "만원",
)

@Serializable
data class NearbyComplex(
    val cid: String,
    val name: String,
    val address: String? = null,
    /** true = the source had no street address; the server built `시도 구 동 지번` (reevl lib/carComplex.ts). */
    val addressApprox: Boolean = false,
    val lat: Double,
    val lng: Double,
    /** Straight line (haversine), integer m — the server's number; the car recomputes from its own fix for the side. */
    val distanceM: Int,
    val households: Int? = null,
    val completionYear: Int? = null,
    /** Exactly the A7 brand object (name, logoUrl|null, avatar). */
    val brand: CarBrand = CarBrand(),
    val latestTrade: LatestTrade? = null,
)

@Serializable
data class NearbyComplexes(
    val center: LatLng? = null,
    val radiusM: Int = 500,
    val items: List<NearbyComplex> = emptyList(),
    val asOf: String? = null,
    val source: String? = null,
    val notice: String? = null,
)

@Serializable
data class NearbyOffice(
    val id: String,
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lng: Double,
    val distanceM: Int,
    val phone: String? = null,
    val registrationNo: String? = null,
    val registrationStatus: String? = null,
    val source: String? = null,
    val checkedAt: String? = null,
)

@Serializable
data class NearbyOffices(
    /** false when the verified dataset is empty → the tab says 개발 예정 (never an invented office). */
    val available: Boolean = false,
    val items: List<NearbyOffice> = emptyList(),
    val source: String? = null,
    val checkedAt: String? = null,
    val notice: String? = null,
)

// ── AI 검색 (`POST /api/car/search`, contract 2026-11-09). The server parses the sentence into conditions and checks
//    them against its own data; relative directions (`status:"car"`) are left to the car (side = car-side only). ──

@Serializable
data class SearchRequest(val q: String, val lat: Double, val lng: Double, val radiusM: Int? = null, val limit: Int? = null,
                         /** Only a heading the car trusts (live, or measured just before stopping); null → the car applies sides. */
                         val headingDeg: Double? = null)

/** One interpreted condition. [status] `applied` | `car` (direction word the car applies) | `unsupported`. */
@Serializable
data class SearchCond(val key: String = "", val label: String = "", val status: String = "unsupported",
                      /** How the server read it (e.g. `공급면적 기준`); shown only in the detail's source line, never on the chip. */
                      val basis: String? = null)

/** Per-item check of one condition: [ok] true / false / null (= could not be checked), with the server's evidence text. */
@Serializable
data class SearchCheck(val label: String = "", val ok: Boolean? = null, val evidence: String? = null)

/** A matched complex: exactly the nearby item fields + its checks + the trade that satisfied a price condition. */
@Serializable
data class SearchItem(
    val cid: String,
    val name: String,
    val address: String? = null,
    val addressApprox: Boolean = false,
    val lat: Double,
    val lng: Double,
    val distanceM: Int,
    val households: Int? = null,
    val completionYear: Int? = null,
    val brand: CarBrand = CarBrand(),
    val latestTrade: LatestTrade? = null,
    val checks: List<SearchCheck> = emptyList(),
    val evidenceTrade: LatestTrade? = null,
    /** `ahead|right|left|behind` when the request carried headingDeg (server-side, same rule as NearbyEngine); else null. */
    val side: String? = null,
) {
    fun asNearby() = NearbyComplex(cid, name, address, addressApprox, lat, lng, distanceM, households, completionYear, brand, latestTrade)
}

@Serializable
data class SearchResponse(
    val q: String = "",
    val conds: List<SearchCond> = emptyList(),
    val radiusM: Int = 1000,
    val scanned: Int = 0,
    val matched: Int = 0,
    /** Candidates left out because a condition could not be judged (null check). scanned = matched + unknown + failed. */
    val unknown: Int = 0,
    /** More candidates in the radius than the server looks at (nearest 200). */
    val poolCapped: Boolean = false,
    /** The distance actually covered (= radiusM unless [poolCapped]). */
    val scannedRadiusM: Int? = null,
    val items: List<SearchItem> = emptyList(),
    /** null | `no_candidates` | `no_match` | `no_condition` */
    val emptyReason: String? = null,
    val asOf: String? = null,
    val source: String? = null,
    val notice: String? = null,
)

// ── 관심 단지 (`GET`/`POST /api/car/favs`, same table as the web's /api/me/favs → phone, web and car stay in sync) ──

/** One saved complex. [name] null = the cid is gone from Reevl's complex data (kept, shown as 정보 없음, can be removed). */
@Serializable
data class FavItem(
    val cid: String,
    val name: String? = null,
    val address: String? = null,
    val addressApprox: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val households: Int? = null,
    val completionYear: Int? = null,
    val brand: CarBrand? = null,
    val latestTrade: LatestTrade? = null,
    /** epoch ms */
    val savedAt: Long = 0,
)

/** Newest first, at most 100. */
@Serializable
data class FavsResponse(val favs: List<FavItem> = emptyList(), val asOf: String? = null, val notice: String? = null,
                        /** All saved complexes of the account (the list carries the newest 100). */
                        val count: Int? = null)

/** `GET /api/car/favs?cid=` — saved or not, exact (not limited by the 100-item list). */
@Serializable data class FavStatus(val cid: String, val on: Boolean)

@Serializable data class FavToggleRequest(val cid: String, val on: Boolean)
@Serializable data class FavToggleResponse(val cid: String, val on: Boolean, val count: Int = 0)

// ── 휴대폰 ↔ 차 동기화 (reevl lib/recentOps.ts, 2026-11-10): 최근 본 단지 · 내 메모 · 저장한 조건 ──

/**
 * One 최근 본 단지 (phone + car merged, newest first, ≤ 30): the same complex facts as a saved complex + when and where it was seen.
 * [source] `web` (the phone / web) | `car`. [approx] true = seen before sync existed: the order is right, the time is not — never shown.
 */
@Serializable
data class RecentItem(
    val cid: String,
    val name: String? = null,
    val address: String? = null,
    val addressApprox: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val households: Int? = null,
    val completionYear: Int? = null,
    val brand: CarBrand? = null,
    val latestTrade: LatestTrade? = null,
    /** epoch ms */
    val viewedAt: Long = 0,
    val source: String = "web",
    val approx: Boolean = false,
)

@Serializable data class RecentResponse(val items: List<RecentItem> = emptyList(), val asOf: String? = null, val notice: String? = null)
@Serializable data class RecentPutItem(val cid: String, val viewedAt: Long)
@Serializable data class RecentPut(val items: List<RecentPutItem>)

/** A note the user wrote on the phone for one complex (read-only in the car). */
@Serializable data class Memo(val cid: String, val text: String, val updatedAt: Long = 0)
@Serializable data class MemosResponse(val memos: List<Memo> = emptyList())

/**
 * A saved web screener as a one-tap car search: [q] = the sentence the car search understands (null = nothing the car can judge),
 * [skipped] = names of the conditions that stay on the phone (지역 · 역세권 …).
 */
@Serializable data class Screener(val id: String, val name: String, val q: String? = null, val skipped: List<String> = emptyList())
@Serializable data class ScreenersResponse(val items: List<Screener> = emptyList())

/** POST /api/car/plans/{id}/stops — `{cid}` or `{officeId}` (+ insertAfterStopId; null/absent = at the end). */
@Serializable
data class AddStopRequest(val cid: String? = null, val officeId: String? = null, val insertAfterStopId: String? = null)

@Serializable data class PlansResponse(val plans: List<PlanSummary>)
@Serializable data class LinkRequest(val code: String, val deviceName: String)
@Serializable data class LinkResponse(val token: String)

// ── QR 연결 (device flow, reevl lib/carAuth.ts 2026-11-10) ──
@Serializable data class QrStartRequest(val deviceName: String)
/** [pollSecret] stays on this car (memory only — never in the QR, never logged, never saved). */
@Serializable data class QrStart(val requestId: String, val pollSecret: String, val match: String, val expiresAt: Long)
@Serializable data class QrPollRequest(val requestId: String, val pollSecret: String)
/** `pending` | `linked` (+ [token], once). Expired / already used arrives as HTTP 410. */
@Serializable data class QrPollResponse(val status: String, val token: String? = null) {
    /** Never print the token. */
    override fun toString(): String = "QrPollResponse($status, token=${token != null})"
}
@Serializable data class StopStatusChange(val id: String, val visitStatus: String)
@Serializable data class PlanPatch(val order: List<String>? = null, val stops: List<StopStatusChange>? = null)
@Serializable data class ErrorBody(val error: String? = null)

/** What went wrong talking to the server, before it is turned into a user sentence ([RepoError]). */
sealed class ApiFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No HTTP answer at all (no network, DNS, refused, timeout). */
    class Network(cause: IOException) : ApiFailure("network: ${cause.javaClass.simpleName}: ${cause.message}", cause)
    /** The server answered with a non-2xx status. `error` is the server's `{error}` text (not shown to users). */
    class Http(val status: Int, val error: String?) : ApiFailure("HTTP $status: $error")
    /** 2xx but the body was not the JSON we expect. */
    class BadBody(cause: Throwable) : ApiFailure("bad body: ${cause.message}", cause)
}

// ── Transport seam (unit tests use MockWebServer through the same OkHttp transport) ─────────────────────────

data class HttpCall(val method: String, val path: String, val jsonBody: String?, val bearer: String?) {
    /** Never print the bearer token (D2 review M-4). */
    override fun toString(): String = "HttpCall($method $path, body=${jsonBody != null}, bearer=${bearer != null})"
}
/** [newToken] = the `x-car-token` response header: the server rotated this car's token (lib/carAuth.ts, every 30 d). */
data class HttpAnswer(val status: Int, val body: String, val newToken: String? = null) {
    override fun toString(): String = "HttpAnswer($status, newToken=${newToken != null})"
}

fun interface HttpTransport {
    /** @throws IOException when there is no HTTP answer. */
    fun execute(call: HttpCall): HttpAnswer
}

class OkHttpTransport(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override fun execute(call: HttpCall): HttpAnswer {
        val body = call.jsonBody?.toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + call.path)
            .method(call.method, body ?: if (call.method == "POST" || call.method == "PATCH" || call.method == "PUT") "".toRequestBody(JSON_TYPE) else null)
            .header("accept", "application/json")
            .apply { call.bearer?.let { header("authorization", "Bearer $it") } }
            .build()
        client.newCall(req).execute().use { res ->
            return HttpAnswer(res.code, res.body?.string().orEmpty(), res.header(TOKEN_HEADER))
        }
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
        const val TOKEN_HEADER = "x-car-token"
    }
}

/**
 * Typed client for the car routes. Every call either returns the decoded body or throws [ApiFailure].
 * Tokens never appear in logs or exception messages.
 */
class Api(private val transport: HttpTransport) {

    /**
     * Called when an answer to a request made with token `used` carries a replacement (`x-car-token`) — on any status,
     * before the status is looked at. [PlanRepository] sets it and swaps the stored token atomically (compare-and-swap).
     */
    @Volatile var onTokenRotated: ((used: String, fresh: String) -> Unit)? = null

    suspend fun link(code: String, deviceName: String): String =
        call<LinkResponse>("POST", "/api/car/link", json.encodeToString(LinkRequest.serializer(), LinkRequest(code, deviceName)), null).token

    /** QR ①: a new link request (10 min). Unauthenticated (IP rate-limited on the server). */
    suspend fun qrStart(deviceName: String): QrStart =
        call("POST", "/api/car/link/start", json.encodeToString(QrStartRequest.serializer(), QrStartRequest(deviceName)), null)

    /** QR ②: has the phone approved? 410 = expired or already used. */
    suspend fun qrPoll(requestId: String, pollSecret: String): QrPollResponse =
        call("POST", "/api/car/link/poll", json.encodeToString(QrPollRequest.serializer(), QrPollRequest(requestId, pollSecret)), null)

    suspend fun unlink(token: String) {
        call<Unit>("DELETE", "/api/car/link", null, token)
    }

    suspend fun plans(token: String): List<PlanSummary> =
        call<PlansResponse>("GET", "/api/car/plans", null, token).plans

    suspend fun plan(token: String, planId: String): Plan =
        call("GET", "/api/car/plans/${seg(planId)}", null, token)

    /**
     * Car complex summary (A7). cid is percent-encoded with [seg] (URLEncoder: also encodes `( ) ! ' ~`, which
     * `encodeURIComponent` leaves as is — the server decodes both the same way).
     */
    suspend fun complex(token: String, cid: String): CarComplex =
        call("GET", "/api/car/complex/${seg(cid)}", null, token)

    suspend fun patch(token: String, planId: String, patch: PlanPatch): Plan =
        call("PATCH", "/api/car/plans/${seg(planId)}", json.encodeToString(PlanPatch.serializer(), patch), token)

    /** V7 §1: insert a verified complex/office stop; the server answers with the whole plan (new order, new version). */
    suspend fun addStop(token: String, planId: String, req: AddStopRequest): Plan =
        call("POST", "/api/car/plans/${seg(planId)}/stops", json.encodeToString(AddStopRequest.serializer(), req), token)

    /** A2: remove one stop (used for `실행 취소` of an insert). Answers with the whole plan. */
    suspend fun deleteStop(token: String, planId: String, stopId: String): Plan =
        call("DELETE", "/api/car/plans/${seg(planId)}/stops/${seg(stopId)}", null, token)

    /**
     * V7 §2. lat/lng go in the query string (API shape fixed by the server). Not "never logged": the REEVL server's application
     * code does not write them, but the platform request logs (Cloudflare Workers Logs) may keep the full request URL, query
     * included, for up to 7 days. The privacy notice says so (reevl.co.kr/privacy#car-location).
     */
    suspend fun nearbyComplexes(token: String, lat: Double, lng: Double, radiusM: Int = 500, limit: Int = 10): NearbyComplexes =
        call("GET", "/api/car/complexes/nearby?${geo(lat, lng)}&radiusM=$radiusM&limit=$limit", null, token)

    /** V7 §3 (verified demo data only; `available:false` when the dataset is empty). */
    suspend fun nearbyOffices(token: String, lat: Double, lng: Double, radiusM: Int = 1000, limit: Int = 10): NearbyOffices =
        call("GET", "/api/car/places/nearby-offices?${geo(lat, lng)}&radiusM=$radiusM&limit=$limit", null, token)

    /** 지나가는 동네 (area-context-car-spec). Same as nearby: coordinates in the query string, so they may appear in the platform's
     *  request logs (Workers Logs, ≤ 7 days) though the server code does not log them. */
    suspend fun area(token: String, lat: Double, lng: Double, headingDeg: Double? = null): AreaResponse =
        call("GET", "/api/car/area?${geo(lat, lng)}" + (headingDeg?.let { String.format(java.util.Locale.US, "&heading=%.0f", ((it % 360) + 360) % 360) } ?: ""), null, token)

    /** AI 검색. Coordinates go in the POST body, so they are not part of a logged request URL. */
    suspend fun search(token: String, req: SearchRequest): SearchResponse =
        call("POST", "/api/car/search", json.encodeToString(SearchRequest.serializer(), req), token)

    suspend fun favs(token: String): FavsResponse = call("GET", "/api/car/favs", null, token)

    suspend fun favStatus(token: String, cid: String): FavStatus = call("GET", "/api/car/favs?cid=${seg(cid)}", null, token)

    suspend fun setFav(token: String, cid: String, on: Boolean): FavToggleResponse =
        call("POST", "/api/car/favs", json.encodeToString(FavToggleRequest.serializer(), FavToggleRequest(cid, on)), token)

    /** 최근 본 단지 (phone + car). */
    suspend fun recent(token: String): RecentResponse = call("GET", "/api/car/recent", null, token)

    /** Complexes opened in this car → merged on the server; the answer is the merged list (same as [recent]). */
    suspend fun putRecent(token: String, items: List<RecentPutItem>): RecentResponse =
        call("PUT", "/api/car/recent", json.encodeToString(RecentPut.serializer(), RecentPut(items)), token)

    suspend fun memos(token: String): MemosResponse = call("GET", "/api/car/memos", null, token)

    suspend fun screeners(token: String): ScreenersResponse = call("GET", "/api/car/screeners", null, token)

    /** 6 decimals ≈ 0.1 m; Locale.US so a decimal comma never reaches the server. */
    private fun geo(lat: Double, lng: Double) = String.format(java.util.Locale.US, "lat=%.6f&lng=%.6f", lat, lng)

    private suspend inline fun <reified T> call(method: String, path: String, body: String?, token: String?): T =
        withContext(Dispatchers.IO) {
            val answer = try {
                transport.execute(HttpCall(method, path, body, token))
            } catch (e: IOException) {
                throw ApiFailure.Network(e)
            }
            val fresh = answer.newToken?.trim()
            if (token != null && fresh != null && TOKEN_RE.matches(fresh) && fresh != token) onTokenRotated?.invoke(token, fresh)
            if (answer.status !in 200..299) {
                val err = runCatching { json.decodeFromString(ErrorBody.serializer(), answer.body).error }.getOrNull()
                throw ApiFailure.Http(answer.status, err)
            }
            if (T::class == Unit::class) return@withContext Unit as T
            try {
                json.decodeFromString<T>(answer.body)
            } catch (e: Exception) {
                throw ApiFailure.BadBody(e)
            }
        }

    private fun seg(id: String) = java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    companion object {
        /** The server's token shape (32 random bytes, base64url, 43 chars). Anything else in the header is ignored. */
        val TOKEN_RE = Regex("^[A-Za-z0-9_-]{43}$")
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true // v7 fields: a `null` where we keep a default (e.g. mode) decodes as that default
            explicitNulls = false // PATCH bodies omit absent fields (the server rejects an empty PATCH)
            encodeDefaults = false
        }
    }
}
