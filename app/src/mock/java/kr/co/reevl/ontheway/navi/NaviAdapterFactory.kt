package kr.co.reevl.ontheway.navi

import android.content.Context

/** mock flavor: no SDK on the classpath at all. Its successes are never SDK evidence. */
object NaviAdapterFactory {
    const val NAME = "mock"

    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, log: NaviLog): NaviAdapter = MockNaviAdapter(log)

    @Suppress("UNUSED_PARAMETER")
    fun probe(adapter: NaviAdapter) = Unit

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun cancelRoute(adapter: NaviAdapter) = Unit
}
