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
import kotlin.math.roundToInt
import kr.co.reevl.ontheway.data.AddStopRequest
import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.NearbyComplex
import kr.co.reevl.ontheway.data.NearbyComplexes
import kr.co.reevl.ontheway.data.NearbyOffices
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.search.AiSearch
import kr.co.reevl.ontheway.ui.state.CondUi
import kr.co.reevl.ontheway.ui.state.Notices
import kr.co.reevl.ontheway.ui.state.SearchConfirmUi
import kr.co.reevl.ontheway.ui.state.ToastUi
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.search.ComplexPick
import kr.co.reevl.ontheway.search.Favs
import kr.co.reevl.ontheway.search.PickSource
import kr.co.reevl.ontheway.data.FavsResponse
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
enum class Screen {
    LINK, HOME,
    /** Several plans → pick one first. */
    PLANS,
    VISIT_PLAN,
    /** 넣을 자리 (ConfirmTemplate). */
    PLAN_INSERT,
    NAV_HANDOFF,
    /** One complex (PaneTemplate) from AI 검색 / 주변 / 저장 / 오늘 방문. */
    DETAIL,
    NEARBY, AI_SEARCH,
    /** 1:1 window: the folded left menu. */
    MENU,
    /** 확인 필요 condition (e.g. 30평대) → ConfirmTemplate. */
    SEARCH_CONFIRM,
    SAVED, SETTINGS, UNLINK_CONFIRM,
    /** 설정 › 개인정보 안내 (the first-run notice, read again; 동의 철회) and 설정 › 오픈소스 라이선스. */
    PRIVACY, LICENSES,
    /** 비교 (≤ 3 complexes side by side) and its picker from 저장. */
    COMPARE, COMPARE_PICK,
    /** 지나가는 동네 (area-context-car-spec): 요약 · 히트맵 · 뉴스 · 주목 단지. Parked only, like every screen. */
    AREA,
    /** 휴대폰으로 넘기기 (link/PhoneHandoff): a QR to the REEVL web; what the phone makes shows up here. */
    PHONE_HANDOFF,
    /** 순서 최적화 미리보기 (plan/PlanGeo): 지금 │ 바꾸면, [적용] [취소] — a proposal, never applied quietly. */
    PLAN_OPTIMIZE,
}

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
                   val target: GuideTarget = GuideTarget.STOP,
                   /** Where `목록으로` / the auto-back goes: the visit list, or the AI 검색 result the guide started from. */
                   val returnTo: Screen = Screen.VISIT_PLAN)

enum class VoiceMode { Hint, Listening }

/** The stop the car's navigation is guiding to, as last verified by the adapter (Ruling 17). */
data class ActiveGuide(val stopId: String, val name: String, val route: RouteSummary?, val target: GuideTarget = GuideTarget.STOP)


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

/** One "방금 지나온 곳" row: the complex, the side it was on, when the car passed it (elapsedRealtime). */
data class PassedRow(val c: NearbyComplex, val side: NearbyEngine.Side, val atMs: Long)

/** One NEARBY 단지 row: the server's complex + the side computed on the car. */
data class NearbyRow(val c: NearbyComplex, val side: NearbyEngine.Side, val inPlan: Boolean)


/** What CHANGE_CONFIRM asks to insert. */
data class PendingAdd(val name: String, val cid: String? = null, val officeId: String? = null, val brand: CarBrand? = null,
                      val sub: String? = null)

/**
 * The QR handoff on screen. [startedAtMs] = elapsedRealtime when checking (re)started; [change] = what came from the phone
 * (the QR closes); [plan] = that plan in full (for the stop name and 안내 / 추가); [isNew] = the plan was not there before.
 */
