package kr.co.reevl.ontheway.plan

import kr.co.reevl.ontheway.data.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D2 review I-2 (stale answers) and M-10 (no raw codes on screen). */
class ResultGateTest {

    private fun plan(id: String, version: Int) = Plan(id, "t", null, "planned", version, 0L, emptyList())

    @Test fun epoch_dropsAnswersCapturedBeforeLogoutOrLink() {
        val e = SessionEpoch()
        val beforeLogout = e.current()
        assertTrue(e.isCurrent(beforeLogout))
        e.bump() // logout
        assertFalse("account A's late answer is dropped", e.isCurrent(beforeLogout))
        val accountB = e.current()
        assertTrue(e.isCurrent(accountB))
    }

    // I-2(b): refreshPlan(A) in flight, the user opens B → A's answer must not replace B.
    @Test fun planSwitch_answerForThePreviousPlan_isRejected() {
        assertFalse(PlanGate.accept(wantedId = "B", shown = plan("B", 1), incoming = plan("A", 9)))
        assertFalse(PlanGate.accept(wantedId = "B", shown = null, incoming = plan("A", 1)))
        assertTrue(PlanGate.accept(wantedId = "B", shown = plan("A", 5), incoming = plan("B", 1)))
    }

    // I-2(c): a GET issued before a PATCH returns after it with an older version → the PATCH result stays.
    @Test fun olderVersion_ofTheSamePlan_isRejected_sameOrNewerAccepted() {
        assertFalse(PlanGate.accept("A", shown = plan("A", 4), incoming = plan("A", 3)))
        assertTrue(PlanGate.accept("A", shown = plan("A", 4), incoming = plan("A", 4)))
        assertTrue(PlanGate.accept("A", shown = plan("A", 4), incoming = plan("A", 5)))
    }

    @Test fun guideFailure_neverShowsTheRawCode() {
        for (code in listOf("NO_VERIFIED_COORDS", "NOT_CONNECTED", "TIMEOUT", "DEST_MISMATCH", "SDK_EXCEPTION", "INVALID_PARAM")) {
            val text = (GuideFailureText.detail(code) ?: "") + GuideFailureText.GENERIC
            assertFalse(text, text.contains(code))
            assertFalse(text, Regex("[A-Z_]{4,}").containsMatchIn(text))
        }
        assertNull(GuideFailureText.detail("SDK_EXCEPTION"))
        assertEquals("길 안내를 연결하지 못했습니다. 다시 시도해 주세요.", GuideFailureText.GENERIC)
    }
}
