package kr.co.reevl.ontheway.navi

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kr.co.reevl.ontheway.spike.SpikeStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockNaviAdapterTest {

    private val lines = mutableListOf<String>()
    private val log = NaviLog(adapter = "mock", clock = { 0L }, sink = { _, l -> lines += l })

    @Test fun verifiedStop_emitsRequestedThenRoutingCompleted_labelledMock() = runTest {
        val events = MockNaviAdapter(log).requestRoute(SpikeStops.FIRST).toList()
        assertEquals(listOf(NaviEvent.Requested, NaviEvent.RoutingCompleted), events)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.contains("\"adapter\":\"mock\"") })
    }

    @Test fun unverifiedStop_isBlocked() = runTest {
        val s = SpikeStops.FIRST.copy(locationVerified = false)
        val events = MockNaviAdapter(log).requestRoute(s).toList()
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, (events.single() as NaviEvent.Failed).code)
    }

    @Test fun nullCoords_isBlocked() = runTest {
        val s = SpikeStops.FIRST.copy(lat = null)
        val events = MockNaviAdapter(log).requestRoute(s).toList()
        assertEquals(NaviFailure.NO_VERIFIED_COORDS, (events.single() as NaviEvent.Failed).code)
    }
}
