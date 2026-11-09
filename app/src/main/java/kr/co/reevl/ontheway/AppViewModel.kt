package kr.co.reevl.ontheway

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.async
import kr.co.reevl.ontheway.data.AddStopRequest
import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.NearbyComplex
import kr.co.reevl.ontheway.data.NearbyComplexes
import kr.co.reevl.ontheway.data.NearbyOffices
import kr.co.reevl.ontheway.nearby.CarLocation
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.plan.Insert
import kr.co.reevl.ontheway.plan.Schedule
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.data.DiskComplexCache
import kr.co.reevl.ontheway.data.DiskPlanCache
import kr.co.reevl.ontheway.data.KeystoreTokenStore
import kr.co.reevl.ontheway.data.OkHttpTransport
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanRepository
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.data.UserMessages
import kr.co.reevl.ontheway.navi.NaviAdapter
import kr.co.reevl.ontheway.navi.NaviAdapterFactory
import kr.co.reevl.ontheway.navi.NaviEvent
import kr.co.reevl.ontheway.navi.NaviLog
import kr.co.reevl.ontheway.navi.RouteSummary
import kr.co.reevl.ontheway.plan.PlanGate
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.plan.SessionEpoch

/**
 * Screen ids (v5 §9-2 names). Each is its own composable so D3 can gate them while driving.
 * STOP_DETAIL (D-UX) replaces D2's STOP_INFO: the car complex detail backed by `GET /api/car/complex/{cid}`.
 */
enum class Screen { LINK, TODAY_PLAN, VISIT_LIST, NAV_HANDOFF, STOP_DETAIL, NEARBY, CHANGE_CONFIRM, CUSTOMER_VIEW }

sealed interface HandoffPhase {
    data object Sending : HandoffPhase
    data class Success(val route: RouteSummary?) : HandoffPhase
    data class Failure(val code: String) : HandoffPhase
}

/** What a guide request sends the navigation to: the stop itself, its meeting point (v7 기능 4), or a just-added place. */
enum class GuideTarget { STOP, MEETING_POINT }

/**
 * [stop] = the plan stop the request belongs to; [navi] = what goes to NaviHelper (the stop, or a meeting-point copy
 * with the server-verified meeting coordinates); [title] = the screen label (`만남 장소 · <label>` for a meeting point).
 */
data class Handoff(val stop: PlanStop, val phase: HandoffPhase, val navi: PlanStop = stop, val title: String = stop.name,
                   val target: GuideTarget = GuideTarget.STOP)

/** The stop the car's navigation is guiding to, as last verified by the adapter (Ruling 17). */
data class ActiveGuide(val stopId: String, val name: String, val route: RouteSummary?, val target: GuideTarget = GuideTarget.STOP)

/** NEARBY tabs (slide 12: `단지` / `중개사무소`). */
enum class NearbyTab { COMPLEX, OFFICE }

/** Search centre: the car's own fix (+ heading for sides) or, without a location, the stop being visited. */
sealed interface NearbyCenter {
    val lat: Double
    val lng: Double
    data class Car(val snapshot: CarLocation.Snapshot) : NearbyCenter {
        override val lat get() = snapshot.fix.lat
        override val lng get() = snapshot.fix.lng
    }
    data class AtStop(val stop: PlanStop, override val lat: Double, override val lng: Double) : NearbyCenter
}

/** One NEARBY 단지 row: the server's complex + the side computed on the car. */
data class NearbyRow(val c: NearbyComplex, val side: NearbyEngine.Side, val inPlan: Boolean)

/** What CHANGE_CONFIRM asks to insert. */
data class PendingAdd(val name: String, val cid: String? = null, val officeId: String? = null, val brand: CarBrand? = null,
                      val sub: String? = null)

/** The last insert on this plan, kept until undone or another change — drives `실행 취소` and `이곳으로 안내`. */
data class LastInsert(val planId: String, val stopId: String, val name: String)

/**
 * The car app's state machine. Holds no Android views; the composables in `ui/` only read these fields and call
 * these functions. Data always comes from [PlanRepository] (server first, cache marked as cache).
 *
 * D2 review fixes applied here (D-UX, 2026-10-27):
 *  · I-2 every async answer carries the [SessionEpoch] it was started in and is dropped if a logout/link happened;
 *    plan answers must also pass [PlanGate] (same plan id, not older than shown); jobs are cancelled on logout.
 *  · I-3 [activeGuide] is cleared when a new guide request starts and stays null on failure/timeout/cancel.
 */
