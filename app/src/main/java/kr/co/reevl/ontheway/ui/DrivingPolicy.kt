package kr.co.reevl.ontheway.ui

/**
 * car-ds-v2 P-6: REEVL is a Lifestyle app — while driving or idling the whole window is the lock screen (no partly
 * open screens). This object only guards the ViewModel actions that must not run from a stale tap while the platform
 * says "restricted"; guiding stays allowed (the car's own navigation takes over).
 */
object DrivingPolicy {
    fun allows(action: ParkedAction, driving: Boolean): Boolean = !driving || action !in parkedOnlyActions

    /**
     * R8 / screen-ux-research P0-3: when the lock lifts (restricted → not restricted) a screen tied to where the car is
     * must be recomputed for the new spot. NEARBY reloads; AI 검색 keeps the results the user chose (P1 banner instead).
     */
    fun reloadNearbyOnUnlock(wasDriving: Boolean, nowDriving: Boolean, screen: kr.co.reevl.ontheway.Screen): Boolean =
        wasDriving && !nowDriving && screen == kr.co.reevl.ontheway.Screen.NEARBY

    private val parkedOnlyActions = setOf(ParkedAction.Nearby, ParkedAction.ChangeConfirm, ParkedAction.EditPlan, ParkedAction.Search, ParkedAction.Save)
}

/** [Guide] is the only action kept while driving. */
enum class ParkedAction { Nearby, ChangeConfirm, EditPlan, Guide, Search, Save }
