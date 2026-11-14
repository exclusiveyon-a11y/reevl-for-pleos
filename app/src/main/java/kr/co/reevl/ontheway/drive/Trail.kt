package kr.co.reevl.ontheway.drive

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kr.co.reevl.ontheway.nearby.NearbyEngine

/**
 * The breadcrumb trail — driving-context-spec §2.1. **Memory only**: never written to disk, logged with coordinates or sent to
 * the server. Kept for the last [maxAgeMs] or [maxLengthM], whichever is shorter, at most [maxPoints]. The user can turn it
 * off in 설정 (spec §7-3) — then nothing is recorded and what was there is dropped.
 *
 * Sampling while moving: a point every [stepM] metres, or when the course turns ≥ [turnDeg], or every [everyMs]. Nothing
 * while idling (GPS noise at a light). Fixes worse than [NearbyEngine.MAX_ACCURACY_M] are dropped.
 */
class Trail(
    private val maxAgeMs: Long = 10 * 60_000L,
    private val maxLengthM: Double = 5_000.0,
    private val maxPoints: Int = 400,
    private val stepM: Double = 25.0,
    private val turnDeg: Double = 30.0,
    private val everyMs: Long = 10_000L,
) {
    data class Point(val lat: Double, val lng: Double, val atMs: Long)

    private val pts = ArrayDeque<Point>()
    var enabled: Boolean = true
        set(v) { field = v; if (!v) pts.clear() }

    val points: List<Point> get() = pts.toList()

    fun clear() = pts.clear()

    /** Offer a fix; [moving] = the engine says S2. Returns true when a point was kept. */
    fun offer(f: NearbyEngine.Fix, moving: Boolean, now: Long): Boolean {
        prune(now)
        if (!enabled || !moving) return false
        if (f.accuracyM != null && f.accuracyM > NearbyEngine.MAX_ACCURACY_M) return false
        val last = pts.lastOrNull()
        val keep = when {
            last == null -> true
            NearbyEngine.haversine(last.lat, last.lng, f.lat, f.lng) >= stepM -> true
            now - last.atMs >= everyMs && NearbyEngine.haversine(last.lat, last.lng, f.lat, f.lng) >= 1.0 -> true
            pts.size >= 2 && turned(pts[pts.size - 2], last, f) -> true
            else -> false
        }
        if (keep) { pts.addLast(Point(f.lat, f.lng, now)); prune(now) }
        return keep
    }

    private fun turned(a: Point, b: Point, f: NearbyEngine.Fix): Boolean {
        if (NearbyEngine.haversine(b.lat, b.lng, f.lat, f.lng) < 5.0) return false
        val h1 = NearbyEngine.initialBearing(a.lat, a.lng, b.lat, b.lng)
        val h2 = NearbyEngine.initialBearing(b.lat, b.lng, f.lat, f.lng)
        return abs(NearbyEngine.normalizeAngle(h2 - h1)) >= turnDeg
    }

    fun prune(now: Long) {
        while (pts.isNotEmpty() && now - pts.first().atMs > maxAgeMs) pts.removeFirst()
        while (pts.size > maxPoints) pts.removeFirst()
        // Length cap measured from the newest point backwards.
        var len = 0.0
        for (i in pts.size - 1 downTo 1) {
            len += NearbyEngine.haversine(pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng)
            if (len > maxLengthM) { repeat(i) { pts.removeFirst() }; break } // keep i..end
        }
    }
}

/**
 * "방금 지나온 곳" — driving-context-spec §2.3 P0: each place is projected onto the trail; it was passed if the closest approach
 * is ≤ [maxLateralM] and happened within [maxAgeMs]. Side = which side of the travel direction (cross product sign). A place
 * whose closest point is the trail's very end (= where the car stopped) is "here", not "passed", and is left out. Pure.
 */
object PassedAlong {
    data class Passed(val id: String, val side: NearbyEngine.Side, val passedAtMs: Long, val lateralM: Double)

    const val MAX_LATERAL_M = 150.0
    const val MAX_AGE_MS = 10 * 60_000L
    /** Closer than this to the line the side is not trusted (the complex point may be on either side of the road). */
    const val MIN_SIDE_M = 8.0
    const val MAX_ROWS = 6

    fun compute(trail: List<Trail.Point>, places: List<NearbyEngine.Place>, now: Long,
                maxLateralM: Double = MAX_LATERAL_M, maxAgeMs: Long = MAX_AGE_MS): List<Passed> {
        if (trail.size < 2) return emptyList()
        val lat0 = trail.last().lat
        val k = cos(Math.toRadians(lat0))
        fun xy(lat: Double, lng: Double) = Pair((lng - trail.last().lng) * 111_320.0 * k, (lat - lat0) * 110_574.0)
        val out = mutableListOf<Passed>()
        for (p in places) {
            val (px, py) = xy(p.lat, p.lng)
            var best: Passed? = null
            var bestAtEnd = false
            for (i in 0 until trail.size - 1) {
                val a = trail[i]; val b = trail[i + 1]
                val (ax, ay) = xy(a.lat, a.lng); val (bx, by) = xy(b.lat, b.lng)
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                if (len2 < 1e-6) continue
                val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
                val cx = ax + t * dx; val cy = ay + t * dy
                val d = hypot(px - cx, py - cy)
                if (best == null || d < best.lateralM) {
                    val cross = dx * (py - ay) - dy * (px - ax) // > 0 → left of the direction of travel
                    val side = when {
                        d < MIN_SIDE_M -> NearbyEngine.Side.UNKNOWN
                        cross > 0 -> NearbyEngine.Side.LEFT
                        else -> NearbyEngine.Side.RIGHT
                    }
                    best = Passed(p.id, side, (a.atMs + t * (b.atMs - a.atMs)).toLong(), d)
                    bestAtEnd = i == trail.size - 2 && t >= 1.0
                }
            }
            val b = best ?: continue
            if (bestAtEnd) continue
            if (b.lateralM <= maxLateralM && now - b.passedAtMs <= maxAgeMs) out += b
        }
        return out.sortedByDescending { it.passedAtMs }.take(MAX_ROWS)
    }

    /** `방금` under a minute, else `n분 전`. */
    fun ago(passedAtMs: Long, now: Long): String {
        val m = ((now - passedAtMs).coerceAtLeast(0) / 60_000L).toInt()
        return if (m < 1) "방금" else "${m}분 전"
    }
}
