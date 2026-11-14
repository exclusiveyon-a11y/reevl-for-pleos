package kr.co.reevl.ontheway

import android.util.Log
import java.io.File
import kr.co.reevl.ontheway.navi.NaviLog

/**
 * Release builds write NO log at all — no logcat line, no file (coordinates, complex names and plan ids are evidence for
 * debug captures only). Sources: sdk-docs-2026-10-19 …vehicle-app-planning-guide_security-privacy.txt 「개인정보 최소 수집」,
 * …review-user-privacy-security.txt (수집 항목은 서비스에 필요한 범위). Debug keeps the evidence stream.
 */
object LogPolicy {
    val ENABLED: Boolean = BuildConfig.DEBUG
}

/**
 * App-level evidence log (screen changes, server reads/writes) — one JSON object per line under tag [TAG],
 * plus an append-only copy in the app sandbox. Never logs tokens, codes or cookies. Off in release ([LogPolicy]).
 */
class AppLog(private val file: File?, private val enabled: Boolean = LogPolicy.ENABLED) {
    /** 동의 철회 / cleanup: the on-device copy goes. */
    fun clear() { runCatching { file?.delete() } }

    fun event(ev: String, fields: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val all = listOf<Pair<String, Any?>>("t" to System.currentTimeMillis(), "ev" to ev) + fields.toList()
        val line = NaviLog.json(*all.toTypedArray())
        Log.i(TAG, line)
        file?.let { f -> runCatching { synchronized(this) { f.appendText("$TAG\t$line\n") } } }
    }

    companion object {
        const val TAG = "OTW-APP"
    }
}
