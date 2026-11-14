package kr.co.reevl.ontheway.ui.ds.tmpl

import kr.co.reevl.ontheway.ui.components.AiGradientText
import kr.co.reevl.ontheway.ui.components.aiLiveGrad
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.ActionBar
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.CondChipSpec
import kr.co.reevl.ontheway.ui.ds.comp.ConditionChip
import kr.co.reevl.ontheway.ui.ds.comp.FieldSpec
import kr.co.reevl.ontheway.ui.ds.comp.LockOverlay
import kr.co.reevl.ontheway.ui.ds.comp.MessageContent
import kr.co.reevl.ontheway.ui.ds.comp.MessageSpec
import kr.co.reevl.ontheway.ui.ds.comp.PaneBody
import kr.co.reevl.ontheway.ui.ds.comp.PaneHeader
import kr.co.reevl.ontheway.ui.ds.comp.PaneSpec
import kr.co.reevl.ontheway.ui.ds.comp.RowGroup
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.SearchField
import kr.co.reevl.ontheway.ui.ds.comp.Skeleton
import kr.co.reevl.ontheway.ui.ds.comp.SourceFooter
import kr.co.reevl.ontheway.ui.ds.comp.Tabs
import kr.co.reevl.ontheway.ui.ds.comp.TabsSpec
import kr.co.reevl.ontheway.ui.ds.comp.Tile
import kr.co.reevl.ontheway.ui.ds.comp.tag
import kr.co.reevl.ontheway.ui.ds.comp.TileSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.comp.SuggestChips
import kr.co.reevl.ontheway.ui.ds.comp.VoiceHintLine
import kr.co.reevl.ontheway.ui.ds.comp.VoicePanel
import kr.co.reevl.ontheway.ui.ds.comp.VoicePanelSpec
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.ontheway.ui.ds.token.ProvidePane
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

// car-ds-v2 §4: the seven templates. Screens fill these slots with data; they never lay out themselves.

/**
 * §13 HOME (v2.1) + §15.7 context-aware fill. Order = what matters now: AI field → [arrived stop] → [방금 지나온 곳] →
 * 오늘 방문 plan → source. The "지금 여기" rows (주변 left/right · 동네 heat · 저장) come from data the app already holds — HOME
 * never asks the server. Compact/narrow: those rows sit under the field (the left menu is folded there). Wide: the pane
 * splits like the built-in Phone (left = field + plan + 가까운 단지, right 1 dp line + "지금 여기" = 주변 · 동네 + the cached 동네 lines), so the wide
 * pane is not 60 % blank under one plan row. Flat rows only — no cards, no bold.
 */