class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    val appLog = AppLog(File(app.filesDir, "otw-app.log"))
    private val naviLog = NaviLog(adapter = NaviAdapterFactory.NAME, file = File(app.filesDir, "otw-navi.log"))
    private val navi: NaviAdapter = NaviAdapterFactory.create(app, naviLog)
    private val tokenStore = KeystoreTokenStore(app.noBackupFilesDir)
    private val repo = PlanRepository(
        api = Api(OkHttpTransport(BuildConfig.BASE_URL)),
        tokens = tokenStore,
        cache = DiskPlanCache(File(app.noBackupFilesDir, "plan-cache")),
        complexCache = DiskComplexCache(File(app.noBackupFilesDir, "complex-cache")),
        deviceName = "REEVL · ${Build.MODEL}",
        log = { ev, fields -> appLog.event("repo.$ev", fields) },
    )

    /** I-2: bumped on logout, on a new link and on 401 — older answers are dropped. */
    private val epoch = SessionEpoch()
    private val jobs = mutableSetOf<Job>()

    private fun track(block: suspend () -> Unit): Job {
        val j = viewModelScope.launch { block() }
        jobs += j
        j.invokeOnCompletion { jobs -= j }
        return j
    }

    /**
     * Restored after process death from [saved] (review guide: return to the last state after background).
     * A route request in flight cannot be resumed, so NAV_HANDOFF comes back as VISIT_LIST.
     */
    var screen by mutableStateOf(initialScreen())
        private set

    /**
     * Driving restriction (CarUxRestrictions) — the hook the screens read. D3 wires the real platform signal; in
     * debug builds `adb shell am start … --ez otw_driving true|false` sets it for verification (MainActivity).
     */
    var driving by mutableStateOf(false)
        private set

    // LINK
    var code by mutableStateOf("")
        private set
    var linkBusy by mutableStateOf(false)
        private set
    var linkMessage by mutableStateOf<String?>(null)
        private set

    // TODAY_PLAN
    var plans by mutableStateOf<Synced<List<PlanSummary>>?>(null)
        private set
    var plansLoading by mutableStateOf(false)
        private set
    var plansMessage by mutableStateOf<String?>(null)
        private set
    /** Stops of plans we already have (cache or fetched) — for the plan card's avatar row. */
    val planStops = mutableStateMapOf<String, List<PlanStop>>()

    // VISIT_LIST / STOP_DETAIL
    var plan by mutableStateOf<Synced<Plan>?>(null)
        private set
    var planLoading by mutableStateOf(false)
        private set
    var planMessage by mutableStateOf<String?>(null)
        private set
    var busyStopId by mutableStateOf<String?>(null)
        private set
    var infoStopId by mutableStateOf<String?>(null)
        private set

    /** Car complex summaries by cid (A7) — avatars in lists, facts on STOP_DETAIL. */
    val complexes = mutableStateMapOf<String, Synced<CarComplex>>()
    var complexLoading by mutableStateOf<String?>(null)
        private set
    var complexMessage by mutableStateOf<String?>(null)
        private set
    private val complexInFlight = mutableSetOf<String>()

    // NAV_HANDOFF
    var handoff by mutableStateOf<Handoff?>(null)
        private set
    private var handoffJob: Job? = null
    var activeGuide by mutableStateOf<ActiveGuide?>(null)
        private set

    // v7 tour: whole plans we have seen (TODAY_PLAN tour badge / first appointment come from the plan, not the summary)
    val planFull = mutableStateMapOf<String, Plan>()

    // v7 NEARBY / CHANGE_CONFIRM
    val location = CarLocation(app) { ev, f -> appLog.event(ev, f) }
    var nearbyTab by mutableStateOf(NearbyTab.COMPLEX)
        private set
    var nearbyCenter by mutableStateOf<NearbyCenter?>(null)
        private set
    var nearbyComplexes by mutableStateOf<NearbyComplexes?>(null)
        private set
    var nearbyRows by mutableStateOf<List<NearbyRow>>(emptyList())
        private set
    var nearbyOffices by mutableStateOf<NearbyOffices?>(null)
        private set
    var nearbyLoading by mutableStateOf(false)
        private set
    var nearbyMessage by mutableStateOf<String?>(null)
        private set
    var pendingAdd by mutableStateOf<PendingAdd?>(null)
        private set
    var addBusy by mutableStateOf(false)
        private set
    var addMessage by mutableStateOf<String?>(null)
        private set
    var lastInsert by mutableStateOf<LastInsert?>(null)
        private set
    /** One-line result after an undo (`추가를 취소했어요 …`), cleared on the next change. */
    var listNotice by mutableStateOf<String?>(null)
        private set

    init {
        appLog.event("app.start", mapOf("screen" to screen.name, "adapter" to NaviAdapterFactory.NAME, "baseUrl" to BuildConfig.BASE_URL))
        when (screen) {
            Screen.TODAY_PLAN -> loadPlans()
            Screen.VISIT_LIST, Screen.STOP_DETAIL -> {
                val id = saved.get<String>(KEY_PLAN)
                infoStopId = saved.get<String>(KEY_STOP)
                plan = id?.let { repo.cachedPlan(it) }
                plan?.value?.let { rememberStops(it) }
                refreshPlan(id)
                if (screen == Screen.STOP_DETAIL) currentStop()?.let { loadComplex(it) }
            }
            else -> Unit
        }
    }

    private fun initialScreen(): Screen {
        if (!repo.isLinked()) return Screen.LINK
        val s = saved.get<String>(KEY_SCREEN)?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: return Screen.TODAY_PLAN
        return when (s) {
            Screen.LINK -> Screen.TODAY_PLAN
            // A route request or an insert in flight cannot be resumed; NEARBY/고객 보기 reopen from the list.
            Screen.NAV_HANDOFF, Screen.NEARBY, Screen.CHANGE_CONFIRM, Screen.CUSTOMER_VIEW ->
                if (saved.get<String>(KEY_PLAN) != null) Screen.VISIT_LIST else Screen.TODAY_PLAN
            Screen.VISIT_LIST, Screen.STOP_DETAIL -> if (saved.get<String>(KEY_PLAN) != null) s else Screen.TODAY_PLAN
            else -> s
        }
    }

    private fun go(s: Screen) {
        if (screen != s) appLog.event("screen", mapOf("from" to screen.name, "to" to s.name))
        screen = s
        saved[KEY_SCREEN] = s.name
    }

    /**
     * Driving restriction from two sources (D4 item 6): the platform's CarUxRestrictions (`source = car-ux`) and, in
     * debug builds only, the `--ez otw_driving` intent override for captures (`source = debug-intent`). The override,
     * when set, wins and is logged as such; clearing it hands control back to the platform signal.
     */
    private var platformRestricted = false
    private var debugOverride: Boolean? = null
    private val carUx = kr.co.reevl.ontheway.car.CarUxGate(app) { restricted, detail ->
        viewModelScope.launch { onPlatformRestriction(restricted, detail) } // callbacks may arrive off the main thread
    }

    init {
        carUx.start() // after its declaration (Kotlin initialises in text order)
    }

    fun updateDriving(on: Boolean) = setDebugDriving(on)

    fun setDebugDriving(on: Boolean?) {
        debugOverride = on
        appLog.event("driving.override", mapOf("override" to on, "source" to "debug-intent"))
        applyDriving("debug-intent")
    }

    fun onPlatformRestriction(restricted: Boolean, detail: Map<String, Any?>) {
        platformRestricted = restricted
        appLog.event("driving.platform", mapOf("restricted" to restricted, "source" to "car-ux") + detail)
        applyDriving("car-ux")
    }

    private fun applyDriving(source: String) {
        val on = debugOverride ?: platformRestricted
        if (driving != on) appLog.event("driving", mapOf("restricted" to on, "source" to (if (debugOverride != null) "debug-intent" else source)))
        driving = on
        // Parked-only screens (NEARBY, CHANGE_CONFIRM, CUSTOMER_VIEW) stay where they are and draw the lock line instead
        // of their content — no screen jumps by itself while driving (Pleos: no automatic UI change while driving).
        if (on && screen in setOf(Screen.NEARBY, Screen.CHANGE_CONFIRM, Screen.CUSTOMER_VIEW)) appLog.event("driving.lock", mapOf("screen" to screen.name))
    }

    // ── LINK ──

    fun typeDigit(d: Char) {
        if (code.length < 6 && d.isDigit()) code += d
        linkMessage = null
    }

    fun deleteDigit() {
        code = code.dropLast(1)
    }

    fun submitCode() {
        if (linkBusy || code.length != 6) return
        linkBusy = true
        epoch.bump() // a new session starts with this code
        val e = epoch.current()
        track {
            val r = repo.link(code)
            linkBusy = false
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    code = ""
                    linkMessage = null
                    appLog.event("link.ok", mapOf("tokenBytesOnDisk" to tokenStore.sizeOnDisk()))
                    go(Screen.TODAY_PLAN)
                    loadPlans()
                }
                is RepoResult.Err -> {
                    code = ""
                    linkMessage = UserMessages.of(r.error)
                }
            }
        }
    }

    // ── TODAY_PLAN ──

    fun loadPlans() {
        if (plans == null) plans = repo.cachedPlans() // first frame from cache, labelled as cache
        plansLoading = true
        val e = epoch.current()
        track {
            val r = repo.plans()
            if (!epoch.isCurrent(e)) return@track // I-2(a)
            plansLoading = false
            when (r) {
                is RepoResult.Ok -> {
                    plans = r.value
                    plansMessage = null // the Synced notice carries the reason; UserMessages.screenLine adds the sync time
                    appLog.event("plans.shown", mapOf("count" to r.value.value.size, "fromCache" to r.value.fromCache,
                        "titles" to r.value.value.joinToString(" | ") { "${it.title} · ${it.stopCount}곳" }))
                    // Avatars for the first plan card: cached stops right away, then a fresh copy.
                    r.value.value.take(3).forEach { s -> repo.cachedPlan(s.id)?.value?.let { rememberStops(it) } }
                    r.value.value.firstOrNull()?.let { prefetchPlan(it.id, e) }
                }
                is RepoResult.Err -> handleError(r.error, onPlans = true)
            }
        }
    }

    /** TODAY_PLAN landscape: the selected plan's stops for the right pane (cache first, then one quiet fetch). */
    fun previewPlan(p: PlanSummary) {
        if (planStops[p.id] != null) return
        repo.cachedPlan(p.id)?.value?.let { rememberStops(it) }
        prefetchPlan(p.id, epoch.current())
    }

    private fun prefetchPlan(id: String, e: Long) = track {
        val r = repo.plan(id)
        if (!epoch.isCurrent(e)) return@track
        if (r is RepoResult.Ok) rememberStops(r.value.value)
    }

    private fun rememberStops(p: Plan) {
        planStops[p.id] = PlanLogic.ordered(p)
        planFull[p.id] = p
        p.stops.forEach { s -> s.cid?.let { cid -> repo.cachedComplex(cid)?.let { if (complexes[cid] == null) complexes[cid] = it } } }
        p.stops.mapNotNull { it.cid }.forEach { fetchComplexQuietly(it) }
    }

    fun openPlan(id: String) {
        saved[KEY_PLAN] = id
        plan = repo.cachedPlan(id)
        planMessage = null
        go(Screen.VISIT_LIST)
        refreshPlan(id)
    }

    // ── VISIT_LIST ──

    fun refreshPlan(id: String? = plan?.value?.id) {
        id ?: return
        planLoading = true
        val e = epoch.current()
        track {
            val r = repo.plan(id)
            if (!epoch.isCurrent(e)) return@track // I-2(a)
            if (saved.get<String>(KEY_PLAN) == id) planLoading = false
            when (r) {
                is RepoResult.Ok -> applyPlan(r.value, "plan.shown")
                is RepoResult.Err -> if (saved.get<String>(KEY_PLAN) == id) handleError(r.error, onPlans = false)
            }
        }
    }

    /** I-2(b)(c): only the plan the screen is on, never older than what is shown. */
    private fun applyPlan(incoming: Synced<Plan>, ev: String, extra: Map<String, Any?> = emptyMap()) {
        val wanted = saved.get<String>(KEY_PLAN)
        val shown = plan?.value
        if (!PlanGate.accept(wanted, shown, incoming.value)) {
            // Same plan but older (e.g. a cache copy while the PATCH answer is on screen): keep the newer plan, but
            // if the incoming copy says "we are offline now", the screen must say so — with the shown copy's time.
            if (incoming.value.id == wanted && shown != null && incoming.fromCache) {
                plan = plan!!.copy(fromCache = true, notice = incoming.notice)
            }
            appLog.event("plan.dropped", mapOf("wanted" to wanted, "got" to incoming.value.id,
                "gotVersion" to incoming.value.version, "shownVersion" to shown?.version))
            return
        }
        plan = incoming
        planMessage = null
        rememberStops(incoming.value)
        logPlan(ev, incoming, extra)
    }

    fun setVisitStatus(stop: PlanStop, visitStatus: String) {
        val p = plan?.value ?: return
        if (busyStopId != null) return
        busyStopId = stop.id
        lastInsert = null // another change: the insert can no longer be undone from the list line
        listNotice = null
        val e = epoch.current()
        track {
            val r = repo.setVisitStatus(p.id, stop.id, visitStatus)
            busyStopId = null
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                // Render what the server saved, never a local guess (v5 §9-4).
                is RepoResult.Ok -> applyPlan(Synced(r.value, System.currentTimeMillis(), fromCache = false), "plan.patched",
                    mapOf("stop" to stop.name, "visitStatus" to visitStatus))
                is RepoResult.Err -> handleError(r.error, onPlans = false)
            }
        }
    }

    fun backToPlans() {
        lastInsert = null
        listNotice = null
        go(Screen.TODAY_PLAN)
        loadPlans()
    }

    // ── STOP_DETAIL ──

    fun currentStop(): PlanStop? = plan?.value?.stops?.firstOrNull { it.id == infoStopId }

    fun openStop(stop: PlanStop) {
        infoStopId = stop.id
        saved[KEY_STOP] = stop.id
        complexMessage = null
        go(Screen.STOP_DETAIL)
        loadComplex(stop)
    }

    fun closeStop() = go(Screen.VISIT_LIST)

    /** Facts for STOP_DETAIL. A stop without a Reevl cid (office/custom) has none — the screen says so. */
    fun loadComplex(stop: PlanStop) {
        val cid = stop.cid ?: return
        if (complexes[cid] == null) repo.cachedComplex(cid)?.let { complexes[cid] = it }
        complexLoading = cid
        val e = epoch.current()
        track {
            val r = repo.complex(cid)
            if (!epoch.isCurrent(e)) return@track
            if (complexLoading == cid) complexLoading = null
            when (r) {
                is RepoResult.Ok -> {
                    complexes[cid] = r.value
                    if (infoStopId == stop.id) complexMessage = null
                    appLog.event("complex.shown", mapOf("cid" to cid, "fromCache" to r.value.fromCache,
                        "trades" to r.value.value.recentTrades?.size, "logo" to (r.value.value.brand.logoUrl != null)))
                }
                is RepoResult.Err -> when (r.error) {
                    RepoError.Stale -> Unit
                    RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                    else -> if (infoStopId == stop.id) complexMessage = UserMessages.of(r.error)
                }
            }
        }
    }

    /** Background fetch for list avatars: errors are not shown (the list still has names and initials). */
    private fun fetchComplexQuietly(cid: String) {
        val have = complexes[cid]
        if (have != null && !have.fromCache) return
        if (!complexInFlight.add(cid)) return
        val e = epoch.current()
        track {
            val r = repo.complex(cid)
            complexInFlight -= cid
            if (epoch.isCurrent(e) && r is RepoResult.Ok) complexes[cid] = r.value
        }
    }

    // ── NAV_HANDOFF ──

    fun guide(stop: PlanStop) {
        if (!PlanLogic.canGuide(stop)) return // button is disabled; CoordsGate would block anyway
        startGuide(Handoff(stop, HandoffPhase.Sending))
    }

    /**
     * v7 기능 4 `만남 장소로 안내`: the same NaviHelper path, Ruling 17 verification and NAV_HANDOFF result, sent to the
     * meeting point's server-verified coordinates (never a client-typed point — CoordsGate sees `verified`).
     */
    fun guideToMeeting(stop: PlanStop) {
        val mp = stop.meetingPoint
        if (!Schedule.canGuideToMeeting(mp) || mp == null) return
        val navi = stop.copy(id = "${stop.id}#meeting", kind = "meeting_point", name = mp.label, address = "",
            lat = mp.lat, lng = mp.lng, locationVerified = mp.verified)
        startGuide(Handoff(stop, HandoffPhase.Sending, navi = navi, title = "만남 장소 · ${mp.label}", target = GuideTarget.MEETING_POINT))
    }

    private fun startGuide(h: Handoff) {
        handoffJob?.cancel()
        // I-3: a new request replaces (cancels) the navigation's current route first — until the adapter verifies the
        // new one, nothing is "안내 중".
        activeGuide = null
        handoff = h
        go(Screen.NAV_HANDOFF)
        val stop = h.stop
        appLog.event("guide.request", mapOf("stop" to stop.name, "position" to stop.position, "target" to h.target.name,
            "lat" to h.navi.lat, "lng" to h.navi.lng))
        val e = epoch.current()
        handoffJob = viewModelScope.launch {
            navi.requestRoute(PlanLogic.toNaviStop(h.navi)).collect { ev ->
                if (!epoch.isCurrent(e)) return@collect
                when (ev) {
                    NaviEvent.Requested -> Unit
                    NaviEvent.RoutingCompleted -> {
                        handoff = h.copy(phase = HandoffPhase.Success(navi.lastRoute))
                        activeGuide = ActiveGuide(stop.id, h.title, navi.lastRoute, h.target)
                        appLog.event("guide.success", mapOf("stop" to stop.name, "target" to h.target.name,
                            "etaSource" to (if (navi.lastRoute?.reportedAtMs != null) "navi_verified" else "unavailable")))
                        delay(AUTO_BACK_MS)
                        if (screen == Screen.NAV_HANDOFF && handoff?.stop?.id == stop.id) closeHandoff()
                    }
                    is NaviEvent.Failed -> {
                        activeGuide = null // I-3
                        handoff = h.copy(phase = HandoffPhase.Failure(ev.code))
                        appLog.event("guide.failed", mapOf("stop" to stop.name, "target" to h.target.name, "code" to ev.code))
                    }
                }
            }
        }
    }

    fun retryHandoff() {
        handoff?.let { startGuide(it.copy(phase = HandoffPhase.Sending)) }
    }

    /**
     * The navigation's ETA for [stop] (to the stop or its meeting point), only from a verified guide of that stop —
     * `eta_source = navi_verified`; otherwise null and the screen says it cannot know.
     */
    fun etaFor(stop: PlanStop?): Schedule.Eta? {
        val g = activeGuide ?: return null
        if (stop == null || g.stopId != stop.id) return null
        return Schedule.etaFromNavi(g.route, verified = true)
    }

    fun today(): LocalDate = LocalDate.now(Schedule.KST)

    // ── v7 NEARBY (parked only) ──

    fun openNearby() {
        if (!DrivingPolicy.allows(ParkedAction.Nearby, driving)) return
        val p = plan?.value ?: return
        lastInsert = null
        listNotice = null
        nearbyMessage = null
        nearbyComplexes = null
        nearbyOffices = null
        nearbyRows = emptyList()
        val snap = location.snapshot()
        val anchor = Insert.anchor(p)
        nearbyCenter = when {
            snap != null -> NearbyCenter.Car(snap)
            anchor?.lat != null && anchor.lng != null -> NearbyCenter.AtStop(anchor, anchor.lat, anchor.lng)
            else -> null
        }
        appLog.event("nearby.open", mapOf("center" to when (nearbyCenter) { is NearbyCenter.Car -> "car"; is NearbyCenter.AtStop -> "stop"; null -> "none" },
            "heading" to (snap?.heading?.source?.name), "permission" to location.hasPermission()))
        go(Screen.NEARBY)
        loadNearby()
    }

    fun selectNearbyTab(t: NearbyTab) { nearbyTab = t }

    fun loadNearby() {
        val c = nearbyCenter ?: run { nearbyMessage = "현재 위치와 방문 중인 아파트 좌표를 모두 확인할 수 없어요."; return }
        nearbyLoading = true
        val e = epoch.current()
        track {
            val cx = viewModelScope.async { repo.nearbyComplexes(c.lat, c.lng) }
            val of = viewModelScope.async { repo.nearbyOffices(c.lat, c.lng) }
            val rc = cx.await()
            val ro = of.await()
            if (!epoch.isCurrent(e)) return@track
            nearbyLoading = false
            val inPlan = plan?.value?.stops?.mapNotNull { it.cid }?.toSet().orEmpty()
            when (rc) {
                is RepoResult.Ok -> {
                    nearbyComplexes = rc.value
                    nearbyRows = rowsFor(c, rc.value.items, inPlan)
                    appLog.event("nearby.complexes", mapOf("count" to rc.value.items.size,
                        "sides" to nearbyRows.joinToString(" | ") { "${it.c.name}:${it.side.name}:${it.c.distanceM}m${if (it.inPlan) ":inPlan" else ""}" }))
                    rc.value.items.forEach { it.cid.let { cid -> if (complexes[cid] == null) repo.cachedComplex(cid)?.let { s -> complexes[cid] = s } } }
                }
                is RepoResult.Err -> if (rc.error == RepoError.Expired || rc.error == RepoError.NotLinked) { handleError(rc.error, onPlans = false); return@track }
                    else nearbyMessage = UserMessages.of(rc.error)
            }
            when (ro) {
                is RepoResult.Ok -> {
                    nearbyOffices = ro.value
                    appLog.event("nearby.offices", mapOf("available" to ro.value.available, "count" to ro.value.items.size))
                }
                is RepoResult.Err -> if (nearbyMessage == null && ro.error != RepoError.Stale) nearbyMessage = UserMessages.of(ro.error)
            }
        }
    }

    private fun rowsFor(c: NearbyCenter, items: List<NearbyComplex>, inPlan: Set<String>): List<NearbyRow> {
        val sides: Map<String, NearbyEngine.Side> = when (c) {
            is NearbyCenter.Car -> NearbyEngine.classify(c.snapshot.fix, c.snapshot.heading?.deg,
                items.map { NearbyEngine.Place(it.cid, it.lat, it.lng) }).associate { it.id to it.side }
            is NearbyCenter.AtStop -> emptyMap() // no car heading → no side, only distance (never a guess)
        }
        return items.map { NearbyRow(it, sides[it.cid] ?: NearbyEngine.Side.UNKNOWN, it.cid in inPlan) }
    }

    fun closeNearby() = go(Screen.VISIT_LIST)

    fun pickComplex(r: NearbyRow) {
        if (r.inPlan || !DrivingPolicy.allows(ParkedAction.ChangeConfirm, driving)) return
        pendingAdd = PendingAdd(r.c.name, cid = r.c.cid, brand = r.c.brand,
            sub = listOfNotNull(NearbyEngine.sideLabel(r.side), NearbyEngine.straightLine(r.c.distanceM.toDouble())).joinToString(" · "))
        addMessage = null
        go(Screen.CHANGE_CONFIRM)
    }

    fun pickOffice(o: kr.co.reevl.ontheway.data.NearbyOffice) {
        if (!DrivingPolicy.allows(ParkedAction.ChangeConfirm, driving)) return
        pendingAdd = PendingAdd(o.name, officeId = o.id, sub = listOfNotNull(o.address, NearbyEngine.straightLine(o.distanceM.toDouble())).joinToString(" · "))
        addMessage = null
        go(Screen.CHANGE_CONFIRM)
    }

    fun cancelAdd() {
        pendingAdd = null
        go(Screen.NEARBY)
    }

    /** `추가` → POST stops after the current/last-visited stop; the list then shows the server's new order. */
    fun confirmAdd() {
        val p = plan?.value ?: return
        val a = pendingAdd ?: return
        if (addBusy || !DrivingPolicy.allows(ParkedAction.EditPlan, driving)) return
        addBusy = true
        addMessage = null
        val anchor = Insert.anchor(p)
        val e = epoch.current()
        track {
            val r = repo.addStop(p.id, AddStopRequest(cid = a.cid, officeId = a.officeId, insertAfterStopId = anchor?.id))
            addBusy = false
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    val added = Insert.addedStop(p, r.value)
                    applyPlan(Synced(r.value, System.currentTimeMillis(), fromCache = false), "plan.inserted",
                        mapOf("added" to a.name, "after" to anchor?.name, "kind" to if (a.officeId != null) "office" else "complex"))
                    lastInsert = added?.let { LastInsert(p.id, it.id, it.name) }
                    pendingAdd = null
                    listNotice = null
                    go(Screen.VISIT_LIST)
                }
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
                    else if (r.error != RepoError.Stale) addMessage = "방문 순서를 저장하지 못했어요. ${UserMessages.of(r.error)}"
            }
        }
    }

    /** `실행 취소`: DELETE the inserted stop → back to the order before. */
    fun undoInsert() {
        val li = lastInsert ?: return
        if (busyStopId != null) return
        busyStopId = li.stopId
        val e = epoch.current()
        track {
            val r = repo.deleteStop(li.planId, li.stopId)
            busyStopId = null
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    applyPlan(Synced(r.value, System.currentTimeMillis(), fromCache = false), "plan.undo", mapOf("removed" to li.name))
                    lastInsert = null
                    listNotice = "${li.name} 추가를 취소했어요 · 원래 순서로 돌아왔어요"
                }
                is RepoResult.Err -> handleError(r.error, onPlans = false)
            }
        }
    }

    // ── v7 고객 보기 (parked only) ──

    fun openCustomerView() {
        if (!DrivingPolicy.allows(ParkedAction.CustomerView, driving) || plan == null) return
        appLog.event("customer.open", emptyMap())
        go(Screen.CUSTOMER_VIEW)
    }

    fun closeCustomerView() = go(Screen.VISIT_LIST)

    fun closeHandoff() {
        // Leaving while still sending = we never learned the outcome → no 안내 중 (I-3).
        if (handoff?.phase == HandoffPhase.Sending) activeGuide = null
        handoffJob?.cancel()
        go(Screen.VISIT_LIST)
    }

    // ── logout / errors ──

    fun logout() {
        // I-2: cancel everything in flight and close the session BEFORE the network call, so no late answer of
        // this account can be applied or cached after the token is gone.
        handoffJob?.cancel()
        jobs.toList().forEach { it.cancel() }
        epoch.bump()
        viewModelScope.launch {
            val confirmed = repo.logout()
            clearSession()
            linkMessage = if (confirmed) null else
                "서버에 연결하지 못해 이 차의 연결 해제를 확인하지 못했어요. 휴대폰 리블 › 내 계정에서 연결된 차량을 해제해 주세요."
            appLog.event("logout", mapOf("serverConfirmed" to confirmed))
            go(Screen.LINK)
        }
    }

    private fun clearSession() {
        epoch.bump()
        saved.remove<String>(KEY_PLAN)
        saved.remove<String>(KEY_STOP)
        activeGuide = null
        plans = null
        plan = null
        handoff = null
        infoStopId = null
        plansMessage = null
        planMessage = null
        complexMessage = null
        complexLoading = null // D2 re-review Minor 2: no stale spinner after logout/relink
        complexes.clear()
        planStops.clear()
        complexInFlight.clear()
        planFull.clear()
        nearbyCenter = null
        nearbyComplexes = null
        nearbyRows = emptyList()
        nearbyOffices = null
        nearbyMessage = null
        nearbyLoading = false
        pendingAdd = null
        addBusy = false
        addMessage = null
        lastInsert = null
        listNotice = null
        plansLoading = false
        planLoading = false
        busyStopId = null
        code = ""
    }

    private fun handleError(e: RepoError, onPlans: Boolean) {
        if (e == RepoError.Stale) return // I-2: an ended session's answer — drop silently
        val text = UserMessages.of(e)
        appLog.event("error", mapOf("kind" to e.javaClass.simpleName, "shown" to text))
        if (onPlans) plansLoading = false else planLoading = false
        when (e) {
            RepoError.Expired, RepoError.NotLinked -> {
                jobs.toList().forEach { it.cancel() }
                clearSession() // the repository already cleared token + cache
                linkMessage = text
                go(Screen.LINK)
            }
            RepoError.PlanNotFound -> {
                plan = null // the repository dropped the cached copy too
                planMessage = text
            }
            else -> if (onPlans) plansMessage = text else planMessage = text
        }
    }

    private fun logPlan(ev: String, p: Synced<Plan>, extra: Map<String, Any?> = emptyMap()) {
        val next = PlanLogic.nextStop(p.value)
        appLog.event(ev, mapOf(
            "title" to p.value.title,
            "version" to p.value.version,
            "fromCache" to p.fromCache,
            "order" to PlanLogic.ordered(p.value).joinToString(" > ") { "${it.position}.${it.name}[${it.visitStatus}]" },
            "next" to next?.name,
        ) + extra)
    }

    override fun onCleared() {
        location.stop()
        carUx.stop()
        navi.release()
        super.onCleared()
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_PLAN = "planId"
        private const val KEY_STOP = "stopId"

        /** Success screen stays this long, then returns to the list by itself (a button does it sooner). */
        const val AUTO_BACK_MS = 6_000L
    }
}
