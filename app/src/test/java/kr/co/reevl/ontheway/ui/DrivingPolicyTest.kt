package kr.co.reevl.ontheway.ui

import java.time.LocalDate
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarJeonseRatio
import kr.co.reevl.ontheway.data.CarStation
import kr.co.reevl.ontheway.format.ComplexFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review Important 3 (one driving gate for VISIT_LIST and STOP_DETAIL) and Important 2 (A7 fix-round wording). */
class DrivingPolicyTest {

    @Test fun driving_hidesDetailParts_onEveryStopScreen_keepsIdentityAndGuide() {
        val d = DrivingPolicy.visible(driving = true)
        for (p in listOf(StopPart.Address, StopPart.Facts, StopPart.Trades, StopPart.VisitActions, StopPart.DetailButton)) assertFalse("$p while driving", p in d)
        for (p in listOf(StopPart.OrderBadge, StopPart.Avatar, StopPart.Name, StopPart.Status, StopPart.GuideButton, StopPart.LockLine)) assertTrue("$p while driving", p in d)
    }

    @Test fun v7_parkedOnlyActions_areLockedWhileDriving_guideStays() {
        for (a in listOf(ParkedAction.Nearby, ParkedAction.ChangeConfirm, ParkedAction.CustomerView, ParkedAction.EditPlan)) {
            assertFalse("$a while driving", DrivingPolicy.allows(a, driving = true))
            assertTrue("$a parked", DrivingPolicy.allows(a, driving = false))
        }
        assertTrue(DrivingPolicy.allows(ParkedAction.Guide, driving = true))
    }

    @Test fun parked_showsEverything_butNoLockLine() {
        val p = DrivingPolicy.visible(driving = false)
        assertTrue(StopPart.entries.filter { it != StopPart.LockLine }.all { it in p })
        assertFalse(StopPart.LockLine in p)
    }

    private val today = LocalDate.of(2026, 10, 27)
    private fun c(vararg pairs: Pair<String, Any?>): CarComplex {
        val m = pairs.toMap()
        return CarComplex(
            cid = "x", name = "n",
            completionText = m["completionText"] as String?, approveDate = m["approveDate"] as String?,
            buildAgeYears = m["buildAgeYears"] as Int?, households = m["households"] as Int?,
            brand = CarBrand(name = m["brand"] as String?, names = (m["names"] as List<String>?) ?: emptyList()),
            builder = m["builder"] as String?, builders = (m["builders"] as List<String>?) ?: emptyList(),
            nearestStation = m["station"] as CarStation?, jeonseRatio = m["jeonse"] as CarJeonseRatio?,
        )
    }

    @Test fun completion_isTheWebCell_verbatim_withKeyStatsFallbacks() {
        assertEquals("2017.02 · 9년차", ComplexFacts.completion(c("completionText" to "2017.02 · 9년차"), today))
        // 841 complexes without an approval date: web shows `N년차` only
        assertEquals("7년차", ComplexFacts.completion(c("completionText" to "7년차"), today))
        // older server without completionText → same KeyStats rule, KST year
        assertEquals("2017.02 · 9년차", ComplexFacts.completion(c("approveDate" to "2017-02-24"), today))
        assertEquals("7년차", ComplexFacts.completion(c("buildAgeYears" to 7), today))
        assertNull(ComplexFacts.completion(c("buildAgeYears" to 0), today))
    }

    @Test fun jvBrandsAndBuilders_useTheWebCountedLabels() {
        val rows = ComplexFacts.rows(c("names" to listOf("래미안", "아이파크"), "builders" to listOf("삼성물산", "HDC현대산업개발")), today)
        assertEquals("브랜드 2" to "래미안 · 아이파크", rows.single { it.key == "brand" }.let { it.label to it.value })
        assertEquals("시공사 2" to "삼성물산 · HDC현대산업개발", rows.single { it.key == "builder" }.let { it.label to it.value })
        val single = ComplexFacts.rows(c("brand" to "자이"), today)
        assertEquals("브랜드" to "자이", single.single { it.key == "brand" }.let { it.label to it.value })
    }

    @Test fun station_and_jeonse_followKeyStats_andJeonseCarriesItsOwnDate() {
        val rows = ComplexFacts.rows(c(
            "station" to CarStation("서대문역", listOf("수도권 5호선"), 436, 8),
            "jeonse" to CarJeonseRatio(48.1, "%", "2026-10-26", 248), "households" to 1148), today)
        assertEquals("역세권" to "수도권 5호선 서대문역 · 436m", rows.single { it.key == "station" }.let { it.label to it.value })
        val j = rows.single { it.key == "jeonse" }
        assertEquals(listOf("48.1%", "전세 248건", "2026-10-26 기준"), listOf(j.value, j.chip, j.note))
        assertNull(rows.single { it.key == "households" }.note) // K-apt facts carry no trade date
        val far = ComplexFacts.rows(c("station" to CarStation("목포역", emptyList(), 12_345, null)), today)
        assertEquals("가장 가까운 역" to "목포역 · 12.3km", far.single().let { it.label to it.value })
    }
}
