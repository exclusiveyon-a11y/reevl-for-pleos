package kr.co.reevl.ontheway.data

import kotlinx.serialization.Serializable

// `GET /api/car/area?lat=&lng=&heading=` (reevl feat/contest-area, lib/carArea.ts) — 지나가는 동네.
// Every number is REEVL's own (리블 매매지수 from 실거래, complex rows); no AI forecast, no news body. Spec:
// docs/design/area-context-car-spec.md.

/** Period returns as fractions (0.032 = +3.2 %); null = no index for that period. */
@Serializable
data class AreaRet(
    @kotlinx.serialization.SerialName("1m") val m1: Double? = null,
    @kotlinx.serialization.SerialName("3m") val m3: Double? = null,
    @kotlinx.serialization.SerialName("1y") val y1: Double? = null,
)

@Serializable
data class AreaInfo(
    val sido: String,
    val gu: String,
    val dong: String,
    val key: String,
    /** `dong` = the 동's own index; `gu` = the 동 has no index of its own, numbers are the 구's (say so on screen). */
    val level: String = "dong",
    val basis: String = "nearest_complex",
    val nearestM: Int = 0,
    val ret: AreaRet = AreaRet(),
    val guRet: AreaRet = AreaRet(),
    val complexes: Int? = null,
    val households: Int? = null,
    val trades12: Int? = null,
    val stations: List<String> = emptyList(),
    val schools: List<String> = emptyList(),
)

@Serializable
data class AreaNeighbor(
    val key: String,
    val dong: String,
    val gu: String,
    val level: String = "dong",
    val ret: AreaRet = AreaRet(),
    /** Metres east / north of the car (centre of the 동's complexes within 2 km). */
    val eastM: Int = 0,
    val northM: Int = 0,
    val distanceM: Int = 0,
    /** Bearing relative to the heading the car sent (−180..180, right +); null without a heading. */
    val relDeg: Int? = null,
    val complexes: Int = 0,
    val current: Boolean = false,
)

@Serializable data class AreaInsight(val kind: String, val text: String)

/** Headline only: title · 언론사 · date. [scope] = complex (기사 about a complex here) / gu / sido; [about] = that name. */
@Serializable data class AreaNews(val title: String, val source: String = "", val date: String = "", val scope: String = "sido", val about: String = "")

@Serializable
data class AreaNotable(
    val cid: String,
    val name: String,
    val households: Int? = null,
    val ret1y: Double? = null,
    val distanceM: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** `new_high` (오늘 새 최고가) or `top_1y` (1년 등락 상위). */
    val reason: String = "top_1y",
)

@Serializable data class AreaAsOf(val index: String? = null, val news: String? = null, val notable: String? = null)

@Serializable
data class AreaResponse(
    val area: AreaInfo? = null,
    val neighbors: List<AreaNeighbor> = emptyList(),
    val insights: List<AreaInsight> = emptyList(),
    val notable: List<AreaNotable> = emptyList(),
    val news: List<AreaNews> = emptyList(),
    /** Voice answer (≤ 120 chars) — Gleo reads this while driving. */
    val summary: String = "",
    val asOf: AreaAsOf = AreaAsOf(),
    val source: String? = null,
    val notice: String? = null,
)
