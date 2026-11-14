package kr.co.reevl.ontheway.ui.screens

import androidx.compose.runtime.Composable
import kotlin.math.abs
import kotlin.math.floor
import kr.co.reevl.ontheway.HandoffPhase
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.BannerKind
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.ChipSpec
import kr.co.reevl.ontheway.ui.ds.comp.CodeCells
import kr.co.reevl.ontheway.ui.ds.comp.CondChipSpec
import kr.co.reevl.ontheway.ui.ds.comp.FieldSpec
import kr.co.reevl.ontheway.ui.ds.comp.IconSpec
import kr.co.reevl.ontheway.ui.ds.comp.Keypad
import kr.co.reevl.ontheway.ui.ds.comp.Leading
import kr.co.reevl.ontheway.ui.ds.comp.MessageSpec
import kr.co.reevl.ontheway.ui.ds.comp.MsgTone
import kr.co.reevl.ontheway.ui.ds.comp.PaneHeader
import kr.co.reevl.ontheway.ui.ds.comp.PaneSpec
import kr.co.reevl.ontheway.ui.ds.comp.RowGroup
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.TabsSpec
import kr.co.reevl.ontheway.ui.ds.comp.TileSpec
import kr.co.reevl.ontheway.ui.ds.comp.Tone
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.comp.TradeSpec
import kr.co.reevl.ontheway.ui.ds.comp.TradesSpec
import kr.co.reevl.ontheway.ui.ds.comp.StatSpec
import kr.co.reevl.ontheway.ui.ds.comp.CompareVs
import kr.co.reevl.ontheway.ui.ds.tmpl.CompareCellSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.CompareColSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.CompareRowSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.CompareTemplate
import kr.co.reevl.ontheway.ui.state.CompareEvents
import kr.co.reevl.ontheway.ui.state.CompareUi
import kr.co.reevl.ontheway.ui.state.ComparePickEvents
import kr.co.reevl.ontheway.ui.state.ComparePickUi
import kr.co.reevl.ontheway.ui.state.DetailLogic
import kr.co.reevl.ontheway.ui.ds.comp.sideChip
import kr.co.reevl.ontheway.ui.ds.comp.saveToggle
import kr.co.reevl.ontheway.ui.ds.tmpl.ConfirmTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.HomeTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.ListTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.MessageTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.PaneTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.SearchTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.ToastSlot
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.ontheway.ui.state.AiSearchEvents
import kr.co.reevl.ontheway.ui.state.AiSearchUi
import kr.co.reevl.ontheway.ui.state.DetailEvents
import kr.co.reevl.ontheway.ui.state.DetailUi
import kr.co.reevl.ontheway.ui.state.HandoffEvents
import kr.co.reevl.ontheway.ui.state.HandoffUi
import kr.co.reevl.ontheway.ui.state.HomeEvents
import kr.co.reevl.ontheway.ui.state.HomeUi
import kr.co.reevl.ontheway.ui.state.InsertEvents
import kr.co.reevl.ontheway.ui.state.InsertUi
import kr.co.reevl.ontheway.ui.state.LinkEvents
import kr.co.reevl.ontheway.ui.ds.comp.QrMatchNumber
import kr.co.reevl.ontheway.ui.ds.comp.QrPanel
import kr.co.reevl.ontheway.ui.state.LinkUi
import kr.co.reevl.ontheway.ui.state.PhoneEvents
import kr.co.reevl.ontheway.ui.state.PhoneUi
import kr.co.reevl.ontheway.ui.state.NearbyEvents
import kr.co.reevl.ontheway.ui.state.NearbyUi
import kr.co.reevl.ontheway.ui.state.Notice
import kr.co.reevl.ontheway.ui.state.NoticeKind
import kr.co.reevl.ontheway.ui.state.PaneEvents
import kr.co.reevl.ontheway.ui.state.PaneSecondary
import kr.co.reevl.ontheway.ui.state.PaneUi
import kr.co.reevl.ontheway.ui.state.PlaceRowUi
import kr.co.reevl.ontheway.ui.state.RowLines
import kr.co.reevl.ontheway.ui.state.PlansEvents
import kr.co.reevl.ontheway.ui.state.PlansUi
import kr.co.reevl.ontheway.ui.state.SavedEvents
import kr.co.reevl.ontheway.ui.state.SavedUi
import kr.co.reevl.ontheway.ui.state.SearchConfirmEvents
import kr.co.reevl.ontheway.ui.state.SearchConfirmUi
import kr.co.reevl.ontheway.ui.state.SettingsEvents
import kr.co.reevl.ontheway.ui.state.SettingsUi
import kr.co.reevl.ontheway.ui.state.UnlinkEvents
import kr.co.reevl.ontheway.ui.state.VisitPlanEvents
import kr.co.reevl.ontheway.ui.state.VisitPlanUi

// Every screen = one template + data in its slots (car-ds-v2 §10.1: no Column/Row/Box here). Copy is the spec's copy.

// ── shared mapping ──

private fun Notice.banner(retry: (() -> Unit)? = null) = BannerSpec(
    when (kind) { NoticeKind.Offline -> BannerKind.Offline; NoticeKind.Error -> BannerKind.Error; NoticeKind.Info -> BannerKind.Info },
    text, action = if (this.retry && retry != null) Btn("다시", retry, tag = "retry") else null)

/** `2026-11-08` → `11.08`. */
private fun md(d: String?) = d?.takeIf { it.length >= 10 }?.let { it.substring(5, 7) + "." + it.substring(8, 10) }

fun sourceLine(asOf: String?) = md(asOf)?.let { "실거래 신고 · 국토교통부 · $it 기준" }

private fun price(m: Long) = ReevlFormat.wonShort(m.toDouble())

