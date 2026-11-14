package kr.co.reevl.ontheway.ui.state

import kr.co.reevl.ontheway.HandoffPhase
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.navi.RouteSummary
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.search.ComplexPick

// Screen state + events (car-ds-v2). AppViewModel builds these from its observable fields; a screen maps them onto a
// template and calls the events. No driving flag here: while driving the whole app shows the lock screen.

/** One line at the top of a screen. Offline = cached data with its time; Error = nothing fresh and nothing cached. */
enum class NoticeKind { Offline, Error, Info }
data class Notice(val kind: NoticeKind, val text: String, val retry: Boolean = false)

/** Short-lived confirmation at the bottom (`저장했어요 [되돌리기]`). */
data class ToastUi(val id: Long, val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** One complex as a list row (NEARBY, AI 검색 results, 저장). */
data class PlaceRowUi(
    val key: String,
    val name: String,
    val cid: String?,
    val brand: CarBrand?,
    val distanceM: Int?,
    val trade: LatestTrade?,
    val side: Side = Side.UNKNOWN,
    /** `계획` (already in the open plan). */
    val inPlan: Boolean = false,
    val savedAt: Long? = null,
    val households: Int? = null,
    /** Word before the price: `최근` (newest trade); null for AI 검색 rows whose price is the condition-meeting trade. */
    val priceLabel: String? = RowLines.RECENT,
    /** Last piece of the numbers line (최근 본: `휴대폰에서 · 어제`). */
    val tail: String? = null,
)

/** The complex pane (DETAIL, and the wide window's preview). */
data class PaneUi(
    val pick: ComplexPick,
    val complex: Synced<CarComplex>?,
    val complexLoading: Boolean,
    val complexError: Boolean,
    val canGuide: Boolean,
    val guiding: Boolean,
    /** 추가 (complex not yet in the plan) or 완료 (a planned stop of the open plan). */
    val secondary: PaneSecondary,
    val secondaryBusy: Boolean,
    val saved: Boolean?,
    val saveBusy: Boolean,
    /** Search only: how the server read the conditions (`30평대 공급면적 기준`) — small, in the source line. */
    val basis: String? = null,
    /** 내 메모 written on the phone for this complex (read-only in the car). */
    val memo: kr.co.reevl.ontheway.data.Memo? = null,
)

enum class PaneSecondary { Add, AddDisabled, Done, None }

data class PaneEvents(val onGuide: () -> Unit, val onSecondary: () -> Unit, val onToggleSave: () -> Unit)

data class HomeUi(
    /** `오른쪽 2 · 왼쪽 3` / `12곳` / `위치 확인 중` */
    val nearbyValue: String,
    /** `3곳 중 1곳` / `계획 없음` */
    val planValue: String,
    val planEnabled: Boolean,
    /** `12곳` / `0곳` */
    val savedValue: String,
    val next: NextStopUi?,
    val asOf: String?,
    val notice: Notice?,
    /** v2.1 home: the AI field's text and today's first plan as rows. */
    val query: String = "",
    val planTitle: String? = null,
    val stops: List<StopRowUi> = emptyList(),
    /** 동네 (cached answer only): `신흥동 · 1년 +26.4%`; null = nothing cached → the row is hidden. */
    val areaValue: String? = null,
    /** §15.7 wide HOME: up to 3 lines of the cached 동네 answer (insights · facts); empty when nothing is cached. */
    val areaLines: List<String> = emptyList(),
    /** §15.7: the 3 nearest complexes already in memory (last nearby answer, cell-cached) — HOME never asks for them. */
    val nearbyTop: List<PlaceRowUi> = emptyList(),
    /** driving-context-spec §3: `방금 지나온 곳` (S4a only, ≤ 3 rows); title gets the 동 when it changed (`서계동 · 방금 지나온 곳`). */
    val passedTitle: String? = null,
    val passed: List<PassedRowUi> = emptyList(),
    /** Voice first: one-tap requests — the phone's saved conditions first, then what the car knows now (search/Recent.chips). */
    val suggest: List<String> = emptyList(),
    val chips: List<kr.co.reevl.ontheway.search.Recent.Chip> = emptyList(),
    /** 최근 본 단지 (phone + car), ≤ 3 rows; [recentMore] = more behind `더보기`. */
    val recent: List<PlaceRowUi> = emptyList(),
    val recentMore: Boolean = false,
    /** `동선 지도` row under the plan: `직선 8.3km · 5곳` (+ `더 짧은 순서가 있어요`); null = fewer than 2 located stops. */
    val route: String? = null,
    /** The same plan's pins for the wide HOME side map (empty = no map). */
    val routePins: List<RoutePinUi> = emptyList(),
)

/** One "방금 지나온 곳" row: name / `2분 전 · 1,148세대 · 최근 23.6억` + side chip. */
data class PassedRowUi(val cid: String, val name: String, val brand: CarBrand?, val side: Side, val secondary: String?)

data class NextStopUi(val stop: PlanStop, val brand: CarBrand?, val secondary: String?, val canGuide: Boolean)

data class HomeEvents(val onQuery: (String) -> Unit, val onSubmit: (String) -> Unit, val onNearby: () -> Unit, val onSearch: () -> Unit, val onPlan: () -> Unit, val onSaved: () -> Unit,
                      val onGuideNext: (PlanStop) -> Unit, val onOpenNext: (PlanStop) -> Unit, val onMenu: () -> Unit, val onRetry: () -> Unit,
                      val onArea: () -> Unit = {}, val onDoneStop: (PlanStop) -> Unit = {}, val onPassed: (String) -> Unit = {},
                      val onOpenNearby: (String) -> Unit = {}, val onVoice: (() -> Unit)? = null, val onSuggest: (String) -> Unit = {},
                      /** `휴대폰으로 찾기` (secondary — voice stays first): the phone handoff QR. */
                      val onPhone: () -> Unit = {},
                      val onRecent: (String) -> Unit = {}, val onRecentMore: () -> Unit = {}, val onScreener: (String) -> Unit = {},
                      /** `동선 지도` → 오늘 방문 with the route map. */
                      val onRoute: () -> Unit = {})

/** A search condition chip; [warn] = could not be checked → tap opens the confirm question. */
data class CondUi(val key: String, val label: String, val warn: Boolean)

data class AiSearchUi(
    val query: String,
    val loading: Boolean,
    /** No search yet → suggestion rows. */
    val idle: Boolean,
    val suggestions: List<String>,
    val chips: List<CondUi>,
    /** `1km 안 60곳 중 4곳` */
    val summary: String?,
    val rows: List<PlaceRowUi>,
    /** Server or car-side checks of the selected row (`8억 이하`). */
    val checksByKey: Map<String, List<String>>,
    val empty: Boolean,
    val canDropCondition: Boolean,
    val selectedKey: String?,
    val preview: PaneUi?,
    val asOf: String?,
    val notice: Notice?,
    /** One sentence about how the list was made (`방향을 몰라 좌우 구분 없이 보여요`), null when nothing needs saying. */
    val note: String? = null,
    /** Why the list is empty (AiSearch.empty) — decides the message and its one action. */
    val emptyWhy: kr.co.reevl.ontheway.search.AiSearch.Empty? = null,
    /** null = closed; else the voice panel (Gleo phrase, or the live text while the in-app recogniser listens). */
    val voice: VoiceUi? = null,
    /** Idle: the phone's saved conditions (one tap = run) and 최근 본 단지 (≤ 3). */
    val screens: List<kr.co.reevl.ontheway.search.Recent.Chip> = emptyList(),
    val recent: List<PlaceRowUi> = emptyList(),
    val recentMore: Boolean = false,
    /** `동선 지도` row under the plan: `직선 8.3km · 5곳` (+ `더 짧은 순서가 있어요`); null = fewer than 2 located stops. */
    val route: String? = null,
    /** The same plan's pins for the wide HOME side map (empty = no map). */
    val routePins: List<RoutePinUi> = emptyList(),
)

data class VoiceUi(val listening: Boolean, val heard: String, val lines: List<String>)

data class AiSearchEvents(val onQuery: (String) -> Unit, val onRun: (String) -> Unit, val onChip: (CondUi) -> Unit, val onDropLast: () -> Unit,
                          val onSelect: (String) -> Unit, val onOpen: (String) -> Unit, val onBack: () -> Unit, val pane: PaneEvents,
                          val onVoice: (() -> Unit)? = null, val onVoiceClose: () -> Unit = {}, val onWiden: () -> Unit = {},
                          val onPhone: () -> Unit = {},
                          val onRecent: (String) -> Unit = {}, val onRecentMore: () -> Unit = {}, val onScreener: (String) -> Unit = {},
                      /** `동선 지도` → 오늘 방문 with the route map. */
                      val onRoute: () -> Unit = {})

/** 확인 필요 condition: question + options (radio) + 적용/취소. */
data class SearchConfirmUi(val label: String, val options: List<String>, val selected: Int)
data class SearchConfirmEvents(val onPick: (Int) -> Unit, val onApply: () -> Unit, val onCancel: () -> Unit)

data class NearbyUi(
    /** null = heading unknown → no tabs, distance order. */
    val tabs: List<String>?,
    val tab: Int,
    val rows: List<PlaceRowUi>,
    val loading: Boolean,
    val empty: Boolean,
    val noLocation: Boolean,
    /** `진행 방향 기준 · 500m` / `현재 위치 기준 · 500m` */
    val basis: String?,
    val selectedKey: String?,
    val preview: PaneUi?,
    val notice: Notice?,
)

data class NearbyEvents(val onTab: (Int) -> Unit, val onSelect: (String) -> Unit, val onOpen: (String) -> Unit, val onRefresh: () -> Unit,
                        val onBack: () -> Unit, val pane: PaneEvents)

data class DetailUi(val pane: PaneUi?, val notice: Notice?, val tab: String = "summary", val inCompare: Boolean = false)
data class DetailEvents(val onBack: () -> Unit, val pane: PaneEvents, val onTab: (String) -> Unit = {}, val onCompare: () -> Unit = {})

/** 비교: one column per complex (≤ 3), rows = the selected tab's metrics lined up by key. */
data class CompareColUi(val cid: String, val name: String, val brand: CarBrand?, val facts: String?, val canGuide: Boolean,
                        val loading: Boolean, val error: Boolean)
data class CompareCellUi(val value: String?, val sub: String?, val color: String?, val best: Boolean)
data class CompareRowUi(val key: String, val label: String, val cells: List<CompareCellUi>)
data class CompareUi(val cols: List<CompareColUi>, val tabs: List<Pair<String, String>>, val tab: String, val rows: List<CompareRowUi>,
                     val asOf: String?, val canAdd: Boolean)
data class CompareEvents(val onBack: () -> Unit, val onTab: (String) -> Unit, val onRemove: (String) -> Unit, val onAdd: () -> Unit,
                         val onGuide: (String) -> Unit, val onOpen: (String) -> Unit)
data class ComparePickUi(val rows: List<PlaceRowUi>, val picked: Set<String>, val loading: Boolean, val empty: Boolean, val notice: Notice?)
data class ComparePickEvents(val onToggle: (String) -> Unit, val onDone: () -> Unit, val onBack: () -> Unit, val onSearch: () -> Unit)

data class SavedUi(val rows: List<PlaceRowUi>, val more: Boolean, val loading: Boolean, val empty: Boolean, val selectedKey: String?,
                   val preview: PaneUi?, val asOf: String?, val notice: Notice?,
                   /** `saved` | `recent` (최근 본 단지 — phone + car). */
                   val tab: String = "saved", val recentRows: List<PlaceRowUi> = emptyList())
data class SavedEvents(val onSelect: (String) -> Unit, val onOpen: (String) -> Unit, val onRefresh: () -> Unit, val onSearch: () -> Unit,
                       val onBack: () -> Unit, val pane: PaneEvents, val onCompare: () -> Unit = {},
                       val onTab: (String) -> Unit = {}, val onOpenRecent: (String) -> Unit = {})

data class StopRowUi(val stop: PlanStop, val brand: CarBrand?, val secondary: String?, val done: Boolean, val next: Boolean, val skipped: Boolean,
                     val canGuide: Boolean, val guiding: Boolean,
                     /** S5: the car is at this stop (navigation arrival or 150 m · 30 s) → `도착` + `[완료]`. */
                     val arrived: Boolean = false)

data class VisitPlanUi(val rows: List<StopRowUi>, val footer: String?, val loading: Boolean, val selectedKey: String?, val preview: PaneUi?,
                       val notice: Notice?, val missing: Boolean,
                       /** 동선 지도 (web /plan map): null = fewer than 2 stops with coordinates. */
                       val map: PlanMapUi? = null)
data class VisitPlanEvents(val onSelect: (String) -> Unit, val onOpen: (String) -> Unit, val onGuide: (PlanStop) -> Unit, val onAddNearby: () -> Unit,
                           val onRefresh: () -> Unit, val onBack: () -> Unit, val pane: PaneEvents, val onDone: (PlanStop) -> Unit = {},
                           val onOptimize: () -> Unit = {})

/** One stop on the route map ([n] = the list's order number). */
data class RoutePinUi(val n: Int, val name: String, val lat: Double?, val lng: Double?, val next: Boolean, val done: Boolean, val skipped: Boolean)

/**
 * The map block: pins in order, `직선 8.3km · 5곳` ([summary], straight line only — never a drive time), and whether a shorter
 * order exists ([canOptimize]: the 순서 최적화 button + its Gleo phrase).
 */
data class PlanMapUi(val pins: List<RoutePinUi>, val summary: String, val canOptimize: Boolean,
                     /** Glance line: `남은 직선 9.0km · 3곳` and `다음 3번 가상푸른숲1단지` (null when everything is done). */
                     val title: String = summary, val next: String? = null,
                     /** `직선 13.9km` / `남은 직선 9.0km`; [hint] = `정리하면 5.8km` when a shorter order exists. */
                     val km: String = "", val hint: String? = null)

/**
 * 순서 최적화 미리보기 (web PlanView preview): 지금 │ 바꾸면 maps, `직선 10.8km → 8.3km`, the new order, [적용] [취소].
 * [rows] = (new number, name, old number); [note] = what stays fixed + "straight line, not the road".
 */
data class PlanOptimizeUi(val before: List<RoutePinUi>, val after: List<RoutePinUi>, val beforeKm: String, val afterKm: String, val saved: String,
                          val rows: List<Triple<Int, String, Int>>, val note: String, val busy: Boolean, val notice: Notice?)
data class PlanOptimizeEvents(val onApply: () -> Unit, val onCancel: () -> Unit)

data class PlanRowUi(val id: String, val title: String, val secondary: String)
data class PlansUi(val rows: List<PlanRowUi>, val loading: Boolean, val notice: Notice?)
data class PlansEvents(val onOpen: (String) -> Unit, val onBack: () -> Unit, val onRetry: () -> Unit)

data class InsertUi(val name: String, val cid: String?, val brand: CarBrand?, val facts: String?, val options: List<String>, val selected: Int,
                    val busy: Boolean, val notice: Notice?)
data class InsertEvents(val onPick: (Int) -> Unit, val onConfirm: () -> Unit, val onCancel: () -> Unit)

data class HandoffUi(val phase: HandoffPhase?, val title: String, val route: RouteSummary?, val arrived: Boolean = false)
data class HandoffEvents(val onRetry: () -> Unit, val onClose: () -> Unit, val onDone: () -> Unit = {})

/** QR side of LINK: phase + the public URL and match number (never the poll secret). */
data class QrUi(val phase: kr.co.reevl.ontheway.link.QrPhase = kr.co.reevl.ontheway.link.QrPhase.IDLE, val url: String? = null, val match: String? = null) {
    /** 새 QR is offered once the request is gone (expired / could not be made). */
    val renewable: Boolean get() = phase == kr.co.reevl.ontheway.link.QrPhase.EXPIRED || phase == kr.co.reevl.ontheway.link.QrPhase.FAILED
}
data class LinkUi(val code: String, val busy: Boolean, val invalid: Boolean, val notice: Notice?,
                  val mode: kr.co.reevl.ontheway.link.LinkMode = kr.co.reevl.ontheway.link.LinkMode.CODE, val qr: QrUi = QrUi())
data class LinkEvents(val onDigit: (Char) -> Unit, val onDelete: () -> Unit, val onSubmit: () -> Unit,
                      val onUseQr: () -> Unit = {}, val onUseCode: () -> Unit = {}, val onNewQr: () -> Unit = {})

data class SettingsUi(val version: String, val trailOn: Boolean = true)
data class SettingsEvents(val onUnlink: () -> Unit, val onBack: () -> Unit, val onToggleTrail: () -> Unit = {},
                          val onPrivacy: () -> Unit = {}, val onLicenses: () -> Unit = {})
data class UnlinkEvents(val onConfirm: () -> Unit, val onCancel: () -> Unit)

/**
 * 휴대폰으로 넘기기. [done] = something came from the phone (the QR is closed, one line + one action); else the QR with its
 * instruction, [stopped] after 10 minutes (`다시 확인`), [notice] when a check failed (offline …).
 */
data class PhoneUi(val kind: kr.co.reevl.ontheway.link.PhoneHandoff.Kind, val url: String, val stopped: Boolean, val notice: Notice?,
                   val done: PhoneDoneUi?)
/** `휴대폰에서 계획을 만들었어요` · `가상한빛마을` · [안내]. */
data class PhoneDoneUi(val title: String, val value: String?, val action: String)
data class PhoneEvents(val onClose: () -> Unit, val onAction: () -> Unit, val onRetry: () -> Unit)
