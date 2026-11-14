package kr.co.reevl.ontheway

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.runtime.LaunchedEffect
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.LocalRail
import kr.co.reevl.ontheway.ui.ds.comp.RailItem
import kr.co.reevl.ontheway.ui.ds.comp.RailSpec
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.screens.MenuScreen
import kr.co.reevl.ontheway.ui.ds.comp.ToastSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.LockTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.ToastSlot
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.ontheway.ui.screens.AiSearchScreen
import kr.co.reevl.ontheway.ui.screens.DetailScreen
import kr.co.reevl.ontheway.ui.screens.CompareScreen
import kr.co.reevl.ontheway.ui.screens.ComparePickScreen
import kr.co.reevl.ontheway.ui.state.compareUi
import kr.co.reevl.ontheway.ui.state.compareEvents
import kr.co.reevl.ontheway.ui.state.comparePickUi
import kr.co.reevl.ontheway.ui.state.comparePickEvents
import kr.co.reevl.ontheway.ui.screens.HandoffScreen
import kr.co.reevl.ontheway.ui.screens.PlanOptimizeScreen
import kr.co.reevl.ontheway.ui.state.planOptimizeUi
import kr.co.reevl.ontheway.ui.state.planOptimizeEvents
import kr.co.reevl.ontheway.ui.screens.HomeScreen
import kr.co.reevl.ontheway.ui.screens.InsertScreen
import kr.co.reevl.ontheway.ui.screens.LinkScreen
import kr.co.reevl.ontheway.ui.screens.NearbyScreen
import kr.co.reevl.ontheway.ui.screens.PlansScreen
import kr.co.reevl.ontheway.ui.screens.SavedScreen
import kr.co.reevl.ontheway.ui.screens.SearchConfirmScreen
import kr.co.reevl.ontheway.ui.screens.SettingsScreen
import kr.co.reevl.ontheway.ui.screens.UnlinkScreen
import kr.co.reevl.ontheway.ui.screens.VisitPlanScreen
import kr.co.reevl.ontheway.ui.state.*
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.ReevlCarTheme

