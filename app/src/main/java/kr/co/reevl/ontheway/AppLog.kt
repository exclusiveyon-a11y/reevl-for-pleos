package kr.co.reevl.ontheway

import android.util.Log
import java.io.File
import kr.co.reevl.ontheway.navi.NaviLog

/**
 * App-level evidence log (screen changes, server reads/writes) — one JSON object per line under tag [TAG],
 * plus an append-only copy in the app sandbox. Never logs tokens, codes or cookies.
 */
class AppLog(private val file: File?) {
    fun event(ev: String, fields: Map<String, Any?> = emptyMap()) {
        val all = listOf<Pair<String, Any?>>("t" to System.currentTimeMillis(), "ev" to ev) + fields.toList()
        val line = NaviLog.json(*all.toTypedArray())
        Log.i(TAG, line)
        file?.let { f -> runCatching { synchronized(this) { f.appendText("$TAG\t$line\n") } } }
    }

    companion object {
        const val TAG = "OTW-APP"
    }
}
