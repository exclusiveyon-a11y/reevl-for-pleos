package kr.co.reevl.ontheway.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kr.co.reevl.ontheway.spike.SpikeStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the navigation would answer to getDestinationInfo(): a function of the last requested RouteInfo. */
private typealias DestinationAnswer = (RouteInfo?) -> DestinationInfo?

/** Answers with exactly the requested coordinate (what the emulator did in all spike runs). */
private val sameAsRequest: DestinationAnswer = { r -> r?.let { dest(it.latitude, it.longitude, it.poiName) } }

private fun dest(lat: Double, lng: Double, name: String = "경희궁자이(2단지)") = DestinationInfo(
    latitude = lat,
    longitude = lng,
    name = name,
    address = "종로구 송월동",
    remainDistance = 24000,
    remainTime = 2220,
    tollFare = 0,
    poiId = "",
    poiSubId = null,
)

/** Fake SDK seam: records calls; optionally answers requestRoute with callbacks and getDestinationInfo with a read-back. */
private class FakeNaviSdk(
    private val connect: Boolean = true,
    private val onRoute: (NaviHelperEventListener) -> Unit = {},
    private val routeThrows: Throwable? = null,
    private val destination: DestinationAnswer = sameAsRequest,
    /** Snapshots the navigation pushes on its own right before answering getDestinationInfo() (D2 stop-2 run). */
    private val unsolicitedBeforeAnswer: List<DestinationInfo> = emptyList(),
) : NaviSdk {
    var initializeCalls = 0
    val routeCalls = mutableListOf<RouteInfo>()
    var destinationCalls = 0
    var listener: NaviHelperEventListener? = null

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
        initializeCalls++
        if (connect) onConnected() else onFailed(IllegalStateException("no navi service"))
    }
    override fun addListener(listener: NaviHelperEventListener) { this.listener = listener }
    override fun removeListener(listener: NaviHelperEventListener) { this.listener = null }
    override fun requestRoute(routeInfo: RouteInfo) {
        routeThrows?.let { throw it }
        routeCalls += routeInfo
        listener?.let(onRoute)
    }
    override fun cancelRoute() = Unit
    override fun getDestinationInfo() {
        destinationCalls++
        unsolicitedBeforeAnswer.forEach { d -> listener?.onDestinationInfo(d) }
        destination(routeCalls.lastOrNull())?.let { d -> listener?.onDestinationInfo(d) }
    }
    override fun getCurrentLocationInfo() = Unit
    override fun getRouteStateInfo() = Unit
    override fun release() = Unit
}

class PleosNaviAdapterTest {

    private val lines = mutableListOf<String>()
    private val log = NaviLog(adapter = "pleos", clock = { 0L }, sink = { _, l -> lines += l })

    private fun stop(lat: Double?, lng: Double?, verified: Boolean = true) = Stop(
        id = "11110|경희궁자이(2단지)|홍파동|199", position = 1, kind = "complex",
        name = "경희궁자이(2단지)", address = "서울특별시 종로구 홍파동 199",
        lat = lat, lng = lng, locationVerified = verified, visitStatus = "planned",
    )

    @Test fun toRouteInfo_putsLatIntoLatitude_andLngIntoLongitude() {
        val adapter = PleosNaviAdapter(FakeNaviSdk(), log)
        val info = adapter.toRouteInfo(stop(lat = 37.569205, lng = 126.964152))
        assertEquals(37.569205, info.latitude, 0.0)
        assertEquals(126.964152, info.longitude, 0.0)
        assertNull(info.displayLatitude)
        assertNull(info.displayLongitude)
        assertEquals("경희궁자이(2단지)", info.poiName)
        assertEquals("서울특별시 종로구 홍파동 199", info.address)
        assertEquals("0", info.poiSubId)
        assertEquals(PleosNaviAdapter.POI_ID_NONE, info.poiId)
    }

    @Test fun toRouteInfo_spikeStop_matchesReevlPairSwapped() {
        val info = PleosNaviAdapter(FakeNaviSdk(), log).toRouteInfo(SpikeStops.FIRST)
        assertEquals(37.569205, info.latitude, 0.0) // Reevl pair [126.964152, 37.569205] is [lon, lat]
        assertEquals(126.964152, info.longitude, 0.0)
    }

