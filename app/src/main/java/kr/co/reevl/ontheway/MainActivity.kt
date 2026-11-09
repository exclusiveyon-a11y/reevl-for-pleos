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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.screens.ChangeConfirmScreen
import kr.co.reevl.ontheway.ui.screens.CustomerViewScreen
import kr.co.reevl.ontheway.ui.screens.LinkScreen
import kr.co.reevl.ontheway.ui.screens.ListV7
import kr.co.reevl.ontheway.ui.screens.NearbyScreen
import kr.co.reevl.ontheway.ui.screens.LocalWide
import kr.co.reevl.ontheway.ui.screens.NavHandoffScreen
import kr.co.reevl.ontheway.ui.screens.StopDetailScreen
import kr.co.reevl.ontheway.ui.screens.TodayPlanScreen
import kr.co.reevl.ontheway.ui.screens.VisitListScreen
import kr.co.reevl.ontheway.ui.theme.CarDims
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
            if (vm.location.hasPermission()) {
                vm.location.start()
                if (vm.screen == Screen.NEARBY) vm.openNearby()
            }
        }

    fun openNearby() {
        if (!vm.location.hasPermission()) {
            locationLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        vm.openNearby()
    }

    override fun onResume() {
        super.onResume()
        vm.location.start() // no-op without permission
    }

    override fun onPause() {
        vm.location.stop() // fixes are kept only while the app is in front (no background location)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NAVI_ROUTE / NAVI_ROUTE_SEARCH are protectionLevel=normal on the Pleos image (granted at install);
        // ask only if a future image makes them runtime permissions.
        val missing = NAVI_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        readDebugExtras(intent)

        setContent {
            ReevlCarTheme(driving = vm.driving) {
                val c = CarTheme.colors
                // Material only for the DropdownMenu surface; every colour comes from the Reevl tokens.
                MaterialTheme(colorScheme = if (c.dark) darkColorScheme(surface = c.card, background = c.page, primary = c.brand)
                    else lightColorScheme(surface = c.card, background = c.page, primary = c.brand)) {
                    App(vm, ::openNearby)
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
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun App(vm: AppViewModel, onNearby: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CarTheme.colors.page)
            .semantics { testTagsAsResourceId = true }, // lets `uiautomator dump` see test tags (evidence scripts)
    ) {
        CompositionLocalProvider(LocalWide provides (maxWidth >= CarDims.wideMin)) { Screens(vm, onNearby) }
    }
}

@Composable
private fun Screens(vm: AppViewModel, onNearby: () -> Unit) {
    val complexOf: (String) -> kr.co.reevl.ontheway.data.Synced<kr.co.reevl.ontheway.data.CarComplex>? = { vm.complexes[it] }
    when (vm.screen) {
        Screen.LINK -> LinkScreen(
            code = vm.code,
            busy = vm.linkBusy,
            message = vm.linkMessage,
            onDigit = vm::typeDigit,
            onDelete = vm::deleteDigit,
            onSubmit = vm::submitCode,
        )
        Screen.TODAY_PLAN -> TodayPlanScreen(
            plans = vm.plans,
            loading = vm.plansLoading,
            message = vm.plansMessage,
            stopsOf = { vm.planStops[it] },
            complexOf = complexOf,
            planOf = { vm.planFull[it] },
            onRefresh = vm::loadPlans,
            onPreview = vm::previewPlan,
            onOpen = { vm.openPlan(it.id) },
            onLogout = vm::logout,
        )
        Screen.VISIT_LIST -> {
            BackHandler { vm.backToPlans() }
            VisitListScreen(
                plan = vm.plan,
                loading = vm.planLoading,
                message = vm.planMessage,
                busyStopId = vm.busyStopId,
                activeGuide = vm.activeGuide,
                complexOf = complexOf,
                onBack = vm::backToPlans,
                onRefresh = { vm.refreshPlan() },
                onGuide = vm::guide,
                onVisited = { vm.setVisitStatus(it, PlanLogic.VISITED) },
                onSkip = { vm.setVisitStatus(it, PlanLogic.SKIPPED) },
                onInfo = vm::openStop,
                onLogout = vm::logout,
                v7 = ListV7(
                    today = vm.today(),
                    eta = vm::etaFor,
                    lastInsert = vm.lastInsert,
                    notice = vm.listNotice,
                    onNearby = onNearby,
                    onCustomerView = vm::openCustomerView,
                    onUndo = vm::undoInsert,
                    onGuideMeeting = vm::guideToMeeting,
                ),
            )
        }
        Screen.NAV_HANDOFF -> {
            BackHandler { vm.closeHandoff() }
            NavHandoffScreen(vm.handoff, onRetry = vm::retryHandoff, onBack = vm::closeHandoff)
        }
        Screen.STOP_DETAIL -> {
            BackHandler { vm.closeStop() }
            val stop = vm.currentStop()
            StopDetailScreen(
                plan = vm.plan,
                stopId = vm.infoStopId,
                complex = stop?.cid?.let { vm.complexes[it] },
                loading = vm.complexLoading != null && vm.complexLoading == stop?.cid,
                message = vm.complexMessage,
                busyStopId = vm.busyStopId,
                activeGuide = vm.activeGuide,
                onBack = vm::closeStop,
                onGuide = vm::guide,
                onVisited = { vm.setVisitStatus(it, PlanLogic.VISITED) },
                onSkip = { vm.setVisitStatus(it, PlanLogic.SKIPPED) },
                onRetry = vm::loadComplex,
                onLogout = vm::logout,
                onGuideMeeting = vm::guideToMeeting,
            )
        }
        Screen.NEARBY -> {
            BackHandler { vm.closeNearby() }
            NearbyScreen(
                tab = vm.nearbyTab,
                center = vm.nearbyCenter,
                complexes = vm.nearbyComplexes,
                rows = vm.nearbyRows,
                offices = vm.nearbyOffices,
                loading = vm.nearbyLoading,
                message = vm.nearbyMessage,
                onTab = vm::selectNearbyTab,
                onPickComplex = vm::pickComplex,
                onPickOffice = vm::pickOffice,
                onRefresh = onNearby,
                onBack = vm::closeNearby,
            )
        }
        Screen.CHANGE_CONFIRM -> {
            BackHandler { vm.cancelAdd() }
            ChangeConfirmScreen(vm.plan?.value, vm.pendingAdd, vm.addBusy, vm.addMessage, complexOf, onAdd = vm::confirmAdd, onCancel = vm::cancelAdd)
        }
        Screen.CUSTOMER_VIEW -> {
            BackHandler { vm.closeCustomerView() }
            CustomerViewScreen(vm.plan?.value, complexOf, onBack = vm::closeCustomerView)
        }
    }
}