/** §2 row anatomy (P0-5): name / `120m · 1,148세대 · 최근 23.6억` — same order on every complex list; 저장 adds its day last. */
private fun placeRow(p: PlaceRowUi, saved: Boolean = false) = RowSpec(
    key = p.key, primary = p.name, leading = Leading.Avatar(p.name, p.cid, p.brand),
    secondary = RowLines.place(p, tail = p.tail ?: if (saved) p.savedAt?.takeIf { it > 0 }?.let { savedDay(it) } else null),
    chip = if (p.inPlan) ChipSpec("계획", Tone.Sub) else sideChip(p.side), tag = "row_${p.name}")

private fun savedDay(at: Long) = java.time.format.DateTimeFormatter.ofPattern("MM.dd").withZone(java.time.ZoneId.of("Asia/Seoul"))
    .format(java.time.Instant.ofEpochMilli(at)) + " 저장"

private fun trades(c: CarComplex?, loading: Boolean, error: Boolean): TradesSpec = when {
    c == null -> if (error) TradesSpec.Error else if (loading) TradesSpec.Loading else TradesSpec.None
    c.recentTrades == null -> TradesSpec.Error
    c.recentTrades.isEmpty() -> TradesSpec.Empty
    else -> {
        // §3.6: ≤ 4, newest first; ▲▼ only against a same-area trade within 365 days (P2 — no 3-year gap read as a rise).
        val rows = DetailLogic.trades(c.recentTrades)
        TradesSpec.Items(rows.mapIndexed { i, t ->
            val d = DetailLogic.deltaOf(rows, i)?.takeIf { it != 0L }
            TradeSpec(price(t.price), d?.let { price(abs(it)) }, (d ?: 0) > 0, ReevlFormat.mdD(t.date),
                listOfNotNull("${floor(t.areaM2).toInt()}㎡", t.floor?.let { "${it}층" }).joinToString(" · "))
        })
    }
}

/** [tab] = DETAIL's selected tab (null = the single pane without tabs, e.g. an older server without `detail`). */
fun paneSpec(p: PaneUi, ev: PaneEvents, checks: List<String> = emptyList(), tab: String? = null, onTab: (String) -> Unit = {}): PaneSpec {
    val pick = p.pick
    val c = p.complex?.value
    // §3.6 P1 ①: `오른쪽 586m · 1,248세대 · 2019년`
    val facts = DetailLogic.facts(pick.side, pick.distanceM, c?.households, c?.completionYear, c?.buildAgeYears) ?: if (!pick.known) "단지 정보 없음" else null
    val save = if (pick.known || p.saved == true) saveToggle(p.saved, p.saveBusy, ev.onToggleSave) else null
    val secondary = when (p.secondary) {
        PaneSecondary.Add -> Btn("추가", ev.onSecondary, loading = p.secondaryBusy, tag = "add")
        PaneSecondary.AddDisabled -> Btn("추가", ev.onSecondary, enabled = false, tag = "add")
        PaneSecondary.Done -> Btn("완료", ev.onSecondary, loading = p.secondaryBusy, tag = "done")
        PaneSecondary.None -> null
    }
    val tr = if (pick.known && pick.cid.contains('|')) trades(c, p.complexLoading, p.complexError) else TradesSpec.None
    val d = c?.detail
    val tabs = if (tab != null && d != null) DetailLogic.visibleTabs(d, hasTrades = tr !is TradesSpec.None) else emptyList()
    val sel = tabs.firstOrNull { it.first == tab }?.first ?: tabs.firstOrNull()?.first
    return PaneSpec(
        key = pick.cid, avatarName = pick.displayName, cid = pick.cid.takeIf { it.contains('|') }, brand = c?.brand ?: pick.brand,
        name = c?.name ?: pick.displayName, facts = facts, chip = if (p.guiding) ChipSpec("안내 중", Tone.Brand) else null, save = save,
        checks = if (sel == null || sel == "summary") (checks.ifEmpty { pick.checks.filter { it.ok == true && it.label.isNotBlank() }.map { it.label } }).take(3) else emptyList(),
        trades = tr,
        // §3.6 P1 ②: source + at most one basis fragment
        footer = if (c != null) DetailLogic.footer(sourceLine(c.tradeAsOf), p.basis) else null,
        // One place in the app: the complex detail, under its source line (prices · AI 지표 are reference statistics).
        disclaimer = if (c != null) DetailLogic.DISCLAIMER else null,
        actions = Actions(Btn("안내", ev.onGuide, enabled = p.canGuide && !p.guiding, tag = "guide"), secondary),
        tabs = if (tabs.size > 1) TabsSpec(tabs.map { it.second }, tabs.indexOfFirst { it.first == sel }, { i -> onTab(tabs[i].first) }) else null,
        stats = if (sel != null) DetailLogic.tabRows(d, sel).map { StatSpec(it.key, it.label, it.value, it.sub, it.color) } else emptyList(),
        tradeLimit = when { sel == null -> 4; sel == "summary" -> 1; sel == d?.trades -> 4; else -> 0 },
        // 내 메모 (phone → car, read-only): on the first view only, under the checks.
        memo = p.memo?.takeIf { sel == null || sel == "summary" }?.let { m ->
            RowSpec("memo", kr.co.reevl.ontheway.search.Recent.memoText(m), kr.co.reevl.ontheway.search.Recent.memoLine(m),
                Leading.Glyph(Lucide.Smartphone), inert = true, wrap = true, tag = "pane_memo")
        },
    )
}

