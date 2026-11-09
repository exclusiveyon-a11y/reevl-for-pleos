package kr.co.reevl.ontheway.ui

/**
 * One parked-only rule for every screen that shows a stop (VISIT_LIST next-stop card / summary pane, list rows,
 * STOP_DETAIL). Sources: Google Design for Driving "clearly distinguish allowed from disallowed features";
 * NHTSA/JAMA "no addresses or detail reading while driving"; our in-car rule "locked items stay as one line:
 * 정차하면 열려요" (car-ui-spec §3). D3 feeds the real CarUxRestrictions signal into LocalDriving; screens only ask here.
 */
enum class StopPart {
    OrderBadge, Avatar, Name, Status, GuideButton,
    Address, Facts, Trades, VisitActions, DetailButton, LockLine,
}

object DrivingPolicy {
    private val always = setOf(StopPart.OrderBadge, StopPart.Avatar, StopPart.Name, StopPart.Status, StopPart.GuideButton)
    private val parkedOnly = setOf(StopPart.Address, StopPart.Facts, StopPart.Trades, StopPart.VisitActions, StopPart.DetailButton)

    /** Parts of a stop shown on any screen. While driving: identity + guide + one lock line, nothing else. */
    fun visible(driving: Boolean): Set<StopPart> = if (driving) always + StopPart.LockLine else always + parkedOnly

    fun shows(part: StopPart, driving: Boolean) = part in visible(driving)

    const val LOCK_TEXT = "주행 중에는 단지 정보를 숨겨요 · 정차하면 열려요"

    /**
     * v7 parked-only actions (master §9-2 "상세 목록·주변 검색·순서 편집은 이용 제한", §9-12 주차 상태, slide 12 "리스트 조작은
     * 주차 중", BACKUP C "검색/목록 편집/상세 비교는 주차 상태로 제한"). Guiding to a stop or its meeting point stays
     * allowed (the car's own navigation takes over).
     */
    fun allows(action: ParkedAction, driving: Boolean): Boolean = !driving || action !in parkedOnlyActions

    private val parkedOnlyActions = setOf(ParkedAction.Nearby, ParkedAction.ChangeConfirm, ParkedAction.CustomerView, ParkedAction.EditPlan)
}

/** Actions the v7 tour adds. [Guide] (stop or meeting point) is the only one kept while driving. */
enum class ParkedAction { Nearby, ChangeConfirm, CustomerView, EditPlan, Guide }