@Composable
fun HomeTemplate(top: TopBarSpec, banner: BannerSpec?, field: FieldSpec, section: String?, rows: List<RowSpec>, empty: MessageSpec?,
                 footer: String?, onRow: (RowSpec) -> Unit, toast: ToastSlot? = null,
                 arrivedTitle: String? = null, arrived: List<RowSpec> = emptyList(),
                 context: List<RowSpec> = emptyList(), contextLines: List<RowSpec> = emptyList(), contextTitle: String? = null,
                 nearTitle: String? = null, near: List<RowSpec> = emptyList(),
                 passedTitle: String? = null, passed: List<RowSpec> = emptyList(),
                 suggest: List<String> = emptyList(), onSuggest: (String) -> Unit = {}, voiceHint: String? = null,
                 /** 최근 본 단지 (phone + car): ≤ 3 rows + `더보기`, after 가까운 단지. */
                 recentTitle: String? = null, recent: List<RowSpec> = emptyList(),
                 /** Wide only: today's route map at the top of the side column (tap → 오늘 방문). */
                 sideMap: (@Composable () -> Unit)? = null) {
    val r = LocalRoles.current
    val wide = LocalWideWindow.current
    // Voice first: under the field, one-tap requests (no keyboard) and what to say to Gleo while driving.
    val ask: @Composable () -> Unit = {
        // Narrow (560): each chip takes a whole line — 2 keep 주변 · 동네 · 저장 on the first screen (QA round 6).
        val narrowWin = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow
        SuggestChips(suggest.take(if (narrowWin) 2 else 3), onSuggest)
        voiceHint?.let { VoiceHintLine(it, Modifier.padding(start = CarSpace.item), tag = "home_voice_hint") }
    }
    val pad = kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current)
    val title: @Composable (String, String) -> Unit = { text, tag ->
        Text(text, style = LocalTypeV2.current.rowSecondary.copy(color = r.sub), modifier = Modifier.padding(top = CarSpace.gap).tag(tag))
    }
    // S5 arrived (driving-context-spec §3): the arrived stop first, right under the field — the one thing to do here.
    val arrival: @Composable () -> Unit = {
        if (arrivedTitle != null && arrived.isNotEmpty()) { title(arrivedTitle, "home_arrived"); RowGroup(arrived, null, onRow) }
    }
    val plan: @Composable () -> Unit = {
        // driving-context-spec §3 S4a: one flat section (≤ 3 rows).
        if (passedTitle != null && passed.isNotEmpty()) { title(passedTitle, "home_passed"); RowGroup(passed.take(3), null, { s -> s.onClick?.invoke() }) }
        section?.let { title(it, "home_section") }
        if (empty != null) MessageContent(empty, Modifier.padding(top = if (wide) CarSize.Empty.top else CarSize.Empty.topCompact)) else RowGroup(rows, null, onRow)
    }
    Frame("screen_home", top, banner, null, toast, onePane = false) {
        if (wide) Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(CarSpace.margin), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                SearchField(field)
                arrival() // S5: the arrived stop stays right under the field (QA round 2: the chips had pushed it down)
                ask()
                plan()
                if (nearTitle != null && near.isNotEmpty()) { title(nearTitle, "home_near"); RowGroup(near, null, { s -> s.onClick?.invoke() }) }
                if (recentTitle != null && recent.isNotEmpty()) { title(recentTitle, "home_recent"); RowGroup(recent, null, { s -> s.onClick?.invoke() }) }
                footer?.let { SourceFooter(it) }
            }
            if (context.isNotEmpty() || contextLines.isNotEmpty() || sideMap != null) {
                Box(Modifier.width(CarSpace.hairline).fillMaxHeight().background(r.line))
                Column(Modifier.width(CarSpace.homeSide).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = CarSpace.margin).padding(bottom = CarSpace.margin),
                    verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                    // §15.7 review: 주변 · 동네 are already in the left menu — the side column IS the 동네 context: its cached headline,
                    // then the insight lines (2 lines each, never cut). Nothing cached → one 동네 row to open it.
                    // Loop round 2: the side column was 70 % empty under one 동네 row — today's route map leads it when there is one.
                    sideMap?.let { title("동선", "home_route_title"); it() }
                    title(contextTitle ?: "지금 여기", "home_context")
                    if (context.isNotEmpty()) RowGroup(context, null, { s -> s.onClick?.invoke() })
                    if (contextLines.isNotEmpty()) RowGroup(contextLines, null, { })
                }
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            SearchField(field)
            arrival() // S5: the arrived stop stays right under the field
            ask()
            // 1:1 / narrow (screen-ux-research P0-4): the left menu is folded behind ☰, so the core entries get flat rows here.
            if (context.isNotEmpty()) RowGroup(context, null, { s -> s.onClick?.invoke() })
            plan()
            if (nearTitle != null && near.isNotEmpty()) { title(nearTitle, "home_near"); RowGroup(near, null, { s -> s.onClick?.invoke() }) }
            if (recentTitle != null && recent.isNotEmpty()) { title(recentTitle, "home_recent"); RowGroup(recent, null, { s -> s.onClick?.invoke() }) }
            footer?.let { SourceFooter(it) }
        }
    }
}

/**
 * §4.2 list (+ preview in the wide window: list 620 │ preview 1059). Row tap: wide → [onSelect], compact → [onOpen].
 * [empty] replaces the rows. [header] = extra fixed line(s) above the split (search field, chips).
 */
