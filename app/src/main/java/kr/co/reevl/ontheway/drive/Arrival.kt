package kr.co.reevl.ontheway.drive

import kr.co.reevl.ontheway.nearby.NearbyEngine

/**
 * S5 도착 — driving-context-spec §2.5. Pure (JVM-tested).
 *  1. The car's navigation says arrived (`onDestinationArrived` / `onWayPointArrived`) while it guides to one of our stops
 *     (the [guideStopId] we sent): arrived at once — if we also have a fix it must be within [NAVI_RADIUS_M] of the stop.
 *  2. Fallback without a navigation signal: parked within [GPS_RADIUS_M] of a planned stop for [GPS_DWELL_MS].
 *  Cleared when the stop is no longer planned (완료·건너뜀) or the car is [LEAVE_M]+ away from it.
 */
class Arrival {
    data class Stop(val id: String, val lat: Double?, val lng: Double?, val planned: Boolean)

    var stopId: String? = null
        private set
    /** `navi` | `gps` */
    var via: String? = null
        private set
    private var candidate: String? = null
    private var candidateSince: Long = 0

    /** Navigation arrival callback. Returns the stop id when it set a new arrival. */
    fun onNavi(guideStopId: String?, stops: List<Stop>, fix: NearbyEngine.Fix?): String? {
        val s = stops.firstOrNull { it.id == guideStopId && it.planned } ?: return null
        if (fix != null && s.lat != null && s.lng != null &&
            NearbyEngine.haversine(fix.lat, fix.lng, s.lat, s.lng) > NAVI_RADIUS_M) return null
        if (stopId == s.id) return null
        stopId = s.id; via = "navi"
        return s.id
    }

    /** Called on every tick/fix. [parked] = S1/S4. Returns the stop id when a new arrival was set. */
    fun onTick(parked: Boolean, stops: List<Stop>, fix: NearbyEngine.Fix?, now: Long): String? {
        val cur = stopId
        if (cur != null) {
            val s = stops.firstOrNull { it.id == cur }
            val gone = s == null || !s.planned ||
                (fix != null && s.lat != null && s.lng != null && NearbyEngine.haversine(fix.lat, fix.lng, s.lat, s.lng) >= LEAVE_M)
            if (gone) { stopId = null; via = null }
            return null
        }
        if (!parked || fix == null) { candidate = null; return null }
        val near = stops.filter { it.planned && it.lat != null && it.lng != null }
            .map { it to NearbyEngine.haversine(fix.lat, fix.lng, it.lat!!, it.lng!!) }
            .filter { it.second <= GPS_RADIUS_M }.minByOrNull { it.second }?.first
        if (near == null) { candidate = null; return null }
        if (candidate != near.id) { candidate = near.id; candidateSince = now; return null }
        if (now - candidateSince < GPS_DWELL_MS) return null
        stopId = near.id; via = "gps"; candidate = null
        return near.id
    }

    fun clear() { stopId = null; via = null; candidate = null }

    /**
     * `[되돌리기]` right after `[완료]` while still at the stop: the arrival comes back at once (QA round 4: the row showed
     * `다음 [안내]` for the 30 s GPS dwell although the car had not moved). [onTick] still clears it if the car has left.
     */
    fun restore(id: String, previousVia: String?) { stopId = id; via = previousVia ?: "gps"; candidate = null }

    companion object {
        const val NAVI_RADIUS_M = 200.0
        const val GPS_RADIUS_M = 150.0
        const val GPS_DWELL_MS = 30_000L
        const val LEAVE_M = 300.0
    }
}

/** Stop events processed per hour (spec §2.2: 12; beyond that only a user tap asks the server). Pure. */
class StopBudget(private val max: Int = 12, private val windowMs: Long = 3_600_000L) {
    private val at = ArrayDeque<Long>()
    fun take(now: Long): Boolean {
        while (at.isNotEmpty() && now - at.first() > windowMs) at.removeFirst()
        if (at.size >= max) return false
        at.addLast(now)
        return true
    }
}
