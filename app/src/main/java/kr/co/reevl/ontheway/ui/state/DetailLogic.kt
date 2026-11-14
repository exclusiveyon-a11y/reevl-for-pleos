package kr.co.reevl.ontheway.ui.state

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kr.co.reevl.ontheway.data.CarDetail
import kr.co.reevl.ontheway.data.CarMetric
import kr.co.reevl.ontheway.data.CarTrade
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side

/**
 * DETAIL and COMPARE rules that are not layout (screen-ux-research §3.6 P1/P2; web CompareView `bestSet`). Pure — unit tested.
 */
object DetailLogic {

    /** Side word for the facts line (same words as the side badge). */
    fun sideWord(side: Side): String? = when (side) {
        Side.LEFT -> "왼쪽"; Side.RIGHT -> "오른쪽"; Side.AHEAD -> "앞"; Side.BEHIND -> "지나옴"; Side.UNKNOWN -> null
    }

    /**
     * §3.6 P1 ①: `오른쪽 586m · 1,248세대 · 2019년` — side first, glued to the distance (R4 order: distance · size · year).
     * Year = approval year when known, else the web's `N년차`. Missing parts are left out, never "0".
     */
    fun facts(side: Side, distanceM: Int?, households: Int?, completionYear: Int?, buildAgeYears: Int?): String? {
        val dist = distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }
        val place = listOfNotNull(sideWord(side), dist).joinToString(" ").ifEmpty { null }
        val year = completionYear?.let { "${it}년" } ?: buildAgeYears?.let { "${it}년차" }
        return listOfNotNull(place, households?.let { "${ReevlFormat.num(it.toDouble())}세대" }, year).joinToString(" · ").ifEmpty { null }
    }

    /**
     * §3.6 P1 ②: source + at most ONE basis fragment. Direction sentences are dropped (the side is already in the facts line).
     * `basis` = search reading joined with ` · ` (AiSearch.basisLine).
     */
    /** Shown once, in the complex detail footer (prices, indices, AI 예측 are statistics, not advice). */
    const val DISCLAIMER = "참고용 정보예요 · 투자 자문이 아니에요"

    fun footer(source: String?, basis: String?): String? {
        val frag = basis?.split(" · ")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && !it.contains("방향") }
        return listOfNotNull(source, frag).joinToString(" · ").ifEmpty { null }
    }

    /** §3.6 P2 ③: change vs the previous trade of the same area — none when that trade is more than 365 days older (no ▲▼ over years). */
    fun deltaOf(rows: List<CarTrade>, i: Int): Long? {
        val k = floor(rows[i].areaM2)
        for (j in i + 1 until rows.size) if (floor(rows[j].areaM2) == k) {
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(rows[j].date.take(10)), LocalDate.parse(rows[i].date.take(10))) }.getOrNull()
            return if (days == null || days > 365) null else rows[i].price - rows[j].price
        }
        return null
    }

    /** Trades shown: newest first, at most 4 (Pane rows ≤ 4). */
    fun trades(rows: List<CarTrade>): List<CarTrade> = rows.sortedByDescending { it.date }.take(4)

    data class StatRowUi(val key: String, val label: String, val value: String, val sub: String?, val color: String?, val n: Double? = null)

    /** Rows of one tab: the tab's fixed order, present keys only. */
    fun tabRows(d: CarDetail?, tabKey: String): List<StatRowUi> {
        val tab = d?.tabs?.firstOrNull { it.key == tabKey } ?: return emptyList()
        return tab.keys.mapNotNull { k -> d.metrics[k]?.let { StatRowUi(k, it.label, it.v, it.sub, it.color, it.n) } }
    }

    /** Tabs that have something to show (요약 always first when present). `trades` tab counts as non-empty when trades exist. */
    fun visibleTabs(d: CarDetail?, hasTrades: Boolean): List<Pair<String, String>> =
        d?.tabs.orEmpty().filter { t -> tabRows(d, t.key).isNotEmpty() || (hasTrades && t.key == d?.trades) }.map { it.key to it.title }

    // ── compare ──

    const val COMPARE_MAX = 3

    /**
     * Web CompareView `bestSet`: winners of one metric — only when it has a direction and at least two complexes have a value;
     * ties all win. Text metrics (no number) and neutral ones (no `better`) never win.
     */
    fun best(cells: List<CarMetric?>): Set<Int> {
        val dir = cells.firstNotNullOfOrNull { it?.better } ?: return emptySet()
        val nums = cells.map { it?.n }
        if (nums.count { it != null } < 2) return emptySet()
        val b = (if (dir == "low") nums.filterNotNull().minOrNull() else nums.filterNotNull().maxOrNull()) ?: return emptySet()
        val win = nums.indices.filter { nums[it] == b }.toSet()
        // Car deviation from the web: when every complex with a value ties, nothing is marked (a full row of colour says nothing).
        return if (win.size == nums.count { it != null }) emptySet() else win
    }

    /** Compare rows of one tab across complexes: the union of keys in the server's tab order; label from the first complex that has it. */
    fun compareRows(details: List<CarDetail?>, tabKey: String): List<Pair<String, List<CarMetric?>>> {
        val order = details.firstNotNullOfOrNull { d -> d?.tabs?.firstOrNull { it.key == tabKey }?.keys } ?: return emptyList()
        return order.mapNotNull { k ->
            val cells = details.map { it?.metrics?.get(k) }
            if (cells.all { it == null }) null else k to cells
        }
    }

    /** Tray add: no duplicates, at most [COMPARE_MAX] (the oldest stays — the user removes explicitly). Returns null when full. */
    fun <T> trayAdd(tray: List<T>, item: T, same: (T, T) -> Boolean): List<T>? = when {
        tray.any { same(it, item) } -> tray
        tray.size >= COMPARE_MAX -> null
        else -> tray + item
    }
}
