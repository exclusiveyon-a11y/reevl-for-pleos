package kr.co.reevl.ontheway.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Sizes for the Pleos Connect-S app window (emulator 2560×1440 @160 dpi → 1 px = 1 dp). car-ui-spec §1.5.
 * Rule of thumb: Reevl web spacing × 2 (Pleos type is ≈ 2× the web scale), clamped to Pleos/Google minimums.
 */
object CarDims {
    /** Landscape (2:0) window ≈ 1706 dp wide, 1:1 window ≈ 850 dp → two columns only in the wide one. */
    val wideMin = 1200.dp

    /** Page Navigation header height (phone/Gleo apps: y 90–189 = 100 dp). */
    val header = 100.dp
    /** App icon in the header (phone app: 62 dp, 19 dp from top). */
    val appIcon = 62.dp

    /** Window side margin (native apps: 32) = Reevl card padding p-4 (16) × 2. */
    val gutter = 32.dp
    /** Reevl p-4 × 2 */
    val cardPad = 32.dp
    /** Reevl gap-3 × 2 = 24 ≥ Google 23 dp between touch targets. */
    val cardGap = 24.dp
    /** Reevl gap-2 × 2 */
    val gapItem = 16.dp
    /** Reevl gap-1.5 × 2 */
    val gapTight = 12.dp
    /** Reevl gap-1 × 2 */
    val gapSnug = 8.dp

    /** Reevl rounded-2xl 16 × 2 = Pleos Large button radius 32. */
    val radiusCard = 32.dp

    /** Pleos Basic button Large: height 100, radius 32. */
    val buttonL = 100.dp
    val radiusL = 32.dp
    /** Pleos Basic button Medium: height 80, radius 24 (≥ Google 76 dp). */
    val buttonM = 80.dp
    val radiusM = 24.dp

    /** List row minimum (native settings rows 100 dp; ours carry a 68 dp avatar + two lines). */
    val rowMin = 120.dp

    /** Reevl AptAvatar 34 (lists) / 40 (complex header) × 2. */
    val avatarList = 68.dp
    val avatarHero = 80.dp
    val avatarStack = 56.dp

    /** Order badge (Reevl TradeHighlights area chip rounded-lg bg-chip, ×2). */
    val orderBadge = 56.dp

    /** Pleos keypad key height / radius / gap. */
    val key = 100.dp
    val keyRadius = 24.dp
    val keyGap = 16.dp
}
