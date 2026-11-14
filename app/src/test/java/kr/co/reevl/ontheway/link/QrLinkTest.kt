package kr.co.reevl.ontheway.link

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR 연결 on the car: URL, poll gate (visible · parked · alive), expiry, and that the drawn code decodes back. */
class QrLinkTest {

    private val id = "AbC_-0123456789xyzQRst"   // 22 chars, base64url

    @Test fun url_isTheProductionWebPage_withTheRequestIdOnly() {
        assertEquals("https://reevl.co.kr/car?r=$id", QrLink.url(id))
        assertTrue(QrLink.validId(id))
        for (bad in listOf("", "short", id + "x", "AbC/-0123456789xyzQRst", "AbC+-0123456789xyzQRst")) assertFalse(bad, QrLink.validId(bad))
    }

    @Test(expected = IllegalArgumentException::class) fun url_rejectsAnythingElse() { QrLink.url("../../x") }

    @Test fun shouldPoll_onlyWhileVisible_parked_qrMode_andAlive() {
        val exp = 1_000_000L
        fun p(link: Boolean = true, qr: Boolean = true, fg: Boolean = true, driving: Boolean = false, showing: Boolean = true, now: Long = exp - 1, e: Long? = exp) =
            QrLink.shouldPoll(link, qr, fg, driving, showing, now, e)
        assertTrue(p())
        assertFalse("another screen", p(link = false))
        assertFalse("keypad mode", p(qr = false))
        assertFalse("app in background", p(fg = false))
        assertFalse("driving-locked", p(driving = true))
        assertFalse("not showing (starting / expired)", p(showing = false))
        assertFalse("no request", p(e = null))
        assertTrue("one last poll just after expiry", p(now = exp + QrLink.FINAL_POLL_MS - 1))
        assertFalse("then stop", p(now = exp + QrLink.FINAL_POLL_MS))
        assertEquals(3_000L, QrLink.POLL_MS)
    }

    @Test fun expired_afterTheFinalPoll() {
        assertFalse(QrLink.expired(999, 1_000))
        assertFalse(QrLink.expired(1_000 + QrLink.FINAL_POLL_MS - 1, 1_000))
        assertTrue(QrLink.expired(1_000 + QrLink.FINAL_POLL_MS, 1_000))
    }

    /** Draw the matrix like the screen does (white, 4-module quiet zone, black modules) and read it back with ZXing. */
    @Test fun matrix_decodesBackToTheUrl_withQuietZone() {
        val url = QrLink.url(id)
        val m = QrLink.matrix(url)
        assertTrue("version ≤ 4 keeps modules big at 400 dp", m.size <= 33)
        val scale = 8
        val side = (m.size + 2 * QrLink.QUIET_MODULES) * scale
        val px = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until m.size) for (x in 0 until m.size) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val X = (x + QrLink.QUIET_MODULES) * scale + dx
                val Y = (y + QrLink.QUIET_MODULES) * scale + dy
                px[Y * side + X] = 0xFF000000.toInt()
            }
        }
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, px)))).text
        assertEquals(url, text)
    }

    @Test fun module_atLeast9dpAt400dp() {
        val m = QrLink.matrix(QrLink.url(id))
        val moduleDp = 400f / (m.size + 2 * QrLink.QUIET_MODULES)
        assertTrue("module $moduleDp dp", moduleDp >= 9f)
    }
}

/** LINK screen state: 새 QR only after the request is gone; the keypad stays the CODE mode. */
class QrUiStateTest {
    @Test fun renewable_onlyWhenExpiredOrFailed() {
        for (p in QrPhase.entries) {
            val r = kr.co.reevl.ontheway.ui.state.QrUi(p).renewable
            assertEquals(p.name, p == QrPhase.EXPIRED || p == QrPhase.FAILED, r)
        }
        assertEquals(LinkMode.CODE, kr.co.reevl.ontheway.ui.state.LinkUi("", false, false, null).mode)
    }
}