@Composable
fun ListTemplate(
    tag: String,
    top: TopBarSpec,
    banner: BannerSpec?,
    rows: List<RowSpec>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    footer: String?,
    @Suppress("UNUSED_PARAMETER") preview: PaneSpec? = null,
    tabs: TabsSpec? = null,
    empty: MessageSpec? = null,
    loading: Boolean = false,
    actions: Actions? = null,
    header: (@Composable () -> Unit)? = null,
    toast: ToastSlot? = null,
    /** The header is the REEVL AI search head: `.ai-live-grad.ai-glow` band behind it (web palette header, CommandPalette.tsx:866). */
    aiHeader: Boolean = false,
) {
    // v2.1: one list per pane; a row opens its detail in the same pane (back returns) — no list | preview split.
    Frame(tag, top, banner, actions, toast, onePane = true) {
        header?.let {
            Box(Modifier.fillMaxWidth().then(if (aiHeader) Modifier.aiLiveGrad(glow = true) else Modifier)
                .padding(horizontal = CarSpace.margin).padding(top = CarSpace.gapTight, bottom = if (aiHeader) CarSpace.gapTight else 0.dp)) { it() }
        }
        tabs?.let { Tabs(it) }
        val pad = kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current)
        if (empty != null && !(loading && rows.isEmpty())) {
            // §15.3: an empty / error state is the screen's one message — centred in the pane (a little above centre), not a
            // line under the header with the rest of the pane blank.
            CenteredMessage(empty, pad)
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.margin)) {
            when {
                loading && rows.isEmpty() -> Column(Modifier.padding(top = CarSpace.gapTight), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) { repeat(4) { Skeleton(CarSpace.rowDouble) } }
                else -> RowGroup(rows.take(12), null, { r -> onOpen(r.key) })
            }
            footer?.let { SourceFooter(it) }
        }
    }
}

/** §4.3 one complex alone: wide window → reading width 1200 with Wide roles. Save toggle sits in the TopBar. */
@Composable
fun PaneTemplate(top: TopBarSpec, banner: BannerSpec?, pane: PaneSpec, toast: ToastSlot? = null) {
    Frame("screen_detail", top, banner, pane.actions, toast, onePane = true) {
        if (pane.tabs == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarSpace.margin)) { PaneBody(pane, showSave = false) }
        } else {
            // Header and tabs stay put; only the tab's rows scroll (the 840 window shows ~6 rows, R3).
            Box(Modifier.padding(horizontal = CarSpace.margin).padding(top = CarSpace.gapTight)) { PaneHeader(pane, showSave = false) }
            Tabs(pane.tabs)
            androidx.compose.runtime.key(pane.tabs.selected) { // a new tab starts at its top
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = CarSpace.margin).padding(bottom = CarSpace.margin)) {
                    PaneBody(pane, showSave = false, header = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** §4.4 AI 검색. Idle: field + suggestion rows (one pane). Results: field + condition chips + summary, then the list rules. */
@Composable
fun SearchTemplate(
    top: TopBarSpec,
    banner: BannerSpec?,
    field: FieldSpec,
    suggestions: List<RowSpec>?,
    chips: List<CondChipSpec>,
    summary: String?,
    rows: List<RowSpec>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    footer: String?,
    preview: PaneSpec?,
    empty: MessageSpec?,
    loading: Boolean,
    toast: ToastSlot? = null,
    voice: VoicePanelSpec? = null,
    /** Idle only: 최근 본 단지 (phone + car) under the suggestion rows. */
    recentTitle: String? = null,
    recent: List<RowSpec> = emptyList(),
) {
    val head: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            SearchField(field)
            voice?.let { VoicePanel(it) }
            val sumText: @Composable () -> Unit = {
                // 2 lines: the count + the note about how the list was made (never cut away).
                // The AI's answer line = `.ai-text-grad` (web StreamText answer, CommandPalette.tsx:966).
                summary?.let { AiGradientText(it, LocalTypeV2.current.rowSecondary, Modifier.tag("search_summary"), maxLines = 2) }
            }
            if (LocalWideWindow.current) {
                if (chips.isNotEmpty() || summary != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                    chips.take(4).forEach { ConditionChip(it) }
                    Box(Modifier.weight(1f))
                    sumText()
                }
            } else {
                // 840 window: chips may take two lines (never cut), the count goes on its own line.
                if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) { chips.take(4).forEach { ConditionChip(it) } }
                sumText()
            }
        }
    }
    if (suggestions != null) {
        Frame("screen_ai_search", top, banner, null, toast, onePane = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                Box(Modifier.fillMaxWidth().aiLiveGrad(glow = true).padding(CarSpace.margin)) { head() }
                RowGroup(suggestions, null, { r -> r.onClick?.invoke() }, Modifier.padding(horizontal = CarSpace.margin).padding(bottom = CarSpace.margin))
                if (recentTitle != null && recent.isNotEmpty()) {
                    Text(recentTitle, style = LocalTypeV2.current.rowSecondary.copy(color = LocalRoles.current.sub), modifier = Modifier.padding(horizontal = CarSpace.margin).tag("search_recent"))
                    RowGroup(recent, null, { r -> r.onClick?.invoke() }, Modifier.padding(horizontal = CarSpace.margin).padding(bottom = CarSpace.margin))
                }
            }
        }
    } else {
        ListTemplate("screen_ai_search", top, banner, rows, selectedKey, onSelect, onOpen, footer, null, empty = empty, loading = loading, header = head, toast = toast, aiHeader = true)
    }
}

