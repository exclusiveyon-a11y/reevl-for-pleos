package kr.co.reevl.ontheway.navi

import kotlinx.coroutines.flow.Flow

/**
 * The only door from the app to the car's navigation.
 *
 * - `pleos` flavor: [PleosNaviAdapter] wraps the Pleos Connect NaviHelper SDK.
 * - `mock` flavor: [MockNaviAdapter] fakes the callbacks. A mock success is NEVER SDK evidence;
 *   its log lines carry `"adapter":"mock"`.
 */
interface NaviAdapter {
    suspend fun connect(): Result<Unit>
    fun requestRoute(stop: Stop): Flow<NaviEvent>
    suspend fun destination(): DestinationSnapshot?
    fun release()

    /**
     * Route summary the navigation reported for the last successful request (Pleos: `onRouteStarted`'s
     * `RouteStartInfo`), or null when none was reported. Display only — never a success signal by itself.
     */
    val lastRoute: RouteSummary? get() = null

    /**
     * Arrival callbacks from the car's navigation (Pleos `onDestinationArrived` / `onWayPointArrived`), whatever route the
     * navigation is on. The app decides whether it was one of its stops (drive/Arrival). Empty for adapters without them.
     */
    val arrivals: Flow<NaviArrival> get() = kotlinx.coroutines.flow.emptyFlow()

    /**
     * The navigation's own idea of where the car is (Pleos `getCurrentLocationInfo()` → `CurrentLocationInfo`: lat/lng,
     * heading, address `수정구 시흥동`). Null = no answer within the timeout or not supported. No server involved.
     */
    suspend fun currentLocation(): NaviLocation? = null
}

data class NaviArrival(val kind: Kind, val name: String?, val address: String?) {
    enum class Kind { DESTINATION, WAYPOINT }
}

data class NaviLocation(val lat: Double, val lng: Double, val headingDeg: Double?, val address: String?, val routing: Boolean)

/** Distance / time as the car's navigation reported them (`RouteStartInfo`). */
data class RouteSummary(
    val name: String?,
    val distanceM: Int?,
    val durationS: Int?,
    /** Device time when the navigation reported this summary (the ETA = this + [durationS]). Null = not reported. */
    val reportedAtMs: Long? = null,
)

sealed interface NaviEvent {
    data object Requested : NaviEvent
    data object RoutingCompleted : NaviEvent
    data class Failed(val code: String, val message: String) : NaviEvent
}

data class Stop(
    val id: String,
    val position: Int,
    val kind: String,
    val name: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val locationVerified: Boolean,
    val visitStatus: String,
)

data class DestinationSnapshot(val lat: Double, val lng: Double, val name: String?)

/** App-level failure codes (not SDK codes). SDK `NaviErrorCode` names are passed through verbatim. */
object NaviFailure {
    const val NO_VERIFIED_COORDS = "NO_VERIFIED_COORDS"
    const val NOT_CONNECTED = "NOT_CONNECTED"
    const val SDK_EXCEPTION = "SDK_EXCEPTION"
    const val TIMEOUT = "TIMEOUT"
    /** Ruling 17: a success signal arrived but getDestinationInfo() did not match the request (or did not answer). */
    const val DEST_MISMATCH = "DEST_MISMATCH"
}

/**
 * Safety gate shared by every adapter: a stop without verified coordinates must never reach
 * the navigation. Returns the failure to emit, or null when the stop may be routed.
 */
object CoordsGate {
    fun check(stop: Stop): NaviEvent.Failed? {
        val lat = stop.lat
        val lng = stop.lng
        return when {
            lat == null || lng == null ->
                NaviEvent.Failed(NaviFailure.NO_VERIFIED_COORDS, "stop ${stop.id} has no coordinates")
            !stop.locationVerified ->
                NaviEvent.Failed(NaviFailure.NO_VERIFIED_COORDS, "stop ${stop.id} coordinates not verified")
            lat !in -90.0..90.0 || lng !in -180.0..180.0 ->
                NaviEvent.Failed(NaviFailure.NO_VERIFIED_COORDS, "stop ${stop.id} coordinates out of range ($lat,$lng)")
            else -> null
        }
    }
}
