package kr.co.reevl.ontheway.nearby

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Left/right candidates around the car — v7 master §3-A "로직 의사코드", ported 1:1 (pure, JVM-tested).
 *
 *   heading  = location bearing, only when speed ≥ [MIN_HEADING_SPEED] (else null)
 *   relative = normalizeAngle(bearing(car → place) − heading)
 *   side     = unknown if heading == null or accuracy > [MAX_ACCURACY_M]
 *              ahead −35..+35 · right (+35, +145] · left [−145, −35) · behind = the rest
 *
 * A side is a **candidate** ("오른쪽 후보"), never "that building" (§3-A 판정의 정직성): the complex coordinate is a
 * representative point, the heading is course over ground, and GPS can be off. Distances are straight lines.
 */
object NearbyEngine {
    /** m/s (≈ 5.4 km/h). Below it the course over ground is noise (§3-A: 저속·정차에서는 좌우 오인 가능). Initial value. */
    const val MIN_HEADING_SPEED = 1.5
    /** m. A fix less accurate than this gives no side (§3-A: GPS 정확도 저하 → unknown). Initial value. */
    const val MAX_ACCURACY_M = 50.0
    /** Course over ground needs the car to have moved at least this far between the two fixes. */
    const val MIN_COURSE_DISTANCE_M = 5.0
    /** A heading measured while moving is reused after stopping only this long … */
    const val PARKED_HEADING_MAX_AGE_MS = 10 * 60_000L
    /** … and only while the car stayed within this distance of where it was measured. */
    const val PARKED_HEADING_MAX_DRIFT_M = 60.0

    const val AHEAD_DEG = 35.0
    const val SIDE_DEG = 145.0

    /** Mean Earth radius (IUGG), m. */
    private const val R = 6_371_008.8

    enum class Side { AHEAD, RIGHT, LEFT, BEHIND, UNKNOWN }

    /** Straight-line distance in metres (haversine). */
    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing from 1 to 2, degrees clockwise from true north, in [0, 360). */
    fun initialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    /** Any angle → (−180, +180]. 0/360 wrap and ±180 handled here (−180 becomes +180 = behind). */
    fun normalizeAngle(deg: Double): Double {
        var a = deg % 360.0
        if (a > 180.0) a -= 360.0
        if (a <= -180.0) a += 360.0
        return a
    }

    /** §3-A buckets on a relative angle that is already normalised. */
    fun sideOf(relative: Double): Side = when {
        relative >= -AHEAD_DEG && relative <= AHEAD_DEG -> Side.AHEAD
        relative > AHEAD_DEG && relative <= SIDE_DEG -> Side.RIGHT
        relative >= -SIDE_DEG && relative < -AHEAD_DEG -> Side.LEFT
        else -> Side.BEHIND
    }

    /** One location fix as the platform reported it. [bearingDeg] null = the platform gave no bearing. */
    data class Fix(
        val lat: Double,
        val lng: Double,
        val timeMs: Long,
        val accuracyM: Double? = null,
        val speedMps: Double? = null,
        val bearingDeg: Double? = null,
    )

    enum class HeadingSource { PLATFORM_BEARING, COURSE_OVER_GROUND, LAST_MOVING }

    /** [estimated] = measured earlier while moving, reused now that the car is (nearly) stopped (§3-A step 2: `방향 추정`). */
    data class Heading(val deg: Double, val source: HeadingSource, val atMs: Long, val lat: Double, val lng: Double) {
        val estimated: Boolean get() = source == HeadingSource.LAST_MOVING
    }

    /**
     * The heading right now, from the last two fixes: the platform bearing when it reports one and the car moves at
     * ≥ [MIN_HEADING_SPEED]; else the course over ground between [previous] and [current] when the implied speed is
     * ≥ [MIN_HEADING_SPEED] over ≥ [MIN_COURSE_DISTANCE_M]. Otherwise null (pseudocode: `takeIf { speed ≥ MIN }`).
     */
    fun liveHeading(current: Fix, previous: Fix?): Heading? {
        val speed = current.speedMps
        val b = current.bearingDeg
        if (b != null && speed != null && speed >= MIN_HEADING_SPEED) {
            return Heading((b % 360.0 + 360.0) % 360.0, HeadingSource.PLATFORM_BEARING, current.timeMs, current.lat, current.lng)
        }
        if (previous == null) return null
        val dt = (current.timeMs - previous.timeMs) / 1000.0
        if (dt <= 0.0) return null
        val d = haversine(previous.lat, previous.lng, current.lat, current.lng)
        if (d < MIN_COURSE_DISTANCE_M || d / dt < MIN_HEADING_SPEED) return null
        return Heading(initialBearing(previous.lat, previous.lng, current.lat, current.lng), HeadingSource.COURSE_OVER_GROUND,
            current.timeMs, current.lat, current.lng)
    }

    /**
     * For the parked NEARBY screen: the live heading if there is one, else the last heading measured while moving —
     * marked [Heading.estimated] — when it is recent and the car has not moved away from where it was measured.
     */
    fun headingForParked(current: Fix, live: Heading?, lastMoving: Heading?): Heading? {
        if (live != null) return live
        val h = lastMoving ?: return null
        if (current.timeMs - h.atMs > PARKED_HEADING_MAX_AGE_MS) return null
        if (haversine(h.lat, h.lng, current.lat, current.lng) > PARKED_HEADING_MAX_DRIFT_M) return null
        return h.copy(source = HeadingSource.LAST_MOVING)
    }

    data class Place(val id: String, val lat: Double, val lng: Double)

    data class Candidate(val id: String, val side: Side, val distanceM: Double, val relativeDeg: Double?, val accuracyM: Double?)

    /**
     * The §3-A pseudocode. A fix without a reported accuracy counts as unreliable (no side). Result keeps the input
     * order (the server sorts by distance).
     */
    fun classify(car: Fix, heading: Double?, places: List<Place>): List<Candidate> = places.map { p ->
        val distance = haversine(car.lat, car.lng, p.lat, p.lng)
        val bearing = initialBearing(car.lat, car.lng, p.lat, p.lng)
        val acc = car.accuracyM
        val relative = if (heading == null) null else normalizeAngle(bearing - heading)
        val side = if (relative == null || acc == null || acc > MAX_ACCURACY_M) Side.UNKNOWN else sideOf(relative)
        Candidate(p.id, side, distance, relative, acc)
    }

    /** `오른쪽 후보` / `왼쪽 후보` / `앞쪽 후보` / `뒤쪽 후보` / null (no side → no label, never a guess). */
    fun sideLabel(side: Side): String? = when (side) {
        Side.RIGHT -> "오른쪽 후보"
        Side.LEFT -> "왼쪽 후보"
        Side.AHEAD -> "앞쪽 후보"
        Side.BEHIND -> "뒤쪽 후보"
        Side.UNKNOWN -> null
    }

    /**
     * `직선 약 140m` (10 m steps under 1 km), `직선 약 1.2km` — straight line, never a driving time (v7 addendum).
     * Under 50 m → `직선 50m 이내` (two real offices sit in the complex's own shopping building and come back as 0 m;
     * never `직선 약 0m`).
     */
    fun straightLine(distanceM: Double): String {
        if (distanceM < 50.0) return "직선 50m 이내"
        val m = Math.round(distanceM / 10.0) * 10
        if (m < 1000) return "직선 약 ${m}m"
        val km = Math.round(distanceM / 100.0) / 10.0
        return "직선 약 ${if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()}km"
    }
}
