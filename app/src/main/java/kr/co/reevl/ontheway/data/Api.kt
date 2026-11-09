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

/** POST /api/car/plans/{id}/stops — `{cid}` or `{officeId}` (+ insertAfterStopId; null/absent = at the end). */
@Serializable
data class AddStopRequest(val cid: String? = null, val officeId: String? = null, val insertAfterStopId: String? = null)

@Serializable data class PlansResponse(val plans: List<PlanSummary>)
@Serializable data class LinkRequest(val code: String, val deviceName: String)
@Serializable data class LinkResponse(val token: String)
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
data class HttpAnswer(val status: Int, val body: String)

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
            .method(call.method, body ?: if (call.method == "POST" || call.method == "PATCH") "".toRequestBody(JSON_TYPE) else null)
            .header("accept", "application/json")
            .apply { call.bearer?.let { header("authorization", "Bearer $it") } }
            .build()
        client.newCall(req).execute().use { res ->
            return HttpAnswer(res.code, res.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}

/**
 * Typed client for the car routes. Every call either returns the decoded body or throws [ApiFailure].
 * Tokens never appear in logs or exception messages.
 */
class Api(private val transport: HttpTransport) {

    suspend fun link(code: String, deviceName: String): String =
        call<LinkResponse>("POST", "/api/car/link", json.encodeToString(LinkRequest.serializer(), LinkRequest(code, deviceName)), null).token

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

    /** V7 §2. lat/lng go in the query only (the server never logs them). */
    suspend fun nearbyComplexes(token: String, lat: Double, lng: Double, radiusM: Int = 500, limit: Int = 10): NearbyComplexes =
        call("GET", "/api/car/complexes/nearby?${geo(lat, lng)}&radiusM=$radiusM&limit=$limit", null, token)

    /** V7 §3 (verified demo data only; `available:false` when the dataset is empty). */
    suspend fun nearbyOffices(token: String, lat: Double, lng: Double, radiusM: Int = 1000, limit: Int = 10): NearbyOffices =
        call("GET", "/api/car/places/nearby-offices?${geo(lat, lng)}&radiusM=$radiusM&limit=$limit", null, token)

    /** 6 decimals ≈ 0.1 m; Locale.US so a decimal comma never reaches the server. */
    private fun geo(lat: Double, lng: Double) = String.format(java.util.Locale.US, "lat=%.6f&lng=%.6f", lat, lng)

    private suspend inline fun <reified T> call(method: String, path: String, body: String?, token: String?): T =
        withContext(Dispatchers.IO) {
            val answer = try {
                transport.execute(HttpCall(method, path, body, token))
            } catch (e: IOException) {
                throw ApiFailure.Network(e)
            }
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
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true // v7 fields: a `null` where we keep a default (e.g. mode) decodes as that default
            explicitNulls = false // PATCH bodies omit absent fields (the server rejects an empty PATCH)
            encodeDefaults = false
        }
    }
}