/** 최근 본 단지 rows (HOME · AI 검색): ≤ 3 + `더보기` (→ 저장 · 최근 본 tab). */
private fun recentRows(rows: List<PlaceRowUi>, more: Boolean, onOpen: (String) -> Unit, onMore: () -> Unit, tagPrefix: String): List<RowSpec> =
    rows.mapIndexed { i, p -> placeRow(p).copy(onClick = { onOpen(p.key) }, tag = "${tagPrefix}_recent_$i") } +
        if (more) listOf(RowSpec("recent_more", "더보기", leading = Leading.Glyph(Lucide.Clock), chevron = true, onClick = onMore, tag = "${tagPrefix}_recent_more")) else emptyList()

// ── HOME ──

@Composable
fun HomeScreen(ui: HomeUi, ev: HomeEvents, toast: ToastSlot?) {
    val arrived = ui.stops.filter { it.arrived }
    val wide = kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow.current
    // §15.7 "지금 여기": all from memory / the device cache (nearby cell cache, area cache, favs) — no request from HOME.
    // Wide side column (560): the value goes on the second line so the label never yields to it.
    val area = if (wide) RowSpec("area", "동네", ui.areaValue ?: "열어서 보기", Leading.Glyph(kr.co.reevl.ontheway.ui.components.AreaIcons.Map),
        chevron = true, onClick = ev.onArea, tag = "home_area")
    else RowSpec("area", "동네", leading = Leading.Glyph(kr.co.reevl.ontheway.ui.components.AreaIcons.Map),
        chip = ChipSpec(ui.areaValue ?: "열어서 보기", Tone.Sub), chevron = true, onClick = ev.onArea, tag = "home_area")
    val nearby = RowSpec("nearby", "주변", leading = Leading.Glyph(Lucide.LocateFixed), chip = ChipSpec(ui.nearbyValue, Tone.Sub),
        chevron = true, onClick = ev.onNearby, tag = "home_nearby")
    val saved = RowSpec("saved", "저장", leading = Leading.Glyph(Lucide.Star), chip = ChipSpec(ui.savedValue, Tone.Sub),
        chevron = true, onClick = ev.onSaved, tag = "home_saved")
    HomeTemplate(
        // 설정 lives in the left menu (☰ in the 1:1 window). `휴대폰으로 찾기` = secondary header action (voice stays the field's button):
        // long queries / setup are typed on the phone and come back here.
        top = TopBarSpec(title = "오늘 방문", actions = listOf(phoneFind(ev.onPhone))),
        banner = ui.notice?.banner(ev.onRetry),
        field = FieldSpec(ui.query, ev.onQuery, ev.onSubmit, loading = false, onVoice = ev.onVoice),
        // The phone's saved conditions come first (search/Recent.chips); a tap on one runs its car sentence.
        suggest = ui.suggest, onSuggest = { label -> ui.chips.firstOrNull { it.label == label }?.let { c -> if (c.screenerId != null) ev.onScreener(c.screenerId) else ev.onSuggest(c.q) } ?: ev.onSuggest(label) },
        voiceHint = kr.co.reevl.ontheway.voice.VoiceHints.DRIVING.takeIf { kr.co.reevl.ontheway.Features.GLEO },
        section = ui.planTitle.takeIf { ui.stops.any { !it.arrived } || ui.stops.isEmpty() },
        rows = ui.stops.filter { !it.arrived }.map { r -> stopRow(r, showGuide = r.next, onGuide = { ev.onGuideNext(r.stop) }, onDone = { ev.onDoneStop(r.stop) }) } +
            // 동선 지도 (web /plan map) one tap away, under the stops it draws.
            listOfNotNull(ui.route?.takeIf { ui.stops.any { !it.arrived } && !(wide && ui.routePins.isNotEmpty()) }?.let { RowSpec("route", "동선 지도", it, Leading.Glyph(Lucide.Route), chevron = true,
                onClick = ev.onRoute, tag = "home_route") }),
        arrivedTitle = arrived.firstOrNull()?.let { "${it.stop.name}에 왔어요" },
        arrived = arrived.map { r -> stopRow(r, showGuide = false, onGuide = {}, onDone = { ev.onDoneStop(r.stop) }) },
        passedTitle = ui.passedTitle,
        passed = ui.passed.mapIndexed { i, p -> RowSpec(p.cid, p.name, p.secondary, Leading.Avatar(p.name, p.cid, p.brand), chip = sideChip(p.side),
            onClick = { ev.onPassed(p.cid) }, tag = "passed_$i") },
        empty = if (ui.stops.isEmpty()) MessageSpec(null, "오늘 방문할 곳이 없어요", "휴대폰 리블에서 계획을 만들면 여기에 보여요") else null,
        footer = sourceLine(ui.asOf), onRow = { row -> ui.stops.firstOrNull { it.stop.id == row.key }?.let { ev.onOpenNext(it.stop) } },
        toast = toast,
        // Compact/narrow: 주변 · 동네 · 저장 under the field (the menu is folded). Wide: those live in the left menu, so the side
        // column is the 동네 context only — headline `지금 여기 · 신흥동 · 1년 +26.4%` + insight lines; uncached → one 동네 row.
        context = if (wide) (if (ui.areaValue == null) listOf(area) else emptyList()) else listOf(nearby, area, saved),
        contextTitle = if (wide) ui.areaValue?.let { "지금 여기 · $it" } else null,
        // The nearest 3 from the last nearby answer in memory (left/right badge) — the 2 s glance of "what is around me".
        nearTitle = "가까운 단지",
        // Review: a complex already in the arrival / plan block is not repeated here (W and C).
        near = ui.nearbyTop.filter { p -> ui.stops.none { it.stop.cid != null && it.stop.cid == p.cid } }.map { p -> placeRow(p).copy(onClick = { p.cid?.let(ev.onOpenNearby) }, tag = "home_near_${p.name}") },
        // Wide: the route itself in the side column (a glance at where today goes), the 동선 지도 row moves there.
        sideMap = if (wide && ui.routePins.isNotEmpty() && ui.route != null) { {
            kr.co.reevl.ontheway.ui.ds.tmpl.RouteSideMap(kr.co.reevl.ontheway.ui.ds.comp.PlanRouteSpec(ui.routePins.map { it.ds() }, tag = "home_route_map"),
                // `직선 13.9km` / `정리하면 5.8km` — the number on the first line, the hint under it (no mid-number wrap).
                RowSpec("route", ui.route.substringBefore(" · "), ui.route.substringAfter(" · ", "").ifEmpty { null }, Leading.Glyph(Lucide.Route),
                    chevron = true, onClick = ev.onRoute, tag = "home_route_row"), ev.onRoute)
        } } else null,
        recentTitle = "최근 본 단지",
        recent = recentRows(ui.recent, ui.recentMore, ev.onRecent, ev.onRecentMore, "home"),
        contextLines = if (wide) ui.areaLines.mapIndexed { i, t -> RowSpec("al$i", t, leading = Leading.Glyph(kr.co.reevl.ontheway.ui.components.AreaIcons.ChartColumn),
            inert = true, wrap = true, tag = "home_area_line_$i") } else emptyList(),
    )
}

