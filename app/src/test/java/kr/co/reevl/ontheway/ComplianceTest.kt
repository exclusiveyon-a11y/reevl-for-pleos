package kr.co.reevl.ontheway

import java.io.File
import kr.co.reevl.ontheway.legal.Consent
import kr.co.reevl.ontheway.legal.OssLicenses
import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import kr.co.reevl.ontheway.ui.ds.comp.PlanMapStyle
import kr.co.reevl.ontheway.ui.state.DetailLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/pleos-compliance.md (2026-11-11) — the checks that can run on the JVM. */
class ComplianceTest {
    private fun src(p: String) = File("src/$p").readText()

    @Test fun consentNotice_short_threeItems_policyLink_twoButtons() {
        assertTrue(Consent.VERSION >= 1)
        assertEquals(3, Consent.ITEMS.size)
        Consent.ITEMS.forEach { assertTrue(it.body, it.body.length <= 44) } // ≤ 2 lines in the narrow window
        assertEquals("https://reevl.co.kr/privacy#car", Consent.PRIVACY_URL)
        assertEquals("https://reevl.co.kr/privacy#car-location", Consent.LOCATION_URL)
        assertEquals(null, Consent.LOCATION_TERMS_URL) // no LBS terms page yet: hidden
        val all = Consent.ITEMS.joinToString { it.title + it.body }
        listOf("위치", "토큰", "Cloudflare", "OpenFreeMap", "지워요").forEach { assertTrue(it, all.contains(it)) }
        val screen = src("main/java/kr/co/reevl/ontheway/ui/screens/LegalScreens.kt")
        assertTrue(screen.contains("Actions(Btn(Consent.ACCEPT") && screen.contains("Btn(Consent.EXIT"))
        // only the accept button stores consent
        val vm = src("main/java/kr/co/reevl/ontheway/AppViewModel.kt")
        assertEquals(1, Regex("putInt\\(PREF_CONSENT").findAll(vm).count())
    }

    @Test fun licences_shipWithTheApp() {
        OssLicenses.LIBS.forEach { assertTrue(it.file, File("src/main/assets/${it.file}").readText().isNotBlank()) }
        assertTrue(OssLicenses.LIBS.any { it.name.startsWith("MapLibre Native") && it.license == "BSD-2-Clause" })
        assertTrue(OssLicenses.LIBS.any { it.license.startsWith("SIL OFL") })
    }

    @Test fun mapAttribution_openFreeMap_openMapTiles_osmContributors() {
        assertEquals("OpenFreeMap © OpenMapTiles © OpenStreetMap contributors", PlanMapStyle.ATTRIBUTION)
    }

    @Test fun disclaimer_once_inTheDetailFooter() {
        assertEquals("참고용 정보예요 · 투자 자문이 아니에요", DetailLogic.DISCLAIMER)
        val uses = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.sumOf { Regex("DetailLogic\\.DISCLAIMER").findAll(it.readText()).count() }
        assertEquals(1, uses)
    }

    @Test fun releaseLog_writesNothing() {
        val f = File.createTempFile("otw", ".log")
        AppLog(f, enabled = false).event("x", mapOf("lat" to 37.5))
        assertEquals(0L, f.length())
        f.delete()
    }

    @Test fun optimize_isParkedOnly() {
        assertFalse(DrivingPolicy.allows(ParkedAction.EditPlan, driving = true))
        val vm = src("main/java/kr/co/reevl/ontheway/AppViewModel.kt")
        listOf("fun openOptimize()", "fun applyOptimize()").forEach {
            assertTrue(it, vm.substringAfter(it).take(400).contains("DrivingPolicy.allows(ParkedAction.EditPlan, driving)"))
        }
    }

    @Test fun manifests_DOOnceInMain_wifiRemoved_pleosHasNoMic_noSilentSpeedAsk() {
        val main = src("main/AndroidManifest.xml")
        assertEquals(1, Regex("android:name=\"distractionOptimized\"").findAll(main).count())
        assertFalse(src("debug/AndroidManifest.xml").contains("android:name=\"distractionOptimized\""))
        assertTrue(main.contains("ACCESS_WIFI_STATE\" tools:node=\"remove\""))
        assertTrue(src("pleos/AndroidManifest.xml").contains("RECORD_AUDIO\" tools:node=\"remove\""))
        val act = src("main/java/kr/co/reevl/ontheway/MainActivity.kt")
        assertFalse(act.substringAfter("override fun onCreate").substringBefore("setContent").contains("PERM_SPEED"))
    }

    @Test fun mapUnderTheLock_isNotComposed() {
        assertTrue(src("main/java/kr/co/reevl/ontheway/ui/ds/comp/PlanRouteMap.kt").contains("!LocalDrivingLock.current"))
        assertTrue(src("main/java/kr/co/reevl/ontheway/MainActivity.kt").contains("LocalDrivingLock provides vm.driving"))
    }
}
