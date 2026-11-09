package kr.co.reevl.ontheway.navi

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake navigation for UI work without an emulator. Emits Requested -> RoutingCompleted after [delayMs].
 * Every log line carries `"adapter":"mock"` — a mock success is NOT an SDK success.
 */
class MockNaviAdapter(
    private val log: NaviLog = NaviLog(adapter = "mock"),
    private val delayMs: Long = 800,
) : NaviAdapter {

    @Volatile private var connected = false
    @Volatile private var lastRouted: Stop? = null

    override suspend fun connect(): Result<Unit> {
        connected = true
        log.event("Connected", msg = "mock adapter (no SDK)")
        return Result.success(Unit)
    }

    override fun requestRoute(stop: Stop): Flow<NaviEvent> = flow {
        CoordsGate.check(stop)?.let { blocked ->
            log.event("Failed", stop = stop.id, lat = stop.lat, lng = stop.lng, code = blocked.code, msg = blocked.message)
            emit(blocked)
            return@flow
        }
        if (!connected) connect()
        log.event("Requested", stop = stop.id, lat = stop.lat, lng = stop.lng, msg = "mock")
        emit(NaviEvent.Requested)
        delay(delayMs)
        lastRouted = stop
        log.event("RoutingCompleted", stop = stop.id, lat = stop.lat, lng = stop.lng, msg = "mock")
        emit(NaviEvent.RoutingCompleted)
    }

    override suspend fun destination(): DestinationSnapshot? {
        val s = lastRouted
        val lat = s?.lat
        val lng = s?.lng
        if (s == null || lat == null || lng == null) {
            log.event("Destination", msg = "mock: none")
            return null
        }
        log.event("Destination", stop = s.id, lat = lat, lng = lng, msg = "mock")
        return DestinationSnapshot(lat = lat, lng = lng, name = s.name)
    }

    override fun release() {
        connected = false
    }
}