/**
 * A plan stop as a row (HOME and 오늘 방문): order mark · name / station · state word · [안내] on the next one.
 * Arrived (driving-context-spec §3 S5): state `도착`, the one button becomes `[완료]` (whatever the 안내 state was).
 */
fun stopRow(r: kr.co.reevl.ontheway.ui.state.StopRowUi, showGuide: Boolean, onGuide: () -> Unit, onDone: () -> Unit = {}) = RowSpec(
    r.stop.id, r.stop.name, r.secondary, Leading.Order(r.stop.position, r.next, r.done),
    // UX writing 2026-11-11: a chip only when the state differs from "planned" and nothing else says it — 완료 = the ✓ mark,
    // 다음 = the brand order mark (+ [안내]), 예정 = every other row (it said nothing).
    // Arrived: the [완료] button is shown only there, so the `도착` chip said it twice and wrapped the meta line (W 620).
    chip = when { r.arrived -> null; r.guiding -> ChipSpec("안내 중", Tone.Brand)
        r.skipped -> ChipSpec("건너뜀", Tone.Sub); r.next && !showGuide && !r.done -> ChipSpec("다음", Tone.Neutral); else -> null },
    button = when {
        r.arrived -> Btn("완료", onDone, tag = "row_done")
        showGuide && !r.guiding -> Btn("안내", onGuide, enabled = r.canGuide, tag = "row_guide")
        else -> null
    },
    // A stop's line leads with the station or, without one, the address (UxP0Test order) — with a chip / [완료] beside it the
    // numbers after it were cut (`1,530세대 …`, QA rounds 2–3). The line wraps to 2 instead.
    enabled = !r.skipped, wrap = true, tag = "stop_${r.stop.position}")

// ── 메뉴 (1:1 window) ──

@Composable
fun MenuScreen(rail: kr.co.reevl.ontheway.ui.ds.comp.RailSpec, more: kr.co.reevl.ontheway.ui.ds.comp.RailItem?, onBack: () -> Unit) {
    // ≤ 6 rows (R3); a folded 설정 sits in the header as ⋯ `설정` (tag menu_settings, same as its row would have).
    ListTemplate("screen_menu", TopBarSpec("메뉴", onBack, actions = listOfNotNull(more?.let { m ->
        IconSpec(m.icon, m.label, m.onClick, text = m.label, tag = "menu_${m.key}") })), null,
        rows = rail.items.map { RowSpec(it.key, it.label, null, Leading.Glyph(it.icon), chip = it.value?.let { v -> ChipSpec(v, Tone.Sub) }, chevron = true, onClick = it.onClick, tag = "menu_${it.key}") },
        selectedKey = null, onSelect = {}, onOpen = {}, footer = null)
}

// ── AI 검색 ──

@Composable
fun AiSearchScreen(ui: AiSearchUi, ev: AiSearchEvents, toast: ToastSlot?) {
    SearchTemplate(
        top = TopBarSpec("AI 검색", actions = listOf(phoneFind(ev.onPhone))),
        banner = ui.notice?.banner { ev.onRun(ui.query) },
        field = FieldSpec(ui.query, ev.onQuery, ev.onRun, ui.loading, onVoice = ev.onVoice),
        voice = ui.voice?.let { v -> kr.co.reevl.ontheway.ui.ds.comp.VoicePanelSpec(
            // The Gleo title / note only once Gleo is approved (Features.GLEO); without it the panel exists only while listening.
            title = if (v.listening || !kr.co.reevl.ontheway.Features.GLEO) "듣고 있어요" else "글레오에게 말해 보세요", lines = v.lines,
            note = if (v.listening || !kr.co.reevl.ontheway.Features.GLEO) null else "운전 중에도 글레오가 리블에서 찾아 말로 알려 줘요", listening = v.listening, heard = v.heard,
            onClose = ev.onVoiceClose) },
        // Idle: the phone's saved conditions first (one tap = run), then the car's own suggestions.
        suggestions = if (ui.idle) ui.screens.mapIndexed { i, c ->
            RowSpec("sc$i", c.label, "휴대폰에서 저장한 조건 · ${c.q}", Leading.Glyph(Lucide.Smartphone), onClick = { c.screenerId?.let(ev.onScreener) }, tag = "screener_$i")
        } + ui.suggestions.mapIndexed { i, s ->
            RowSpec("s$i", s, leading = Leading.Glyph(Lucide.Search), onClick = { ev.onQuery(s); ev.onRun(s) }, tag = "suggest_$i")
        } else null,
        recentTitle = "최근 본 단지",
        recent = if (ui.idle) recentRows(ui.recent, ui.recentMore, ev.onRecent, ev.onRecentMore, "search") else emptyList(),
        chips = ui.chips.map { c -> CondChipSpec(c.label, c.warn, { ev.onChip(c) }, tag = "cond_${c.key}") },
        summary = listOfNotNull(ui.summary, ui.note).joinToString(" · ").ifEmpty { null },
        rows = ui.rows.map { placeRow(it) },
        selectedKey = ui.selectedKey, onSelect = ev.onSelect, onOpen = ev.onOpen,
        footer = sourceLine(ui.asOf),
        preview = null,
        // AiSearch.empty: nothing in the radius → widen (dropping a condition cannot help); no match → say which check failed most.
        empty = if (ui.empty) when (val why = ui.emptyWhy) {
            is kr.co.reevl.ontheway.search.AiSearch.Empty.NoCandidates -> MessageSpec(null, "${kr.co.reevl.ontheway.search.AiSearch.radiusText(why.radiusM)} 안에 단지가 없어요",
                primary = if (why.canWiden) Btn("넓히기", ev.onWiden, tag = "widen") else null, tag = "search_empty")
            else -> MessageSpec(null, "맞는 단지가 없어요",
                value = (why as? kr.co.reevl.ontheway.search.AiSearch.Empty.NoMatch)?.failed?.let { "$it 조건에 맞는 곳이 없어요" },
                primary = if (ui.canDropCondition) Btn("조건 빼기", ev.onDropLast, tag = "drop") else null, tag = "search_empty")
        } else null,
        loading = ui.loading, toast = toast,
    )
}

