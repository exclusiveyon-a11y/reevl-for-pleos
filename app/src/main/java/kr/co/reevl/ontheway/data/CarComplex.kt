package kr.co.reevl.ontheway.data

import java.io.File
import kotlinx.serialization.Serializable

// ── `GET /api/car/complex/{cid}` (task A7, reevl frontend/lib/carComplex.ts — contract as of fix round 1,
//    commit d334181f55: + completionText · completionYearSource · buildAgeYears · builders[] · brand.names[]) ──
// Facts only — no prediction. Every missing value is null on the wire (never "" or 0), so every field here is
// nullable and the screen leaves the line out instead of saying "0세대".

@Serializable
data class CarAvatar(
    /** `logo` = draw [CarBrand.logoUrl] (fall back to the initial circle if it fails), `initial` = initial circle. */
    val kind: String = "initial",
    /** First letter of the complex name (web AptAvatar: `(row.name || '아').trim()[0]`). */
    val initials: String = "아",
    /** `#rrggbb` — letter colour; background = this colour at [bgAlpha] (web Avatar: color-mix 14%). */
    val color: String = "#3182f6",
    val bgAlpha: Float = 0.14f,
    /** Letter size = diameter × this (web Avatar: `fontSize: size * 0.42`), weight 800. */
    val fontScale: Float = 0.42f,
)

@Serializable
data class CarBrand(
    val name: String? = null,
    /** Web KeyStats `brandList` names (JV: several, same order as [logoUrls]); label `브랜드 N` when > 1. */
    val names: List<String> = emptyList(),
    /** Absolute URL on the Reevl app origin (`/brands/<브랜드>.png`). No Referer/cookie/token needed (A7 curl proof). */
    val logoUrl: String? = null,
    /** JV complexes have several (web rotates them every 6 s). */
    val logoUrls: List<String> = emptyList(),
    val avatar: CarAvatar = CarAvatar(),
)

@Serializable
data class CarStation(
    /** Already ends in '역' (web `stName`). */
    val name: String,
    val lines: List<String> = emptyList(),
    val distanceM: Int? = null,
    val walkMin: Int? = null,
)

@Serializable
data class CarJeonseRatio(val value: Double, val unit: String = "%", val asOf: String? = null, val jeonseCount: Int? = null)

@Serializable
data class CarTrade(
    /** `YYYY-MM-DD` contract date. */
    val date: String,
    /** ㎡, 전용면적 as reported. */
    val areaM2: Double,
    /** 평 (supply-area estimate, web formula) — the car formats from [areaM2] itself with the ported web function. */
    val pyeong: Int? = null,
    /** 만원 */
    val price: Long,
    val priceUnit: String = "만원",
    val priceText: String? = null,
    val floor: Int? = null,
    val kind: String = "매매",
)

@Serializable
data class CarSource(val item: String, val source: String)

@Serializable
data class CarComplex(
    val cid: String,
    val name: String,
    val address: String? = null,
    val addressApprox: Boolean = false,
    val sido: String? = null,
    val gu: String? = null,
    val dong: String? = null,
    val brand: CarBrand = CarBrand(),
    /** Builders joined with ` · ` (web KeyStats displayBuilders). */
    val builder: String? = null,
    /** Web '시공사 N' list (displayBuilders) — label `시공사 N` when > 1. */
    val builders: List<String> = emptyList(),
    val households: Int? = null,
    /** Approval year — only when the approval date is known (fix round 1: no longer derived from build_age). */
    val completionYear: Int? = null,
    /** `approve` when [completionYear] comes from the approval date, else null. */
    val completionYearSource: String? = null,
    /** Web 'N년차' (KST year − approval year, else build_age; null when 0). */
    val buildAgeYears: Int? = null,
    /** The web 준공 cell verbatim (KeyStats `yeon`): `2017.02 · 9년차` or `7년차`. Render as is. */
    val completionText: String? = null,
    /** `YYYY-MM-DD` 사용승인일 when known. */
    val approveDate: String? = null,
    val dongCount: Int? = null,
    val nearestStation: CarStation? = null,
    val jeonseRatio: CarJeonseRatio? = null,
    /** `[]` = no sale on record · `null` = trades exist but the server could not load them (do NOT say "없음"). */
    val recentTrades: List<CarTrade>? = null,
    /** `YYYY-MM-DD` — when the trade data was collected (status.json). */
    val tradeAsOf: String? = null,
    val dataUpdatedAt: String? = null,
    val sources: List<CarSource> = emptyList(),
    val notice: String = "참고용 통계 · 실거래 신고 기준",
)

/** Complex summaries by cid. Memory first, disk behind it. Cleared with the plan cache (logout, 401, new link). */
interface ComplexCache {
    fun get(cid: String): Stamped<CarComplex>?
    fun put(cid: String, value: CarComplex, at: Long)
    fun remove(cid: String)
    fun clear()
}

class MemoryComplexCache : ComplexCache {
    private val byCid = mutableMapOf<String, Stamped<CarComplex>>()
    @Synchronized override fun get(cid: String) = byCid[cid]
    @Synchronized override fun put(cid: String, value: CarComplex, at: Long) { byCid[cid] = Stamped(at, value) }
    @Synchronized override fun remove(cid: String) { byCid.remove(cid) }
    @Synchronized override fun clear() = byCid.clear()
}

class DiskComplexCache(private val dir: File) : ComplexCache {
    private val mem = MemoryComplexCache()
    private val ser = Stamped.serializer(CarComplex.serializer())

    @Synchronized override fun get(cid: String): Stamped<CarComplex>? = mem.get(cid) ?: file(cid).takeIf { it.exists() }?.let { f ->
        runCatching { Api.json.decodeFromString(ser, f.readText()) }.getOrElse { f.delete(); null }
    }?.also { mem.put(cid, it.value, it.syncedAt) }

    @Synchronized override fun put(cid: String, value: CarComplex, at: Long) {
        mem.put(cid, value, at)
        dir.mkdirs()
        val f = file(cid)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(Api.json.encodeToString(ser, Stamped(at, value)))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    @Synchronized override fun remove(cid: String) { mem.remove(cid); file(cid).delete() }
    @Synchronized override fun clear() { mem.clear(); dir.listFiles()?.forEach { it.delete() } }

    /** cid contains `|` and Hangul — hash it into a safe file name. */
    private fun file(cid: String) = File(dir, "complex_" + Integer.toHexString(cid.hashCode()) + "_" + cid.length + ".json")
}
