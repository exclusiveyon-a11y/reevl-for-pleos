package kr.co.reevl.ontheway.ui.state

import kr.co.reevl.ontheway.AppViewModel
import kr.co.reevl.ontheway.BuildConfig
import kr.co.reevl.ontheway.NearbyCenter
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.format.ComplexFacts
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.search.AiSearch
import kr.co.reevl.ontheway.search.ComplexPick
import kr.co.reevl.ontheway.search.PickSource

// UiState builders: read the VM's observable fields (Compose tracks the reads), never write.

private fun AppViewModel.inPlan(cid: String) = cid in planCids()

/** The pane for [pick] — the open DETAIL or the selected row of a wide list. */
fun AppViewModel.paneFor(pick: ComplexPick): PaneUi {
    val cid = pick.cid
    val real = cid.contains('|')
    val cx = if (real) complexes[cid] else null
    val current = detailPick?.cid == cid
    val planStop = pick.stopId?.let { id -> plan?.value?.stops?.firstOrNull { it.id == id } }
    val stop = planStop ?: pick.stop()
    val secondary = when {
        planStop != null -> if (planStop.visitStatus == PlanLogic.PLANNED) PaneSecondary.Done else PaneSecondary.None
        !pick.known -> PaneSecondary.None
        inPlan(cid) -> PaneSecondary.AddDisabled
        else -> PaneSecondary.Add
    }
    return PaneUi(
        pick = pick, complex = cx,
        complexLoading = real && pick.known && cx == null && (complexLoading == cid || (current && complexMessage == null)),
        complexError = real && cx == null && current && complexMessage != null,
        canGuide = PlanLogic.canGuide(stop), guiding = activeGuide?.stopId == stop.id,
        secondary = secondary, secondaryBusy = (current && detailAddBusy) || (planStop != null && busyStopId == planStop.id),
        saved = if (real) favState(cid) else null, saveBusy = favBusy == cid,
        basis = if (pick.source == PickSource.SEARCH) AiSearch.basisLine(searchResult?.conds.orEmpty()) else null,
        memo = if (real) memoFor(cid) else null,
    )
}

fun AppViewModel.paneEvents() = PaneEvents(onGuide = ::guideDetail, onSecondary = ::paneSecondary, onToggleSave = ::toggleFav)

private fun AppViewModel.currentPane(source: PickSource): PaneUi? = detailPick?.takeIf { it.source == source }?.let { paneFor(it) }

// ── HOME ──

