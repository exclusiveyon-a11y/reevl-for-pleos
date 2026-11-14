package kr.co.reevl.ontheway.ui.ds.token

import androidx.compose.ui.unit.dp
import kr.co.reevl.design.ReevlSpace
import kr.co.reevl.design.car.ReevlCarTheme

/**
 * car-ds-v2 §3.2–3.3 and §5 sizes (dp = px @160 dpi on the Pleos emulator). Sizes the generated Reevl car tokens define
 * ([ReevlCarTheme.Space], touch targets, radius) are read from there; the rest are this app's layout constants.
 */
object CarSpace {
    private val T = ReevlCarTheme.Space
    val margin = T.margin
    val gap = T.gap
    val gapTight = T.gapTight
    val topBar = T.topBar
    val actionBar = T.actionBar
    val actionPad = 24.dp
    val rowSingle = T.rowSingle
    val rowDouble = T.rowDouble
    val rowPadV = 20.dp
    /** Everything pressable: [ReevlCarTheme.touchTarget] 100 (floor [ReevlCarTheme.touchTargetMin] 76). */
    val hit = ReevlCarTheme.touchTarget
    /** The floor for any pressable that is not a full [hit] (76 dp, Google visual principles / AAOS-TARGET-1). */
    val hitMin = ReevlCarTheme.touchTargetMin
    val chipVisual = T.chipVisual
    val statusChip = 48.dp
    val button = ReevlCarTheme.touchTarget
    /** 1 dp hairline (list divider, rail edge). */
    val hairline = 1.dp
    /** Small inner spacing = the web spacing roles 1:1 ([ReevlSpace], generated): text-to-text, icon-to-label, badge padding. */
    val hair = ReevlSpace.hair
    val snug = ReevlSpace.snug
    val tight = ReevlSpace.tight
    val item = ReevlSpace.item
    val card = ReevlSpace.card
    val block = ReevlSpace.block
    val section = ReevlSpace.section
    /**
     * v2.2 REEVL radius, web px × 2 (the car scale of car-ui-spec §1.5, same as AiSurface.kt). globals.css:191
     * `--radius-sm 6 · md 8 · lg 10 · xl 12`. Lists and the page stay square (v2.1 flat rows); controls round.
     */
    /** Primary/secondary button, AI field: `rounded-xl` 12 → 24 (Button.tsx:51 md). */
    val radius = ReevlCarTheme.Radius.xl
    /** Compact button, toggle pill, selected menu row: `rounded-lg` 10 → 20 (Button.tsx:51 sm, Sidebar.tsx:29). */
    val radiusM = ReevlCarTheme.Radius.lg
    /** Badge, order mark, toast, skeleton: `rounded-md` 8 → 16 (Badge.tsx:27). */
    val radiusS = ReevlCarTheme.Radius.md
    /**
     * Selected menu row inset: Sidebar `lg:mx-3` 12 (Sidebar.tsx:30) → 16, not 24: at × 2 the 400 menu leaves no room for
     * `오늘 방문 · 1곳 중 0곳` / `주변 · 오른쪽 1 · 왼쪽 0` (measured on the emulator, §14.2). 8 top/bottom keeps the 100 row.
     */
    val selectInsetH = 16.dp
    val selectInsetV = 8.dp
    /** Tab indicator: TabsBar `h-[1.5px]` → 3 (TabsBar.tsx:96), brand. */
    val tabIndicator = 3.dp
    /** Left menu of the wide window (built-in Settings: x 854–1253 → 400). */
    val rail = 400.dp
    val avatar = 68.dp
    val avatarHeader = 80.dp
    val orderBadge = 56.dp
    val icon = T.icon
    val iconSmall = T.iconSmall
    val tileIcon = 56.dp
    val tileCompactH = 280.dp
    val tileWideH = 320.dp
    val split = 620.dp
    val readWidth = T.readWidth
    val messageIcon = 128.dp
    val lockMark = 96.dp
    val banner = 72.dp
    val tradeLine = 112.dp
    val footer = 56.dp
    val toastH = 88.dp
    val toastMax = 792.dp
    val searchButton = 200.dp

    /** Window ≥ this → wide window (split lists allowed). Between the two Pleos windows 840 and 1680. */
    val wideWindow = T.wideMin
    /** Window < this → Narrow class (Connect-W 1:2 narrow side), §15. Between 640 and the 840 1:1 window. */
    val narrowWindow = 760.dp
    /** Pane side margin in the narrow window (Android UX-2: ≥ 24 dp from the edges). */
    val marginNarrow = 24.dp
    /** Reading width of a centred single-purpose block (QR, keypad, message) — fits 840 − 2 × 32. */
    val focusWidth = 720.dp
    /** HOME in the wide window: the "지금 여기" side column (pane 1280 − this = plan column 720). */
    val homeSide = 560.dp
    /** Pane ≥ this → Wide type roles (one step up). */
    val widePane = 1100.dp
}
