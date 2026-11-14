package kr.co.reevl.ontheway.ui.ds.token

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** true when the app window is the landscape (2:0) one — lists may split into list + preview. */
val LocalWideWindow = staticCompositionLocalOf { true }

/** Pane width class: type roles step up when the pane (not the window) is ≥ 1100 dp. */
fun isWidePane(width: Dp): Boolean = width >= CarSpace.widePane

/** Provides the type roles for a pane of [width]. */
@Composable
fun ProvidePane(width: Dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTypeV2 provides if (isWidePane(width)) CarTypeV2.Wide else CarTypeV2.Compact, content = content)
}

/** Measures the available width and provides the matching type roles. */
@Composable
fun MeasuredPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) { ProvidePane(maxWidth, content) }
}

object Ds {
    val roles @Composable get() = kr.co.reevl.ontheway.ui.ds.token.LocalRoles.current
    val type @Composable get() = LocalTypeV2.current
}

/**
 * car-ds-v2 §15 window classes, by the app window's width (height is 1210 in every Pleos window):
 * - [Wide]    ≥ 1200 — 2:0 landscape (1680) and the wide side of Connect-W 2:1. Left menu 400 + pane.
 * - [Compact] 760 ‥ 1199 — the Connect-S 1:1 window (840) next to the navigation. Menu folds behind ☰.
 * - [Narrow]  < 760 — the narrow side of Connect-W 1:2 (width not published; tested 560–720 with `am task resize`).
 *   Same structure as Compact, with 24 dp margins, the AI 검색 button as an icon, one compare column and stacked actions.
 */
enum class WindowClass { Narrow, Compact, Wide }

fun windowClassOf(width: Dp): WindowClass = when {
    width >= CarSpace.wideWindow -> WindowClass.Wide
    width >= CarSpace.narrowWindow -> WindowClass.Compact
    else -> WindowClass.Narrow
}

/** Provided by the activity next to [LocalWideWindow] (which stays = class Wide). */
val LocalWindowClass = staticCompositionLocalOf { WindowClass.Wide }

/** Side margin of a pane: 32 (§3.2), 24 in the narrow window (Android UX-2 floor "24 dp from screen edges"). */
fun marginFor(c: WindowClass): Dp = if (c == WindowClass.Narrow) CarSpace.marginNarrow else CarSpace.margin

/**
 * The folded menu (Compact/Narrow, ☰ → MENU) shows at most [MENU_ROWS_MAX] rows (screen-ux-research R3: ≤ 6 visible).
 * 설정 is the least used destination, so it leaves the list first and becomes the header's ⋯ action (built-in apps keep
 * settings behind ⋯). Returns (rows, header action). The wide left menu keeps every item (1110 dp of height = 11 rows).
 */
const val MENU_ROWS_MAX = 6

fun <T> foldMenu(items: List<T>, key: (T) -> String, cls: WindowClass): Pair<List<T>, T?> {
    if (cls == WindowClass.Wide || items.size <= MENU_ROWS_MAX) return items to null
    val settings = items.firstOrNull { key(it) == "settings" }
    val rest = items.filter { key(it) != "settings" }
    return rest.take(MENU_ROWS_MAX) to settings
}
