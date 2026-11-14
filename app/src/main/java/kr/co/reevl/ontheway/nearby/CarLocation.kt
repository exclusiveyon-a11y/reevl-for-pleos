package kr.co.reevl.ontheway.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock

/**
 * The car's position for NEARBY, from the standard Android location API on AAOS (v7 master §3-A "대안: AAOS의 지원되는
 * 표준 Android 위치 API를 권한·기기 정책 검증 후 사용" — the Pleos FusedLocation SDK is still "준비 중"). On the emulator
 * fixes come from `adb emu geo fix <lon> <lat>`; those are samples, not road evidence.
 *
 * Privacy (§3-A step 8, slide 13 "동의 없는 위치 이력 보관" 제외): this class keeps only the last two fixes and the last heading
 * measured while moving, in memory, while the app is in front. Nothing is written to disk or logged with coordinates. The
 * 10-minute "방금 지나온 곳" trail (driving-context-spec §2.1) lives in drive/Trail — memory only, and 설정 can turn it off.
 */
class CarLocation(
    private val context: Context,
    private val log: (String, Map<String, Any?>) -> Unit = { _, _ -> },
    /** Every accepted fix, for the driving-context engine (drive/DriveContext + its in-memory Trail). Main thread. */
    private val onFix: (NearbyEngine.Fix) -> Unit = {},
) {

    @Volatile private var current: NearbyEngine.Fix? = null
    @Volatile private var previous: NearbyEngine.Fix? = null
    @Volatile private var lastMoving: NearbyEngine.Heading? = null
    @Volatile private var listening = false

    private val manager: LocationManager? get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val listener = LocationListener { loc -> accept(loc) }

    /** Called from the activity's onResume. No permission → nothing (NEARBY falls back to the stop's coordinates). */
    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !hasPermission()) return
        val m = manager ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER).filter { runCatching { m.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { log("location.start", mapOf("providers" to 0)); return }
        providers.forEach { p ->
            runCatching { m.getLastKnownLocation(p) }.getOrNull()?.let { accept(it) }
            runCatching { m.requestLocationUpdates(p, 1_000L, 0f, listener, Looper.getMainLooper()) }
        }
        listening = true
        log("location.start", mapOf("providers" to providers.joinToString(",")))
    }

    fun stop() {
        if (!listening) return
        runCatching { manager?.removeUpdates(listener) }
        listening = false
    }

    private fun accept(loc: Location) {
        val f = NearbyEngine.Fix(
            lat = loc.latitude,
            lng = loc.longitude,
            timeMs = loc.elapsedRealtimeNanos / 1_000_000L,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            bearingDeg = if (loc.hasBearing()) loc.bearing.toDouble() else null,
        )
        val cur = current
        if (cur != null && f.timeMs < cur.timeMs) return // an older fix (e.g. getLastKnownLocation after an update)
        if (cur != null && cur.lat == f.lat && cur.lng == f.lng) { current = f; onFix(f); return } // same point repeated: keep the pair
        previous = cur
        current = f
        NearbyEngine.liveHeading(f, cur)?.let { lastMoving = it }
        onFix(f)
    }

    /** A fix younger than [maxAgeMs] plus the heading to classify with (live, or the last moving one as an estimate). */
    data class Snapshot(val fix: NearbyEngine.Fix, val heading: NearbyEngine.Heading?, val ageMs: Long)

    fun snapshot(maxAgeMs: Long = MAX_FIX_AGE_MS): Snapshot? {
        val f = current ?: return null
        val age = SystemClock.elapsedRealtime() - f.timeMs
        if (age > maxAgeMs) return null
        // NEARBY is parked-only, so a heading is "live" only if the platform itself still reports motion with a bearing;
        // otherwise it is the course measured while the car was last moving, shown as an estimate (§3-A step 2).
        val live = NearbyEngine.liveHeading(f.copy(timeMs = f.timeMs), null)
        val now = f.copy(timeMs = SystemClock.elapsedRealtime())
        return Snapshot(f, NearbyEngine.headingForParked(now, live, lastMoving), age)
    }

    companion object {
        /** A parked car's last fix is still where it is; older than this we do not trust it. */
        const val MAX_FIX_AGE_MS = 10 * 60_000L
    }
}