fun AppViewModel.homeUi(): HomeUi {
    val rows = nearbyRows
    val nearbyValue = when {
        rows.isEmpty() && nearbyCenter == null -> "위치 확인 중"
        rows.isEmpty() && nearbyLoading -> "위치 확인 중"
        rows.any { it.side == Side.LEFT || it.side == Side.RIGHT } ->
            "오른쪽 ${rows.count { it.side == Side.RIGHT }} · 왼쪽 ${rows.count { it.side == Side.LEFT }}"
        else -> "${rows.size}곳"
    }
    // P0-2: only today's / an upcoming (or undated) plan — a past plan is never "오늘 방문".
    val first = plans?.value?.let { PlanLogic.homePlan(it, today()) }
    val stops = first?.let { planStops[it.id] }
    val planValue = when {
        first == null -> "계획 없음"
        stops == null -> "${first.stopCount}곳"
        else -> "${stops.size}곳 중 ${stops.count { it.visitStatus != PlanLogic.PLANNED }}곳"
    }
    val full = first?.let { planFull[it.id] }
    val next = full?.let { PlanLogic.nextStop(it) }?.let { s ->
        val cx = s.cid?.let { complexes[it]?.value }
        NextStopUi(s, cx?.brand, RowLines.stop(cx, s.address), PlanLogic.canGuide(s))
    }
    val nextId = full?.let { PlanLogic.nextStop(it) }?.id
    val stopRows = full?.let { PlanLogic.ordered(it) }.orEmpty().map { s ->
        val cx = s.cid?.let { complexes[it]?.value }
        StopRowUi(s, cx?.brand, RowLines.stop(cx, s.address),
            done = s.visitStatus == PlanLogic.VISITED, next = s.id == nextId, skipped = s.visitStatus == PlanLogic.SKIPPED,
            canGuide = PlanLogic.canGuide(s), guiding = activeGuide?.stopId == s.id,
            arrived = arrivedStopId == s.id && s.visitStatus == PlanLogic.PLANNED)
    }
    @Suppress("UNUSED_VARIABLE") val minute = minuteTick // re-read once a minute so `n분 전` moves
    val now = android.os.SystemClock.elapsedRealtime()
    val passedRows = if (!trailOn || driveState != kr.co.reevl.ontheway.drive.DriveContext.State.JUST_STOPPED) emptyList()
        else passed.take(3).map { r ->
            PassedRowUi(r.c.cid, r.c.name, r.c.brand, r.side,
                RowLines.line(kr.co.reevl.ontheway.drive.PassedAlong.ago(r.atMs, now), r.c.households, r.c.latestTrade?.price))
        }
    val ownChips = kr.co.reevl.ontheway.search.Recent.chips(screeners?.value?.items, voiceSuggestions(), 3)
    val recentRows = recentRowsUi()
    return HomeUi(nearbyValue = nearbyValue, planValue = planValue, planEnabled = true, savedValue = favCountLabel() ?: "0곳", next = next,
        asOf = nearbyComplexes?.asOf ?: favs?.value?.asOf, notice = Notices.of(plans, plansMessage), query = homeQuery,
        planTitle = first?.let { PlanLogic.homeSection(it, today()) }, stops = stopRows, areaValue = areaValue(), areaLines = areaLines(),
        nearbyTop = nearest3(nearbyRows, stopRows.mapNotNull { it.stop.cid }.toSet()).map { PlaceRowUi(it.c.cid, it.c.name, it.c.cid, it.c.brand, it.c.distanceM, it.c.latestTrade, it.side, it.inPlan, households = it.c.households) },
        passedTitle = if (passedRows.isEmpty()) null else listOfNotNull(passedDong, "방금 지나온 곳").joinToString(" · "), passed = passedRows,
        suggest = ownChips.map { it.label }, chips = ownChips,
        recent = recentRows.take(kr.co.reevl.ontheway.search.Recent.HOME_ROWS), recentMore = recentRows.size > kr.co.reevl.ontheway.search.Recent.HOME_ROWS,
        route = full?.let { planMapUi(it) }?.let { m -> listOfNotNull(m.km, m.hint).joinToString(" · ") },
        routePins = full?.let { planMapUi(it) }?.pins.orEmpty())
}

/** 최근 본 단지 rows (phone + car merged): name / `1,148세대 · 최근 23.6억 · 휴대폰에서 · 어제`. Wall clock for the label. */
fun AppViewModel.recentRowsUi(): List<PlaceRowUi> {
    @Suppress("UNUSED_VARIABLE") val minute = minuteTick // `방금` → `n분 전` moves
    val now = System.currentTimeMillis()
    return recentList().map { r -> PlaceRowUi(r.cid, r.name ?: ComplexPick.NO_INFO, r.cid, r.brand, null, r.latestTrade, households = r.households,
        tail = kr.co.reevl.ontheway.search.Recent.sourceLabel(r, now)) }
}

/**
 * HOME `가까운 단지`: the 3 nearest not already in the plan block. Sorted here (QA 2026-11-10: the list kept the answer's order
 * and showed 439 m above 303 m under the title "가까운").
 */
fun nearest3(rows: List<kr.co.reevl.ontheway.NearbyRow>, planCids: Set<String>): List<kr.co.reevl.ontheway.NearbyRow> =
    rows.filter { it.c.cid !in planCids }.sortedBy { it.c.distanceM }.take(3)

/** One-tap requests from the car's context (shared by HOME chips and the AI 검색 idle rows). */
fun AppViewModel.voiceSuggestions(): List<String> = kr.co.reevl.ontheway.voice.VoiceSuggest.build(
    sided = nearbyRows.any { it.side == Side.LEFT || it.side == Side.RIGHT },
    passedRecently = passed.isNotEmpty(), trailOn = trailOn)

fun AppViewModel.homeEvents(onNearby: () -> Unit, onSearch: () -> Unit, onArea: () -> Unit = {}, onVoice: () -> Unit = {}) = HomeEvents(onArea = onArea, onQuery = ::typeHomeQuery,
    onSubmit = { q -> typeHomeQuery(""); onSearch(); typeQuery(q); runSearch(q) }, onNearby = onNearby, onSearch = onSearch,
    onPlan = ::openToday, onSaved = ::openSaved, onOpenNearby = ::openNearbyItem, onGuideNext = ::guide, onOpenNext = ::openNextStop, onMenu = ::openSettings, onRetry = ::loadPlans,
    onDoneStop = ::completeStop, onPassed = ::openPassed, onVoice = onVoice.takeIf { voiceOffered }, onSuggest = { q -> onSearch(); runSearch(q) }, onPhone = ::openPhoneSearch,
    onRecent = ::openRecentItem, onRecentMore = ::openRecent, onScreener = { id -> onSearch(); runScreener(id) }, onRoute = ::openHomePlan)

