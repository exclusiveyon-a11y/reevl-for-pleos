package kr.co.reevl.ontheway.spike

import kr.co.reevl.ontheway.data.ReevlCoords
import kr.co.reevl.ontheway.navi.Stop

/** The single hard-coded stop the NaviHelper spike routes to. */
object SpikeStops {
    /**
     * cid `11110|경희궁자이(2단지)|홍파동|199`
     * source: reevl `web/data/coords/11110.json` (file mtime 2026-09-06), raw entry `[126.964152, 37.569205]` = [lon, lat].
     */
    const val CID = "11110|경희궁자이(2단지)|홍파동|199"
    private val RAW_LON_LAT = doubleArrayOf(126.964152, 37.569205)

    val FIRST: Stop = ReevlCoords.fromReevlPair(RAW_LON_LAT).let { p ->
        Stop(
            id = CID,
            position = 1,
            kind = "complex",
            name = "경희궁자이(2단지)",
            address = "서울특별시 종로구 홍파동 199",
            lat = p.lat, // 37.569205
            lng = p.lng, // 126.964152
            locationVerified = true,
            visitStatus = "planned",
        )
    }
}
