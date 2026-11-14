package kr.co.reevl.ontheway.car

import kr.co.reevl.ontheway.ui.DrivingPolicy
import kr.co.reevl.ontheway.ui.ParkedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform signal → our one driving gate. Rows = the UX restriction configuration this Pleos emulator image
 * reports (`dumpsys car_service --services CarUxRestrictionsManagerService`, 2026-10-28): parked DO=false 0x0,
 * idling DO=true 0x50, moving DO=true 0x50, unknown DO=true 0x1ff.
 */
class CarUxMappingTest {

    @Test fun emulatorConfiguration_parkedOpen_idlingMovingUnknownLocked() {
        assertFalse("parked", CarUxMapping.restricted(false, 0x0))
        assertTrue("idling (stopped at a light, gear D)", CarUxMapping.restricted(true, 0x50))
        assertTrue("moving", CarUxMapping.restricted(true, 0x50))
        assertTrue("unknown state", CarUxMapping.restricted(true, 0x1ff))
    }

    @Test fun baselineWithDo_isNotLocked_andRestrictionsWithoutDo_areNotLocked() {
        assertFalse(CarUxMapping.restricted(true, CarUxMapping.BASELINE))
        assertFalse(CarUxMapping.restricted(false, CarUxMapping.NO_SETUP))
    }

    @Test fun flagNames_forTheLog() {
        assertEquals(listOf("NO_VIDEO", "NO_SETUP"), CarUxMapping.names(0x50))
        assertEquals(listOf("BASELINE"), CarUxMapping.names(0))
        assertEquals(9, CarUxMapping.names(CarUxMapping.FULLY_RESTRICTED).size)
    }

    @Test fun lockedSignal_closesTheParkedOnlyActions() {
        val driving = CarUxMapping.restricted(true, 0x50)
        for (a in listOf(ParkedAction.Nearby, ParkedAction.ChangeConfirm, ParkedAction.EditPlan)) {
            assertFalse("$a", DrivingPolicy.allows(a, driving))
        }
        assertTrue(DrivingPolicy.allows(ParkedAction.Guide, driving))
    }
}