/** §4.5 one question + one input; wide: input left 620 (driver side) │ question + value right. */
@Composable
fun ConfirmTemplate(tag: String, top: TopBarSpec, banner: BannerSpec?, question: String, value: (@Composable () -> Unit)?, input: (@Composable () -> Unit)?,
                    actions: Actions, toast: ToastSlot? = null) {
    val wide = LocalWideWindow.current
    val r = LocalRoles.current
    // Yes/no (no input): the question alone, centred like a message.
    Frame(tag, top, banner, actions, toast, onePane = input == null) {
        val q: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                Text(question, style = LocalTypeV2.current.display.copy(color = r.ink), maxLines = 2, modifier = Modifier.padding(top = CarSpace.item))
                value?.invoke()
            }
        }
        when {
            input == null -> Box(Modifier.fillMaxSize().padding(CarSpace.margin), contentAlignment = Alignment.Center) { q() }
            wide -> Row(Modifier.fillMaxSize()) {
                ProvidePane(CarSpace.split) { Column(Modifier.width(CarSpace.split).fillMaxHeight().verticalScroll(rememberScrollState()).padding(CarSpace.margin)) { input() } }
                Box(Modifier.width(CarSpace.hairline).fillMaxHeight().background(r.line))
                ProvidePane(CarSpace.split) { Column(Modifier.weight(1f).fillMaxHeight().padding(CarSpace.margin)) { q() } }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarSpace.margin), verticalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                q(); input()
            }
        }
    }
}

/**
 * §15.3 single-purpose screen (QR 연결, 6자리 번호): ONE focal block — question, value, input stacked — centred horizontally
 * at ≤ [CarSpace.focusWidth] and centred vertically in the pane (scrolls only if the window is too short); the actions sit
 * in the action bar under the same column. No split columns, no dead quadrant, the same in 2:0, 1:1 and narrow.
 */
@Composable
fun FocusTemplate(tag: String, top: TopBarSpec, banner: BannerSpec?, question: String, value: (@Composable () -> Unit)?,
                  input: (@Composable () -> Unit)?, actions: Actions, toast: ToastSlot? = null) {
    val r = LocalRoles.current
    Frame(tag, top, banner, actions.copy(centered = true), toast, onePane = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pad = kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current)
            Column(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = CarSpace.gap),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Column(Modifier.widthIn(max = CarSpace.readWidth).fillMaxWidth().padding(bottom = CarSpace.gap), // a little above centre (optical)
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                    // The question may use the reading width (one line in 2:0); value and input keep the focus column.
                    Text(question, style = LocalTypeV2.current.display.copy(color = r.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
                    Column(Modifier.widthIn(max = CarSpace.focusWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                    value?.invoke()
                    input?.invoke()
                    }
                }
            }
        }
    }
}

/** A message centred in the rest of the pane, optically (bottom padding lifts it a little), scrolling only if too short. */
@Composable
internal fun CenteredMessage(msg: MessageSpec, pad: androidx.compose.ui.unit.Dp = CarSpace.margin) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = CarSpace.gap * 2),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.widthIn(max = CarSpace.focusWidth)) { MessageContent(msg) }
        }
    }
}

/** §4.6 icon 128 · title ≤ 2 lines · value 1 line · ≤ 2 buttons, centred. */
@Composable
fun MessageTemplate(tag: String, top: TopBarSpec?, banner: BannerSpec?, msg: MessageSpec, toast: ToastSlot? = null) {
    Frame(tag, top, banner, null, toast, onePane = true) {
        CenteredMessage(msg, kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current))
    }
}

/** §4.7 driving / idling: one screen, no actions. */
@Composable
fun LockTemplate() = LockOverlay()

@Suppress("unused") private val fieldUnused: Actions? = null
