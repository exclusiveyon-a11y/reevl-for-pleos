package kr.co.reevl.ontheway.navi

import android.util.Log

/**
 * Evidence log. Every navi event is ONE JSON object on ONE line under tag [TAG]:
 * `{"t":<epochMs>,"adapter":"pleos|mock","ev":"Requested|RoutingCompleted|Failed|Connected|ConnectFailed|Destination","stop":"<id>","lat":..,"lng":..,"signal":"<callback>","verified":true|false,"code":"..","msg":".."}`
 *
 * `signal` / `verified` (Ruling 17): on the outcome of a route request, which SDK callback arrived first
 * (onRoutingCompleted / onRouteStarted / onRouteEnded) and whether getDestinationInfo() matched the request.
 *
 * Raw SDK callbacks that have no [NaviEvent] counterpart (onRouteStarted, onDestinationChanged, ...)
 * go to [SDK_TAG] with `"ev":"Sdk"` and `"cb":"<callback name>"`, so the OTW-NAVI stream stays
 * limited to the agreed event names.
 */
class NaviLog(
    private val adapter: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sink: (tag: String, line: String) -> Unit = { tag, line -> Log.i(tag, line) },
    /** Optional append-only copy (`<tag>\t<json>` per line) — survives logcat ring-buffer rotation. */
    private val file: java.io.File? = null,
) {
    /** Lines written in this process, newest last (shown on the spike screen). */
    val history: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    fun event(
        ev: String,
        stop: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        code: String? = null,
        msg: String? = null,
        signal: String? = null,
        verified: Boolean? = null,
    ): String {
        val line = json(
            "t" to clock(),
            "adapter" to adapter,
            "ev" to ev,
            "stop" to stop,
            "lat" to lat,
            "lng" to lng,
            "signal" to signal,
            "verified" to verified,
            "code" to code,
            "msg" to msg,
        )
        emit(TAG, line)
        return line
    }

    fun sdk(callback: String, stop: String? = null, msg: String? = null): String {
        val line = json(
            "t" to clock(),
            "adapter" to adapter,
            "ev" to "Sdk",
            "cb" to callback,
            "stop" to stop,
            "msg" to msg,
        )
        emit(SDK_TAG, line)
        return line
    }

    private fun emit(tag: String, line: String) {
        history += line
        sink(tag, line)
        file?.let { f -> runCatching { synchronized(this) { f.appendText("$tag\t$line\n") } } }
    }

    companion object {
        const val TAG = "OTW-NAVI"
        const val SDK_TAG = "OTW-NAVI-SDK"

        /** Minimal JSON writer (org.json is a stub in JVM unit tests). Null values are omitted. */
        fun json(vararg fields: Pair<String, Any?>): String = buildString {
            append('{')
            var first = true
            for ((k, v) in fields) {
                if (v == null) continue
                if (!first) append(',')
                first = false
                append('"').append(escape(k)).append("\":")
                when (v) {
                    is Number, is Boolean -> append(v.toString())
                    else -> append('"').append(escape(v.toString())).append('"')
                }
            }
            append('}')
        }

        private fun escape(s: String): String = buildString(s.length + 8) {
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
                }
            }
        }
    }
}