// ── AI 검색 ──

fun AppViewModel.aiSearchUi(): AiSearchUi {
    val r = searchResult
    val sh = searchShown
    // P0-1: the row price is the trade that met the condition (evidenceTrade), not simply the newest one.
    val rows = sh?.rows.orEmpty().map { RowLines.searchRow(it.item, it.side, inPlan(it.item.cid)) }
    val checks = sh?.rows.orEmpty().associate { row -> row.item.cid to row.item.checks.filter { it.ok == true && it.label.isNotBlank() }.map { it.label } }
    val chips = r?.conds.orEmpty().filter { it.label.isNotBlank() }.map {
        CondUi(it.key, it.label, (it.status != "applied" && it.status != "car") || AiSearch.directionUnchecked(it, sh))
    }
    val screens = kr.co.reevl.ontheway.search.Recent.runnable(screeners?.value?.items, 2).map { kr.co.reevl.ontheway.search.Recent.Chip(it.name, it.q!!, it.id) }
    return AiSearchUi(
        query = searchQuery, loading = searchLoading, idle = r == null && !searchLoading && searchMessage == null,
        suggestions = kr.co.reevl.ontheway.search.Recent.idleVoice(screens, voiceSuggestions()), chips = chips,
        summary = if (r != null && sh != null) AiSearch.summary(r, sh) else null,
        rows = rows, checksByKey = checks, empty = r != null && rows.isEmpty(), canDropCondition = chips.isNotEmpty(),
        selectedKey = searchSelected, preview = currentPane(PickSource.SEARCH),
        asOf = r?.asOf, notice = searchMessage?.let { Notice(NoticeKind.Error, it, retry = true) },
        note = if (r != null && sh != null) AiSearch.noteLine(r, sh) else null,
        emptyWhy = if (r != null && sh != null && rows.isEmpty()) AiSearch.empty(r, sh) else null,
        voice = voice?.let { m -> VoiceUi(m == kr.co.reevl.ontheway.VoiceMode.Listening, voiceHeard,
            if (!kr.co.reevl.ontheway.Features.GLEO) emptyList() else kr.co.reevl.ontheway.voice.VoiceHints.examples(sided = nearbyRows.any { it.side == Side.LEFT || it.side == Side.RIGHT },
                hasPlan = plans?.value?.let { PlanLogic.homePlan(it, today()) } != null)) },
        screens = screens,
        recent = recentRowsUi().take(kr.co.reevl.ontheway.search.Recent.HOME_ROWS),
        recentMore = recentList().size > kr.co.reevl.ontheway.search.Recent.HOME_ROWS,
    )
}

fun AppViewModel.aiSearchEvents(onVoice: () -> Unit = {}) = AiSearchEvents(onQuery = ::typeQuery, onRun = { runSearch(it) }, onChip = ::openCondition,
    onDropLast = { dropCondition(null) }, onWiden = { runSearch(searchQuery, AiSearch.WIDE_RADIUS_M) }, onSelect = ::selectSearch, onOpen = ::openSearchItem, onBack = ::closeAiSearch, pane = paneEvents(),
    onVoice = onVoice.takeIf { voiceOffered }, onVoiceClose = ::closeVoice, onPhone = ::openPhoneSearch,
    onRecent = ::openRecentItem, onRecentMore = ::openRecent, onScreener = ::runScreener)

fun AppViewModel.searchConfirmEvents() = SearchConfirmEvents(onPick = ::pickConfirmOption, onApply = ::applyConfirm, onCancel = ::cancelConfirm)

// ── NEARBY ──

private val TAB_SIDES = listOf<Side?>(null, Side.LEFT, Side.RIGHT, Side.BEHIND)