@Composable
fun SearchConfirmScreen(ui: SearchConfirmUi, ev: SearchConfirmEvents) {
    ConfirmTemplate("screen_search_confirm", TopBarSpec("AI 검색", ev.onCancel), null, "${ui.label}${kr.co.reevl.ontheway.search.AiSearch.objParticle(ui.label)} 어떻게 볼까요?", null,
        input = { RowGroup(ui.options.mapIndexed { i, o -> RowSpec("$i", o, onClick = { ev.onPick(i) }, tag = "opt_$i") }, "${ui.selected}", { }) },
        actions = Actions(Btn("적용", ev.onApply, tag = "apply"), Btn("취소", ev.onCancel, tag = "cancel")))
}

// ── NEARBY ──

@Composable
fun NearbyScreen(ui: NearbyUi, ev: NearbyEvents, toast: ToastSlot?) {
    ListTemplate(
        tag = "screen_nearby",
        top = TopBarSpec("주변", null, listOf(IconSpec(Lucide.RefreshCw, "새로고침", ev.onRefresh, tag = "refresh"))),
        banner = ui.notice?.banner(ev.onRefresh),
        tabs = ui.tabs?.let { TabsSpec(it, ui.tab, ev.onTab) },
        rows = ui.rows.map { placeRow(it) }, selectedKey = ui.selectedKey, onSelect = ev.onSelect, onOpen = ev.onOpen,
        footer = ui.basis, preview = null,
        empty = when {
            ui.noLocation -> MessageSpec(null, "위치를 확인하지 못했어요", primary = Btn("다시", ev.onRefresh, tag = "retry"))
            ui.empty -> MessageSpec(null, "주변에 단지가 없어요")
            else -> null
        },
        loading = ui.loading, toast = toast,
    )
}

// ── DETAIL ──

@Composable
fun DetailScreen(ui: DetailUi, ev: DetailEvents, toast: ToastSlot?) {
    val pane = ui.pane ?: return
    val spec = paneSpec(pane, ev.pane, tab = ui.tab, onTab = ev.onTab)
    // Header: ☆ 저장 + VS 비교 담기 (REEVL web compare mark). Only real REEVL complexes can be compared.
    val compare = if (pane.pick.known && pane.pick.cid.contains('|'))
        IconSpec(CompareVs, "비교", ev.onCompare, on = ui.inCompare, text = if (ui.inCompare) "비교 담김" else "비교", tag = "compare") else null
    PaneTemplate(TopBarSpec("단지", ev.onBack, listOfNotNull(compare, spec.save)), ui.notice?.banner(), spec, toast)
}

// ── COMPARE ──

@Composable
fun CompareScreen(ui: CompareUi, ev: CompareEvents, toast: ToastSlot?) {
    if (ui.cols.isEmpty()) {
        MessageTemplate("screen_compare", TopBarSpec("비교", ev.onBack), null,
            MessageSpec(null, "비교할 단지를 담아 주세요", "단지 상세의 [비교]로 담을 수 있어요", primary = Btn("담기", ev.onAdd, tag = "compare_add"), tag = "compare_empty"), toast)
        return
    }
    CompareTemplate(
        top = TopBarSpec("비교", ev.onBack),
        cols = ui.cols.map { c -> CompareColSpec(c.cid, c.name, c.brand, c.facts, c.loading, c.error,
            guide = Btn("안내", { ev.onGuide(c.cid) }, enabled = c.canGuide, tag = "cmp_guide"), open = Btn("상세", { ev.onOpen(c.cid) }, tag = "cmp_open"),
            onRemove = { ev.onRemove(c.cid) }) },
        tabs = if (ui.tabs.size > 1) TabsSpec(ui.tabs.map { it.second }, ui.tabs.indexOfFirst { it.first == ui.tab }.coerceAtLeast(0), { i -> ev.onTab(ui.tabs[i].first) }) else null,
        rows = ui.rows.map { r -> CompareRowSpec(r.key, r.label, r.cells.map { CompareCellSpec(it.value, it.sub, it.color, it.best) }) },
        onAdd = if (ui.canAdd) ev.onAdd else null,
        footer = listOfNotNull(sourceLine(ui.asOf), if (ui.cols.size < 2) "한 곳 더 담으면 나란히 비교돼요" else "색 칸 = 더 나은 값").joinToString(" · "),
        toast = toast,
    )
}