    @Test fun unverifiedCoords_failNoVerifiedCoords_andSdkIsNotCalled() = runTest {
        val sdk = FakeNaviSdk()
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.5, 126.9, verified = false)).toList()
        assertEquals(listOf<NaviEvent>(NaviEvent.Failed(NaviFailure.NO_VERIFIED_COORDS, "stop 11110|경희궁자이(2단지)|홍파동|199 coordinates not verified")), events)
        assertEquals(0, sdk.routeCalls.size)
        assertEquals(0, sdk.initializeCalls)
    }

    @Test fun nullCoords_failNoVerifiedCoords_andSdkIsNotCalled() = runTest {
        val sdk = FakeNaviSdk()
        val adapter = PleosNaviAdapter(sdk, log)
        val a = adapter.requestRoute(stop(null, 126.9)).toList()
        val b = adapter.requestRoute(stop(37.5, null)).toList()
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, (a.single() as NaviEvent.Failed).code)
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, (b.single() as NaviEvent.Failed).code)
        assertEquals(0, sdk.routeCalls.size)
        assertEquals(0, sdk.initializeCalls)
        assertTrue(lines.all { it.contains("\"adapter\":\"pleos\"") && it.contains("\"code\":\"NO_VERIFIED_COORDS\"") })
    }

    @Test fun routingCompletedCallback_withMatchingReadBack_mapsToRoutingCompleted() = runTest {
        val sdk = FakeNaviSdk(onRoute = { it.onRoutingCompleted() })
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.569205, 126.964152)).toList()
        assertEquals(listOf(NaviEvent.Requested, NaviEvent.RoutingCompleted), events)
        assertEquals(1, sdk.routeCalls.size)
        assertEquals(37.569205, sdk.routeCalls.single().latitude, 0.0)
        assertEquals(1, sdk.destinationCalls)
        assertTrue(lines.any { it.contains("\"ev\":\"Connected\"") })
        assertTrue(lines.any { it.contains("\"ev\":\"RoutingCompleted\"") && it.contains("\"signal\":\"onRoutingCompleted\",\"verified\":true") })
    }

    @Test fun onError_mapsToFailedWithSdkCode() = runTest {
        val sdk = FakeNaviSdk(onRoute = { it.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE) })
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.569205, 126.964152)).toList()
        assertEquals(NaviEvent.Requested, events[0])
        assertEquals("CAUSE_SEARCH_ROUTE_OVER_DISTANCE", (events[1] as NaviEvent.Failed).code)
    }

    @Test fun noCallback_timesOut() = runTest {
        val events = PleosNaviAdapter(FakeNaviSdk(), log, routeTimeoutMs = 1_000).requestRoute(stop(37.5, 126.9)).toList()
        assertEquals(NaviFailure.TIMEOUT, (events.last() as NaviEvent.Failed).code)
    }

    @Test fun defaultRouteTimeout_is60s() = runTest {
        val t0 = testScheduler.currentTime
        val events = PleosNaviAdapter(FakeNaviSdk(), log).requestRoute(stop(37.5, 126.9)).toList()
        assertEquals(NaviFailure.TIMEOUT, (events.last() as NaviEvent.Failed).code)
        assertEquals(60_000L, testScheduler.currentTime - t0)
    }

    /** What the Pleos Connect Emulator 2.1.0 (Navi service 3.0.4) actually sent on 2026-10-26. */
    private val emulatorSequence: (NaviHelperEventListener) -> Unit = {
        it.onRouteStarted(RouteStartInfo(2220, 24000, 700, "경희궁자이(2단지)", 74299))
        @Suppress("DEPRECATION") it.onRouteEnded()
    }

    @Test fun emulatorSequence_withMatchingReadBack_isRoutingCompleted_signalOnRouteStarted() = runTest {
        val sdk = FakeNaviSdk(onRoute = emulatorSequence)
        val adapter = PleosNaviAdapter(sdk, log)
        val events = adapter.requestRoute(stop(37.569205, 126.964152)).toList()
        assertEquals(listOf(NaviEvent.Requested, NaviEvent.RoutingCompleted), events)
        assertEquals("read back once, after the first signal only", 1, sdk.destinationCalls)
        val done = lines.single { it.contains("\"ev\":\"RoutingCompleted\"") }
        assertTrue(done, done.contains("\"signal\":\"onRouteStarted\",\"verified\":true"))
        assertTrue(lines.any { it.contains("\"ev\":\"Destination\"") && it.contains("\"lat\":37.569205,\"lng\":126.964152") })
        assertEquals(RouteSummary(name = "경희궁자이(2단지)", distanceM = 24000, durationS = 2220), adapter.lastRoute!!.copy(reportedAtMs = null))
        // v7: the report time is kept so the car can say `도착 예정 HH:MM` (= report time + duration, eta_source navi_verified)
        assertTrue("report time recorded", adapter.lastRoute!!.reportedAtMs != null)
    }

    @Test fun emulatorSequence_withMismatchingReadBack_isDestMismatch_notRoutingCompleted() = runTest {
        // e.g. the navigation still holds the previous stop, or another app changed it.
        val sdk = FakeNaviSdk(onRoute = emulatorSequence, destination = { dest(37.570000, 126.964152) })
        val adapter = PleosNaviAdapter(sdk, log)
        val events = adapter.requestRoute(stop(37.569205, 126.964152)).toList()
        val f = events.last() as NaviEvent.Failed
        assertEquals(NaviFailure.DEST_MISMATCH, f.code)
        assertTrue(events.none { it == NaviEvent.RoutingCompleted })
        assertTrue(lines.none { it.contains("\"ev\":\"RoutingCompleted\"") })
        assertTrue(lines.any { it.contains("\"ev\":\"Failed\"") && it.contains("\"signal\":\"onRouteStarted\",\"verified\":false") && it.contains("DEST_MISMATCH") })
        assertNull(adapter.lastRoute)
    }

    // M-2 (D2 review): stop-2 sequence on the emulator — route replacement sends onRouteCancelled → FREE_DRIVE →
    // an unsolicited DestinationInfo(0,0,"") before the real read-back. The empty snapshot must be skipped.
    @Test fun unsolicitedEmptyDestination_isSkipped_thenRealReadBackVerifies() = runTest {
        val stop2Sequence: (NaviHelperEventListener) -> Unit = {
            it.onRouteCancelled()
            it.onRouteStarted(RouteStartInfo(1980, 24000, 700, "경희궁자이(3단지)", 74280))
        }
        val sdk = FakeNaviSdk(onRoute = stop2Sequence, unsolicitedBeforeAnswer = listOf(dest(0.0, 0.0, name = "")))
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.568855, 126.965171)).toList()
        assertEquals(listOf(NaviEvent.Requested, NaviEvent.RoutingCompleted), events)
        assertTrue(lines.any { it.contains("skipped empty snapshot") })
        assertTrue(lines.any { it.contains("\"signal\":\"onRouteStarted\",\"verified\":true") })
    }

    @Test fun onlyEmptyDestination_withinWindow_isStillDestMismatch() = runTest {
        val sdk = FakeNaviSdk(onRoute = emulatorSequence, destination = { null }, unsolicitedBeforeAnswer = listOf(dest(0.0, 0.0, name = "")))
        val events = PleosNaviAdapter(sdk, log, infoTimeoutMs = 500).requestRoute(stop(37.569205, 126.964152)).toList()
        assertEquals(NaviFailure.DEST_MISMATCH, (events.last() as NaviEvent.Failed).code)
        assertTrue(events.none { it == NaviEvent.RoutingCompleted })
    }

    @Test fun legacyOnRouteEndedAlone_isASignal_too() = runTest {
        val sdk = FakeNaviSdk(onRoute = { @Suppress("DEPRECATION") it.onRouteEnded() })
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.569205, 126.964152)).toList()
        assertEquals(listOf(NaviEvent.Requested, NaviEvent.RoutingCompleted), events)
        assertTrue(lines.any { it.contains("\"signal\":\"onRouteEnded\",\"verified\":true") })
    }

    @Test fun readBackWithinTolerance_passes_justOutside_fails() = runTest {
        val near = PleosNaviAdapter(FakeNaviSdk(onRoute = emulatorSequence, destination = { dest(37.569205 + 0.000009, 126.964152 - 0.000009) }), log)
        assertEquals(NaviEvent.RoutingCompleted, near.requestRoute(stop(37.569205, 126.964152)).toList().last())
        val far = PleosNaviAdapter(FakeNaviSdk(onRoute = emulatorSequence, destination = { dest(37.569205, 126.964152 + 0.00002) }), log)
        assertEquals(NaviFailure.DEST_MISMATCH, (far.requestRoute(stop(37.569205, 126.964152)).toList().last() as NaviEvent.Failed).code)
    }

    @Test fun signalWithoutReadBackAnswer_isDestMismatch() = runTest {
        val sdk = FakeNaviSdk(onRoute = emulatorSequence, destination = { null })
        val events = PleosNaviAdapter(sdk, log, infoTimeoutMs = 500).requestRoute(stop(37.569205, 126.964152)).toList()
        val f = events.last() as NaviEvent.Failed
        assertEquals(NaviFailure.DEST_MISMATCH, f.code)
        assertTrue(f.message, f.message.contains("read-back none"))
    }

    @Test fun onlyNoisyCallbacks_timeOut_andAreListed() = runTest {
        val sdk = FakeNaviSdk(onRoute = { it.onCurrentRoadSpeedLimit(0) })
        val events = PleosNaviAdapter(sdk, log, routeTimeoutMs = 1_000).requestRoute(stop(37.569205, 126.964152)).toList()
        val f = events.last() as NaviEvent.Failed
        assertEquals(NaviFailure.TIMEOUT, f.code)
        assertTrue(f.message, f.message.contains("observed=[onCurrentRoadSpeedLimit]"))
        assertEquals(0, sdk.destinationCalls)
    }

    @Test fun sdkThrows_mapsToSdkException() = runTest {
        val sdk = FakeNaviSdk(routeThrows = SecurityException("Permission [pleos.car.permission.NAVI_ROUTE_SEARCH] is required"))
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.5, 126.9)).toList()
        val f = events.single() as NaviEvent.Failed
        assertEquals(NaviFailure.SDK_EXCEPTION, f.code)
        assertTrue(f.message.contains("NAVI_ROUTE_SEARCH"))
    }

    @Test fun connectFailure_blocksRoute() = runTest {
        val sdk = FakeNaviSdk(connect = false)
        val events = PleosNaviAdapter(sdk, log).requestRoute(stop(37.5, 126.9)).toList()
        assertEquals(NaviFailure.NOT_CONNECTED, (events.single() as NaviEvent.Failed).code)
        assertEquals(0, sdk.routeCalls.size)
        assertTrue(lines.any { it.contains("\"ev\":\"ConnectFailed\"") })
    }
}
