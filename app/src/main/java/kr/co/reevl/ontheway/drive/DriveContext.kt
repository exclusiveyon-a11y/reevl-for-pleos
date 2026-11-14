package kr.co.reevl.ontheway.drive

import kr.co.reevl.ontheway.nearby.NearbyEngine

/**
 * Driving context engine — docs/design/driving-context-spec.md §1.2 (states) + pleos-signals-and-cost.md §2 (signal priority).
 * Pure (JVM-tested, DriveContextTest): no Android, no clock of its own — every input carries `now` (elapsedRealtime ms).
 *
 * The screen only ever has two states (open / lock, decided by CarUxRestrictions elsewhere). This engine runs underneath and
 * decides *what the car is doing*, so the app can prepare "방금 지나온 곳" and "도착" before the lock lifts.
 *
 * Signal priority (pleos-signals §2):
 *  1. Vehicle properties (AAOS CarPropertyManager): GEAR_SELECTION / PARKING_BRAKE_ON → parked vs not; PERF_VEHICLE_SPEED → idling
 *     vs moving (precise, works in tunnels). IGNITION_STATE OFF/ACC → away (S6).
 *  2. Fallback when a property is missing / permission denied: CarUxRestrictions (unrestricted = parked) and the GPS speed rule
 *     (≥ [GPS_MOVING_MPS] for [GPS_MOVING_HOLD_MS] → moving; < [GPS_IDLE_MPS] for [GPS_IDLE_HOLD_MS] → idling; 15 m from the
 *     idle anchor → moving; no fix for [GPS_STALE_MS] while restricted → moving = tunnel rule).
 */
class DriveContext {

    enum class State(val code: String) {
        /** S1 — parked, no movement yet (or the last one > 30 min ago). */
        BEFORE_START("S1"),
        /** S2 — moving. Screen locked. */
        MOVING("S2"),
        /** S3 — gear not P, speed ≈ 0 (light, jam). Screen locked. */
        IDLING("S3"),
        /** S4a — just parked after a real trip: the stop event fired, "방금 지나온 곳" is shown for [JUST_STOPPED_MS]. */
        JUST_STOPPED("S4a"),
        /** S4b — parked and staying. */
        STAYING("S4b"),
        /** S6 — app in background or ignition off/acc: no location, trail kept in memory for a while. */
        AWAY("S6"),
    }

    enum class Motion { PARKED, IDLING, MOVING }

    sealed interface Event {
        data class StateChanged(val from: State, val to: State, val via: String) : Event
        /** The stop event (S2/S3 → S4a): trip since the last stop, and when the car last moved. */
        data class Stopped(val tripM: Double, val atMs: Long) : Event
    }

    enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }
    enum class Ignition { LOCK, OFF, ACC, ON, START, UNKNOWN }

    var state: State = State.BEFORE_START
        private set

    // ── inputs as last seen (null = never reported / unsupported) ──
    var restricted: Boolean = false
        private set
    var gear: Gear? = null
        private set
    var parkingBrake: Boolean? = null
        private set
    var vehicleSpeedMps: Double? = null
        private set
    var ignition: Ignition? = null
        private set
    var lastFix: NearbyEngine.Fix? = null
        private set
    private var foreground = true

    // ── derived ──
    /** Where the decision came from, for logs and the debug HUD: `vehicle` | `ux+gps` | `ux`. */
    var basis: String = "ux"
        private set
    var tripM: Double = 0.0
        private set
    private var speedTripM = 0.0
    private var gpsTripM = 0.0
    private var lastMovingAt: Long? = null
    private var stateSince: Long = 0L
    /** When the last stop event fired: S4a lasts [JUST_STOPPED_MS] from here, also across a trip to the background. */
    private var stoppedAt: Long? = null

    // vehicle speed hysteresis
    private var vehicleMoving = false
    // GPS rule
    private var gpsMoving = false
    private var gpsFastSince: Long? = null
    private var gpsSlowSince: Long? = null
    private var idleAnchor: NearbyEngine.Fix? = null
    private var lastSpeedAt: Long? = null

    // ── inputs ──

    fun ux(restricted: Boolean, now: Long): List<Event> { this.restricted = restricted; return step(now, "ux") }

    fun gear(g: Gear?, now: Long): List<Event> { gear = g; return step(now, "gear") }

    fun parkingBrake(on: Boolean?, now: Long): List<Event> { parkingBrake = on; return step(now, "brake") }

    fun ignition(i: Ignition?, now: Long): List<Event> { ignition = i; return step(now, "ignition") }

    fun speed(mps: Double?, now: Long): List<Event> {
        integrateSpeed(now)
        vehicleSpeedMps = mps
        if (mps != null) vehicleMoving = if (vehicleMoving) mps >= VEHICLE_IDLE_MPS else mps >= VEHICLE_MOVING_MPS
        return step(now, "speed")
    }

    /** A GPS fix (time = elapsedRealtime). Inaccurate fixes still count for staleness but not for distance. */
    fun fix(f: NearbyEngine.Fix, now: Long): List<Event> {
        val prev = lastFix
        lastFix = f
        val good = f.accuracyM == null || f.accuracyM <= NearbyEngine.MAX_ACCURACY_M
        val sp = f.speedMps ?: prev?.let { p ->
            val dt = (f.timeMs - p.timeMs) / 1000.0
            if (dt > 0) NearbyEngine.haversine(p.lat, p.lng, f.lat, f.lng) / dt else null
        }
        if (sp != null) {
            if (sp >= GPS_MOVING_MPS) { gpsFastSince = gpsFastSince ?: now; gpsSlowSince = null }
            else if (sp < GPS_IDLE_MPS) { gpsSlowSince = gpsSlowSince ?: now; gpsFastSince = null }
            else { gpsFastSince = null; gpsSlowSince = null }
        }
        if (good && prev != null && state != State.STAYING && state != State.BEFORE_START && state != State.JUST_STOPPED) {
            gpsTripM += NearbyEngine.haversine(prev.lat, prev.lng, f.lat, f.lng)
        }
        if (!gpsMoving && good) {
            val a = idleAnchor
            if (a == null) idleAnchor = f
            else if (restricted && NearbyEngine.haversine(a.lat, a.lng, f.lat, f.lng) >= GPS_MOVED_M) { gpsMoving = true; gpsFastSince = null }
        }
        return step(now, "gps")
    }

    fun background(now: Long): List<Event> { foreground = false; return step(now, "background") }

    fun foreground(now: Long): List<Event> { foreground = true; return step(now, "foreground") }

    /** Called about once a second: time-based rules (hysteresis holds, S4a → S4b, tunnel rule). */
    fun tick(now: Long): List<Event> = step(now, "tick")

    // ── rules ──

    private fun integrateSpeed(now: Long) {
        val v = vehicleSpeedMps
        val at = lastSpeedAt
        if (v != null && at != null && now > at) speedTripM += v * (now - at) / 1000.0
        lastSpeedAt = now
    }

    private fun gpsRule(now: Long) {
        gpsFastSince?.let { if (now - it >= GPS_MOVING_HOLD_MS) gpsMoving = true }
        gpsSlowSince?.let { if (now - it >= GPS_IDLE_HOLD_MS) { gpsMoving = false; idleAnchor = lastFix } }
    }

    /** Parked vs idling vs moving, vehicle properties first. Pure function of the current inputs (+ GPS hysteresis state). */
    fun motion(now: Long): Motion {
        val g = gear?.takeIf { it != Gear.UNKNOWN }
        val parkedBySignal = g == Gear.PARK || parkingBrake == true
        if (g != null || parkingBrake != null) {
            if (parkedBySignal) { basis = "vehicle"; return Motion.PARKED }
            if (vehicleSpeedMps != null) { basis = "vehicle"; return if (vehicleMoving) Motion.MOVING else Motion.IDLING }
            // Gear known (not P) but no speed: GPS decides idling vs moving.
            basis = "vehicle+gps"
            return gpsMotion(now)
        }
        if (!restricted) {
            // No gear signal: an unrestricted car counts as parked (AOSP: parked = no DO required). A vehicle speed > 0 wins.
            if (vehicleSpeedMps != null && vehicleMoving) { basis = "vehicle"; return Motion.MOVING }
            basis = "ux"; return Motion.PARKED
        }
        if (vehicleSpeedMps != null) { basis = "ux+vehicle"; return if (vehicleMoving) Motion.MOVING else Motion.IDLING }
        basis = "ux+gps"
        return gpsMotion(now)
    }

    private fun gpsMotion(now: Long): Motion {
        val f = lastFix
        // Tunnel rule (spec §1.2 S3): no fix for a while while restricted → treat as moving.
        if (f == null || now - f.timeMs > GPS_STALE_MS) return Motion.MOVING
        return if (gpsMoving) Motion.MOVING else Motion.IDLING
    }

    private fun step(now: Long, via: String): List<Event> {
        integrateSpeed(now)
        gpsRule(now)
        tripM = maxOf(speedTripM, gpsTripM)
        val out = mutableListOf<Event>()
        // Ignition OFF/ACC = engine off (spec S6). Guarded: an image that reports OFF while in D or moving is not trusted.
        val engineOff = (ignition == Ignition.OFF || ignition == Ignition.ACC) && gear != Gear.DRIVE && !vehicleMoving
        val away = !foreground || engineOff
        val next: State = if (away) State.AWAY else when (motion(now)) {
            Motion.MOVING -> { lastMovingAt = now; State.MOVING }
            Motion.IDLING -> State.IDLING
            Motion.PARKED -> parkedState(now, out)
        }
        if (next != state) {
            out.add(0, Event.StateChanged(state, next, via))
            state = next
            stateSince = now
        }
        return out
    }

    private fun parkedState(now: Long, out: MutableList<Event>): State = when (state) {
        State.MOVING, State.IDLING -> {
            val recent = lastMovingAt?.let { now - it <= JUST_MOVED_MS } == true
            if (recent && tripM >= STOP_TRIP_M) {
                out += Event.Stopped(tripM, lastMovingAt ?: now)
                resetTrip()
                stoppedAt = now
                State.JUST_STOPPED
            } else State.STAYING
        }
        State.JUST_STOPPED -> if (now - (stoppedAt ?: stateSince) >= JUST_STOPPED_MS) State.STAYING else State.JUST_STOPPED
        State.AWAY -> {
            // Back in front (another app covered us / pushed us off-screen — Pleos keeps the app alive): the S4a moment
            // resumes if it has not run out — no new stop event, so no new request. Otherwise the same trip context if the
            // car moved recently, else a fresh start.
            val moved = lastMovingAt?.let { now - it <= FRESH_START_MS } == true
            val stillJustStopped = stoppedAt?.let { now - it < JUST_STOPPED_MS } == true && lastMovingAt?.let { it <= stoppedAt!! } != false
            when { stillJustStopped -> State.JUST_STOPPED; moved -> State.STAYING; else -> State.BEFORE_START }
        }
        State.STAYING -> if (lastMovingAt?.let { now - it > FRESH_START_MS } != false) State.BEFORE_START else State.STAYING
        State.BEFORE_START -> State.BEFORE_START
    }

    private fun resetTrip() { speedTripM = 0.0; gpsTripM = 0.0; tripM = 0.0 }

    /** One line for logs / the debug HUD: `S2 MOVING · vehicle · D · 20km/h · ux on`. */
    fun describe(): String = listOfNotNull(
        "${state.code} ${state.name}", basis,
        gear?.name?.first()?.toString(),
        vehicleSpeedMps?.let { "${Math.round(it * 3.6)}km/h" },
        parkingBrake?.let { if (it) "brake" else null },
        if (restricted) "ux on" else "ux off",
    ).joinToString(" · ")

    companion object {
        /** Vehicle speed hysteresis (m/s): enter moving at ≥ 0.5 (1.8 km/h), leave below 0.2. */
        const val VEHICLE_MOVING_MPS = 0.5
        const val VEHICLE_IDLE_MPS = 0.2
        /** GPS rule (spec §1.2): ≥ 1.5 m/s for 2 s → moving; < 0.5 m/s for 3 s → idling; 15 m from the idle anchor → moving. */
        const val GPS_MOVING_MPS = 1.5
        const val GPS_MOVING_HOLD_MS = 2_000L
        const val GPS_IDLE_MPS = 0.5
        const val GPS_IDLE_HOLD_MS = 3_000L
        const val GPS_MOVED_M = 15.0
        /** No GPS fix for this long while restricted → moving (tunnel). */
        const val GPS_STALE_MS = 30_000L
        /** S4a needs the car to have moved within this window … */
        const val JUST_MOVED_MS = 120_000L
        /** … and at least this far since the last stop event. */
        const val STOP_TRIP_M = 300.0
        /** S4a → S4b. */
        const val JUST_STOPPED_MS = 180_000L
        /** S1 again after this long without moving. */
        const val FRESH_START_MS = 30 * 60_000L
    }
}
