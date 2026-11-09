package kr.co.reevl.ontheway.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NaviLogTest {

    @Test fun eventLine_isOneJsonObject_withAgreedKeys() {
        val out = mutableListOf<Pair<String, String>>()
        val log = NaviLog(adapter = "pleos", clock = { 1761470000000L }, sink = { t, l -> out += t to l })
        val line = log.event("Failed", stop = "c1", lat = 37.5, lng = 126.9, code = "NO_VERIFIED_COORDS", msg = "say \"hi\"\nnext")
        assertEquals(
            "{\"t\":1761470000000,\"adapter\":\"pleos\",\"ev\":\"Failed\",\"stop\":\"c1\",\"lat\":37.5,\"lng\":126.9," +
                "\"code\":\"NO_VERIFIED_COORDS\",\"msg\":\"say \\\"hi\\\"\\nnext\"}",
            line,
        )
        assertFalse(line.contains('\n'))
        assertEquals(NaviLog.TAG to line, out.single())
    }

    @Test fun nullFields_areOmitted() {
        val log = NaviLog(adapter = "mock", clock = { 1L }, sink = { _, _ -> })
        assertEquals("{\"t\":1,\"adapter\":\"mock\",\"ev\":\"Connected\"}", log.event("Connected"))
    }

    @Test fun sdkLines_goToSdkTag() {
        val out = mutableListOf<String>()
        val log = NaviLog(adapter = "pleos", clock = { 2L }, sink = { t, _ -> out += t })
        log.sdk("onRouteStarted", stop = "c1", msg = "x")
        assertEquals(listOf(NaviLog.SDK_TAG), out)
    }
}
