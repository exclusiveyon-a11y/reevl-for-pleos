package kr.co.reevl.ontheway.navi

import android.content.Context

/** pleos flavor: the real Pleos Connect NaviHelper SDK. */
object NaviAdapterFactory {
    const val NAME = "pleos"

    fun create(context: Context, log: NaviLog): NaviAdapter = PleosNaviAdapter(context, log)

    /** Spike-only diagnostics hook (asks the Navi service for current location / route state). */
    fun probe(adapter: NaviAdapter) {
        (adapter as? PleosNaviAdapter)?.probeInfo()
    }

    /** Spike-only: cancel the active route before a run. */
    suspend fun cancelRoute(adapter: NaviAdapter) {
        (adapter as? PleosNaviAdapter)?.cancelRouteForSpike()
    }
}