fun AppViewModel.nearbyUi(): NearbyUi {
    val all = nearbyRows
    val sided = all.any { it.side != Side.UNKNOWN }
    val tab = if (sided) nearbyTabIndex else 0
    val want = TAB_SIDES.getOrNull(tab)
    val rows = all.filter { want == null || it.side == want }
        .map { PlaceRowUi(it.c.cid, it.c.name, it.c.cid, it.c.brand, it.c.distanceM, it.c.latestTrade, it.side, it.inPlan, households = it.c.households) }
    val radius = nearbyComplexes?.radiusM ?: 500
    val basis = when {
        nearbyComplexes == null -> null
        sided -> "진행 방향 기준 · ${AiSearch.radiusText(radius)}"
        nearbyCenter is NearbyCenter.AtStop -> "방문지 기준 · ${AiSearch.radiusText(radius)}"
        else -> "현재 위치 기준 · ${AiSearch.radiusText(radius)}"
    }
    return NearbyUi(tabs = if (sided) listOf("전체", "왼쪽", "오른쪽", "지나옴") else null, tab = tab, rows = rows,
        loading = nearbyLoading, empty = nearbyComplexes != null && rows.isEmpty(), noLocation = nearbyCenter == null && !nearbyLoading,
        basis = basis, selectedKey = detailPick?.takeIf { it.source == PickSource.NEARBY }?.cid,
        preview = currentPane(PickSource.NEARBY),
        notice = Notices.forList(nearbyMessage, rows.isNotEmpty(), nearbyAtMs))
}

fun AppViewModel.nearbyEvents(onRefresh: () -> Unit) = NearbyEvents(onTab = ::selectNearbyTab, onSelect = ::selectNearby, onOpen = ::openNearbyItem,
    onRefresh = onRefresh, onBack = ::closeNearby, pane = paneEvents())

// ── DETAIL ──

fun AppViewModel.detailUi() = DetailUi(detailPick?.let { paneFor(it) }, null, detailTab, detailPick?.let { inCompare(it.cid) } == true)
fun AppViewModel.detailEvents() = DetailEvents(onBack = ::closeDetail, pane = paneEvents(), onTab = ::selectDetailTab, onCompare = ::toggleCompareDetail)

// ── COMPARE ──

fun AppViewModel.compareUi(): CompareUi {
    val tray = compareTray
    val cxs = tray.map { complexes[it.cid]?.value }
    val details = cxs.map { it?.detail }
    val tabs = DetailLogic.visibleTabs(details.firstOrNull { it != null }, hasTrades = false)
    val tab = compareTab.takeIf { k -> tabs.any { it.first == k } } ?: tabs.firstOrNull()?.first ?: "summary"
    val rows = DetailLogic.compareRows(details, tab).map { (k, cells) ->
        val best = DetailLogic.best(cells)
        CompareRowUi(k, cells.firstNotNullOf { it }.label, cells.mapIndexed { i, m -> CompareCellUi(m?.v, m?.sub, m?.color, i in best) })
    }
    val cols = tray.mapIndexed { i, c ->
        val cx = cxs[i]
        CompareColUi(c.cid, cx?.name ?: c.name, cx?.brand ?: c.brand,
            DetailLogic.facts(kr.co.reevl.ontheway.nearby.NearbyEngine.Side.UNKNOWN, null, cx?.households, cx?.completionYear, cx?.buildAgeYears),
            canGuide = PlanLogic.canGuide(c.pick().stop()), loading = cx == null && complexFailed[c.cid] != true, error = cx == null && complexFailed[c.cid] == true)
    }
    return CompareUi(cols, tabs, tab, rows, cxs.firstNotNullOfOrNull { it?.tradeAsOf }, canAdd = tray.size < DetailLogic.COMPARE_MAX)
}

fun AppViewModel.compareEvents(onSearch: () -> Unit) = CompareEvents(onBack = ::goBack, onTab = ::selectCompareTab, onRemove = ::removeCompare,
    onAdd = ::openComparePick, onGuide = ::guideCompare, onOpen = ::openCompareDetail)

fun AppViewModel.comparePickUi(): ComparePickUi {
    val list = favs?.value
    val rows = list?.favs.orEmpty().map { PlaceRowUi(it.cid, it.name ?: ComplexPick.NO_INFO, it.cid, it.brand, null, it.latestTrade, households = it.households) }
    return ComparePickUi(rows.take(12), compareTray.map { it.cid }.toSet(), favsLoading && list == null, list != null && rows.isEmpty(), Notices.of(favs, favsMessage))
}

fun AppViewModel.comparePickEvents(onSearch: () -> Unit) = ComparePickEvents(onToggle = ::toggleComparePick, onDone = ::doneComparePick, onBack = ::goBack, onSearch = onSearch)