/** Launcher. One activity; [AppViewModel] decides which screen composable is shown. */
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            vm.appLog.event("permission.result", result.mapKeys { it.key.substringAfterLast('.') })
        }

    /** v7 NEARBY: location is asked when NEARBY is opened, not at start. Granted → listen and re-centre on the car. */
    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            vm.appLog.event("permission.result", result.mapKeys { it.key.substringAfterLast('.') })
            if (result[kr.co.reevl.ontheway.car.VehicleSignals.PERM_SPEED] == true) vm.vehicle.registerAll()
            if (vm.location.hasPermission()) {
                vm.location.start()
                if (vm.screen == Screen.NEARBY) vm.loadNearbyHere()
                if (vm.screen == Screen.AREA) vm.loadArea()
            }
        }

    /**
     * Location (+ CAR_SPEED once) — asked only on a parked tap of 주변 / 동네 / AI 검색, after the first-run notice told what
     * they are for (legal/Legal.kt "위치·차 속도 … 주변 단지 찾기와 도착 확인"). pleos-signals §1.1: PERF_VEHICLE_SPEED needs
     * `CAR_SPEED` (dangerous); denied = the engine keeps the GPS speed rule (DriveContext fallback), never asked again.
     */
    private fun askLocation() {
        if (vm.driving || vm.consentNeeded) return
        val perms = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val prefs = getSharedPreferences("otw-settings", MODE_PRIVATE)
        if (!vm.vehicle.hasSpeedPermission() && !prefs.getBoolean("asked_car_speed", false)) {
            prefs.edit().putBoolean("asked_car_speed", true).apply()
            perms += kr.co.reevl.ontheway.car.VehicleSignals.PERM_SPEED
        }
        locationLauncher.launch(perms.toTypedArray())
    }

    /** AI 검색 needs the car's position the same way NEARBY does: ask on first open, never at start. */
    fun openAiSearch() {
        if (!vm.location.hasPermission()) {
            askLocation()
        }
        vm.openAiSearch()
    }

    /** 동네 needs the position like 주변: ask on first open, never at start. */
    fun openArea() {
        if (!vm.location.hasPermission()) {
            askLocation()
        }
        vm.openArea()
    }

    fun openNearby() {
        if (!vm.location.hasPermission()) {
            askLocation()
        }
        vm.openNearby()
    }

    /**
     * 말하기: in-app speech only when the car has a recogniser we may bind (voice/Voice.kt); then the microphone permission
     * is asked on this first tap, never at start. No recogniser (Pleos 2.1.0) → the panel shows what to say to Gleo.
     */
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.appLog.event("permission.result", mapOf("RECORD_AUDIO" to granted))
        vm.startVoice(permitted = granted)
    }

    fun startVoice() {
        if (vm.driving) return
        if (vm.screen != Screen.AI_SEARCH) openAiSearch() // the answer is an AI 검색 (asks for the position like AI 검색 does)
        if (vm.voiceIn.available() && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        else vm.startVoice()
    }

    /** HOME asks for the position once, quietly (the 주변 tile shows `위치 확인 중` until a fix arrives). */
    fun homeNearby() { if (vm.location.hasPermission()) vm.loadNearbyHere() }

    override fun onResume() {
        super.onResume()
        vm.location.start() // no-op without permission
        vm.onForeground()
    }

    override fun onPause() {
        vm.location.stop() // fixes are kept only while the app is in front (no background location)
        vm.onBackground()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NAVI_ROUTE / NAVI_ROUTE_SEARCH are protectionLevel=normal on the Pleos image (granted at install);
        // ask only if a future image makes them runtime permissions.
        val missing = NAVI_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        readDebugExtras(intent)
        // CAR_SPEED is no longer asked here, silently at start: it goes with the first location request (after the notice
        // that names "위치·차 속도" and why — legal/Legal.kt), see [askLocation].

        setContent {
            ReevlCarTheme(driving = vm.driving, moving = vm.driveState == kr.co.reevl.ontheway.drive.DriveContext.State.MOVING ||
                vm.driveState == kr.co.reevl.ontheway.drive.DriveContext.State.IDLING) {
                val dark = CarTheme.colors.dark
                CompositionLocalProvider(LocalRoles provides if (dark) CarRoles.Night else CarRoles.Day) {
                    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                        App(vm, ::openNearby, ::openAiSearch, ::homeNearby, ::openArea, ::startVoice, ::finish)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDebugExtras(intent)
    }

    /**
     * Debug builds only: `--ez otw_driving true|false` overrides the driving restriction for screenshots;
     * `--es otw_driving auto` removes the override so the platform signal (CarUxRestrictions) rules again.
     * Every change is logged with `"source":"debug-intent"`.
     */
    private fun readDebugExtras(i: Intent?) {
        if (BuildConfig.DEBUG && i != null && i.hasExtra(EXTRA_DRIVE_HUD)) vm.driveHud = i.getBooleanExtra(EXTRA_DRIVE_HUD, false)
        if (!BuildConfig.DEBUG || i == null || !i.hasExtra(EXTRA_DRIVING)) return
        @Suppress("DEPRECATION") val raw = i.extras?.get(EXTRA_DRIVING)
        when (raw) {
            is Boolean -> vm.setDebugDriving(raw)
            "auto" -> vm.setDebugDriving(null)
            "true" -> vm.setDebugDriving(true)
            "false" -> vm.setDebugDriving(false)
        }
    }

    companion object {
        val NAVI_PERMISSIONS = listOf(
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
        )
        const val EXTRA_DRIVING = "otw_driving"
        /** Debug: `--ez otw_drive_hud true` shows the engine state line over the lock for the screen recording. */
        const val EXTRA_DRIVE_HUD = "otw_drive_hud"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun App(vm: AppViewModel, onNearby: () -> Unit, onAiSearch: () -> Unit, homeNearby: () -> Unit, onArea: () -> Unit, onVoice: () -> Unit,
                onExit: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalRoles.current.app)
            .semantics { testTagsAsResourceId = true }, // lets `uiautomator dump` see test tags (evidence scripts)
    ) {
        val home = vm.homeUi()
        val nav: (() -> Unit) -> () -> Unit = { go -> { vm.navHome(); go() } }
        val items = listOf(
            RailItem("nearby", Lucide.LocateFixed, "주변", home.nearbyValue, nav(onNearby)),
            RailItem("area", kr.co.reevl.ontheway.ui.components.AreaIcons.Map, "동네", vm.areaValue(short = true), nav(onArea)),
            RailItem("search", Lucide.Sparkles, "AI 검색", null, nav(onAiSearch)),
            RailItem("today", Lucide.Flag, "오늘 방문", home.planValue, nav { vm.refreshPlansOnAsk() }),
            RailItem("saved", Lucide.Star, "저장", home.savedValue, nav(vm::openSaved)),
        ) + (if (vm.compareTray.isNotEmpty()) listOf(
            // 비교 shows up only once something is in the tray: 6 rows otherwise, 7 with it. The folded 1:1 / narrow menu shows ≤ 6
            // (R3): 설정 then moves to the menu header's ⋯ ([foldMenu]); one tap from the menu (depth 1 → detail 2 ≤ 3, §2A G4). Order: place-bound (주변·동네) → AI → plan → saved.
            RailItem("compare", kr.co.reevl.ontheway.ui.ds.comp.CompareVs, "비교", "${vm.compareTray.size}곳", nav(vm::openCompare)),
        ) else emptyList()) + listOf(
            RailItem("settings", Lucide.Ellipsis, "설정", null, nav(vm::openSettings)),
        )
        // v2.1: one left menu for the whole app (built-in Settings structure); folded into the header button in the 1:1 window.
        val rail = vm.section()?.let { sel -> RailSpec(items, sel, vm::openMenu) }
        // car-ds-v2 §15: one class per window width; the window bar / 3-finger swipe can change it at any time (no activity
        // restart — configChanges), so everything that must survive lives in the ViewModel, not in this tree.
        val cls = kr.co.reevl.ontheway.ui.ds.token.windowClassOf(maxWidth)
        val wide = cls == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Wide
        // MENU is the folded left menu: if the window turns wide while it is open, the left menu is back → return to the screen
        // the menu was opened from (that screen's state is untouched in the ViewModel).
        if (wide && vm.screen == Screen.MENU) LaunchedEffect(Unit) { vm.goBack() }
        CompositionLocalProvider(LocalWideWindow provides wide, kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass provides cls, LocalRail provides rail,
            kr.co.reevl.ontheway.ui.ds.comp.LocalDrivingLock provides vm.driving) {
            if (vm.consentNeeded) {
                // First run (legal/Legal.kt): the notice covers the app until [동의하고 시작]; back = 나가기 (not consent).
                BackHandler { onExit() }
                // No left menu on the notice: nothing else is reachable before the choice.
                CompositionLocalProvider(LocalRail provides null) { kr.co.reevl.ontheway.ui.screens.ConsentScreen(vm::acceptConsent, onExit) }
            } else if (vm.screen == Screen.MENU) {
                BackHandler { vm.goBack() }
                val (rows, more) = kr.co.reevl.ontheway.ui.ds.token.foldMenu(items, { it.key }, cls)
                MenuScreen(RailSpec(rows, null, {}), more, vm::goBack)
            } else Screens(vm, onNearby, onAiSearch, homeNearby, onArea, onVoice)
            // car-ds-v2 P-6: Lifestyle app → driving / idling = one lock screen over everything; the screen under it keeps its state.
            if (vm.driving) LockTemplate()
            // Debug-only evidence strip (never in release): engine state + server calls so far.
            if (BuildConfig.DEBUG && vm.driveHud) DriveHud("${vm.driveLine} · lock ${if (vm.driving) "on" else "off"} · 서버 ${vm.netCount}건")
        }
    }
}

@Composable
private fun Screens(vm: AppViewModel, onNearby: () -> Unit, onAiSearch: () -> Unit, homeNearby: () -> Unit, onArea: () -> Unit, onVoice: () -> Unit) {
    val toast = vm.toastUi?.let { t -> ToastSlot(ToastSpec(t.id, t.text, t.actionLabel?.let { l -> Btn(l, { t.action?.invoke() }, tag = "toast_action") }), vm::dismissToast) }
    when (vm.screen) {
        Screen.LINK -> LinkScreen(vm.linkUi(), vm.linkEvents())
        Screen.HOME -> {
            LaunchedEffect(Unit) { homeNearby() }
            HomeScreen(vm.homeUi(), vm.homeEvents(onNearby, onAiSearch, onArea, onVoice), toast)
        }
        Screen.PLANS -> { BackHandler { vm.goBack() }; PlansScreen(vm.plansUi(), vm.plansEvents()) }
        Screen.VISIT_PLAN -> { BackHandler { vm.backToPlans() }; VisitPlanScreen(vm.visitPlanUi(), vm.visitPlanEvents(onNearby), toast) }
        Screen.PLAN_OPTIMIZE -> { BackHandler { vm.cancelOptimize() }; vm.planOptimizeUi()?.let { PlanOptimizeScreen(it, vm.planOptimizeEvents(), toast) } }
        Screen.PLAN_INSERT -> { BackHandler { vm.cancelAdd() }; vm.insertUi()?.let { InsertScreen(it, vm.insertEvents(), toast) } }
        Screen.NAV_HANDOFF -> { BackHandler { vm.closeHandoff() }; HandoffScreen(vm.handoffUi(), vm.handoffEvents()) }
        Screen.DETAIL -> { BackHandler { vm.closeDetail() }; DetailScreen(vm.detailUi(), vm.detailEvents(), toast) }
        Screen.NEARBY -> { BackHandler { vm.closeNearby() }; NearbyScreen(vm.nearbyUi(), vm.nearbyEvents(onNearby), toast) }
        Screen.AI_SEARCH -> { BackHandler { if (vm.voice != null) vm.closeVoice() else vm.closeAiSearch() }; AiSearchScreen(vm.aiSearchUi(), vm.aiSearchEvents(onVoice), toast) }
        Screen.SEARCH_CONFIRM -> { BackHandler { vm.cancelConfirm() }; vm.searchConfirm?.let { SearchConfirmScreen(it, vm.searchConfirmEvents()) } }
        Screen.SAVED -> { BackHandler { vm.closeSaved() }; SavedScreen(vm.savedUi(), vm.savedEvents(onAiSearch), toast) }
        Screen.COMPARE -> { BackHandler { vm.goBack() }; CompareScreen(vm.compareUi(), vm.compareEvents(onAiSearch), toast) }
        Screen.COMPARE_PICK -> { BackHandler { vm.goBack() }; ComparePickScreen(vm.comparePickUi(), vm.comparePickEvents(onAiSearch), toast) }
        Screen.AREA -> { BackHandler { vm.closeArea() }; kr.co.reevl.ontheway.ui.screens.AreaScreen(vm.areaUi(), vm.areaEvents(vm::loadArea), toast) }
        Screen.MENU -> Unit
        Screen.SETTINGS -> { BackHandler { vm.goBack() }; SettingsScreen(vm.settingsUi(), vm.settingsEvents()) }
        Screen.UNLINK_CONFIRM -> { BackHandler { vm.goBack() }; UnlinkScreen(vm.unlinkEvents()) }
        Screen.PRIVACY -> { BackHandler { vm.goBack() }; kr.co.reevl.ontheway.ui.screens.ConsentScreen(null, vm::goBack, vm::withdrawConsent) }
        Screen.LICENSES -> { BackHandler { vm.backFromLicenses() }; kr.co.reevl.ontheway.ui.screens.LicensesScreen(vm.licenseOpen, vm::openLicense, vm::backFromLicenses) }
        Screen.PHONE_HANDOFF -> { BackHandler { vm.closePhone() }; vm.phoneUi()?.let { kr.co.reevl.ontheway.ui.screens.PhoneHandoffScreen(it, vm.phoneEvents()) } }
    }
}

@Composable
private fun DriveHud(text: String) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomStart) {
        val r = kr.co.reevl.ontheway.ui.ds.token.LocalRoles.current
        androidx.compose.material3.Text(text, color = r.inverse,
            style = kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2.current.rowSecondary,
            modifier = Modifier.background(r.ink.copy(alpha = 0.8f)).padding(horizontal = CarSpace.gapTight, vertical = CarSpace.item))
    }
}
