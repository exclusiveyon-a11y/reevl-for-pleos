package kr.co.reevl.ontheway.ui.state

import kr.co.reevl.ontheway.format.ReevlFormat

/**
 * The numbers line of every complex row (screen-ux-research §2 R3/R4, P0-5): always `거리 · 세대 · 가격` in that order,
 * short units (`120m` · `1,148세대` · `최근 23.6억`). A missing value drops only its own piece. Pure — unit-tested.
 */
object RowLines {
    const val RECENT = "최근"

    /** 만원 → `23.6억` / `8,800만` (list form; the detail pane keeps `23억 6,500만`). */
    fun shortPrice(manwon: Long): String = ReevlFormat.eok(manwon.toDouble())

    /**
     * [lead] = the distance piece (`120m`) or, for a plan stop, the station piece (`서대문역 361m`).
     * [priceLabel] null = value only (AI 검색: the price IS the trade that met the condition).
     */
    fun line(lead: String?, households: Int?, priceManwon: Long?, priceLabel: String? = RECENT, tail: String? = null): String? =
        listOfNotNull(
            lead?.takeIf { it.isNotBlank() },
            households?.takeIf { it > 0 }?.let { "${ReevlFormat.num(it.toDouble())}세대" },
            priceManwon?.let { p -> listOfNotNull(priceLabel, shortPrice(p)).joinToString(" ") },
            tail,
        ).joinToString(" · ").ifEmpty { null }

    fun place(p: PlaceRowUi, tail: String? = null): String? =
        line(p.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }, p.households, p.trade?.price, p.priceLabel, tail)

    /** AI 검색 row: the price shown is the trade that met the price condition (P0-1), without the word `최근`. */
    fun searchRow(item: kr.co.reevl.ontheway.data.SearchItem, side: kr.co.reevl.ontheway.nearby.NearbyEngine.Side, inPlan: Boolean) = PlaceRowUi(
        key = item.cid, name = item.name, cid = item.cid, brand = item.brand, distanceM = item.distanceM,
        trade = item.evidenceTrade ?: item.latestTrade, side = side, inPlan = inPlan, households = item.households,
        priceLabel = if (item.evidenceTrade != null) null else RECENT,
    )

    /** Plan stop row: station piece (`서대문역 361m`, from [kr.co.reevl.ontheway.format.ComplexFacts.summaryLine]) · 세대 · 최근가. */
    fun stop(c: kr.co.reevl.ontheway.data.CarComplex?, fallback: String?): String? {
        if (c == null) return fallback
        val station = c.nearestStation?.let { st -> listOfNotNull(ReevlFormat.stName(st.name), st.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }).joinToString(" ") }
        // UX writing 2026-11-11: one short meta line (car-ds P-4). No station → the facts alone; the address (often the name again)
        // only when there is nothing else to say.
        return line(station, c.households, c.recentTrades?.firstOrNull()?.price) ?: fallback
    }
}
