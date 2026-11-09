package kr.co.reevl.ontheway.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context

/**
 * The platform driving restriction (Pleos 주행 규제 개발 가이드: "CarUxRestrictionsManager에 registerListener()를 등록하면
 * onUxRestrictionsChanged() 콜백으로 변경된 CarUxRestrictions를 수신") wired to the app's one driving gate
 * ([kr.co.reevl.ontheway.ui.DrivingPolicy] via AppViewModel.onPlatformRestriction). Public AAOS Car API only
 * (`android.car` optional SDK library) — nothing from the Pleos SDK.
 */
class CarUxGate(private val context: Context, private val onChange: (restricted: Boolean, detail: Map<String, Any?>) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (!ready) { manager = null; return@createCar }
                val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return@createCar
                manager = m
                m.registerListener { r -> report(r, "listener") }
                m.currentCarUxRestrictions?.let { report(it, "initial") }
            }
        } catch (t: Throwable) { // no car service / no android.car library on this device
            onChange(false, mapOf("available" to false, "error" to t.javaClass.simpleName))
        }
    }

    private fun report(r: CarUxRestrictions, via: String) {
        val restricted = CarUxMapping.restricted(r.isRequiresDistractionOptimization, r.activeRestrictions)
        onChange(restricted, mapOf("via" to via, "requiresDO" to r.isRequiresDistractionOptimization,
            "active" to "0x" + Integer.toHexString(r.activeRestrictions), "flags" to CarUxMapping.names(r.activeRestrictions).joinToString("|")))
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }
}

/**
 * Pure mapping (JVM-tested). Flag values are the public `CarUxRestrictions.UX_RESTRICTIONS_*` constants
 * (developer.android.com reference). Our gate locks the parked-only parts whenever the platform requires distraction
 * optimisation with any active restriction — the emulator's idling and moving states both report DO + 0x50
 * (NO_VIDEO|NO_SETUP), so a car stopped at a light stays locked (v7 BACKUP C: 신호 정차 ≠ 주차).
 */
object CarUxMapping {
    const val BASELINE = 0
    const val NO_DIALPAD = 1
    const val NO_FILTERING = 2
    const val LIMIT_STRING_LENGTH = 4
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val LIMIT_CONTENT = 32
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
    const val NO_VOICE_TRANSCRIPTION = 256
    const val FULLY_RESTRICTED = 511

    fun restricted(requiresDistractionOptimization: Boolean, activeRestrictions: Int): Boolean =
        requiresDistractionOptimization && activeRestrictions != BASELINE

    private val NAMES = listOf(NO_DIALPAD to "NO_DIALPAD", NO_FILTERING to "NO_FILTERING", LIMIT_STRING_LENGTH to "LIMIT_STRING_LENGTH",
        NO_KEYBOARD to "NO_KEYBOARD", NO_VIDEO to "NO_VIDEO", LIMIT_CONTENT to "LIMIT_CONTENT", NO_SETUP to "NO_SETUP",
        NO_TEXT_MESSAGE to "NO_TEXT_MESSAGE", NO_VOICE_TRANSCRIPTION to "NO_VOICE_TRANSCRIPTION")

    fun names(active: Int): List<String> = if (active == BASELINE) listOf("BASELINE") else NAMES.filter { active and it.first != 0 }.map { it.second }
}