@Composable
fun ComparePickScreen(ui: ComparePickUi, ev: ComparePickEvents, toast: ToastSlot?) {
    val rows = ui.rows.map { p ->
        val on = p.key in ui.picked
        RowSpec(p.key, p.name, RowLines.place(p), Leading.Avatar(p.name, p.cid, p.brand),
            chip = if (on) ChipSpec("담김", Tone.Brand) else null, tag = "pick_${p.name}")
    }
    ListTemplate(
        tag = "screen_compare_pick",
        top = TopBarSpec("비교에 담기 · ${ui.picked.size}/3", ev.onBack),
        banner = ui.notice?.banner(),
        rows = rows, selectedKey = null, onSelect = ev.onToggle, onOpen = ev.onToggle,
        footer = "저장한 단지에서 최대 3곳", preview = null,
        empty = if (ui.empty) MessageSpec(null, "저장한 집이 없어요", primary = Btn("찾기", ev.onSearch, tag = "find"), tag = "pick_empty") else null,
        loading = ui.loading, toast = toast,
        actions = Actions(Btn("비교 보기", ev.onDone, enabled = ui.picked.isNotEmpty(), tag = "pick_done")),
    )
}

// ── SAVED ──

@Composable
fun SavedScreen(ui: SavedUi, ev: SavedEvents, toast: ToastSlot?) {
    val recentTab = ui.tab == "recent"
    val rows = if (recentTab) ui.recentRows.map { placeRow(it) } else ui.rows.map { placeRow(it, saved = true) } +
        if (ui.more) listOf(RowSpec("more", "휴대폰에서 더 보기", leading = Leading.Glyph(Lucide.Smartphone), inert = true, tag = "saved_more")) else emptyList()
    ListTemplate(
        tag = "screen_saved",
        // 저장한 단지 비교: the VS mark opens the compare picker (or the table when something is already in the tray).
        top = TopBarSpec("저장", null, listOfNotNull(if (ui.rows.size >= 2) IconSpec(CompareVs, "비교", ev.onCompare, text = "비교", tag = "saved_compare") else null,
            IconSpec(Lucide.RefreshCw, "새로고침", ev.onRefresh, tag = "refresh"))),
        banner = ui.notice?.banner(ev.onRefresh),
        rows = rows, selectedKey = ui.selectedKey, onSelect = ev.onSelect, onOpen = if (recentTab) ev.onOpenRecent else ev.onOpen,
        footer = sourceLine(ui.asOf), preview = null,
        // 저장 · 최근 본 (phone + car): two lists of my complexes in one place — no new menu item (≤ 6, R3).
        tabs = TabsSpec(listOf("저장", "최근 본"), if (recentTab) 1 else 0, { i -> ev.onTab(if (i == 1) "recent" else "saved") }),
        empty = if (recentTab) (if (ui.recentRows.isEmpty()) MessageSpec(null, "최근 본 단지가 없어요", "휴대폰이나 차에서 단지를 열면 여기에 함께 보여요", tag = "recent_empty") else null)
        else if (ui.empty) MessageSpec(null, "저장한 집이 없어요", "단지 상세에서 저장하면 휴대폰·웹과 함께 보여요", primary = Btn("찾기", ev.onSearch, tag = "find"), tag = "saved_empty") else null,
        loading = ui.loading, toast = toast,
    )
}

// ── VISIT_PLAN · PLANS · PLAN_INSERT ──

@Composable
fun VisitPlanScreen(ui: VisitPlanUi, ev: VisitPlanEvents, toast: ToastSlot?) {
    val rows = ui.rows.map { r -> stopRow(r, showGuide = r.next, onGuide = { ev.onGuide(r.stop) }, onDone = { ev.onDone(r.stop) })
    } + if (ui.rows.isNotEmpty()) listOf(RowSpec("add", "주변에서 추가", leading = Leading.Glyph(Lucide.Plus), onClick = ev.onAddNearby, tag = "add_nearby")) else emptyList()
    // 동선 지도 (web /plan map): straight lines in order + `직선 8.3km · 5곳`; `순서 최적화` only when a shorter order exists, with the
    // Gleo phrase for the same request (voice first — the button is the parked fallback).
    val map = ui.map?.let { m ->
        kr.co.reevl.ontheway.ui.ds.tmpl.PlanMapBlock(
            route = kr.co.reevl.ontheway.ui.ds.comp.PlanRouteSpec(m.pins.map { it.ds() }),
            title = m.title, sub = m.next,
            optimize = if (m.canOptimize) Btn("순서 정리", ev.onOptimize, icon = Lucide.Route, tag = "plan_optimize") else null,
            voiceHint = if (m.canOptimize && kr.co.reevl.ontheway.Features.GLEO) kr.co.reevl.ontheway.voice.VoiceHints.say("순서 정리해줘") else null)
    }
    kr.co.reevl.ontheway.ui.ds.tmpl.PlanTemplate(
        tag = "screen_plan",
        top = TopBarSpec("오늘 방문", ev.onBack, listOf(IconSpec(Lucide.RefreshCw, "새로고침", ev.onRefresh, tag = "refresh"))),
        banner = ui.notice?.banner(ev.onRefresh),
        map = map, rows = rows, onOpen = ev.onOpen, footer = ui.footer,
        empty = if (ui.missing) MessageSpec(Lucide.Flag, "계획을 찾을 수 없어요") else null,
        loading = ui.loading, toast = toast,
    )
}

private fun kr.co.reevl.ontheway.ui.state.RoutePinUi.ds() =
    kr.co.reevl.ontheway.ui.ds.comp.RoutePin(n, name, lat, lng, next = next, done = done, skipped = skipped)