data class PhoneState(val kind: kr.co.reevl.ontheway.link.PhoneHandoff.Kind, val url: String, val startedAtMs: Long,
                      val stopped: Boolean = false, val change: kr.co.reevl.ontheway.link.PhoneHandoff.Change? = null,
                      val plan: Plan? = null, val isNew: Boolean = true, val savedName: String? = null,
                      /** Wanted complex (from `추가`) — the plan result offers 추가 when it is not in the plan yet. */
                      val wantedCid: String? = null, val wantedName: String? = null,
                      val message: String? = null)

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
    /** Server calls so far (driving-context-spec §2.2 evidence). Declared before [repo]: the first load runs in an init block. */
    private val netCalls = java.util.concurrent.atomic.AtomicInteger()
    var netCount by mutableStateOf(0)
        private set
    private val repo = PlanRepository(
        api = Api(kr.co.reevl.ontheway.data.CountingTransport(OkHttpTransport(BuildConfig.BASE_URL)) { m, path -> onNetCall(m, path) }),
        tokens = tokenStore,
        cache = DiskPlanCache(File(app.noBackupFilesDir, "plan-cache")),
        complexCache = DiskComplexCache(File(app.noBackupFilesDir, "complex-cache")),
        favCache = kr.co.reevl.ontheway.data.DiskFavCache(File(app.noBackupFilesDir, "fav-cache")),
        syncStore = kr.co.reevl.ontheway.data.DiskSyncStore(File(app.noBackupFilesDir, "sync-cache")),
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
    // 음성 (voice/Voice.kt) — declared before anything that may toggle [driving] during init.
    val voiceIn: kr.co.reevl.ontheway.voice.VoiceInput = kr.co.reevl.ontheway.voice.AndroidVoiceInput(app) { e, f -> appLog.event(e, f) }
    /**
     * The 말하기 button is offered only when it can do something: in-app speech exists (mock flavor with a recogniser) or the
     * Gleo phrase may be shown (Features.GLEO). Pleos release today: neither → no button (no claim that does not work).
     */
    val voiceOffered: Boolean by lazy { Features.GLEO || voiceIn.available() }
    /** null = closed; Hint = the car has no recogniser for us → say it to Gleo; Listening = in-app speech running. */
    var voice by mutableStateOf<VoiceMode?>(null)
        private set
    var voiceHeard by mutableStateOf("")
        private set


    // LINK
    var code by mutableStateOf("")
        private set
    var linkBusy by mutableStateOf(false)
        private set
    var linkMessage by mutableStateOf<String?>(null)
        private set
    /** QR 연결 (2026-11-10): QR first, keypad one tap away. */
    var linkMode by mutableStateOf(kr.co.reevl.ontheway.link.LinkMode.QR)
        private set
    var qrPhase by mutableStateOf(kr.co.reevl.ontheway.link.QrPhase.IDLE)
        private set
    var qrShown by mutableStateOf<kr.co.reevl.ontheway.link.QrShown?>(null)
        private set
    /** The poll secret of [qrShown]: memory only — not in [saved], not in the log, not on screen. */
    private var qrSecret: String? = null
    private var qrJob: Job? = null
    private var qrStartJob: Job? = null
    /** Activity between onResume and onPause (the QR is polled only while it can be seen). */
    private var foreground = true

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
    /** Quiet loads (COMPARE columns) that failed with nothing cached — the column says so instead of loading forever. */
    val complexFailed = mutableStateMapOf<String, Boolean>()

    // NAV_HANDOFF
    var handoff by mutableStateOf<Handoff?>(null)
        private set
    private var handoffJob: Job? = null
    var activeGuide by mutableStateOf<ActiveGuide?>(null)
        private set

    // v7 tour: whole plans we have seen (TODAY_PLAN tour badge / first appointment come from the plan, not the summary)
    val planFull = mutableStateMapOf<String, Plan>()

    // v7 NEARBY / CHANGE_CONFIRM
    val location = CarLocation(app, { ev, f -> appLog.event(ev, f) }) { f -> onFix(f) }
    /** 전체 · 왼쪽 · 오른쪽 · 지나옴 */
    var nearbyTabIndex by mutableStateOf(0)
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
    /** Wall clock of the nearby answer on screen (the offline banner's `hh:mm 기준`). */
    var nearbyAtMs = 0L
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
    /** Where CHANGE_CONFIRM's 취소 goes back to (NEARBY, or the AI 검색 result it was opened from). */
    private var addReturn = Screen.NEARBY

    // AI 검색
    var searchQuery by mutableStateOf("")
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchResult by mutableStateOf<SearchResponse?>(null)
        private set
    var searchShown by mutableStateOf<AiSearch.Shown?>(null)
        private set
    var searchMessage by mutableStateOf<String?>(null)
        private set
    var searchCenter by mutableStateOf<NearbyCenter?>(null)
        private set
    var detailPick by mutableStateOf<ComplexPick?>(null)
        private set
    var detailAddBusy by mutableStateOf(false)
        private set
    var detailAddMessage by mutableStateOf<String?>(null)
        private set
    var searchSelected by mutableStateOf<String?>(null)
        private set
    var searchConfirm by mutableStateOf<kr.co.reevl.ontheway.ui.state.SearchConfirmUi?>(null)
        private set
    private var searchConfirmKey: String? = null
    private var searchJob: Job? = null

    // 관심 단지 / SAVED
    var favs by mutableStateOf<Synced<FavsResponse>?>(null)
        private set
    var favsLoading by mutableStateOf(false)
        private set
    var favsMessage by mutableStateOf<String?>(null)
        private set
    /** cid whose 저장 request is in flight (the star shows progress, not a guessed result). */
    var favBusy by mutableStateOf<String?>(null)
        private set
    var favMessage by mutableStateOf<String?>(null)
        private set
    /** Toggles the server confirmed since the last fresh list (cid → on). */
    private val favConfirmed = mutableStateMapOf<String, Boolean>()
    private var favCount by mutableStateOf<Int?>(null)
    var savedSelected by mutableStateOf<String?>(null)
        private set
    /** When the favs list was last asked for (elapsedRealtime) — app resume refreshes it at most every [Recent.SYNC_EVERY_MS]. */
    private var favsAskedAt: Long? = null

    // 휴대폰 ↔ 차 동기화 (2026-11-10, search/Recent.kt): 최근 본 단지 · 내 메모 · 저장한 조건. First frame from the device cache.
    var recent by mutableStateOf(repo.cachedRecent())
        private set
    /** Opened in this car, not on the server yet (shown at once, sent with the next account sync). */
    var recentPending by mutableStateOf(repo.pendingRecent())
        private set
    var memos by mutableStateOf(repo.cachedMemos())
        private set
    var screeners by mutableStateOf(repo.cachedScreeners())
        private set
    /** Last account sync (elapsedRealtime); null = not yet in this process. */
    private var accountSyncedAt: Long? = null
    /** 저장 screen tab: `saved` (저장한 단지) | `recent` (최근 본 단지). */
    var savedTab by mutableStateOf(SAVED_TAB)
        private set

    // DETAIL tabs / COMPARE (2026-11-10: every REEVL metric of the web detail, compare ≤ 3)
    var detailTab by mutableStateOf("summary")
        private set
    var compareTab by mutableStateOf("summary")
        private set
    private val compareStore: kr.co.reevl.ontheway.data.CompareStore =
        kr.co.reevl.ontheway.data.DiskCompareStore(File(app.noBackupFilesDir, "compare-tray.json"))
    var compareTray by mutableStateOf(compareStore.load())
        private set
    /** Exact saved state per cid from `GET /api/car/favs?cid=` (or a confirmed toggle). */
    private val favExact = mutableStateMapOf<String, Boolean>()

    // PLAN_INSERT
    var insertOptions by mutableStateOf<List<Insert.Option>>(emptyList())
        private set
    var insertIndex by mutableStateOf(0)
        private set
    private var insertAfter: String? = null

    var toastUi by mutableStateOf<kr.co.reevl.ontheway.ui.state.ToastUi?>(null)
        private set

    init {
        kr.co.reevl.ontheway.plan.Tour.uiEnabled = BuildConfig.TOUR_MODE
        appLog.event("app.start", mapOf("tourMode" to BuildConfig.TOUR_MODE, "screen" to screen.name, "adapter" to NaviAdapterFactory.NAME, "baseUrl" to BuildConfig.BASE_URL))
        when (screen) {
            Screen.HOME -> loadPlans()
            Screen.VISIT_PLAN -> {
                val id = saved.get<String>(KEY_PLAN)
                plan = id?.let { repo.cachedPlan(it) }
                plan?.value?.let { rememberStops(it) }
                refreshPlan(id)
                loadPlans()
            }
            Screen.LINK -> ensureQr()
            else -> Unit
        }
    }

    private fun initialScreen(): Screen {
        if (!repo.isLinked()) return Screen.LINK
        val s = saved.get<String>(KEY_SCREEN)?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
        // Only the plan reopens where it was; everything else (requests, inserts, locations) starts again from HOME.
        return if (s == Screen.VISIT_PLAN && saved.get<String>(KEY_PLAN) != null) Screen.VISIT_PLAN else Screen.HOME
    }

    /** Back stack (car-ds-v2: ‹ always returns to the screen it came from). */
    private val backStack = ArrayDeque<Screen>()

    private fun set(s: Screen) {
        val was = screen
        if (screen != s) appLog.event("screen", mapOf("from" to screen.name, "to" to s.name))
        screen = s
        saved[KEY_SCREEN] = s.name
        if (s == Screen.LINK) ensureQr()
        syncQrPolling()
        if (was == Screen.PHONE_HANDOFF && s != Screen.PHONE_HANDOFF) leavePhone()
        syncPhonePolling()
    }

    private fun go(s: Screen) {
        if (screen == s) return
        backStack.addLast(screen)
        set(s)
    }

    /** Clears the stack (link, logout, 401) and shows [s]. */
    private fun reset(s: Screen) {
        backStack.clear()
        set(s)
    }

    fun goBack() {
        val prev = backStack.removeLastOrNull() ?: Screen.HOME
        set(if (prev == Screen.LINK && repo.isLinked()) Screen.HOME else prev)
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
        handleDrive(drive.ux(restricted, nowEr()))
    }

    private fun applyDriving(source: String) {
        val on = debugOverride ?: platformRestricted
        val was = driving
        if (was != on) appLog.event("driving", mapOf("restricted" to on, "source" to (if (debugOverride != null) "debug-intent" else source)))
        driving = on
        if (on && voice != null) closeVoice() // the lock covers everything: no listening, no panel under it
        // R8 (P0-3): the car has moved while locked — NEARBY is recomputed for where it stopped (data only; the list is
        // another place's, so it starts at the top).
        if (DrivingPolicy.reloadNearbyOnUnlock(was, on, screen)) {
            appLog.event("nearby.unlock_reload", mapOf("source" to source))
            loadNearbyHere(force = true)
        }
        if (was && !on) {
            // Requests held back while restricted (driving-context-spec §2.2): the stop event first, then a deferred plan refresh.
            pendingStop?.let { pendingStop = null; runStop(it) }
            if (deferredPlans) { deferredPlans = false; loadPlans() }
        }
        // QR 연결: no polling while locked; a QR that could not be started while locked is started on unlock.
        if (was && !on && screen == Screen.LINK) ensureQr()
        syncQrPolling()
        syncPhonePolling() // 휴대폰으로 넘기기: no checks while locked, resumes on unlock
        // Parked-only screens (NEARBY, CHANGE_CONFIRM, CUSTOMER_VIEW) stay where they are and draw the lock line instead
        // of their content — no screen jumps by itself while driving (Pleos: no automatic UI change while driving).
        // Lifestyle app: every screen becomes the lock screen; the screen underneath (and its scroll) stays as it was.
        if (on) appLog.event("driving.lock", mapOf("screen" to screen.name))
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
                    reset(Screen.HOME)
                    loadPlans()
                }
                is RepoResult.Err -> {
                    code = ""
                    linkMessage = UserMessages.of(r.error)
                }
            }
        }
    }

    // ── LINK · QR (device flow; server reevl lib/carAuth.ts) ──

    fun useQrLink() {
        linkMode = kr.co.reevl.ontheway.link.LinkMode.QR
        linkMessage = null
        appLog.event("link.mode", mapOf("mode" to "qr"))
        ensureQr()
        syncQrPolling()
    }

    fun useCodeLink() {
        linkMode = kr.co.reevl.ontheway.link.LinkMode.CODE
        linkMessage = null
        appLog.event("link.mode", mapOf("mode" to "code"))
        syncQrPolling()
    }

    /** 새 QR (after expiry or a failed start). */
    fun newQr() = startQr()

    /** Start a request when the QR screen shows and none is alive. An expired one waits for 새 QR (no endless new rows). */
    private fun ensureQr() {
        if (screen != Screen.LINK || linkMode != kr.co.reevl.ontheway.link.LinkMode.QR) return
        if (qrPhase == kr.co.reevl.ontheway.link.QrPhase.IDLE) startQr()
    }

    private fun startQr() {
        if (qrStartJob?.isActive == true) return
        if (driving) { appLog.event("qr.start_skipped", mapOf("reason" to "driving")); return }
        qrJob?.cancel(); qrJob = null
        qrSecret = null
        qrShown = null
        qrPhase = kr.co.reevl.ontheway.link.QrPhase.STARTING
        linkMessage = null
        val e = epoch.current()
        qrStartJob = track {
            val r = repo.qrStart()
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    val v = r.value
                    if (!kr.co.reevl.ontheway.link.QrLink.validId(v.requestId)) {
                        qrPhase = kr.co.reevl.ontheway.link.QrPhase.FAILED; linkMessage = UserMessages.SERVER; return@track
                    }
                    qrSecret = v.pollSecret
                    qrShown = kr.co.reevl.ontheway.link.QrShown(v.requestId, kr.co.reevl.ontheway.link.QrLink.url(v.requestId), v.match, v.expiresAt)
                    qrPhase = kr.co.reevl.ontheway.link.QrPhase.SHOWING
                    appLog.event("qr.shown", mapOf("expiresInS" to (v.expiresAt - System.currentTimeMillis()) / 1000)) // no id, no secret
                    syncQrPolling()
                }
                is RepoResult.Err -> {
                    qrPhase = kr.co.reevl.ontheway.link.QrPhase.FAILED
                    linkMessage = UserMessages.of(r.error)
                }
            }
        }
    }

    /** Start or stop the 3 s poll so that it runs ONLY while the QR is on screen, in front, and not driving-locked. */
    private fun syncQrPolling() {
        val q = qrShown
        val want = kr.co.reevl.ontheway.link.QrLink.shouldPoll(screen == Screen.LINK, linkMode == kr.co.reevl.ontheway.link.LinkMode.QR,
            foreground, driving, qrPhase == kr.co.reevl.ontheway.link.QrPhase.SHOWING, System.currentTimeMillis(), q?.expiresAt)
        if (want && qrJob?.isActive != true) {
            appLog.event("qr.poll", mapOf("on" to true))
            qrJob = track { pollQr() }
        } else if (!want && qrJob != null) {
            if (qrJob?.isActive == true) appLog.event("qr.poll", mapOf("on" to false, "driving" to driving, "foreground" to foreground))
            qrJob?.cancel(); qrJob = null
        }
        // Came back after it ran out (background / driving): show 새 QR instead of a dead code.
        if (q != null && qrPhase == kr.co.reevl.ontheway.link.QrPhase.SHOWING && kr.co.reevl.ontheway.link.QrLink.expired(System.currentTimeMillis(), q.expiresAt)) qrExpired()
    }

    private fun qrExpired() {
        qrPhase = kr.co.reevl.ontheway.link.QrPhase.EXPIRED
        qrSecret = null
        appLog.event("qr.expired", emptyMap())
    }

    private suspend fun pollQr() {
        val q = qrShown ?: return
        val secret = qrSecret ?: return
        while (true) {
            kotlinx.coroutines.delay(kr.co.reevl.ontheway.link.QrLink.POLL_MS)
            if (qrShown?.requestId != q.requestId) return
            if (kr.co.reevl.ontheway.link.QrLink.expired(System.currentTimeMillis(), q.expiresAt)) { qrExpired(); return }
            val r = repo.qrPoll(q.requestId, secret)
            if (qrShown?.requestId != q.requestId) return
            when (r) {
                is RepoResult.Ok -> when (r.value) {
                    kr.co.reevl.ontheway.data.QrPoll.Pending -> if (linkMessage != null) linkMessage = null
                    kr.co.reevl.ontheway.data.QrPoll.Expired -> { qrExpired(); return }
                    kr.co.reevl.ontheway.data.QrPoll.Linked -> { onQrLinked(); return }
                }
                // Offline / 5xx / 429: keep the QR and keep polling (the line says why); Storage: the token could not be kept.
                is RepoResult.Err -> {
                    linkMessage = UserMessages.of(r.error)
                    if (r.error == RepoError.Storage) { qrPhase = kr.co.reevl.ontheway.link.QrPhase.FAILED; qrSecret = null; return }
                }
            }
        }
    }

    private fun onQrLinked() {
        epoch.bump() // a new session starts with this approval
        qrSecret = null
        qrShown = null
        qrPhase = kr.co.reevl.ontheway.link.QrPhase.IDLE
        qrJob = null
        linkMessage = null
        appLog.event("link.ok", mapOf("via" to "qr", "tokenBytesOnDisk" to tokenStore.sizeOnDisk()))
        reset(Screen.HOME)
        loadPlans()
    }

    // ── TODAY_PLAN ──

    private var plansLoadedAt = 0L

    /**
     * The user asked for 오늘 방문 (menu row): plans made on the phone since the app started show up. Before (QA round 2)
     * plans were fetched only at app start / link / unlock, so HOME's own promise "휴대폰 리블에서 계획을 만들면 여기에 보여요"
     * never came true in a running app. Budget (driving-context-spec §2.2 계획·저장: 앱 열림·⟳·쓰기 후, 30초 디바운스):
     * a user action, ≥ 30 s apart; never from a window change or a return from the background (§15.4: 0 requests).
     */
    fun refreshPlansOnAsk(nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        if (PlanLogic.plansStale(plansLoadedAt, nowMs)) loadPlans()
    }

    fun loadPlans() {
        plansLoadedAt = android.os.SystemClock.elapsedRealtime()
        if (plans == null) plans = repo.cachedPlans() // first frame from cache, labelled as cache
        if (driving) { deferredPlans = true; netSkip("plans"); return }
        loadFavs() // home tile `저장 N곳`
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
                    (PlanLogic.homePlan(r.value.value, today()) ?: r.value.value.firstOrNull())?.let { prefetchPlan(it.id, e) }
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
        detailPick = null
        infoStopId = null
        saved[KEY_PLAN] = id
        plan = repo.cachedPlan(id)
        planMessage = null
        go(Screen.VISIT_PLAN)
        ensurePlanSelection()
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
        if (screen == Screen.VISIT_PLAN || screen == Screen.HOME) ensurePlanSelection()
        logPlan(ev, incoming, extra)
    }

    fun setVisitStatus(stop: PlanStop, visitStatus: String, onSaved: (() -> Unit)? = null, onFailed: (() -> Unit)? = null) {
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
                is RepoResult.Ok -> {
                    applyPlan(Synced(r.value, System.currentTimeMillis(), fromCache = false), "plan.patched",
                        mapOf("stop" to stop.name, "visitStatus" to visitStatus))
                    onSaved?.invoke()
                }
                is RepoResult.Err -> { handleError(r.error, onPlans = false); if (r.error != RepoError.Stale) onFailed?.invoke() }
            }
        }
    }

    fun backToPlans() {
        lastInsert = null
        listNotice = null
        goBack()
    }

    // ── STOP_DETAIL ──


    /** A plan stop → the same complex pane, actions 안내 · 완료. */
    fun openStop(stop: PlanStop) {
        infoStopId = stop.id
        saved[KEY_STOP] = stop.id
        openDetail(ComplexPick.fromStop(stop, stop.cid?.let { complexes[it]?.value?.brand }))
    }

    /** Wide plan list: the stop shown in the preview. */
    fun selectStop(stop: PlanStop) {
        infoStopId = stop.id
        showPick(ComplexPick.fromStop(stop, stop.cid?.let { complexes[it]?.value?.brand }))
    }


    /** Facts for STOP_DETAIL. A stop without a Reevl cid (office/custom) has none — the screen says so. */

    /** Background fetch for list avatars: errors are not shown (the list still has names and initials). */
    private fun fetchComplexQuietly(cid: String) {
        if (driving) { netSkip("complex"); return }
        val have = complexes[cid]
        if (have != null && !have.fromCache) return
        if (!complexInFlight.add(cid)) return
        val e = epoch.current()
        track {
            val r = repo.complex(cid)
            complexInFlight -= cid
            if (epoch.isCurrent(e) && r is RepoResult.Ok) { complexes[cid] = r.value; complexFailed -= cid }
            if (epoch.isCurrent(e) && r is RepoResult.Err && r.error != RepoError.Stale) complexFailed[cid] = true
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
        // Already on NEARBY = the ⟳ button (same entry): a forced refresh, debounced 10 s per cell by [nearbyCache].
        val refresh = screen == Screen.NEARBY
        lastInsert = null
        listNotice = null
        if (!refresh) nearbyTabIndex = 0
        go(Screen.NEARBY)
        loadNearbyHere(force = refresh)
    }

    /** Centre on the car (or, without a fix, the open plan's current stop) and load. Also used quietly by HOME. */
    fun loadNearbyHere(force: Boolean = false) {
        nearbyMessage = null
        val snap = location.snapshot()
        val anchor = plan?.value?.let { Insert.anchor(it) }
        nearbyCenter = when {
            snap != null -> NearbyCenter.Car(snap)
            anchor?.lat != null && anchor.lng != null -> NearbyCenter.AtStop(anchor, anchor.lat, anchor.lng)
            else -> null
        }
        appLog.event("nearby.open", mapOf("center" to when (nearbyCenter) { is NearbyCenter.Car -> "car"; is NearbyCenter.AtStop -> "stop"; null -> "none" },
            "heading" to (snap?.heading?.source?.name), "permission" to location.hasPermission()))
        if (nearbyCenter != null) loadNearby(force)
    }

    fun selectNearbyTab(i: Int) { nearbyTabIndex = i }

    /**
     * One request at most (driving-context-spec §2.2 / pleos-signals §4.2): never while restricted, one answer per ~100 m cell for
     * 10 min, a forced refresh of the same cell debounced 10 s. The 중개사무소 call is gone (no tab uses it; its data was empty).
     */
    fun loadNearby(force: Boolean = false) {
        val c = nearbyCenter ?: run { nearbyMessage = "현재 위치와 방문 중인 아파트 좌표를 모두 확인할 수 없어요."; return }
        if (driving) { netSkip("nearby"); return }
        nearbyLoading = true
        val e = epoch.current()
        track {
            val a = nearbyCache.get(c.lat, c.lng, NEARBY_RADIUS_M, nowEr(), force)
            if (!epoch.isCurrent(e)) return@track
            nearbyLoading = false
            when (val rc = a.result) {
                is RepoResult.Ok -> {
                    applyNearby(c, rc.value, a.cached)
                    if (screen == Screen.NEARBY && detailPick?.source != kr.co.reevl.ontheway.search.PickSource.NEARBY) nearbyRows.firstOrNull()?.let { pickComplex(it, open = false) }
                }
                is RepoResult.Err -> if (rc.error == RepoError.Expired || rc.error == RepoError.NotLinked) { handleError(rc.error, onPlans = false); return@track }
                    else if (rc.error != RepoError.Stale) nearbyMessage = UserMessages.of(rc.error)
            }
        }
    }

    /** Shows a nearby answer around [c]. A cached answer was centred elsewhere in the cell: distances are redone from [c]. */
    private fun applyNearby(c: NearbyCenter, value: NearbyComplexes, cached: Boolean) {
        val v = if (!cached) value else value.copy(items = value.items.map {
            it.copy(distanceM = NearbyEngine.haversine(c.lat, c.lng, it.lat, it.lng).roundToInt())
        }.sortedBy { it.distanceM })
        nearbyComplexes = v
        if (!cached || nearbyAtMs == 0L) nearbyAtMs = System.currentTimeMillis()
        nearbyMessage = null
        val inPlan = planCids()
        nearbyRows = rowsFor(c, v.items, inPlan)
        appLog.event("nearby.complexes", mapOf("count" to v.items.size, "cached" to cached,
            "sides" to nearbyRows.joinToString(" | ") { "${it.c.name}:${it.side.name}:${it.c.distanceM}m${if (it.inPlan) ":inPlan" else ""}" }))
        v.items.forEach { it.cid.let { cid -> if (complexes[cid] == null) repo.cachedComplex(cid)?.let { s -> complexes[cid] = s } } }
    }

    /**
     * Complexes in "the plan": the one opened, else the one HOME shows as 오늘 방문 (same fallback as [arrivalStops]).
     * QA round 5: with only HOME's plan (never opened), 주변/검색 lost the `계획` badge and 상세 offered `추가` for a stop
     * already in today's plan.
     */
    fun planCids(): Set<String> = PlanLogic.planCids(plan?.value, plans?.value?.let { PlanLogic.homePlan(it, today()) }?.let { planFull[it.id] })

    /** Wide plan list: keep a stop in the preview (the next one by default). */
    private fun ensurePlanSelection() {
        val p = plan?.value ?: return
        if (detailPick?.stopId != null && p.stops.any { it.id == detailPick?.stopId }) return
        val s = p.stops.firstOrNull { it.id == infoStopId } ?: PlanLogic.nextStop(p) ?: PlanLogic.ordered(p).firstOrNull() ?: return
        if (screen == Screen.VISIT_PLAN) selectStop(s)
    }

    private fun rowsFor(c: NearbyCenter, items: List<NearbyComplex>, inPlan: Set<String>): List<NearbyRow> {
        val sides: Map<String, NearbyEngine.Side> = when (c) {
            is NearbyCenter.Car -> NearbyEngine.classify(c.snapshot.fix, c.snapshot.heading?.deg,
                items.map { NearbyEngine.Place(it.cid, it.lat, it.lng) }).associate { it.id to it.side }
            is NearbyCenter.AtStop -> emptyMap() // no car heading → no side, only distance (never a guess)
        }
        return items.map { NearbyRow(it, sides[it.cid] ?: NearbyEngine.Side.UNKNOWN, it.cid in inPlan) }
    }

    fun closeNearby() = goBack()

    /** Compact: open the complex; wide: show it in the preview. */
    fun pickComplex(r: NearbyRow, open: Boolean) {
        if (!DrivingPolicy.allows(ParkedAction.Search, driving)) return
        val p = ComplexPick.fromNearby(r.c, r.side)
        if (open) openDetail(p) else showPick(p)
    }


    fun cancelAdd() {
        pendingAdd = null
        goBack()
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
            val after = insertAfter ?: anchor?.id
            val r = repo.addStop(p.id, AddStopRequest(cid = a.cid, officeId = a.officeId, insertAfterStopId = after))
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
                    reset(Screen.HOME)
                    go(Screen.VISIT_PLAN)
                    toast("넣었어요", "되돌리기") { undoInsert() }
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
                    listNotice = null
                    toast("되돌렸어요")
                }
                is RepoResult.Err -> handleError(r.error, onPlans = false)
            }
        }
    }

    // ── v7 고객 보기 (parked only) ──



    fun closeHandoff() {
        // Leaving while still sending = we never learned the outcome → no 안내 중 (I-3).
        if (handoff?.phase == HandoffPhase.Sending) activeGuide = null
        handoffJob?.cancel()
        goBack()
    }

    // ── AI 검색 ──

    fun openAiSearch() {
        if (screen != Screen.AI_SEARCH) {
            // A fresh visit starts clean: an old answer (or an old error) is never shown as if it were about now.
            searchJob?.cancel()
            searchLoading = false
            searchResult = null
            searchShown = null
            searchMessage = null
            searchSelected = null
            searchQuery = "" // with no answer on screen an old query read as if it had just run (QA round 3)
        }
        appLog.event("search.open", mapOf("from" to screen.name, "permission" to location.hasPermission()))
        go(Screen.AI_SEARCH)
    }

    // ── 음성 (voice first, voice/Voice.kt) ──

    /** 말하기 (parked only). Opens AI 검색 and listens when a recogniser exists, else shows what to say to Gleo. */
    fun startVoice(permitted: Boolean = true) {
        if (driving) return
        if (screen != Screen.AI_SEARCH) openAiSearch()
        typeQuery("") // a voice request starts clean: no old query + 찾기 above the panel (QA round 3)
        voiceHeard = ""
        val can = voiceIn.available()
        appLog.event("voice.start", mapOf("recognizer" to can, "permitted" to permitted))
        // Without Gleo (not approved yet, Features.GLEO) there is no phrase to show: the panel closes instead of the Gleo hint.
        val fallback = if (Features.GLEO) VoiceMode.Hint else null
        if (!can || !permitted) { voice = fallback; if (!permitted && fallback == null) toast("마이크 권한이 없어 들을 수 없어요"); return }
        voice = VoiceMode.Listening
        voiceIn.listen(onPartial = { voiceHeard = it }, onText = { t -> voice = null; typeQuery(t); runSearch(t) },
            onEnd = { e -> voice = fallback; if (e == kr.co.reevl.ontheway.voice.VoiceEnd.NoMatch) toast("잘 듣지 못했어요") })
    }

    fun closeVoice() { voiceIn.cancel(); voice = null; voiceHeard = "" }

    fun closeAiSearch() {
        closeVoice()
        searchJob?.cancel()
        searchLoading = false
        goBack()
    }

    /**
     * HOME's field is an entry, not the AI 검색 query (QA round 2: after a search HOME kept showing the old query with
     * `찾기`, hiding `말하기`). Typed text goes to AI 검색 on submit and the HOME field empties again.
     */
    var homeQuery by mutableStateOf("")
        private set

    fun typeHomeQuery(q: String) { homeQuery = q.take(AiSearch.MAX_QUERY) }

    fun typeQuery(q: String) {
        searchQuery = q.take(AiSearch.MAX_QUERY)
        if (q.isEmpty()) { searchJob?.cancel(); searchLoading = false; searchResult = null; searchShown = null; searchMessage = null }
    }

    /** Runs [q] around the car; sends a heading only when the car trusts it (then the server applies sides). */
    fun runSearch(q: String = searchQuery, radiusM: Int = AiSearch.DEFAULT_RADIUS_M) {
        val query = AiSearch.normalize(q) ?: return
        if (voice != null) closeVoice()
        searchQuery = query
        searchJob?.cancel()
        searchResult = null
        searchShown = null
        searchMessage = null
        searchSelected = null
        val snap = location.snapshot()
        val anchor = plan?.value?.let { Insert.anchor(it) }
        val center = when {
            snap != null -> NearbyCenter.Car(snap)
            anchor?.lat != null && anchor.lng != null -> NearbyCenter.AtStop(anchor, anchor.lat, anchor.lng)
            else -> null
        }
        searchCenter = center
        if (center == null) {
            searchLoading = false
            searchMessage = "위치를 확인하지 못했어요"
            appLog.event("search.noLocation", mapOf("qLen" to query.length, "permission" to location.hasPermission()))
            return
        }
        val heading = (center as? NearbyCenter.Car)?.snapshot?.heading
        val fix = (center as? NearbyCenter.Car)?.snapshot?.fix
        val trusted = heading?.deg?.takeIf { AiSearch.directionKnown(fix, it) }
        searchLoading = true
        val started = System.currentTimeMillis()
        val e = epoch.current()
        appLog.event("search.request", mapOf("qLen" to query.length, "radiusM" to radiusM, "heading" to (trusted != null),
            "center" to (if (center is NearbyCenter.Car) "car" else "stop")))
        searchJob = track {
            val r = repo.search(query, center.lat, center.lng, radiusM, AiSearch.LIMIT, trusted)
            if (!epoch.isCurrent(e)) return@track
            searchLoading = false
            val ms = System.currentTimeMillis() - started
            when (r) {
                is RepoResult.Ok -> {
                    val shown = AiSearch.shown(r.value.items, r.value.conds, fix, heading?.deg, heading?.estimated == true)
                    searchResult = r.value
                    searchShown = shown
                    r.value.items.forEach { it.cid.let { cid -> if (complexes[cid] == null) repo.cachedComplex(cid)?.let { s -> complexes[cid] = s } } }
                    shown.rows.firstOrNull()?.let { selectSearch(it.item.cid) }
                    appLog.event("search.result", mapOf("qLen" to query.length, "latencyMs" to ms, "scanned" to r.value.scanned,
                        "matched" to r.value.matched, "items" to r.value.items.size, "shown" to shown.rows.size, "poolCapped" to r.value.poolCapped,
                        "conds" to r.value.conds.joinToString("|") { it.status }, "serverSides" to r.value.items.any { it.side != null },
                        "emptyReason" to r.value.emptyReason))
                }
                is RepoResult.Err -> {
                    appLog.event("search.failed", mapOf("qLen" to query.length, "latencyMs" to ms, "kind" to r.error.javaClass.simpleName))
                    when (r.error) {
                        RepoError.Stale -> Unit
                        RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                        else -> searchMessage = Notices.failed(r.error)
                    }
                }
            }
        }
    }

    /** Drop one condition and search again with the remaining condition words (the server re-reads them). */
    fun dropCondition(key: String?) {
        val conds = searchResult?.conds?.filter { it.label.isNotBlank() } ?: return
        val left = if (key == null) conds.dropLast(1) else conds.filter { it.key != key }
        val q = left.joinToString(" ") { it.label }.ifBlank { "주변 단지" }
        typeQuery(q)
        runSearch(q)
    }

    /** A 확인 필요 chip: area words get the three-way question; anything else 빼기/취소. */
    fun openCondition(c: CondUi) {
        if (!c.warn) { dropCondition(c.key); return }
        searchConfirm = SearchConfirmUi(c.label, AiSearch.confirmOptions(c.label), 0)
        searchConfirmKey = c.key
        go(Screen.SEARCH_CONFIRM)
    }

    fun pickConfirmOption(i: Int) { searchConfirm = searchConfirm?.copy(selected = i) }

    fun applyConfirm() {
        val c = searchConfirm ?: return
        val key = searchConfirmKey
        val choice = c.options.getOrNull(c.selected)
        goBack()
        val rest = searchResult?.conds?.filter { it.key != key && it.label.isNotBlank() }?.map { it.label }.orEmpty()
        val q = (rest + listOfNotNull(choice?.takeIf { it != AiSearch.DROP })).joinToString(" ").ifBlank { "주변 단지" }
        typeQuery(q)
        runSearch(q)
    }

    fun cancelConfirm() = goBack()

    private fun searchRow(cid: String) = searchShown?.rows?.firstOrNull { it.item.cid == cid }

    fun selectSearch(cid: String) { searchRow(cid)?.let { searchSelected = cid; showPick(ComplexPick.fromSearch(it)) } }

    fun openSearchItem(cid: String) { searchRow(cid)?.let { searchSelected = cid; openDetail(ComplexPick.fromSearch(it)) } }

    // ── the complex pane: DETAIL screen or the wide window's preview ──

    /** Shows [p] in the preview (wide lists) without changing screen; loads facts and the exact saved state. */
    private fun showPick(p: ComplexPick) {
        if (detailPick?.cid == p.cid && detailPick?.source == p.source) { detailPick = p; return }
        detailPick = p
        detailAddMessage = null
        favMessage = null
        if (p.known && p.cid.contains('|')) loadDetailComplex(p.cid) else { complexLoading = null; complexMessage = null }
        // pleos-signals §6.1-4: the saved state comes from the favs list loaded at start; `?cid=` only when the list cannot
        // answer (not loaded yet, or capped at 100 without this cid).
        if (p.cid.contains('|') && favState(p.cid) == null) loadFavStatus(p.cid)
    }

    private fun openDetail(p: ComplexPick) {
        appLog.event("detail.open", mapOf("source" to p.source.name, "known" to p.known))
        noteViewed(p)
        if (detailPick?.cid != p.cid) detailTab = "summary"
        showPick(p)
        go(Screen.DETAIL)
    }

    fun closeDetail() = goBack()

    private fun loadDetailComplex(cid: String) {
        if (complexes[cid] == null) repo.cachedComplex(cid)?.let { complexes[cid] = it }
        complexLoading = cid
        complexMessage = null
        val e = epoch.current()
        track {
            val r = repo.complex(cid)
            if (!epoch.isCurrent(e)) return@track
            if (complexLoading == cid) complexLoading = null
            when (r) {
                is RepoResult.Ok -> { complexes[cid] = r.value; if (detailPick?.cid == cid) complexMessage = null }
                is RepoResult.Err -> when (r.error) {
                    RepoError.Stale -> Unit
                    RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                    else -> if (detailPick?.cid == cid) complexMessage = UserMessages.of(r.error)
                }
            }
        }
    }

    /** `안내`: a plan stop goes as the plan stop; any other complex with server coordinates as a verified cid stop. */
    fun guideDetail() {
        val p = detailPick ?: return
        val planStop = p.stopId?.let { id -> plan?.value?.stops?.firstOrNull { it.id == id } }
        if (planStop != null) { guide(planStop); return }
        val stop = p.stop()
        if (!PlanLogic.canGuide(stop)) return
        startGuide(Handoff(stop, HandoffPhase.Sending, returnTo = screen))
    }

    /** Secondary pane action: `완료` for a planned stop, else `추가`. */
    fun paneSecondary() {
        val p = detailPick ?: return
        val planStop = p.stopId?.let { id -> plan?.value?.stops?.firstOrNull { it.id == id } }
        if (planStop != null) setVisitStatus(planStop, PlanLogic.VISITED) else addDetail()
    }

    /**
     * `추가`: the plan open now (or today's first plan) → PLAN_INSERT (어디에 넣을까요?) → POST stops.
     * No plan at all → the phone handoff QR (make the plan on the phone; it comes back here and `추가` continues).
     * Never a server endpoint the server does not have.
     */
    fun addDetail() {
        val p = detailPick ?: return
        if (!p.known || detailAddBusy) return
        detailAddMessage = null
        addReturn = screen
        pendingAdd = PendingAdd(p.displayName, cid = p.cid, brand = p.brand,
            sub = listOfNotNull(p.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }).joinToString(" · ").ifEmpty { null })
        addMessage = null
        if (plan?.value != null) { openInsert(); return }
        val target = plans?.value?.let { PlanLogic.addTarget(it, today()) }
        if (target == null) {
            // Was a bare "계획이 없어요" toast (user 2026-11-10: hand typing/setup to the phone). pendingAdd stays: once the plan
            // arrives the result offers 추가 for this complex.
            openPhone(kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN, wantedCid = p.cid, wantedName = p.displayName)
            return
        }
        saved[KEY_PLAN] = target.id
        plan = repo.cachedPlan(target.id)
        detailAddBusy = true
        val e = epoch.current()
        track {
            val r = repo.plan(target.id)
            detailAddBusy = false
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> { applyPlan(r.value, "plan.shown", mapOf("for" to "detail.add")); openInsert() }
                is RepoResult.Err -> when (r.error) {
                    RepoError.Stale -> Unit
                    RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                    else -> { pendingAdd = null; toast(Notices.failed(r.error)) }
                }
            }
        }
    }

    private fun openInsert() {
        val p = plan?.value ?: return
        val opts = Insert.options(p)
        insertOptions = opts
        insertIndex = Insert.defaultOption(p, opts)
        insertAfter = opts.getOrNull(insertIndex)?.afterStopId
        go(Screen.PLAN_INSERT)
    }

    fun pickInsert(i: Int) {
        insertIndex = i
        insertAfter = insertOptions.getOrNull(i)?.afterStopId
    }

    // ── 관심 단지 (저장) ──

    /** Saved or not, only as the server said: exact `?cid=` answer, a confirmed toggle, else the last list. */
    fun favState(cid: String): Boolean? = favExact[cid] ?: Favs.state(cid, favs?.value, favConfirmed)

    /** Home tile value: the server's total, else the list size. */
    fun favCountLabel(): String? = favs?.value?.count?.let { "${it}곳" } ?: Favs.countLabel(favCount, favs?.value)

    private fun loadFavStatus(cid: String) {
        if (driving) { netSkip("favStatus"); return }
        val e = epoch.current()
        track {
            val r = repo.favStatus(cid)
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> favExact[r.value.cid] = r.value.on
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
            }
        }
    }

    fun loadFavs(forceSync: Boolean = false) {
        if (favs == null) favs = repo.cachedFavs()
        if (driving) { netSkip("favs"); return }
        favsAskedAt = nowEr()
        syncAccount(forceSync) // 최근 본 단지 · 메모 · 저장한 조건 ride on the same moments (≤ once per 5 min unless forced)
        favsLoading = true
        val e = epoch.current()
        track {
            val r = repo.favs()
            if (!epoch.isCurrent(e)) return@track
            favsLoading = false
            when (r) {
                is RepoResult.Ok -> {
                    favs = r.value
                    favsMessage = null
                    if (!r.value.fromCache) { favConfirmed.clear(); favCount = null }
                    appLog.event("favs.shown", mapOf("count" to r.value.value.favs.size, "total" to r.value.value.count, "fromCache" to r.value.fromCache))
                    if (screen == Screen.SAVED && savedSelected == null) r.value.value.favs.firstOrNull()?.let { selectSaved(it.cid) }
                }
                is RepoResult.Err -> when (r.error) {
                    RepoError.Stale -> Unit
                    RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                    else -> favsMessage = Notices.failed(r.error)
                }
            }
        }
    }

    /** ☆ / ★: shown as saved only after the server answered; failure keeps the old state + a toast. */
    fun toggleFav() {
        val p = detailPick ?: return
        if (favBusy != null) return
        val now = favState(p.cid) ?: return
        val want = !now
        if (want && !p.known) return
        favBusy = p.cid
        favMessage = null
        val e = epoch.current()
        track {
            val r = repo.setFav(p.cid, want)
            favBusy = null
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    favConfirmed[r.value.cid] = r.value.on
                    favExact[r.value.cid] = r.value.on
                    favCount = r.value.count
                    appLog.event("fav.set", mapOf("on" to r.value.on, "count" to r.value.count, "source" to p.source.name))
                    if (r.value.on) toast("저장했어요", "되돌리기") { toggleFav() } else toast("저장을 뺐어요")
                    loadFavs()
                }
                is RepoResult.Err -> when (r.error) {
                    RepoError.Stale -> Unit
                    RepoError.Expired, RepoError.NotLinked -> handleError(r.error, onPlans = false)
                    else -> toast(if (want) "저장하지 못했어요" else "빼지 못했어요")
                }
            }
        }
    }

    fun selectDetailTab(key: String) { detailTab = key }

    // ── COMPARE ──

    fun inCompare(cid: String) = compareTray.any { it.cid == cid }

    private fun setTray(items: List<kr.co.reevl.ontheway.data.CompareItem>) {
        compareTray = items
        runCatching { compareStore.save(items) }
        appLog.event("compare.tray", mapOf("count" to items.size))
    }

    /** DETAIL header `비교 담기` / `비교 담김`: toggles the open complex in the tray (≤ 3). */
    fun toggleCompareDetail() {
        val p = detailPick ?: return
        if (!p.known || !p.cid.contains('|')) return
        if (inCompare(p.cid)) { setTray(compareTray.filterNot { it.cid == p.cid }); toast("비교에서 뺐어요"); return }
        val next = kr.co.reevl.ontheway.ui.state.DetailLogic.trayAdd(compareTray, kr.co.reevl.ontheway.data.CompareItem.of(p)) { a, b -> a.cid == b.cid }
        if (next == null) { toast("비교는 3곳까지예요", "비교 보기") { openCompare() }; return }
        setTray(next)
        toast("비교에 담았어요 · ${next.size}곳", "비교 보기") { openCompare() }
    }

    /** COMPARE_PICK row: toggles a saved complex in the tray. */
    fun toggleComparePick(cid: String) {
        if (inCompare(cid)) { setTray(compareTray.filterNot { it.cid == cid }); return }
        val f = favItem(cid) ?: return
        val next = kr.co.reevl.ontheway.ui.state.DetailLogic.trayAdd(compareTray, kr.co.reevl.ontheway.data.CompareItem.of(ComplexPick.fromFav(f))) { a, b -> a.cid == b.cid }
        if (next == null) toast("비교는 3곳까지예요") else setTray(next)
    }

    fun removeCompare(cid: String) = setTray(compareTray.filterNot { it.cid == cid })

    /** Left menu `비교` / toast `비교 보기` / 저장 `비교`: empty tray → the saved picker. Facts load per complex (same cache as DETAIL). */
    fun openCompare() {
        if (compareTray.isEmpty()) { openComparePick(); return }
        go(Screen.COMPARE)
        loadTrayFacts()
    }

    /** pleos-signals §6.1-2: a tray complex already cached (memory or the daily disk cache) is not asked again. */
    private fun loadTrayFacts() = compareTray.forEach { c ->
        if (complexes[c.cid] == null) repo.cachedComplex(c.cid)?.let { complexes[c.cid] = it }
        if (complexes[c.cid] == null) fetchComplexQuietly(c.cid)
    }

    fun openComparePick() {
        go(Screen.COMPARE_PICK)
        loadFavs()
    }

    /** Picker `완료` → the table. */
    fun doneComparePick() {
        if (screen == Screen.COMPARE_PICK) goBack()
        if (screen != Screen.COMPARE && compareTray.isNotEmpty()) openCompare()
        else loadTrayFacts()
    }

    fun selectCompareTab(key: String) { compareTab = key }

    fun openCompareDetail(cid: String) { compareTray.firstOrNull { it.cid == cid }?.let { openDetail(it.pick()) } }

    fun guideCompare(cid: String) {
        val p = compareTray.firstOrNull { it.cid == cid }?.pick() ?: return
        val stop = p.stop()
        if (!PlanLogic.canGuide(stop)) return
        startGuide(Handoff(stop, HandoffPhase.Sending, returnTo = screen))
    }

    // ── SAVED ──

    fun openSaved() {
        savedSelected = null
        savedTab = SAVED_TAB
        go(Screen.SAVED)
        loadFavs()
        favs?.value?.favs?.firstOrNull()?.let { selectSaved(it.cid) }
    }

    /** HOME / AI 검색 `더보기` under 최근 본 단지 → 저장 screen on its 최근 본 tab. */
    fun openRecent() {
        savedSelected = null
        savedTab = RECENT_TAB
        go(Screen.SAVED)
        loadFavs()
    }

    fun selectSavedTab(key: String) { savedTab = key; savedSelected = null }

    // ── 휴대폰 ↔ 차 동기화 (search/Recent.kt) ──

    /** The 최근 본 단지 list on screen: server (phone + car) ∪ this car's unsent views. */
    fun recentList(): List<kr.co.reevl.ontheway.data.RecentItem> = kr.co.reevl.ontheway.search.Recent.merged(recent?.value, recentPending)

    private fun recentItem(cid: String) = recentList().firstOrNull { it.cid == cid }

    fun openRecentItem(cid: String) { recentItem(cid)?.let { openDetail(ComplexPick.fromRecent(it)) } }

    /** The memo the user wrote on the phone for [cid] (read-only here). */
    fun memoFor(cid: String): kr.co.reevl.ontheway.data.Memo? = kr.co.reevl.ontheway.search.Recent.memoOf(memos?.value?.memos, cid)

    /** A complex opened in the car → the local 최근 본 list at once (disk); it reaches the phone with the next account sync. */
    private fun noteViewed(p: ComplexPick) {
        if (!p.known || !p.cid.contains('|')) return
        val cx = complexes[p.cid]?.value
        repo.noteRecent(kr.co.reevl.ontheway.data.RecentItem(p.cid, p.name, p.address, p.addressApprox, p.lat, p.lng,
            cx?.households, cx?.completionYear, p.brand ?: cx?.brand, p.latestTrade))
        recentPending = repo.pendingRecent()
    }

    /**
     * One account sync (parked only): 최근 본 단지 (PUT the car's views if any, else GET) + 내 메모 + 저장한 조건 — 3 requests at most,
     * ≤ once per [kr.co.reevl.ontheway.search.Recent.SYNC_EVERY_MS] unless [force]. Never polled; 0 while driving-locked.
     */
    fun syncAccount(force: Boolean = false) {
        if (!repo.isLinked()) return
        if (driving) { netSkip("account"); return }
        val now = nowEr()
        if (!kr.co.reevl.ontheway.search.Recent.syncDue(accountSyncedAt, now, force)) return
        accountSyncedAt = now
        pushRecent()
        val e = epoch.current()
        track {
            val r = repo.memos()
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> memos = r.value
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
            }
        }
        track {
            val r = repo.screeners()
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> { screeners = r.value; appLog.event("screeners.shown", mapOf("count" to r.value.value.items.size, "fromCache" to r.value.fromCache)) }
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
            }
        }
    }

    /** 최근 본 단지 only: send the car's views (or fetch). Used by [syncAccount] and before handing over to the phone. */
    private fun pushRecent() {
        if (driving) { netSkip("recent"); return }
        val e = epoch.current()
        track {
            val r = repo.recent()
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    recent = r.value
                    recentPending = repo.pendingRecent()
                    appLog.event("recent.shown", mapOf("count" to r.value.value.items.size, "pending" to recentPending.size,
                        "fromPhone" to r.value.value.items.count { it.source != kr.co.reevl.ontheway.search.Recent.CAR }, "fromCache" to r.value.fromCache))
                }
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
            }
        }
    }

    /** A saved condition from the phone (HOME chip / AI 검색 row): run its car sentence; say what stays on the phone. */
    fun runScreener(id: String) {
        val s = screeners?.value?.items?.firstOrNull { it.id == id } ?: return
        val q = s.q ?: return
        appLog.event("screener.run", mapOf("skipped" to s.skipped.size))
        typeQuery(q)
        runSearch(q)
        kr.co.reevl.ontheway.search.Recent.skippedNote(s)?.let { toast("${s.name} · $it") }
    }

    fun closeSaved() = goBack()

    private fun favItem(cid: String) = favs?.value?.favs?.firstOrNull { it.cid == cid }

    fun selectSaved(cid: String) { favItem(cid)?.let { savedSelected = cid; showPick(ComplexPick.fromFav(it)) } }

    fun openSavedItem(cid: String) { favItem(cid)?.let { savedSelected = cid; openDetail(ComplexPick.fromFav(it)) } }

    // ── 휴대폰으로 넘기기 (link/PhoneHandoff) ──
    // Requests: plans + favs every 10 s ONLY while the QR screen is in front, the app is in the foreground and the car is not
    // driving-locked, for at most 10 min (then `다시 확인`); one plans(+favs) refresh when the user leaves without a change.
    // 0 calls while locked. Nothing else polls.

    var phone by mutableStateOf<PhoneState?>(null)
        private set
    private var phoneJob: Job? = null
    private var phoneBase = kr.co.reevl.ontheway.link.PhoneHandoff.Baseline(emptyMap(), emptySet())
    /** false = the baseline came from a cached copy (SEARCH): the first fresh answer becomes the baseline, not a "change". */
    private var phoneBaseFresh = true

    /** HOME / AI 검색 `휴대폰으로 찾기`. */
    fun openPhoneSearch() = openPhone(kr.co.reevl.ontheway.link.PhoneHandoff.Kind.SEARCH)

    private fun openPhone(kind: kr.co.reevl.ontheway.link.PhoneHandoff.Kind, wantedCid: String? = null, wantedName: String? = null) {
        phoneJob?.cancel(); phoneJob = null
        // Going to the phone: what this car opened should be there too (one PUT, only if something is waiting).
        if (recentPending.isNotEmpty()) pushRecent()
        val ps = plans
        val fs = favs
        phoneBase = kr.co.reevl.ontheway.link.PhoneHandoff.baseline(ps?.value, fs?.value?.favs?.map { it.cid })
        // PLAN: opened because there is no plan to add to — any target that appears is the answer, even against a cached list.
        phoneBaseFresh = kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN || (ps?.fromCache == false && fs?.fromCache == false)
        phone = PhoneState(kind, kr.co.reevl.ontheway.link.PhoneHandoff.url(kind, wantedCid), nowEr(), wantedCid = wantedCid, wantedName = wantedName)
        appLog.event("phone.open", mapOf("kind" to kind.name, "baseFresh" to phoneBaseFresh)) // no URL params beyond the public route
        go(Screen.PHONE_HANDOFF)
    }

    /** `닫기` / ‹ on the handoff screen. */
    fun closePhone() {
        if (phone?.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN) pendingAdd = null
        if (screen == Screen.PHONE_HANDOFF) goBack() else leavePhone()
    }

    /** `다시 확인` after the 10 minutes ran out: one check now, then another 10 minutes. */
    fun retryPhone() {
        val st = phone ?: return
        if (driving) return
        phone = st.copy(startedAtMs = nowEr(), stopped = false, message = null)
        track { phoneCheck() }
        syncPhonePolling()
    }

    private fun leavePhone() {
        val st = phone ?: return
        phone = null
        phoneJob?.cancel(); phoneJob = null
        val refresh = kr.co.reevl.ontheway.link.PhoneHandoff.refreshOnLeave(st.change != null, driving) && repo.isLinked() && screen != Screen.LINK
        appLog.event("phone.leave", mapOf("kind" to st.kind.name, "found" to (st.change != null), "refresh" to refresh))
        if (refresh) loadPlans() // also refreshes favs (HOME `저장 N곳`)
    }

    private fun syncPhonePolling() {
        val st = phone
        val now = nowEr()
        val want = st != null && kr.co.reevl.ontheway.link.PhoneHandoff.shouldPoll(screen == Screen.PHONE_HANDOFF, foreground, driving,
            st.change != null, st.startedAtMs, now)
        if (want && phoneJob?.isActive != true) {
            appLog.event("phone.poll", mapOf("on" to true))
            phoneJob = track { pollPhone() }
        } else if (!want && phoneJob != null) {
            if (phoneJob?.isActive == true) appLog.event("phone.poll", mapOf("on" to false, "driving" to driving, "foreground" to foreground))
            phoneJob?.cancel(); phoneJob = null
        }
        if (st != null && st.change == null && !st.stopped && kr.co.reevl.ontheway.link.PhoneHandoff.timedOut(st.startedAtMs, now)) phoneTimedOut()
    }

    private fun phoneTimedOut() {
        phone = phone?.copy(stopped = true)
        appLog.event("phone.stopped", mapOf("afterMin" to kr.co.reevl.ontheway.link.PhoneHandoff.MAX_MS / 60_000))
    }

    private suspend fun pollPhone() {
        while (true) {
            delay(kr.co.reevl.ontheway.link.PhoneHandoff.POLL_MS)
            val st = phone ?: return
            if (kr.co.reevl.ontheway.link.PhoneHandoff.timedOut(st.startedAtMs, nowEr())) { phoneTimedOut(); return }
            if (!kr.co.reevl.ontheway.link.PhoneHandoff.shouldPoll(screen == Screen.PHONE_HANDOFF, foreground, driving, st.change != null, st.startedAtMs, nowEr())) return
            phoneCheck()
            if (phone?.change != null) return
        }
    }

    /** One check: fresh plans + favs (applied to HOME / 저장 too), then [PhoneHandoff.detect]. */
    private suspend fun phoneCheck() {
        if (driving) { netSkip("phone"); return }
        val e = epoch.current()
        val pr = repo.plans()
        if (!epoch.isCurrent(e)) return
        val fr = repo.favs()
        if (!epoch.isCurrent(e)) return
        var freshPlans: List<PlanSummary>? = null
        var freshFavs: List<String>? = null
        var problem: RepoError? = null
        when (pr) {
            is RepoResult.Ok -> { plans = pr.value; plansLoadedAt = nowEr(); if (pr.value.fromCache) problem = pr.value.notice else freshPlans = pr.value.value }
            is RepoResult.Err -> problem = pr.error
        }
        when (fr) {
            is RepoResult.Ok -> {
                favs = fr.value
                if (fr.value.fromCache) problem = problem ?: fr.value.notice
                else { freshFavs = fr.value.value.favs.map { it.cid }; favConfirmed.clear(); favCount = null; favsMessage = null }
            }
            is RepoResult.Err -> problem = problem ?: fr.error
        }
        if (problem == RepoError.Expired || problem == RepoError.NotLinked) { handleError(problem, onPlans = true); return }
        val st = phone ?: return
        appLog.event("phone.check", mapOf("plans" to freshPlans?.size, "favs" to freshFavs?.size, "problem" to problem?.javaClass?.simpleName))
        if (freshPlans == null || freshFavs == null) { phone = st.copy(message = problem?.let { Notices.failed(it) } ?: "불러오지 못했어요"); return }
        if (!phoneBaseFresh) {
            phoneBase = kr.co.reevl.ontheway.link.PhoneHandoff.baseline(freshPlans, freshFavs)
            phoneBaseFresh = true
            phone = st.copy(message = null)
            return
        }
        val ch = kr.co.reevl.ontheway.link.PhoneHandoff.detect(st.kind, phoneBase, freshPlans, freshFavs, today())
        if (ch == null) { if (st.message != null) phone = st.copy(message = null); return }
        when (ch) {
            is kr.co.reevl.ontheway.link.PhoneHandoff.Change.PlanMade -> {
                val r = repo.plan(ch.planId)
                if (!epoch.isCurrent(e)) return
                val full = (r as? RepoResult.Ok)?.value?.value
                full?.let { rememberStops(it) }
                // PLAN: there was nothing to add to — from the car's side the plan is new even if the phone moved an old one to today.
                val isNew = st.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN || ch.planId !in phoneBase.plans
                phone = (phone ?: return).copy(change = ch, plan = full, isNew = isNew, message = null)
                appLog.event("phone.change", mapOf("kind" to "plan", "new" to isNew, "stops" to full?.stops?.size))
            }
            is kr.co.reevl.ontheway.link.PhoneHandoff.Change.Saved -> {
                val name = favs?.value?.favs?.firstOrNull { it.cid == ch.cid }?.name
                phone = st.copy(change = ch, savedName = name, message = null)
                appLog.event("phone.change", mapOf("kind" to "saved"))
            }
        }
        syncPhonePolling() // found → the poll stops
    }

    /** The result's one action: 안내 (the stop) · 추가 (넣을 자리 for the wanted complex) · 열기 (the plan / the saved complex). */
    fun phoneAction() {
        val st = phone ?: return
        when (val ch = st.change) {
            is kr.co.reevl.ontheway.link.PhoneHandoff.Change.PlanMade -> {
                val p = st.plan
                if (p == null) { goBack(); openPlan(ch.planId); return }
                val wanted = st.wantedCid?.takeIf { st.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN && pendingAdd?.cid == it }
                val act = kr.co.reevl.ontheway.link.PhoneHandoff.planAction(p, wanted) { PlanLogic.canGuide(it) }
                appLog.event("phone.action", mapOf("action" to act.name))
                when (act) {
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.ADD -> {
                        saved[KEY_PLAN] = p.id
                        applyPlan(Synced(p, System.currentTimeMillis(), fromCache = false), "plan.shown", mapOf("for" to "phone.add"))
                        goBack()
                        openInsert()
                    }
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.GUIDE -> {
                        val s = kr.co.reevl.ontheway.link.PhoneHandoff.planStop(p, wanted) ?: return
                        saved[KEY_PLAN] = p.id
                        applyPlan(Synced(p, System.currentTimeMillis(), fromCache = false), "plan.shown", mapOf("for" to "phone.guide"))
                        pendingAdd = null
                        goBack()
                        guide(s)
                    }
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.OPEN -> { pendingAdd = null; goBack(); openPlan(p.id) }
                }
            }
            is kr.co.reevl.ontheway.link.PhoneHandoff.Change.Saved -> { goBack(); openSavedItem(ch.cid) }
            null -> Unit
        }
    }

    // ── PLANS / VISIT_PLAN entry ──

    /** HOME `오늘 방문`: one plan → open it; several → PLANS. */
    fun openToday() {
        val list = plans?.value?.let { PlanLogic.sortForToday(it, today()) }.orEmpty()
        when {
            list.size == 1 -> openPlan(list.first().id)
            list.isNotEmpty() -> go(Screen.PLANS)
            else -> toast("계획이 없어요")
        }
    }

    fun selectNearby(cid: String) { nearbyRows.firstOrNull { it.c.cid == cid }?.let { pickComplex(it, open = false) } }
    fun openNearbyItem(cid: String) { nearbyRows.firstOrNull { it.c.cid == cid }?.let { pickComplex(it, open = true) } }

    /** HOME `동선 지도` → 오늘 방문 (the HOME plan) with its route map. */
    fun openHomePlan() {
        val id = plans?.value?.let { PlanLogic.homePlan(it, today()) }?.id ?: return
        openPlan(id)
    }

    /** HOME status row → that stop's pane alone (the plan is opened underneath so 완료 works). */
    fun openNextStop(stop: PlanStop) {
        val id = plans?.value?.let { PlanLogic.homePlan(it, today()) }?.id ?: return
        if (plan?.value?.id != id) { saved[KEY_PLAN] = id; plan = repo.cachedPlan(id); refreshPlan(id) }
        openStop(stop)
    }

    // ── left menu (v2.1) ──

    /** Top-level jump from the left menu: the stack restarts at HOME (오늘 방문), then the chosen section. */
    fun navHome() { reset(Screen.HOME) }
    fun openMenu() = go(Screen.MENU)

    /** Which left-menu item the current screen belongs to. */
    fun section(): String? = when (screen) {
        Screen.LINK -> null
        Screen.NEARBY -> "nearby"
        Screen.AI_SEARCH, Screen.SEARCH_CONFIRM -> "search"
        Screen.SAVED -> "saved"
        Screen.COMPARE, Screen.COMPARE_PICK -> "compare"
        Screen.AREA -> "area"
        Screen.SETTINGS, Screen.UNLINK_CONFIRM, Screen.PRIVACY, Screen.LICENSES -> "settings"
        Screen.DETAIL -> when (detailPick?.source) { PickSource.SEARCH -> "search"; PickSource.NEARBY -> "nearby"; PickSource.SAVED, PickSource.RECENT -> "saved"; PickSource.COMPARE -> "compare"; PickSource.AREA -> "area"; else -> "today" }
        Screen.MENU -> null
        Screen.PHONE_HANDOFF -> if (phone?.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.SEARCH) "search" else "today"
        else -> "today"
    }

    // ── 동네 (area-context-car-spec · driving-context-spec §2.4) ──
    // Requests: only when the user opens the screen or taps ⟳, and from [onDongChanged] (the stop-event hook DriveContext will
    // call). Never while restricted (moving/idling = no server call). Answers are kept per 동 for 6 h ([AreaCache]); HOME shows
    // the cached one only (HOME itself never asks the server).

    var areaData by mutableStateOf<kr.co.reevl.ontheway.data.AreaResponse?>(null)
        private set
    var areaTab by mutableStateOf(0)
        private set
    var areaPeriod by mutableStateOf(kr.co.reevl.ontheway.area.AreaLogic.Period.Y1)
        private set
    var areaSelected by mutableStateOf<String?>(null)
        private set
    var areaLoading by mutableStateOf(false)
        private set
    var areaNoLocation by mutableStateOf(false)
        private set
    var areaMessage by mutableStateOf<String?>(null)
        private set
    private val areaCache = kr.co.reevl.ontheway.area.AreaCache()
    /** One seam for the 동네 source (server today; Pleos address + device pack next — area-context-car-spec §2.5). */
    private val areaSource: kr.co.reevl.ontheway.area.AreaSource = kr.co.reevl.ontheway.area.ServerAreaSource(repo)

    fun openArea() {
        if (!DrivingPolicy.allows(ParkedAction.Nearby, driving)) return
        areaTab = 0
        areaSelected = null
        go(Screen.AREA)
        // driving-context-spec §2.2: 동네 is asked when the 동 changes (6 h per 동). Opening the screen reuses a fresh answer for
        // the 동 the car is in (navigation address, else the last answer); ⟳ on the screen still asks.
        val now = System.currentTimeMillis()
        val have = areaData?.area?.key?.let { k -> areaCache.get(k, now) }
        val dong = currentDong
        if (have != null && (dong == null || kr.co.reevl.ontheway.drive.DongLogic.matchesKey(dong, have.area!!.key))) {
            appLog.event("area.reuse", mapOf("dongVia" to dong?.via))
            return
        }
        loadArea()
    }

    fun closeArea() = goBack()
    fun selectAreaTab(i: Int) { areaTab = i }
    fun selectAreaPeriod(p: kr.co.reevl.ontheway.area.AreaLogic.Period) { areaPeriod = p }
    fun selectAreaDong(key: String) { areaSelected = key }

    /** The area for where the car is now (one request). */
    fun loadArea() {
        if (driving) { netSkip("area"); return } // driving-context-spec: no server call while restricted
        val snap = location.snapshot()
        areaNoLocation = snap == null
        areaMessage = null
        if (snap == null) return
        areaLoading = true
        val e = epoch.current()
        track {
            val r = areaSource.area(snap.fix.lat, snap.fix.lng, snap.heading?.deg)
            if (!epoch.isCurrent(e)) return@track
            areaLoading = false
            when (r) {
                is RepoResult.Ok -> {
                    areaData = r.value
                    areaCache.put(r.value, System.currentTimeMillis())
                    appLog.event("area.loaded", mapOf("level" to r.value.area?.level, "neighbors" to r.value.neighbors.size,
                        "news" to r.value.news.size, "notable" to r.value.notable.size))
                }
                is RepoResult.Err -> if (r.error == RepoError.Expired || r.error == RepoError.NotLinked) handleError(r.error, onPlans = false)
                    else areaMessage = UserMessages.of(r.error)
            }
        }
    }

    /**
     * Hook for DriveContext's `DongChanged` stop event (driving-context-spec §2.4, wired at merge): a fresh cached answer for
     * that 동 is reused; otherwise one request — only when parked/stopped (never while restricted). [dongKey] = the heat key
     * (`시도 구 동`) DriveContext learned, or null when it does not know the 동 (then ask once).
     */
    fun onDongChanged(dongKey: String?) {
        // [dongKey] may be the full heat key (`경기 성남시 수정구 시흥동`) or the navigation's `구 동` (`수정구 시흥동`): suffix match.
        fun hit(k: String) = dongKey != null && (k == dongKey || k.endsWith(" $dongKey"))
        val cached = dongKey?.let { areaCache.find(System.currentTimeMillis()) { k -> hit(k) } }
        if (cached != null) { areaData = cached; appLog.event("area.cache_hit", emptyMap()); return }
        if (areaData?.area?.key?.let { hit(it) } == true) return
        loadArea()
    }

    /** HOME / menu value from the cached answer only (`신흥동 · 1년 +26.4%`); null = nothing cached yet. */
    fun areaValue(short: Boolean = false): String? = areaData?.area?.let { a ->
        if (areaCache.get(a.key, System.currentTimeMillis()) == null) null
        else if (short) kr.co.reevl.ontheway.area.AreaLogic.railValue(a) else kr.co.reevl.ontheway.area.AreaLogic.homeValue(a)
    }

    /** Wide HOME (§15.7): the cached answer's insight lines + the facts line, ≤ 3 — never a request. */
    fun areaLines(): List<String> {
        if (areaValue() == null) return emptyList()
        val d = areaData ?: return emptyList()
        val a = d.area ?: return emptyList()
        return (d.insights.map { it.text } + listOfNotNull(kr.co.reevl.ontheway.area.AreaLogic.factsLine(a))).take(3)
    }

    fun openAreaNotable(cid: String) {
        val n = areaData?.notable?.firstOrNull { it.cid == cid } ?: return
        openDetail(ComplexPick(n.cid, n.name, lat = n.lat, lng = n.lng, distanceM = n.distanceM, source = PickSource.AREA))
    }

    // ── 주행 맥락 (docs/design/driving-context-spec.md P0 · pleos-signals-and-cost.md) ──
    // The engine runs under the lock: vehicle properties (gear/speed/brake/ignition) first, CarUxRestrictions + GPS as fallback.
    // It never changes the screen; it prepares content (방금 지나온 곳, 도착) and holds every server call while restricted.

    private fun nowEr() = android.os.SystemClock.elapsedRealtime()

    private val prefs = app.getSharedPreferences("otw-settings", android.content.Context.MODE_PRIVATE)
    private val drive = kr.co.reevl.ontheway.drive.DriveContext()
    private val trail = kr.co.reevl.ontheway.drive.Trail().also { it.enabled = prefs.getBoolean(PREF_TRAIL, true) }
    private val arrival = kr.co.reevl.ontheway.drive.Arrival()
    private val stopBudget = kr.co.reevl.ontheway.drive.StopBudget()
    private val nearbyCache = kr.co.reevl.ontheway.nearby.CachedNearbySource(kr.co.reevl.ontheway.nearby.ServerNearbySource(repo))
    /** 동 without a server call: the car's navigation address first, then the 동 of the cached 동네 answer (LocalIndex later). */
    private val dongSource: kr.co.reevl.ontheway.drive.DongSource = kr.co.reevl.ontheway.drive.ChainDongSource(listOf(
        kr.co.reevl.ontheway.drive.DongSource { navi.currentLocation()?.also { l ->
            appLog.event("navi.location", mapOf("hasAddress" to (l.address != null), "routing" to l.routing, "address" to l.address))
        }?.address?.let { kr.co.reevl.ontheway.drive.DongLogic.parse(it, "navi") } },
        kr.co.reevl.ontheway.drive.DongSource { areaData?.area?.let { kr.co.reevl.ontheway.drive.Dong(it.dong, it.gu, "area-cache") } },
    ))

    var driveState by mutableStateOf(kr.co.reevl.ontheway.drive.DriveContext.State.BEFORE_START)
        private set
    /** Debug HUD line (`S2 MOVING · vehicle · D · 20km/h · ux on`). */
    var driveLine by mutableStateOf("")
        private set
    /** Debug builds: `--ez otw_drive_hud true` draws [driveLine] over everything for the screen recording. */
    var driveHud by mutableStateOf(false)
    /** "방금 지나온 곳" rows (S4a only), newest first. */
    var passed by mutableStateOf<List<PassedRow>>(emptyList())
        private set
    /** 동 name for the section title when it changed since the previous stop (`서계동 · 방금 지나온 곳`). */
    var passedDong by mutableStateOf<String?>(null)
        private set
    var currentDong by mutableStateOf<kr.co.reevl.ontheway.drive.Dong?>(null)
        private set
    private var lastStopDong: kr.co.reevl.ontheway.drive.Dong? = null
    var arrivedStopId by mutableStateOf<String?>(null)
        private set
    var trailOn by mutableStateOf(trail.enabled)
        private set
    /** Minute clock for `n분 전` (recomposes once a minute). */
    var minuteTick by mutableStateOf(0L)
        private set
    private var pendingStop: kr.co.reevl.ontheway.drive.DriveContext.Event.Stopped? = null
    private var deferredPlans = false

    val vehicle = kr.co.reevl.ontheway.car.VehicleSignals(app, { ev, f -> appLog.event(ev, f) },
        onSpeed = { v -> viewModelScope.launch { handleDrive(drive.speed(v, nowEr())) } },
        onGear = { g -> viewModelScope.launch { handleDrive(drive.gear(g, nowEr())) } },
        onBrake = { b -> viewModelScope.launch { handleDrive(drive.parkingBrake(b, nowEr())) } },
        onIgnition = { i -> viewModelScope.launch { handleDrive(drive.ignition(i, nowEr())) } },
    )

    init {
        carUx.start() // after its declaration and the engine's (Kotlin initialises in text order; callbacks feed [drive])
        vehicle.start()
        // Pleos navigation: connect once so the listener (arrivals, current location) is registered; failures are only logged.
        viewModelScope.launch { navi.connect() }
        viewModelScope.launch { navi.arrivals.collect { onNaviArrival(it) } }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = nowEr()
                handleDrive(drive.tick(now))
                arrivalTick(now)
                val m = System.currentTimeMillis() / 60_000L
                if (m != minuteTick) minuteTick = m
            }
        }
    }

    private fun onNetCall(method: String, path: String) {
        val n = netCalls.incrementAndGet()
        appLog.event("net.request", mapOf("method" to method, "path" to path, "restricted" to driving, "n" to n))
        viewModelScope.launch { netCount = n }
    }

    private fun netSkip(what: String) = appLog.event("net.skip", mapOf("what" to what, "reason" to "restricted"))

    fun onForeground() {
        foreground = true; syncQrPolling(); syncPhonePolling(); handleDrive(drive.foreground(nowEr()))
        // 휴대폰 ↔ 차 (user 2026-11-10): back in front after a while → the phone's 저장 · 최근 본 · 메모 · 조건, once per 5 min at most
        // (favsAskedAt == null = the first frame of a new process: init already asks). Never while locked; nothing on LINK.
        if (repo.isLinked() && screen != Screen.LINK && !driving && favsAskedAt != null &&
            kr.co.reevl.ontheway.search.Recent.syncDue(favsAskedAt, nowEr())) { appLog.event("resume.refresh", mapOf("screen" to screen.name)); loadFavs() }
    }
    fun onBackground() { foreground = false; syncQrPolling(); syncPhonePolling(); handleDrive(drive.background(nowEr())) }

    private fun onFix(f: NearbyEngine.Fix) {
        val now = nowEr()
        handleDrive(drive.fix(f, now))
        trail.offer(f, drive.state == kr.co.reevl.ontheway.drive.DriveContext.State.MOVING, now)
    }

    private fun handleDrive(evs: List<kr.co.reevl.ontheway.drive.DriveContext.Event>) {
        for (e in evs) when (e) {
            is kr.co.reevl.ontheway.drive.DriveContext.Event.StateChanged -> {
                appLog.event("drive.state", mapOf("from" to e.from.code, "to" to e.to.code, "via" to e.via, "basis" to drive.basis,
                    "tripM" to drive.tripM.roundToInt(), "line" to drive.describe()))
                // S4a → anything else: the section folds (spec §1.3 "S4a → S4b"); the data itself is not kept.
                // Going to the background (AWAY) keeps it: back in front within the S4a window shows the same rows again (§15.4).
                if (e.to != kr.co.reevl.ontheway.drive.DriveContext.State.JUST_STOPPED && e.to != kr.co.reevl.ontheway.drive.DriveContext.State.AWAY &&
                    (passed.isNotEmpty() || passedDong != null)) { passed = emptyList(); passedDong = null }
            }
            is kr.co.reevl.ontheway.drive.DriveContext.Event.Stopped -> onStopped(e)
        }
        driveState = drive.state
        driveLine = drive.describe()
    }

    /** The stop event (S2/S3 → S4a). At most 12 per hour are processed (spec §2.2); requests wait for the lock to lift. */
    private fun onStopped(e: kr.co.reevl.ontheway.drive.DriveContext.Event.Stopped) {
        val ok = stopBudget.take(nowEr())
        appLog.event("drive.stopped", mapOf("tripM" to e.tripM.roundToInt(), "trailPoints" to trail.points.size, "budget" to ok, "restricted" to driving))
        if (!ok) return
        if (driving) { pendingStop = e; return }
        runStop(e)
    }

    private fun runStop(e: kr.co.reevl.ontheway.drive.DriveContext.Event.Stopped) {
        val snap = location.snapshot()
        val ep = epoch.current()
        // ① one nearby request (cell-cached) at the stop → "방금 지나온 곳" = those complexes projected on the trail.
        if (snap != null) track {
            val c = NearbyCenter.Car(snap)
            val a = nearbyCache.get(c.lat, c.lng, NEARBY_RADIUS_M, nowEr())
            if (!epoch.isCurrent(ep)) return@track
            val v = (a.result as? RepoResult.Ok)?.value ?: return@track
            nearbyCenter = c
            applyNearby(c, v, a.cached)
            if (trail.enabled && drive.state == kr.co.reevl.ontheway.drive.DriveContext.State.JUST_STOPPED) {
                val now = nowEr()
                trail.prune(now)
                val got = kr.co.reevl.ontheway.drive.PassedAlong.compute(trail.points, v.items.map { NearbyEngine.Place(it.cid, it.lat, it.lng) }, now)
                passed = got.mapNotNull { p -> v.items.firstOrNull { it.cid == p.id }?.let { PassedRow(it, p.side, p.passedAtMs) } }
                appLog.event("drive.passed", mapOf("rows" to passed.size, "trailPoints" to trail.points.size, "cached" to a.cached,
                    "sides" to passed.joinToString("|") { it.side.name }))
            }
        }
        // ② 동: Pleos navigation address first (no server); a changed 동 → the area hook (one request, 6 h per 동).
        track {
            val d = dongSource.current()
            if (!epoch.isCurrent(ep)) return@track
            val prev = lastStopDong
            val changed = d != null && !kr.co.reevl.ontheway.drive.DongLogic.same(d, prev)
            if (d != null) { currentDong = d; lastStopDong = d }
            passedDong = if (changed && prev != null) d?.name else null
            appLog.event("drive.dong", mapOf("dong" to d?.guDong, "via" to d?.via, "changed" to changed, "prev" to prev?.guDong))
            when {
                changed -> onDongChanged(d!!.guDong)
                d == null && areaData == null -> onDongChanged(null) // nothing known at all: ask once
            }
        }
    }

    // ── 도착 (spec §2.5) ──

    private fun arrivalStops(): List<kr.co.reevl.ontheway.drive.Arrival.Stop> {
        val p = plan?.value ?: plans?.value?.let { PlanLogic.homePlan(it, today()) }?.let { planFull[it.id] } ?: return emptyList()
        return p.stops.map { s -> kr.co.reevl.ontheway.drive.Arrival.Stop(s.id, s.lat.takeIf { s.locationVerified }, s.lng.takeIf { s.locationVerified },
            planned = s.visitStatus == PlanLogic.PLANNED) }
    }

    private fun onNaviArrival(a: kr.co.reevl.ontheway.navi.NaviArrival) {
        val id = arrival.onNavi(activeGuide?.stopId, arrivalStops(), location.snapshot()?.fix)
        appLog.event("arrival.navi", mapOf("kind" to a.kind.name, "name" to a.name, "matched" to id, "guide" to activeGuide?.stopId))
        if (id != null) arrivedStopId = id
    }

    private fun arrivalTick(now: Long) {
        val st = drive.state
        val parked = st == kr.co.reevl.ontheway.drive.DriveContext.State.BEFORE_START || st == kr.co.reevl.ontheway.drive.DriveContext.State.JUST_STOPPED ||
            st == kr.co.reevl.ontheway.drive.DriveContext.State.STAYING
        val stops = arrivalStops()
        if (stops.isEmpty() && arrival.stopId == null) return
        val id = arrival.onTick(parked, stops, location.snapshot()?.fix, now)
        if (id != null) appLog.event("arrival", mapOf("stop" to id, "via" to arrival.via))
        if (arrivedStopId != arrival.stopId) {
            if (arrival.stopId == null) appLog.event("arrival.cleared", mapOf("stop" to arrivedStopId))
            arrivedStopId = arrival.stopId
        }
    }

    // ── 순서 최적화 (web /plan PlanView preview → car) ──

    /** The proposal on screen (PLAN_OPTIMIZE). Kept in memory only: a restart lands on the plan, never on a stale proposal. */
    var optimize by mutableStateOf<kr.co.reevl.ontheway.plan.PlanGeo.Proposal?>(null)
        private set
    var optimizeBusy by mutableStateOf(false)
        private set
    var optimizeMessage by mutableStateOf<String?>(null)
        private set

    /** `순서 최적화`: only a shorter order opens the preview (the button is shown only then; a stale tap says why). */
    fun openOptimize() {
        // Parked only (Pleos 주행 규제 개발 가이드: DO 앱도 기능별 UX 제한을 직접 적용) — a stale tap under the lock does nothing.
        if (!DrivingPolicy.allows(ParkedAction.EditPlan, driving)) return
        val p = plan?.value ?: return
        val prop = kr.co.reevl.ontheway.plan.PlanGeo.proposal(p)
        if (prop == null || !prop.changed) { toast(if (prop == null) "3곳부터 정리할 수 있어요" else "지금 순서가 가장 짧아요"); return }
        optimize = prop
        optimizeMessage = null
        appLog.event("plan.optimize.preview", mapOf("plan" to p.id, "beforeM" to prop.beforeM.toInt(), "afterM" to prop.afterM.toInt(), "fixed" to prop.fixed))
        go(Screen.PLAN_OPTIMIZE)
    }

    fun cancelOptimize() {
        if (optimizeBusy) return
        optimize = null
        optimizeMessage = null
        goBack()
    }

    /**
     * `적용`: PATCH `{order}` (the same route the phone uses — /api/car/plans/{id} = /api/me/plans/{id}), then the server's plan.
     * The plan changed under the preview (phone edit, sync) → recompute and show the new proposal instead of sending an old order.
     */
    fun applyOptimize() {
        val prop = optimize ?: return
        if (optimizeBusy || !DrivingPolicy.allows(ParkedAction.EditPlan, driving)) return
        val cur = plan?.value
        if (cur == null || cur.id != prop.planId || cur.version != prop.version) {
            val again = cur?.let { kr.co.reevl.ontheway.plan.PlanGeo.proposal(it) }
            if (again == null || !again.changed) { optimize = null; goBack(); toast("계획이 바뀌어 그대로 둘게요"); return }
            optimize = again
            toast("계획이 바뀌어 다시 계산했어요")
            return
        }
        optimizeBusy = true
        optimizeMessage = null
        val e = epoch.current()
        track {
            val r = repo.reorder(prop.planId, prop.order)
            optimizeBusy = false
            if (!epoch.isCurrent(e)) return@track
            when (r) {
                is RepoResult.Ok -> {
                    applyPlan(Synced(r.value, System.currentTimeMillis(), fromCache = false), "plan.reordered",
                        mapOf("beforeM" to prop.beforeM.toInt(), "afterM" to prop.afterM.toInt()))
                    optimize = null
                    goBack()
                    toast("순서를 정리해서 ${kr.co.reevl.ontheway.plan.PlanGeo.fmtDist(prop.savedM)} 줄었어요")
                }
                is RepoResult.Err -> {
                    optimizeMessage = when (r.error) { is RepoError.Offline -> "오프라인이라 정리하지 못했어요"; else -> "순서를 정리하지 못했어요" }
                    if (r.error == RepoError.PlanNotFound || r.error == RepoError.Expired || r.error == RepoError.NotLinked) {
                        optimize = null; goBack(); handleError(r.error, onPlans = false)
                    }
                }
            }
        }
    }

    /** `[완료]` on an arrived row / the handoff screen: the server's answer first, then `완료했어요 [되돌리기]`. */
    fun completeStop(stop: PlanStop) {
        if (plan?.value?.stops?.any { it.id == stop.id } != true) {
            val p = planFull.values.firstOrNull { f -> f.stops.any { it.id == stop.id } } ?: return
            saved[KEY_PLAN] = p.id
            plan = repo.cachedPlan(p.id) ?: Synced(p, System.currentTimeMillis(), fromCache = true)
        }
        val wasArrived = arrivedStopId == stop.id
        val via = arrival.via
        setVisitStatus(stop, PlanLogic.VISITED, onSaved = {
            toast("완료했어요", "되돌리기") { plan?.value?.stops?.firstOrNull { it.id == stop.id }?.let {
                setVisitStatus(it, PlanLogic.PLANNED, onSaved = { if (wasArrived) { arrival.restore(stop.id, via); arrivedStopId = stop.id } })
            } }
        }, onFailed = { toast("완료하지 못했어요") })
    }

    // ── "방금 지나온 곳" ──

    fun openPassed(cid: String) {
        val r = passed.firstOrNull { it.c.cid == cid } ?: return
        openDetail(ComplexPick.fromNearby(r.c, r.side))
    }

    /** 설정 `지나온 길 기억`: off = nothing recorded, what was there is dropped (spec §7-3). */
    fun toggleTrail() {
        val on = !trail.enabled
        trail.enabled = on
        trailOn = on
        prefs.edit().putBoolean(PREF_TRAIL, on).apply()
        if (!on) { passed = emptyList(); passedDong = null }
        appLog.event("trail.toggle", mapOf("on" to on))
    }

    // ── SETTINGS ──

    fun openSettings() = go(Screen.SETTINGS)
    fun askUnlink() = go(Screen.UNLINK_CONFIRM)

    // ── 개인정보·위치 안내 (legal/Legal.kt · review-user-privacy-security.txt 투명성·동의·철회) ──

    /** true = the first-run notice covers the app (never accepted, or [Consent.VERSION] went up). */
    var consentNeeded by mutableStateOf(prefs.getInt(PREF_CONSENT, 0) < kr.co.reevl.ontheway.legal.Consent.VERSION)
        private set

    /** Only the [동의하고 시작] button calls this (no other path counts as consent). */
    fun acceptConsent() {
        if (driving) return
        prefs.edit().putInt(PREF_CONSENT, kr.co.reevl.ontheway.legal.Consent.VERSION).apply()
        consentNeeded = false
        appLog.event("consent.accept", mapOf("version" to kr.co.reevl.ontheway.legal.Consent.VERSION))
    }

    /**
     * 동의 철회: the guide's "취소한 경우 즉시 … 경로를 끊어야 하며, 저장된 사용자의 데이터를 삭제". Unlinks the car (token + caches,
     * like 연결 해제), drops the voice files and the log, and shows the notice again.
     */
    fun withdrawConsent() {
        prefs.edit().remove(PREF_CONSENT).apply()
        consentNeeded = true
        location.stop()
        val app = getApplication<Application>()
        listOf("gleo-last.json", "gleo-pending.json").forEach { java.io.File(app.noBackupFilesDir, it).delete() }
        appLog.clear()
        if (repo.isLinked()) logout() else { clearSession(); reset(Screen.LINK) }
    }

    fun openPrivacy() = go(Screen.PRIVACY)

    /** 오픈소스 라이선스: null = the list, else the asset path of the licence text on screen. */
    var licenseOpen by mutableStateOf<String?>(null)
        private set
    fun openLicenses() { licenseOpen = null; go(Screen.LICENSES) }
    fun openLicense(file: String) { licenseOpen = file }
    fun backFromLicenses() { if (licenseOpen != null) licenseOpen = null else goBack() }

    // ── toast ──

    private var toastSeq = 0L
    fun toast(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) { toastUi = ToastUi(++toastSeq, text, actionLabel, action) }
    fun dismissToast() { toastUi = null }

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
            reset(Screen.LINK)
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
        complexFailed.clear()
        planFull.clear()
        nearbyCenter = null
        nearbyComplexes = null
        nearbyRows = emptyList()
        nearbyOffices = null
        nearbyCache.clear()
        trail.clear()
        arrival.clear()
        arrivedStopId = null
        passed = emptyList()
        passedDong = null
        nearbyMessage = null
        nearbyLoading = false
        pendingAdd = null
        addBusy = false
        addMessage = null
        lastInsert = null
        listNotice = null
        searchJob?.cancel()
        searchQuery = ""
        searchLoading = false
        searchResult = null
        searchShown = null
        searchMessage = null
        searchCenter = null
        detailPick = null
        setTray(emptyList())
        detailAddBusy = false
        detailAddMessage = null
        favs = null
        favsLoading = false
        favsMessage = null
        favBusy = null
        favMessage = null
        favConfirmed.clear()
        favCount = null
        favExact.clear()
        favsAskedAt = null
        recent = null
        recentPending = emptyList()
        memos = null
        screeners = null
        accountSyncedAt = null
        savedTab = SAVED_TAB
        searchSelected = null
        savedSelected = null
        toastUi = null
        backStack.clear()
        plansLoading = false
        planLoading = false
        busyStopId = null
        code = ""
        qrJob?.cancel(); qrJob = null
        qrStartJob?.cancel(); qrStartJob = null
        qrSecret = null
        qrShown = null
        qrPhase = kr.co.reevl.ontheway.link.QrPhase.IDLE
        linkMode = kr.co.reevl.ontheway.link.LinkMode.QR
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
                reset(Screen.LINK)
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
        vehicle.stop()
        navi.release()
        super.onCleared()
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_PLAN = "planId"
        private const val KEY_STOP = "stopId"
        private const val PREF_TRAIL = "trail_on"
        /** The accepted legal/Legal.kt Consent.VERSION (0 = never). */
        private const val PREF_CONSENT = "consent_version"
        const val NEARBY_RADIUS_M = 500
        /** 저장 screen tabs. */
        const val SAVED_TAB = "saved"
        const val RECENT_TAB = "recent"


    }
}
