package kr.co.reevl.ontheway.navi

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/**
 * Thin seam over the Pleos NaviHelper SDK (3.0.5) so unit tests can prove exactly what reaches it
 * (and that blocked stops never do). Production uses [RealNaviSdk]; nothing else touches [NaviHelper].
 */
internal interface NaviSdk {
    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun addListener(listener: NaviHelperEventListener)
    fun removeListener(listener: NaviHelperEventListener)
    fun requestRoute(routeInfo: RouteInfo)
    fun cancelRoute()
    fun getDestinationInfo()
    fun getCurrentLocationInfo()
    fun getRouteStateInfo()
    fun release()
}

internal class RealNaviSdk(context: Context) : NaviSdk {
    private val helper = NaviHelper(context.applicationContext)

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) =
        helper.initialize(onConnected = onConnected, onFailed = onFailed)

    override fun addListener(listener: NaviHelperEventListener) = helper.addListener(listener)
    override fun removeListener(listener: NaviHelperEventListener) = helper.removeListener(listener)
    override fun requestRoute(routeInfo: RouteInfo) = helper.requestRoute(routeInfo)
    override fun cancelRoute() = helper.cancelRoute()
    override fun getDestinationInfo() = helper.getDestinationInfo()
    override fun getCurrentLocationInfo() = helper.getCurrentLocationInfo()
    override fun getRouteStateInfo() = helper.getRouteStateInfo()
    override fun release() = helper.release()
}