/** 순서 최적화 미리보기: `2.5km 짧은 순서로 바꿀까요?` · 지금 │ 바꾸면 · the new order · [적용] [취소]. */
@Composable
fun PlanOptimizeScreen(ui: kr.co.reevl.ontheway.ui.state.PlanOptimizeUi, ev: kr.co.reevl.ontheway.ui.state.PlanOptimizeEvents, toast: ToastSlot?) {
    val rows = ui.rows.map { (n, name, old) ->
        // Name only; a moved stop gets a compact `지금 3번` chip (UX writing 2026-11-11 — no sub-line on every row).
        RowSpec("o$n", name, null, Leading.Order(n, next = false, done = false), chip = if (old != n) ChipSpec("지금 ${old}번", Tone.Sub) else null,
            inert = true, tag = "optimize_row_$n")
    }
    kr.co.reevl.ontheway.ui.ds.tmpl.OptimizeTemplate(
        tag = "screen_plan_optimize",
        top = TopBarSpec("순서 정리", ev.onCancel),
        banner = ui.notice?.banner(),
        question = "이 순서로 바꿀까요?",
        before = ui.beforeKm, after = ui.afterKm, saved = ui.saved, note = ui.note,
        left = kr.co.reevl.ontheway.ui.ds.tmpl.RouteSide("지금",
            kr.co.reevl.ontheway.ui.ds.comp.PlanRouteSpec(ui.before.map { it.ds() }, tag = "optimize_before"), emphasis = false),
        right = kr.co.reevl.ontheway.ui.ds.tmpl.RouteSide("바꾸면",
            kr.co.reevl.ontheway.ui.ds.comp.PlanRouteSpec(ui.after.map { it.ds() }, dashed = true, tag = "optimize_after"), emphasis = true),
        rows = rows,
        actions = Actions(Btn("적용", ev.onApply, loading = ui.busy, tag = "optimize_apply"), Btn("취소", ev.onCancel, enabled = !ui.busy, tag = "optimize_cancel")),
        toast = toast,
    )
}

@Composable
fun PlansScreen(ui: PlansUi, ev: PlansEvents) {
    ListTemplate("screen_plans", TopBarSpec("오늘 방문", ev.onBack), ui.notice?.banner(ev.onRetry),
        rows = ui.rows.map { RowSpec(it.id, it.title, it.secondary, Leading.Glyph(Lucide.Flag), chevron = true, onClick = { ev.onOpen(it.id) }, tag = "plan_${it.id}") },
        selectedKey = null, onSelect = {}, onOpen = ev.onOpen, footer = null, preview = null, loading = ui.loading,
        empty = if (!ui.loading && ui.rows.isEmpty()) MessageSpec(Lucide.Flag, "계획이 없어요") else null)
}

@Composable
fun InsertScreen(ui: InsertUi, ev: InsertEvents, toast: ToastSlot?) {
    ConfirmTemplate("screen_insert", TopBarSpec("추가", ev.onCancel), ui.notice?.banner(), "어디에 넣을까요?",
        value = { PaneHeader(PaneSpec("insert", ui.name, ui.cid, ui.brand, ui.name, ui.facts), showSave = false) },
        input = { RowGroup(ui.options.mapIndexed { i, o -> RowSpec("$i", o, onClick = { ev.onPick(i) }, tag = "opt_$i") }, "${ui.selected}", { }) },
        actions = Actions(Btn("넣기", ev.onConfirm, loading = ui.busy, tag = "insert"), Btn("취소", ev.onCancel, enabled = !ui.busy, tag = "cancel")),
        toast = toast)
}

// ── NAV 핸드오프 ──

@Composable
fun HandoffScreen(ui: HandoffUi, ev: HandoffEvents) {
    val msg = when (val p = ui.phase) {
        null, HandoffPhase.Sending -> MessageSpec(null, "내비로 보내는 중", ui.title, spinner = true, tag = "handoff_sending")
        // driving-context-spec §1.3 S5: the same screen, new words — never an automatic jump to another screen.
        is HandoffPhase.Success -> if (ui.arrived) MessageSpec(Lucide.Flag, "${ui.title}에 왔어요", null,
            primary = Btn("완료", ev.onDone, tag = "done"), secondary = Btn("닫기", ev.onClose, tag = "close"), tone = MsgTone.Brand, tag = "handoff_arrived")
        else MessageSpec(Lucide.Check, "안내를 시작했어요",
            listOfNotNull(ui.title, PlanLogic.distance(p.route?.distanceM), PlanLogic.duration(p.route?.durationS)).joinToString(" · "),
            primary = Btn("닫기", ev.onClose, tag = "close"), tone = MsgTone.Brand, tag = "handoff_success")
        is HandoffPhase.Failure -> MessageSpec(Lucide.CircleAlert, "안내를 못 했어요", null,
            primary = Btn("다시", ev.onRetry, tag = "retry"), secondary = Btn("닫기", ev.onClose, tag = "close"), tone = MsgTone.Error, tag = "handoff_failure")
    }
    MessageTemplate("screen_handoff", TopBarSpec("안내", ev.onClose), null, msg)
}

// ── 휴대폰으로 넘기기 ──

/** Header action `휴대폰으로 찾기` (narrow: the phone glyph alone, the label stays its a11y name). */
private fun phoneFind(onClick: () -> Unit) = IconSpec(Lucide.Smartphone, "휴대폰으로 찾기", onClick, text = "휴대폰으로 찾기", tag = "phone_find")

/**
 * The QR handoff (§15.3 single-purpose, same centred column as the QR link): question → QR → one line, `닫기` in the centred bar.
 * When the phone's change arrives the QR closes and the same screen says what came, with one action (안내 · 추가 · 열기).
 */