// ── SAVED ──

fun AppViewModel.savedUi(): SavedUi {
    val list = favs?.value
    val rows = list?.favs.orEmpty().map { PlaceRowUi(it.cid, it.name ?: ComplexPick.NO_INFO, it.cid, it.brand, null, it.latestTrade, savedAt = it.savedAt, households = it.households) }
    return SavedUi(rows = rows.take(12), more = rows.size > 12 || (list?.count ?: 0) > 12, loading = favsLoading && list == null,
        empty = list != null && rows.isEmpty(), selectedKey = savedSelected, preview = currentPane(PickSource.SAVED), asOf = list?.asOf,
        notice = if (savedTab == AppViewModel.RECENT_TAB) recent?.let { Notices.of(it, null) } else Notices.of(favs, favsMessage),
        tab = savedTab, recentRows = recentRowsUi())
}

fun AppViewModel.savedEvents(onSearch: () -> Unit) = SavedEvents(onSelect = ::selectSaved, onOpen = ::openSavedItem, onRefresh = { loadFavs(forceSync = true) },
    onSearch = onSearch, onBack = ::closeSaved, pane = paneEvents(), onCompare = ::openCompare, onTab = ::selectSavedTab, onOpenRecent = ::openRecentItem)

// ── VISIT_PLAN / PLANS ──

fun AppViewModel.visitPlanUi(): VisitPlanUi {
    val p = plan?.value
    val next = p?.let { PlanLogic.nextStop(it) }
    val rows = p?.let { PlanLogic.ordered(it) }.orEmpty().map { s ->
        val cx = s.cid?.let { complexes[it]?.value }
        StopRowUi(s, cx?.brand, RowLines.stop(cx, s.address),
            done = s.visitStatus == PlanLogic.VISITED, next = s.id == next?.id, skipped = s.visitStatus == PlanLogic.SKIPPED,
            canGuide = PlanLogic.canGuide(s), guiding = activeGuide?.stopId == s.id,
            arrived = arrivedStopId == s.id && s.visitStatus == PlanLogic.PLANNED)
    }
    val footer = p?.let { PlanLogic.dateLabel(it.plannedDate) } // the count is in the map line (UX writing 2026-11-11)
    return VisitPlanUi(rows, footer, loading = planLoading && p == null, selectedKey = detailPick?.stopId,
        preview = currentPane(PickSource.PLAN), notice = Notices.of(plan, planMessage), missing = p == null && planMessage != null,
        map = p?.let { planMapUi(it) })
}

/** Pins of [stops] in this order, numbered 1..n (the proposal renumbers; the list's numbers are the server positions). */
internal fun routePins(stops: List<kr.co.reevl.ontheway.data.PlanStop>, renumber: Boolean,
                        /** The stop to highlight: the first planned stop IN THIS ORDER (a proposal's next is not today's next). */
                        next: kr.co.reevl.ontheway.data.PlanStop? = stops.firstOrNull { it.visitStatus == PlanLogic.PLANNED }) =
    stops.mapIndexed { i, s -> RoutePinUi(if (renumber) i + 1 else s.position, s.name, s.lat, s.lng, next = s.id == next?.id,
        done = s.visitStatus == PlanLogic.VISITED, skipped = s.visitStatus == PlanLogic.SKIPPED) }

/**
 * The map block of 오늘 방문: shown once 2 stops have coordinates (one pin alone says nothing about a route). The line under it
 * is straight-line only (web: `직선 합계`), plus how many stops are not on the map.
 */
internal fun planMapUi(p: kr.co.reevl.ontheway.data.Plan): PlanMapUi? {
    val ordered = PlanLogic.ordered(p)
    val located = ordered.count { kr.co.reevl.ontheway.plan.PlanGeo.ll(it) != null }
    if (located < 2) return null
    val total = kr.co.reevl.ontheway.plan.PlanGeo.planTotalM(p)
    val prop = kr.co.reevl.ontheway.plan.PlanGeo.proposal(p)
    val missing = ordered.size - located
    val fmt = kr.co.reevl.ontheway.plan.PlanGeo::fmtDist
    val next = PlanLogic.nextStop(p)
    val left = ordered.count { it.visitStatus == PlanLogic.PLANNED }
    val anyDone = left < ordered.size
    // UX writing 2026-11-11: one number line, one hint line. 다음 is the brand row + [안내]; the count lives here only.
    val km = if (anyDone) "남은 직선 ${fmt(kr.co.reevl.ontheway.plan.PlanGeo.remainingM(p).m)}" else "직선 ${fmt(total.m)}"
    val title = if (left == 0) "모두 다녀왔어요" else "$km · ${if (anyDone) left else ordered.size}곳"
    val hint = prop?.takeIf { it.changed }?.let { "정리하면 ${fmt(it.afterM)}" }
    val sub = listOfNotNull(hint, if (missing > 0) "${missing}곳은 위치를 몰라 빠졌어요" else null).joinToString(" · ").ifEmpty { null }
    return PlanMapUi(routePins(ordered, renumber = false, next = next), title, canOptimize = prop?.changed == true, title = title, next = sub,
        km = km, hint = hint)
}

