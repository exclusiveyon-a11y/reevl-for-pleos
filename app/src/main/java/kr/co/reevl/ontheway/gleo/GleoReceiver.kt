package kr.co.reevl.ontheway.gleo

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kr.co.reevl.ontheway.AppLog
import kr.co.reevl.ontheway.BuildConfig
import kr.co.reevl.ontheway.data.AddStopRequest
import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.DiskComplexCache
import kr.co.reevl.ontheway.data.DiskFavCache
import kr.co.reevl.ontheway.data.DiskPlanCache
import kr.co.reevl.ontheway.data.KeystoreTokenStore
import kr.co.reevl.ontheway.data.OkHttpTransport
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanRepository
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.plan.PlanLogic

/**
 * Gleo Action receiver (schema `component: "service"` → Gleo calls `sendBroadcast` with our package). No UI: the request
 * may arrive while driving, when this Lifestyle app must not draw. Answers one Callback broadcast within 6 s (Gleo: 7 s).
 */
class GleoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val function = intent.action ?: return
        if (function !in GleoProtocol.FUNCTIONS) return
        val app = context.applicationContext
        val log = AppLog(File(app.filesDir, "otw-app.log"))
        // Caller check, second layer (the manifest already requires Gleo's signature permission in release builds):
        // Android 14 tells us the sender only if it opted in to share its identity; a known sender that is not Gleo is refused.
        val sender = if (Build.VERSION.SDK_INT >= 34) runCatching { sentFromPackage }.getOrNull() else null
        if (!GleoProtocol.senderAllowed(sender)) {
            log.event("gleo.rejected", mapOf("function" to function, "sender" to sender))
            return
        }
        val extras = intent.extras?.keySet()?.associateWith { k -> runCatching { intent.extras?.getString(k) }.getOrNull() }.orEmpty()
        val params = GleoProtocol.parseParams(intent.getStringExtra(GleoProtocol.ACTION_PARAM), extras)
        val started = System.currentTimeMillis()
        log.event("gleo.action", mapOf("function" to function, "params" to params.keys.sorted().joinToString(","))) // names only, no values
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val cb = try { GleoActions(AndroidGleoBackend(app)).handle(function, params) }
                catch (t: Throwable) { GleoCallback(function, GleoStatus.Fail, "An unexpected internal error occurred.") }
            send(app, cb)
            log.event("gleo.callback", mapOf("function" to function, "status" to cb.status.wire, "rows" to cb.data.size,
                "latencyMs" to (System.currentTimeMillis() - started)))
            pending.finish()
        }
    }

    companion object {
        /**
         * Implicit broadcast, exactly as the guide's sample (sdk-docs-2026-10-19 …app-integration-guide_guide_examples.txt
         * `sendGleoBroadcast`: no setPackage). The guide does not name the receiving package; `ai.umos.ambientai` is only what
         * the 2.1.0 emulator shows (GleoProtocol.GLEO_PACKAGE), so pinning it could silently break on a car image. The payload
         * carries no token or personal data (names, prices, plan stops the user asked Gleo for).
         */
        fun send(context: Context, cb: GleoCallback) {
            context.sendBroadcast(Intent(GleoProtocol.CALLBACK_ACTION).apply {
                putExtra("result", cb.status.wire)
                putExtra("packageName", context.packageName)
                putExtra("response", cb.toJson())
            })
        }
    }
}

@Serializable private data class StoredRef(val cid: String, val name: String)

/** The app's repositories, opened the same way as AppViewModel (same token, same disk caches). */
private class AndroidGleoBackend(private val app: Context) : GleoBackend {
    private val repo = PlanRepository(
        api = Api(OkHttpTransport(BuildConfig.BASE_URL)),
        tokens = KeystoreTokenStore(app.noBackupFilesDir),
        cache = DiskPlanCache(File(app.noBackupFilesDir, "plan-cache")),
        complexCache = DiskComplexCache(File(app.noBackupFilesDir, "complex-cache")),
        favCache = DiskFavCache(File(app.noBackupFilesDir, "fav-cache")),
        deviceName = "REEVL · ${Build.MODEL}",
    )
    private val lastFile = File(app.noBackupFilesDir, "gleo-last.json")
    private val ser = ListSerializer(StoredRef.serializer())

    @SuppressLint("MissingPermission")
    override fun position(): GleoPosition? {
        if (app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val m = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER).mapNotNull { runCatching { m.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos } ?: return null
        if (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos > 10 * 60_000_000_000L) return null
        // A heading only from a moving fix with a bearing and a good accuracy (same rule as the screens).
        val heading = if (loc.hasBearing() && loc.hasSpeed() && loc.speed >= 1.5f && loc.hasAccuracy() && loc.accuracy <= 50f) loc.bearing.toDouble() else null
        return GleoPosition(loc.latitude, loc.longitude, heading)
    }

    override suspend fun search(q: String, at: GleoPosition) = repo.search(q, at.lat, at.lng, 1000, 10, at.headingDeg)
    override suspend fun complex(cid: String) = repo.complex(cid)
    override suspend fun area(at: GleoPosition) = kr.co.reevl.ontheway.area.ServerAreaSource(repo).area(at.lat, at.lng, at.headingDeg)

    override suspend fun todayPlan(): RepoResult<Plan?> = when (val r = repo.plans()) {
        is RepoResult.Err -> r
        is RepoResult.Ok -> {
            val first = PlanLogic.sortForToday(r.value.value, java.time.LocalDate.now(kr.co.reevl.ontheway.plan.Schedule.KST)).firstOrNull()
            if (first == null) RepoResult.Ok(null) else when (val p = repo.plan(first.id)) {
                is RepoResult.Err -> p
                is RepoResult.Ok -> RepoResult.Ok(p.value.value)
            }
        }
    }

    override suspend fun addStop(planId: String, cid: String, afterStopId: String?) = repo.addStop(planId, AddStopRequest(cid = cid, insertAfterStopId = afterStopId))
    override suspend fun deleteStop(planId: String, stopId: String) = repo.deleteStop(planId, stopId)
    override suspend fun reorder(planId: String, order: List<String>) = repo.reorder(planId, order)
    override suspend fun setFav(cid: String, on: Boolean) = repo.setFav(cid, on)

    override fun lastResults(): List<GleoRef> = runCatching { Api.json.decodeFromString(ser, lastFile.readText()).map { GleoRef(it.cid, it.name) } }.getOrDefault(emptyList())
    override fun rememberResults(refs: List<GleoRef>) { runCatching { lastFile.writeText(Api.json.encodeToString(ser, refs.take(10).map { StoredRef(it.cid, it.name) })) } }
    private val pendingFile = File(app.noBackupFilesDir, "gleo-pending.json")
    override fun pending(): PendingWrite? = runCatching { Api.json.decodeFromString(PendingWrite.serializer(), pendingFile.readText()) }.getOrNull()
    override fun setPending(p: PendingWrite?) { runCatching { if (p == null) pendingFile.delete() else pendingFile.writeText(Api.json.encodeToString(PendingWrite.serializer(), p)) } }
    override fun now(): Long = System.currentTimeMillis()

    override fun saved(): List<GleoRef> = repo.cachedFavs()?.value?.favs.orEmpty().filter { it.name != null }.map { GleoRef(it.cid, it.name!!) }
}
