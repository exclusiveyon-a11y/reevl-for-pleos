package kr.co.reevl.ontheway.link

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR 연결 (device flow) — the car side's rules, kept free of Android so they are unit-tested.
 *
 * Server: reevl `lib/carAuth.ts` (start → QR → phone approves → poll). The QR holds ONLY the public request id
 * (`https://reevl.co.kr/car?r=<id>`); the poll secret stays in this process's memory and is never drawn, logged or saved.
 * The QR is generated on the device with ZXing core (Apache-2.0) — no network QR service sees the id.
 */
object QrLink {
    /** Always the production web: a phone cannot open the emulator's 10.0.2.2 debug server. */
    const val WEB = "https://reevl.co.kr"
    /** Poll period while the QR is on screen (spec: every 3 s). */
    const val POLL_MS = 3_000L
    /** One last poll right after expiry (the server lets an approval made in the final seconds be collected for 60 s). */
    const val FINAL_POLL_MS = 3_500L
    /** Quiet zone in modules on each side (ISO/IEC 18004 asks for 4). */
    const val QUIET_MODULES = 4

    private val ID = Regex("^[A-Za-z0-9_-]{22}$")

    fun validId(id: String) = ID.matches(id)

    fun url(requestId: String): String {
        require(validId(requestId)) { "bad request id" }
        return "$WEB/car?r=$requestId"
    }

    /** Poll only while the QR screen is actually visible, the car is not driving-locked, and the request is still alive. */
    fun shouldPoll(onLinkScreen: Boolean, qrMode: Boolean, foreground: Boolean, driving: Boolean, showing: Boolean, now: Long, expiresAt: Long?): Boolean =
        onLinkScreen && qrMode && foreground && !driving && showing && expiresAt != null && now < expiresAt + FINAL_POLL_MS

    /** The request ran out (no more polls; the screen offers 새 QR). */
    fun expired(now: Long, expiresAt: Long) = now >= expiresAt + FINAL_POLL_MS

    /** QR modules for [text] (ECC M, UTF-8, no margin — the drawing adds [QUIET_MODULES]). */
    fun matrix(text: String): QrMatrix {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val n = m.width
        return QrMatrix(n, BooleanArray(n * n) { m.get(it % n, it / n) })
    }
}

/** Square module grid; `true` = dark. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

/** Which way the LINK screen asks for the account: QR (default since 2026-11-10) or the 6-digit keypad. */
enum class LinkMode { QR, CODE }

/** IDLE (nothing asked yet) → STARTING → SHOWING (polling while visible) → EXPIRED | FAILED (→ 새 QR). */
enum class QrPhase { IDLE, STARTING, SHOWING, EXPIRED, FAILED }

/** What the screen draws. No secret here — only the public URL and the 2-digit match number. */
data class QrShown(val requestId: String, val url: String, val match: String, val expiresAt: Long)
