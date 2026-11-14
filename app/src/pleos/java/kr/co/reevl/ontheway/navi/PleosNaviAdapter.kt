package kr.co.reevl.ontheway.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real-SDK adapter (pleos flavor) over Pleos Connect NaviHelper 3.0.5.
 *
 * initialize(onConnected, onFailed) -> addListener -> requestRoute(RouteInfo) -> callbacks -> NaviEvent.
 * `requestRoute()` returning normally only means "sent". Every step is logged as one JSON line under tag OTW-NAVI.
 *
 * Success semantics (controller Ruling 17, 2026-10-26): the FIRST of `onRoutingCompleted` / `onRouteStarted` /
 * legacy `onRouteEnded` is the signal; the adapter then calls `getDestinationInfo()` and reports
 * [NaviEvent.RoutingCompleted] only when the read-back lat/lng are both within [DEST_TOLERANCE_DEG] of the request
 * (`"signal":"<callback>","verified":true`). A signal with a different or missing read-back is
 * `Failed("DEST_MISMATCH")` (`"verified":false`); no signal and no `onError` within [routeTimeoutMs] is
 * `Failed("TIMEOUT")`. Why: the Pleos Connect Emulator 2.1.0 (Navi service 3.0.4) never sends
 * `onRoutingCompleted` — it sends `onRouteStarted` + `onRouteEnded` (D0+D1 spike, 3 runs).
 */