// ── PLAN_OPTIMIZE ──

fun AppViewModel.planOptimizeUi(): PlanOptimizeUi? {
    val prop = optimize ?: return null
    val oldPos = prop.before.withIndex().associate { (i, s) -> s.id to i + 1 }
    val fmt = kr.co.reevl.ontheway.plan.PlanGeo::fmtDist
    // One caveat only (UX writing 2026-11-11): stop 1 / the visited ones stay put — the maps and the rows show it.
    val note = if (prop.fixed > 1) "남은 길 · 직선거리 기준이에요" else "직선거리 기준이에요"
    return PlanOptimizeUi(
        before = routePins(prop.before, renumber = true),
        after = routePins(prop.after, renumber = true),
        beforeKm = fmt(prop.beforeM), afterKm = fmt(prop.afterM), saved = "",
        rows = prop.after.mapIndexed { i, s -> Triple(i + 1, s.name, oldPos[s.id] ?: (i + 1)) },
        note = note, busy = optimizeBusy, notice = optimizeMessage?.let { Notice(NoticeKind.Error, it) })
}

fun AppViewModel.planOptimizeEvents() = PlanOptimizeEvents(onApply = ::applyOptimize, onCancel = ::cancelOptimize)

fun AppViewModel.visitPlanEvents(onNearby: () -> Unit) = VisitPlanEvents(
    onSelect = { id -> plan?.value?.stops?.firstOrNull { it.id == id }?.let(::selectStop) },
    onOpen = { id -> plan?.value?.stops?.firstOrNull { it.id == id }?.let(::openStop) },
    onGuide = ::guide, onAddNearby = onNearby, onRefresh = { refreshPlan() }, onBack = ::backToPlans, pane = paneEvents(), onDone = ::completeStop,
    onOptimize = ::openOptimize)

fun AppViewModel.plansUi(): PlansUi = PlansUi(
    rows = plans?.value?.let { PlanLogic.sortForToday(it, today()) }.orEmpty().map { PlanRowUi(it.id, it.title, "${PlanLogic.dateLabel(it.plannedDate)} · ${it.stopCount}곳") },
    loading = plansLoading && plans == null, notice = Notices.of(plans, plansMessage))

fun AppViewModel.plansEvents() = PlansEvents(onOpen = ::openPlan, onBack = ::goBack, onRetry = ::loadPlans)

// ── PLAN_INSERT ──

fun AppViewModel.insertUi(): InsertUi? {
    val a = pendingAdd ?: return null
    val cx = a.cid?.let { complexes[it]?.value }
    return InsertUi(a.name, a.cid, a.brand ?: cx?.brand, cx?.let { ComplexFacts.summaryLine(it) } ?: a.sub, insertOptions.map { it.label }, insertIndex,
        busy = addBusy, notice = addMessage?.let { Notice(NoticeKind.Error, Notices.short(it)) })
}

fun AppViewModel.insertEvents() = InsertEvents(onPick = ::pickInsert, onConfirm = ::confirmAdd, onCancel = ::cancelAdd)

// ── handoff, link, settings ──

fun AppViewModel.handoffUi() = HandoffUi(handoff?.phase, handoff?.title.orEmpty(), (handoff?.phase as? kr.co.reevl.ontheway.HandoffPhase.Success)?.route,
    arrived = handoff?.stop?.let { s -> arrivedStopId == s.id && plan?.value?.stops?.firstOrNull { it.id == s.id }?.visitStatus != PlanLogic.VISITED } == true)
