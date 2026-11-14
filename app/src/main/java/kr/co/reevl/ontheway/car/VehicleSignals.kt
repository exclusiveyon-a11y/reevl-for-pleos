package kr.co.reevl.ontheway.car

import android.car.Car
import android.car.VehicleAreaType
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import kr.co.reevl.ontheway.drive.DriveContext

/**
 * Vehicle properties from the standard AAOS Car API (pleos-signals-and-cost.md §1.1 — Pleos FAQ: read PERF_VEHICLE_SPEED with
 * CarPropertyManager; "속도, 기어 상태 … 표준 데이터 → AAOS Car API"):
 *  · PERF_VEHICLE_SPEED (m/s) — `android.car.permission.CAR_SPEED`, dangerous → asked at runtime; denied = GPS fallback.
 *  · GEAR_SELECTION · PARKING_BRAKE_ON · IGNITION_STATE — `android.car.permission.CAR_POWERTRAIN`, normal (granted at install).
 * Each property is registered on its own: an unsupported one (no config) or a denied one is logged and skipped; the engine
 * then falls back for that input only. Values are logged (first few + every change) — the shell cannot read them on a user
 * build, so the app log is the evidence that they arrive.
 */
class VehicleSignals(
    private val context: Context,
    private val log: (String, Map<String, Any?>) -> Unit,
    private val onSpeed: (Double?) -> Unit,
    private val onGear: (DriveContext.Gear?) -> Unit,
    private val onBrake: (Boolean?) -> Unit,
    private val onIgnition: (DriveContext.Ignition?) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private val registered = mutableSetOf<Int>()
    private val seen = mutableMapOf<Int, Int>()

    fun hasSpeedPermission(): Boolean = context.checkSelfPermission(PERM_SPEED) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (car != null) { registerAll(); return }
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (!ready) { manager = null; registered.clear(); return@createCar }
                manager = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                registerAll()
            }
        } catch (t: Throwable) {
            log("vehicle.unavailable", mapOf("error" to t.javaClass.simpleName))
        }
    }

    /** Also called again after CAR_SPEED is granted. */
    fun registerAll() {
        val m = manager ?: return
        listOf(
            VehiclePropertyIds.PERF_VEHICLE_SPEED to CarPropertyManager.SENSOR_RATE_NORMAL,
            VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
            VehiclePropertyIds.PARKING_BRAKE_ON to CarPropertyManager.SENSOR_RATE_ONCHANGE,
            VehiclePropertyIds.IGNITION_STATE to CarPropertyManager.SENSOR_RATE_ONCHANGE,
        ).forEach { (id, rate) -> register(m, id, rate) }
    }

    private fun register(m: CarPropertyManager, id: Int, rate: Float) {
        if (id in registered) return
        val name = VehiclePropertyIds.toString(id)
        if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED && !hasSpeedPermission()) {
            log("vehicle.skip", mapOf("prop" to name, "reason" to "CAR_SPEED not granted → GPS speed rule")); return
        }
        val config = runCatching { m.getCarPropertyConfig(id) }.getOrNull()
        if (config == null) { log("vehicle.skip", mapOf("prop" to name, "reason" to "unsupported (no config)")); return }
        val ok = runCatching { m.registerCallback(callback, id, rate) }
            .onFailure { log("vehicle.skip", mapOf("prop" to name, "reason" to "${it.javaClass.simpleName}: ${it.message}")) }
            .getOrDefault(false)
        if (ok) registered += id
        log("vehicle.register", mapOf("prop" to name, "ok" to ok, "rate" to rate))
        // First value right away (the callback also sends the current value, but not on every image).
        runCatching { m.getProperty<Any>(id, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) }.getOrNull()?.let { deliver(it) }
    }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = deliver(value)
        override fun onErrorEvent(propId: Int, zone: Int) {
            log("vehicle.error", mapOf("prop" to VehiclePropertyIds.toString(propId), "zone" to zone))
        }
    }

    private var lastLogged = mutableMapOf<Int, Any?>()

    private fun deliver(v: CarPropertyValue<*>) {
        if (v.status != CarPropertyValue.STATUS_AVAILABLE) {
            log("vehicle.value", mapOf("prop" to VehiclePropertyIds.toString(v.propertyId), "status" to v.status)); return
        }
        val raw = v.value
        val id = v.propertyId
        val shown: Any? = when (id) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> (raw as? Float)?.toDouble()?.let { s -> onSpeed(s); Math.round(s * 3.6 * 10) / 10.0 }
            VehiclePropertyIds.GEAR_SELECTION -> VehicleMapping.gear(raw as? Int).also { onGear(it) }
            VehiclePropertyIds.PARKING_BRAKE_ON -> (raw as? Boolean).also { onBrake(it) }
            VehiclePropertyIds.IGNITION_STATE -> VehicleMapping.ignition(raw as? Int).also { onIgnition(it) }
            else -> raw
        }
        // Log the first 3 values of each property and every change (speed rounded to 1 km/h so 1 Hz noise stays quiet).
        val n = (seen[id] ?: 0) + 1
        seen[id] = n
        val key = if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED) (shown as? Double)?.let { Math.round(it) } else shown
        if (n <= 3 || lastLogged[id] != key) {
            lastLogged[id] = key
            log("vehicle.value", mapOf("prop" to VehiclePropertyIds.toString(id), "value" to shown,
                "unit" to (if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED) "km/h" else null), "n" to n))
        }
    }

    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        registered.clear()
        manager = null
        car = null
    }

    companion object {
        const val PERM_SPEED = "android.car.permission.CAR_SPEED"
    }
}

/** Pure mapping of the public `VehicleGear` / `VehicleIgnitionState` constants (JVM-tested). */
object VehicleMapping {
    // android.car.VehicleGear
    const val GEAR_UNKNOWN = 0
    const val GEAR_NEUTRAL = 1
    const val GEAR_REVERSE = 2
    const val GEAR_PARK = 4
    const val GEAR_DRIVE = 8
    // android.car.VehicleIgnitionState
    const val IGN_UNDEFINED = 0
    const val IGN_LOCK = 1
    const val IGN_OFF = 2
    const val IGN_ACC = 3
    const val IGN_ON = 4
    const val IGN_START = 5

    fun gear(v: Int?): DriveContext.Gear? = when (v) {
        null -> null
        GEAR_PARK -> DriveContext.Gear.PARK
        GEAR_REVERSE -> DriveContext.Gear.REVERSE
        GEAR_NEUTRAL -> DriveContext.Gear.NEUTRAL
        GEAR_UNKNOWN -> DriveContext.Gear.UNKNOWN
        // GEAR_DRIVE and the numbered forward gears (GEAR_1 = 16 … GEAR_9 = 4096) all mean "in drive".
        else -> if (v >= GEAR_DRIVE) DriveContext.Gear.DRIVE else DriveContext.Gear.UNKNOWN
    }

    fun ignition(v: Int?): DriveContext.Ignition? = when (v) {
        null -> null
        IGN_LOCK -> DriveContext.Ignition.LOCK
        IGN_OFF -> DriveContext.Ignition.OFF
        IGN_ACC -> DriveContext.Ignition.ACC
        IGN_ON -> DriveContext.Ignition.ON
        IGN_START -> DriveContext.Ignition.START
        else -> DriveContext.Ignition.UNKNOWN
    }
}