class PleosNaviAdapter internal constructor(
    private val sdk: NaviSdk,
    private val log: NaviLog,
    private val connectTimeoutMs: Long = 15_000,
    private val routeTimeoutMs: Long = 60_000,
    private val infoTimeoutMs: Long = 5_000,
) : NaviAdapter {

    constructor(context: Context, log: NaviLog) : this(RealNaviSdk(context), log)

    /** Outcome-relevant callbacks, in arrival order: a success signal (callback name) or an SDK error. */
    private sealed interface Raw {
        data class Signal(val callback: String) : Raw
        data class SdkError(val code: String) : Raw
    }

    private val terminal = MutableSharedFlow<Raw>(extraBufferCapacity = 16)
    private val destinationInfos = MutableSharedFlow<DestinationInfo>(extraBufferCapacity = 4)
    private val locationInfos = MutableSharedFlow<CurrentLocationInfo>(extraBufferCapacity = 4)
    private val arrivalFlow = MutableSharedFlow<NaviArrival>(extraBufferCapacity = 8)

    override val arrivals: Flow<NaviArrival> get() = arrivalFlow

    @Volatile private var connected = false
    @Volatile private var listenerAdded = false
    @Volatile private var currentStopId: String? = null
    @Volatile private var routeStart: RouteSummary? = null
    @Volatile private var verifiedRoute: RouteSummary? = null

    override val lastRoute: RouteSummary? get() = verifiedRoute

    private val noisyCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** Callback names seen since the last requestRoute() — reported verbatim when the outcome times out. */
    private val observed: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())

    private fun cb(name: String, msg: String? = null) {
        observed += name
        log.sdk(name, currentStopId, msg)
    }

    /** High-frequency callbacks: log only the first few so the evidence log stays readable. */
    private fun noisy(cb: String, msg: String?) {
        observed += cb
        val n = noisyCounts.getOrPut(cb) { AtomicInteger() }.incrementAndGet()
        if (n <= 3) log.sdk(cb, currentStopId, if (n == 3) "$msg (further $cb lines suppressed)" else msg)
    }

    private fun observedSoFar(): String = synchronized(observed) { observed.joinToString(",") }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            cb("onRouteStarted", routeStartInfo.toString())
            routeStart = RouteSummary(
                name = routeStartInfo.name,
                distanceM = routeStartInfo.distance,
                durationS = routeStartInfo.duration,
                reportedAtMs = System.currentTimeMillis(),
            )
            terminal.tryEmit(Raw.Signal("onRouteStarted"))
        }

        override fun onRoutingCompleted() {
            cb("onRoutingCompleted")
            terminal.tryEmit(Raw.Signal("onRoutingCompleted"))
        }

        @Deprecated("SDK 3.0.5: use onRoutingCompleted")
        override fun onRouteEnded() {
            // Deprecated in 3.0.5, but it is what Navi service 3.0.4 (emulator 2.1.0) sends. A signal only —
            // success still needs the destination read-back (Ruling 17).
            cb("onRouteEnded")
            terminal.tryEmit(Raw.Signal("onRouteEnded"))
        }

        override fun onReRouteEnded() {
            cb("onReRouteEnded")
        }

        override fun onRouteCancelled() {
            cb("onRouteCancelled")
        }

        override fun onDestinationChanged() {
            cb("onDestinationChanged")
        }

        override fun onRouteOptionChanged(routeOption: RouteOption) {
            cb("onRouteOptionChanged", routeOption.name)
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            cb("onWaypointChanged", waypointChangedInfo.toString())
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            cb("onDestinationArrived", destinationArrivedInfo.toString())
            arrivalFlow.tryEmit(NaviArrival(NaviArrival.Kind.DESTINATION, destinationArrivedInfo.name, destinationArrivedInfo.address))
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            cb("onWayPointArrived", waypointArrivedInfo.toString())
            arrivalFlow.tryEmit(NaviArrival(NaviArrival.Kind.WAYPOINT, waypointArrivedInfo.name, waypointArrivedInfo.address))
        }

        override fun onError(errorCode: NaviErrorCode) {
            cb("onError", errorCode.name)
            terminal.tryEmit(Raw.SdkError(errorCode.name))
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            cb("onDestinationInfo", destinationInfo.toString())
            destinationInfos.tryEmit(destinationInfo)
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            cb("onCurrentLocationInfo", currentLocationInfo.toString())
            locationInfos.tryEmit(currentLocationInfo)
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            cb("onRouteStateInfo", routeStateInfo.toString())
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            cb("onNavigationStatus", status.name)
        }

        override fun onTripInfo(tripInfo: TripInfo) = noisy("onTripInfo", tripInfo.toString())
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = noisy("onDrivingInfo", drivingInfo.toString())
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = noisy("onTBTInfo", "size=${tbtInfoList.size}")
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = noisy("onCurrentRoadSpeedLimit", speedLimit.toString())
    }

    override suspend fun connect(): Result<Unit> {
        if (connected) return Result.success(Unit)
        val result = withTimeoutOrNull(connectTimeoutMs) {
            suspendCancellableCoroutine<Result<Unit>> { cont ->
                try {
                    sdk.initialize(
                        onConnected = {
                            if (!listenerAdded) {
                                sdk.addListener(listener)
                                listenerAdded = true
                            }
                            connected = true
                            log.event("Connected", msg = "NaviHelper.initialize onConnected; listener registered")
                            if (cont.isActive) cont.resume(Result.success(Unit))
                        },
                        onFailed = { t ->
                            log.event("ConnectFailed", code = t.javaClass.simpleName, msg = t.message ?: t.toString())
                            if (cont.isActive) cont.resume(Result.failure(t))
                        },
                    )
                } catch (t: Throwable) {
                    log.event("ConnectFailed", code = NaviFailure.SDK_EXCEPTION, msg = "${t.javaClass.simpleName}: ${t.message}")
                    if (cont.isActive) cont.resume(Result.failure(t))
                }
            }
        }
        return result ?: run {
            log.event("ConnectFailed", code = NaviFailure.TIMEOUT, msg = "neither onConnected nor onFailed within ${connectTimeoutMs}ms")
            Result.failure(IllegalStateException("NaviHelper connect timeout"))
        }
    }

    override fun requestRoute(stop: Stop): Flow<NaviEvent> = channelFlow {
        CoordsGate.check(stop)?.let { blocked ->
            log.event("Failed", stop = stop.id, lat = stop.lat, lng = stop.lng, code = blocked.code, msg = blocked.message)
            send(blocked)
            return@channelFlow
        }
        if (!connected && connect().isFailure) {
            val f = NaviEvent.Failed(NaviFailure.NOT_CONNECTED, "NaviHelper not connected; route not sent")
            log.event("Failed", stop = stop.id, lat = stop.lat, lng = stop.lng, code = f.code, msg = f.message)
            send(f)
            return@channelFlow
        }

        val info = toRouteInfo(stop)
        val inbox = Channel<Raw>(Channel.UNLIMITED)
        // Subscribe BEFORE calling the SDK so a fast callback can't be missed.
        val sub = launch(start = CoroutineStart.UNDISPATCHED) { terminal.collect { inbox.send(it) } }
        currentStopId = stop.id
        observed.clear()
        routeStart = null
        verifiedRoute = null
        try {
            sdk.requestRoute(info)
        } catch (t: Throwable) {
            sub.cancel()
            val f = NaviEvent.Failed(NaviFailure.SDK_EXCEPTION, "${t.javaClass.simpleName}: ${t.message}")
            log.event("Failed", stop = stop.id, lat = info.latitude, lng = info.longitude, code = f.code, msg = f.message)
            send(f)
            return@channelFlow
        }
        log.event("Requested", stop = stop.id, lat = info.latitude, lng = info.longitude, msg = info.toString())
        send(NaviEvent.Requested)

        val first = withTimeoutOrNull(routeTimeoutMs) { inbox.receive() }
        sub.cancel()
        val outcome: NaviEvent = when (first) {
            null -> NaviEvent.Failed(
                NaviFailure.TIMEOUT,
                "no onRoutingCompleted/onRouteStarted/onRouteEnded/onError within ${routeTimeoutMs}ms; observed=[${observedSoFar()}]",
            ).also { log.event("Failed", stop = stop.id, lat = info.latitude, lng = info.longitude, code = it.code, msg = it.message) }
            is Raw.SdkError -> NaviEvent.Failed(first.code, "NaviHelperEventListener.onError")
                .also { log.event("Failed", stop = stop.id, lat = info.latitude, lng = info.longitude, code = it.code, msg = it.message) }
            is Raw.Signal -> verifyDestination(stop, info, first.callback)
        }
        send(outcome)
    }

    /** Ruling 17: after the first success signal, read the destination back and compare it with the request. */
    private suspend fun verifyDestination(stop: Stop, info: RouteInfo, signal: String): NaviEvent {
        val d = destination()
        val matches = d != null &&
            abs(d.lat - info.latitude) <= DEST_TOLERANCE_DEG &&
            abs(d.lng - info.longitude) <= DEST_TOLERANCE_DEG
        val readBack = d?.let { "(${it.lat}, ${it.lng}) name=${it.name}" } ?: "none"
        val requested = "(${info.latitude}, ${info.longitude}) name=${info.poiName}"
        return if (matches) {
            verifiedRoute = routeStart ?: RouteSummary(name = d?.name, distanceM = null, durationS = null)
            log.event(
                "RoutingCompleted", stop = stop.id, lat = info.latitude, lng = info.longitude,
                signal = signal, verified = true,
                msg = "destination read-back $readBack matches request $requested (tolerance $DEST_TOLERANCE_DEG)" +
                    (routeStart?.let { "; route distance=${it.distanceM}m duration=${it.durationS}s" } ?: ""),
            )
            NaviEvent.RoutingCompleted
        } else {
            val f = NaviEvent.Failed(
                NaviFailure.DEST_MISMATCH,
                "after $signal: destination read-back $readBack != request $requested (tolerance $DEST_TOLERANCE_DEG)",
            )
            log.event(
                "Failed", stop = stop.id, lat = info.latitude, lng = info.longitude,
                signal = signal, verified = false, code = f.code, msg = f.message,
            )
            f
        }
    }

    override suspend fun destination(): DestinationSnapshot? {
        if (!connected) {
            log.event("Destination", stop = currentStopId, code = NaviFailure.NOT_CONNECTED, msg = "destination() before connect")
            return null
        }
        return coroutineScope {
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                // M-2 (D2 review): while a route is being replaced the navigation sends an unsolicited
                // DestinationInfo(0.0, 0.0, name="") (D2 report, stop 2). That is not an answer to our
                // getDestinationInfo() — skip it and wait for a real snapshot within the same window.
                withTimeoutOrNull(infoTimeoutMs) {
                    destinationInfos.first { d ->
                        val empty = isEmptySnapshot(d)
                        if (empty) log.sdk("onDestinationInfo", currentStopId, "skipped empty snapshot (0,0) during read-back")
                        !empty
                    }
                }
            }
            try {
                sdk.getDestinationInfo()
            } catch (t: Throwable) {
                pending.cancel()
                log.event("Destination", stop = currentStopId, code = NaviFailure.SDK_EXCEPTION, msg = "${t.javaClass.simpleName}: ${t.message}")
                return@coroutineScope null
            }
            val d = pending.await()
            if (d == null) {
                log.event("Destination", stop = currentStopId, code = NaviFailure.TIMEOUT, msg = "no onDestinationInfo within ${infoTimeoutMs}ms")
                null
            } else {
                log.event("Destination", stop = currentStopId, lat = d.latitude, lng = d.longitude, msg = d.toString())
                DestinationSnapshot(lat = d.latitude, lng = d.longitude, name = d.name)
            }
        }
    }

    /**
     * pleos-signals §1.1: `getCurrentLocationInfo()` → `onCurrentLocationInfo` (lat/lng, heading, address `수정구 시흥동`). Connects
     * first if needed (the listener must be registered). Whether it answers without an active route is what the emulator log
     * shows (`routing` = a routeId came back).
     */
    override suspend fun currentLocation(): NaviLocation? {
        if (!connected && connect().isFailure) return null
        return coroutineScope {
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(infoTimeoutMs) { locationInfos.first { abs(it.latitude) > EMPTY_DEG || abs(it.longitude) > EMPTY_DEG } }
            }
            try { sdk.getCurrentLocationInfo() } catch (t: Throwable) {
                pending.cancel()
                log.event("CurrentLocation", code = NaviFailure.SDK_EXCEPTION, msg = "${t.javaClass.simpleName}: ${t.message}")
                return@coroutineScope null
            }
            val c = pending.await()
            if (c == null) { log.event("CurrentLocation", code = NaviFailure.TIMEOUT, msg = "no onCurrentLocationInfo within ${infoTimeoutMs}ms"); null }
            else NaviLocation(c.latitude, c.longitude, c.heading.takeIf { it != 0.0 }, c.address.takeIf { it.isNotBlank() },
                routing = !c.routeId.isNullOrBlank())
        }
    }

    /** (0,0) with no name = "no destination" (route cancelled / being replaced), never a read-back of our request. */
    private fun isEmptySnapshot(d: DestinationInfo): Boolean =
        abs(d.latitude) < EMPTY_DEG && abs(d.longitude) < EMPTY_DEG

    /** Spike diagnostics: ask the Navi service for its state; answers arrive as OTW-NAVI-SDK lines. */
    fun probeInfo() {
        if (!connected) return
        runCatching { sdk.getCurrentLocationInfo() }.onFailure { log.sdk("getCurrentLocationInfo", currentStopId, "throw ${it.javaClass.simpleName}: ${it.message}") }
        runCatching { sdk.getRouteStateInfo() }.onFailure { log.sdk("getRouteStateInfo", currentStopId, "throw ${it.javaClass.simpleName}: ${it.message}") }
    }

    /** Spike-only: clear the active route so the next run starts from "no destination". */
    suspend fun cancelRouteForSpike() {
        if (!connected && connect().isFailure) return
        runCatching { sdk.cancelRoute() }
            .onSuccess { log.sdk("cancelRoute", currentStopId, "called") }
            .onFailure { log.sdk("cancelRoute", currentStopId, "throw ${it.javaClass.simpleName}: ${it.message}") }
    }

    override fun release() {
        runCatching { if (listenerAdded) sdk.removeListener(listener) }
        runCatching { sdk.release() }
        listenerAdded = false
        connected = false
    }

    /**
     * Stop -> SDK RouteInfo. Named arguments on purpose: SDK 3.0.5 changed the constructor order to
     * (latitude, longitude, ...). Requires coordinates; callers go through [CoordsGate] first.
     */
    internal fun toRouteInfo(stop: Stop): RouteInfo {
        val lat = requireNotNull(stop.lat) { "stop ${stop.id}: lat is null" }
        val lng = requireNotNull(stop.lng) { "stop ${stop.id}: lng is null" }
        return RouteInfo(
            latitude = lat,
            longitude = lng,
            displayLongitude = null,
            displayLatitude = null,
            poiName = stop.name,
            poiId = POI_ID_NONE,
            poiSubId = "0",
            address = stop.address,
        )
    }

    companion object {
        /** Ruling 17: read-back lat and lng must each be within this many degrees of the request (~1.1 m). */
        const val DEST_TOLERANCE_DEG = 1e-5

        /** |lat| and |lng| below this = the navigation's "no destination" snapshot (0.0, 0.0) — M-2. */
        const val EMPTY_DEG = 1e-9

        /**
         * We have no Pleos/navigation POI id for a Reevl complex. `poiId` is a non-null String in 3.0.5;
         * empty string = "no POI, route by coordinates". (Open question for the 10/28 briefing.)
         */
        const val POI_ID_NONE = ""
    }
}