fun AppViewModel.handoffEvents() = HandoffEvents(onRetry = ::retryHandoff, onClose = ::closeHandoff,
    onDone = { handoff?.stop?.let { completeStop(it) }; closeHandoff() })

fun AppViewModel.linkUi(): LinkUi {
    val m = linkMessage
    val notice = when {
        m == null -> null
        m == kr.co.reevl.ontheway.data.UserMessages.EXPIRED -> Notice(NoticeKind.Info, "연결이 끝났어요")
        m == kr.co.reevl.ontheway.data.UserMessages.CODE_INVALID -> Notice(NoticeKind.Error, "코드가 맞지 않아요")
        m.startsWith(kr.co.reevl.ontheway.data.UserMessages.OFFLINE) -> Notice(NoticeKind.Offline, "오프라인이에요")
        m.startsWith("서버에 연결하지 못해") -> Notice(NoticeKind.Info, "휴대폰에서도 연결을 끊어 주세요")
        m == kr.co.reevl.ontheway.data.UserMessages.RATE_LIMITED -> Notice(NoticeKind.Error, "잠시 후 다시 해 주세요")
        else -> Notice(NoticeKind.Error, "연결하지 못했어요")
    }
    val q = qrShown
    return LinkUi(code, linkBusy, invalid = m == kr.co.reevl.ontheway.data.UserMessages.CODE_INVALID, notice = notice,
        mode = linkMode, qr = QrUi(qrPhase, q?.url, q?.match))
}

fun AppViewModel.linkEvents() = LinkEvents(onDigit = ::typeDigit, onDelete = ::deleteDigit, onSubmit = ::submitCode,
    onUseQr = ::useQrLink, onUseCode = ::useCodeLink, onNewQr = ::newQr)

fun AppViewModel.settingsUi() = SettingsUi(BuildConfig.VERSION_NAME, trailOn)
fun AppViewModel.settingsEvents() = SettingsEvents(onUnlink = ::askUnlink, onBack = ::goBack, onToggleTrail = ::toggleTrail,
    onPrivacy = ::openPrivacy, onLicenses = ::openLicenses)
fun AppViewModel.unlinkEvents() = UnlinkEvents(onConfirm = ::logout, onCancel = ::goBack)

// ── 휴대폰으로 넘기기 ──

fun AppViewModel.phoneUi(): PhoneUi? {
    val st = phone ?: return null
    val done = when (val ch = st.change) {
        is kr.co.reevl.ontheway.link.PhoneHandoff.Change.PlanMade -> {
            val p = st.plan
            val title = if (st.isNew) "휴대폰에서 계획을 만들었어요" else "휴대폰에서 계획을 바꿨어요"
            if (p == null) PhoneDoneUi(title, plans?.value?.firstOrNull { it.id == ch.planId }?.title, "열기")
            else {
                val wanted = st.wantedCid?.takeIf { st.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN && pendingAdd?.cid == it }
                when (kr.co.reevl.ontheway.link.PhoneHandoff.planAction(p, wanted) { PlanLogic.canGuide(it) }) {
                    // The wanted complex is not in it yet: `<plan> · 가상별빛타운 넣기` → [추가]
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.ADD -> PhoneDoneUi(title, listOfNotNull(p.title, st.wantedName?.let { "$it 넣기" }).joinToString(" · "), "추가")
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.GUIDE -> PhoneDoneUi(title, kr.co.reevl.ontheway.link.PhoneHandoff.planStop(p, wanted)?.name ?: p.title, "안내")
                    kr.co.reevl.ontheway.link.PhoneHandoff.PlanAction.OPEN -> PhoneDoneUi(title, kr.co.reevl.ontheway.link.PhoneHandoff.planStop(p, wanted)?.name ?: p.title, "열기")
                }
            }
        }
        is kr.co.reevl.ontheway.link.PhoneHandoff.Change.Saved -> PhoneDoneUi("휴대폰에서 저장했어요", st.savedName, "열기")
        null -> null
    }
    val notice = st.message?.let { Notice(if (it == "오프라인이에요") NoticeKind.Offline else NoticeKind.Error, if (st.stopped) it else "$it · 10초 뒤 다시 확인해요") }
    return PhoneUi(st.kind, st.url, st.stopped, notice, done)
}

fun AppViewModel.phoneEvents() = PhoneEvents(onClose = ::closePhone, onAction = ::phoneAction, onRetry = ::retryPhone)

@Suppress("unused") private fun unused(t: LatestTrade?, s: PlanStop?) = Unit