@Composable
fun PhoneHandoffScreen(ui: PhoneUi, ev: PhoneEvents) {
    val plan = ui.kind == kr.co.reevl.ontheway.link.PhoneHandoff.Kind.PLAN
    val top = TopBarSpec(if (plan) "계획 만들기" else "휴대폰으로 찾기", ev.onClose)
    ui.done?.let { d ->
        MessageTemplate("screen_phone_handoff", top, null, MessageSpec(Lucide.Check, d.title, d.value,
            primary = Btn(d.action, ev.onAction, tag = "phone_action"), secondary = Btn("닫기", ev.onClose, tag = "close"),
            tone = MsgTone.Brand, tag = "phone_done"))
        return
    }
    kr.co.reevl.ontheway.ui.ds.tmpl.FocusTemplate("screen_phone_handoff", top, ui.notice?.banner(),
        if (plan) "휴대폰에서 계획을 만들어 주세요" else "휴대폰에서 찾아 주세요",
        value = { kr.co.reevl.ontheway.ui.ds.comp.QrCode(ui.url, tag = "phone_qr", label = "휴대폰으로 여는 QR") },
        input = { kr.co.reevl.ontheway.ui.ds.comp.PhoneHandoffNote(
            if (plan) "카메라로 찍어 오늘 계획을 만들면 여기에 바로 보여요" else "긴 조건은 휴대폰에 쓰고, 저장하면 여기에 바로 보여요", ui.stopped) },
        actions = Actions(if (ui.stopped) Btn("다시 확인", ev.onRetry, tag = "phone_retry") else null, Btn("닫기", ev.onClose, tag = "close")))
}

// ── LINK · SETTINGS ──

/**
 * LINK — QR first (2026-11-10): the phone scans `reevl.co.kr/car?r=…`, checks the same 2-digit number and taps 연결; the car
 * polls while this screen is visible. The 6-digit keypad stays one tap away (번호 입력) and keeps its tags (`key_*`, `connect`).
 */
@Composable
fun LinkScreen(ui: LinkUi, ev: LinkEvents) {
    if (ui.mode == kr.co.reevl.ontheway.link.LinkMode.CODE) {
        // §15.3: one centred column (question · cells · keypad), actions under it — the same in every window.
        kr.co.reevl.ontheway.ui.ds.tmpl.FocusTemplate("screen_link", TopBarSpec(), ui.notice?.banner(), "휴대폰에 보이는 6자리",
            value = { CodeCells(ui.code, ui.invalid) },
            input = { Keypad(ev.onDigit, ev.onDelete) },
            actions = Actions(Btn("연결", ev.onSubmit, enabled = ui.code.length == 6, loading = ui.busy, tag = "connect"),
                Btn("QR 연결", ev.onUseQr, tag = "use_qr")))
        return
    }
    // §15.3 (user 2026-11-10 "QR must be centred"): instruction → QR → 확인 숫자 stacked in one centred column; 번호 입력 in
    // the centred action bar. Was: QR top-left of a 620 | 1059 split with an empty right/bottom quadrant.
    kr.co.reevl.ontheway.ui.ds.tmpl.FocusTemplate("screen_link_qr", TopBarSpec(), ui.notice?.banner(), "휴대폰 카메라로 QR을 찍어 주세요",
        value = { QrPanel(ui.qr.phase, ui.qr.url) },
        input = { QrMatchNumber(ui.qr.phase, ui.qr.match) },
        actions = Actions(if (ui.qr.renewable) Btn("새 QR", ev.onNewQr, tag = "new_qr") else null,
            Btn("번호 입력", ev.onUseCode, tag = "use_code")))
}

@Composable
fun SettingsScreen(ui: SettingsUi, ev: SettingsEvents) {
    ListTemplate("screen_settings", TopBarSpec("설정"), null,
        rows = listOf(
            // driving-context-spec §7-3: the 10-minute in-memory trail behind "방금 지나온 곳" can be turned off.
            RowSpec("trail", "지나온 길 기억", "최근 10분 · 이 차 안에만", Leading.Glyph(Lucide.Route),
                chip = ChipSpec(if (ui.trailOn) "켜짐" else "꺼짐", if (ui.trailOn) Tone.Brand else Tone.Sub, tag = "trail_state"),
                onClick = ev.onToggleTrail, tag = "trail_toggle"),
            RowSpec("unlink", "연결 해제", leading = Leading.Glyph(Lucide.LogOut), chevron = true, onClick = ev.onUnlink, tag = "unlink"),
            // review-user-privacy-security: the notice can be read again and consent withdrawn (legal/Legal.kt).
            RowSpec("privacy", "개인정보 안내", leading = Leading.Glyph(Lucide.Info), chevron = true, onClick = ev.onPrivacy, tag = "privacy"),
            RowSpec("oss", "오픈소스 라이선스", leading = Leading.Glyph(Lucide.Info), chevron = true, onClick = ev.onLicenses, tag = "oss"),
        ) + listOfNotNull(
            // The one place Gleo is mentioned before approval: a neutral fact, no promise of when (Features.GLEO).
            RowSpec("gleo", "글레오 음성 연동", "승인 후 열려요", leading = Leading.Glyph(Lucide.Mic), inert = true, tag = "gleo_pending")
                .takeIf { !kr.co.reevl.ontheway.Features.GLEO },
            RowSpec("version", "버전 ${ui.version}", leading = Leading.Glyph(Lucide.Info), inert = true, tag = "version"),
        ),
        selectedKey = null, onSelect = {}, onOpen = {}, footer = null, preview = null)
}

@Composable
fun UnlinkScreen(ev: UnlinkEvents) {
    ConfirmTemplate("screen_unlink", TopBarSpec("설정", ev.onCancel), null, "연결을 끊을까요?", null, input = null,
        actions = Actions(Btn("끊기", ev.onConfirm, tag = "confirm_unlink"), Btn("취소", ev.onCancel, tag = "cancel")))
}
