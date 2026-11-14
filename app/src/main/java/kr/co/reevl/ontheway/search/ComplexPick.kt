package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.data.FavItem
import kr.co.reevl.ontheway.data.LatestTrade
import kr.co.reevl.ontheway.data.NearbyComplex
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.SearchCheck
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side
import kr.co.reevl.ontheway.plan.PlanLogic

/** Where COMPLEX_DETAIL was opened from (back goes there; the subtitle names it). */
enum class PickSource(val label: String) { SEARCH("AI 검색"), NEARBY("주변"), SAVED("저장"), PLAN("오늘 방문"), COMPARE("비교"), AREA("동네"), RECENT("최근 본") }

/**
 * One complex on COMPLEX_DETAIL, whatever list it came from (AI 검색 result, NEARBY row, 저장한 집). Pure.
 * [name] null = a saved cid that Reevl's complex data no longer has (`정보 없음`): it can only be removed.
 */
data class ComplexPick(
    val cid: String,
    val name: String?,
    val address: String? = null,
    val addressApprox: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val distanceM: Int? = null,
    val brand: CarBrand? = null,
    val latestTrade: LatestTrade? = null,
    val side: Side = Side.UNKNOWN,
    val checks: List<SearchCheck> = emptyList(),
    val evidenceTrade: LatestTrade? = null,
    val savedAt: Long? = null,
    val source: PickSource,
    /** Set when the complex is a stop of the open plan (actions 안내 · 완료). */
    val stopId: String? = null,
) {
    val known: Boolean get() = name != null
    val displayName: String get() = name ?: NO_INFO

    /**
     * The PlanStop the existing guide/facts code works with. Coordinates come from the server's own data (complex
     * coords, same source as a cid stop), so they count as verified; no coordinates → not guidable (CoordsGate).
     */
    fun stop(): PlanStop = PlanStop(id = "pick:$cid", position = 0, kind = "apartment_complex", cid = cid, name = displayName,
        address = address, lat = lat, lng = lng, locationVerified = lat != null && lng != null, source = "reevl_${source.name.lowercase()}",
        visitStatus = PlanLogic.PLANNED)

    companion object {
        const val NO_INFO = "정보 없음"

        fun fromSearch(r: AiSearch.Row) = with(r.item) {
            ComplexPick(cid, name, address, addressApprox, lat, lng, distanceM, brand, latestTrade, r.side, checks, evidenceTrade, source = PickSource.SEARCH)
        }

        fun fromNearby(c: NearbyComplex, side: Side) =
            ComplexPick(c.cid, c.name, c.address, c.addressApprox, c.lat, c.lng, c.distanceM, c.brand, c.latestTrade, side, source = PickSource.NEARBY)

        fun fromStop(s: PlanStop, brand: CarBrand?) = ComplexPick(s.cid ?: "stop:${s.id}", s.name, s.address, false, s.lat, s.lng, null, brand,
            source = PickSource.PLAN, stopId = s.id)

        /** 최근 본 단지 (phone + car, search/Recent.kt). */
        fun fromRecent(r: kr.co.reevl.ontheway.data.RecentItem) = ComplexPick(r.cid, r.name, r.address, r.addressApprox, r.lat, r.lng, null, r.brand,
            r.latestTrade, source = PickSource.RECENT)

        fun fromFav(f: FavItem) = ComplexPick(f.cid, f.name, f.address, f.addressApprox, f.lat, f.lng, null, f.brand, f.latestTrade,
            savedAt = f.savedAt, source = PickSource.SAVED)
    }
}
