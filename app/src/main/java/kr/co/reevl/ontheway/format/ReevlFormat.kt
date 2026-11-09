package kr.co.reevl.ontheway.format

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

/**
 * Reevl web number/area/date wording, ported 1:1 from the web source so the car says exactly what reevl.co.kr says.
 * Expected values come from running the real TS (tools/gen_format_golden.mjs → test resources/golden/reevl_format.json,
 * checked by ReevlFormatGoldenTest). Source lines are at reevl `feat/contest` 2026-10-26.
 *
 * JS semantics kept on purpose: `Math.round` = round half up (toward +∞), `toFixed` = round half away from zero on the
 * exact binary value, `toLocaleString()` = grouping commas, at most 3 fraction digits.
 */
object ReevlFormat {
    /** lib/rt.ts:1216 `PY = 3.3058` (1평 = 3.3058㎡) */
    const val PY = 3.3058

    /** lib/rt.ts:1238-1255 `AREA_RATIO` / `areaRatio(kind)` — 전용률(공급면적 추정 계수). Default = apt. */
    private val AREA_RATIO = mapOf(
        "apt" to 0.77, "presale" to 0.77, "villa" to 0.70, "villa_rent" to 0.70, "officetel" to 0.50, "officetel_rent" to 0.50,
        "detached" to 1.0, "detached_rent" to 1.0, "commercial" to 1.0, "land" to 1.0, "auction" to 1.0, "subscription" to 1.0, "rental" to 1.0,
    )

    fun areaRatio(kind: String? = null): Double = AREA_RATIO[kind ?: "apt"] ?: 1.0

    // ── JS primitives ──

    /** JS `Math.round` (ties toward +∞). */
    fun jsRound(x: Double): Double = floor(x + 0.5)

    private fun finite(x: Double?): Boolean = x != null && x.isFinite()

    /** JS `Number.prototype.toLocaleString()` for ko-KR/en-US: grouping commas, ≤ 3 fraction digits, half away from zero. */
    fun locale(x: Double): String {
        val bd = BigDecimal(x).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        val neg = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = plain.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        val s = if (frac.isEmpty()) grouped else "$grouped.$frac"
        return if (neg && s != "0") "-$s" else s
    }

    /** JS `x.toFixed(d)` — half away from zero on the exact double value. */
    fun toFixed(x: Double, d: Int): String = BigDecimal(x).setScale(d, RoundingMode.HALF_UP).toPlainString()

    /** JS `String(number)` for the values we print (integers without `.0`, otherwise shortest repr). */
    fun jsNumber(x: Double): String =
        if (x == floor(x) && kotlin.math.abs(x) < 1e15) x.toLong().toString() else x.toString().removeSuffix(".0")

    // ── lib/rt.ts:1289-1301 `fmt` ──

    /** `fmt.won` — "10억 3,300만" / "10억" / "3,300만". */
    fun won(m: Double?): String {
        if (!finite(m)) return "—"
        val e = floor(m!! / 10000)
        val man = jsRound(m % 10000)
        return if (e > 0) (if (man > 0) "${jsNumber(e)}억 ${locale(man)}만" else "${jsNumber(e)}억") else "${locale(man)}만"
    }

    /** `fmt.wonShort` — 상세·차트 계열 표기, the single source for detail prices ("23억 6,500만"). */
    fun wonShort(m: Double?): String {
        if (!finite(m)) return "—"
        val e = floor(m!! / 10000)
        val r = jsRound(m % 10000)
        return if (e > 0) "${jsNumber(e)}억${if (r > 0) " ${locale(r)}만" else ""}" else "${locale(r)}만"
    }

    /** `fmt.eok` — 308000만 → "30.8억" (feeds/short). */
    fun eok(m: Double?): String {
        if (!finite(m)) return "—"
        if (m!! < 10000) return "${locale(jsRound(m))}만"
        val v = m / 10000
        return "${if (v == floor(v)) jsNumber(v) else toFixed(v, 1)}억"
    }

    /** `fmt.num` — "1,148". */
    fun num(x: Double?): String = if (!finite(x)) "—" else locale(x!!)

    /** `fmt.pct` — fraction → "+8.1%". */
    fun pct(x: Double?, d: Int = 1): String = if (!finite(x)) "—" else "${if (x!! >= 0) "+" else ""}${toFixed(x * 100, d)}%"

    // ── lib/rt.ts:1257-1270 area ──

    /** `areaFmt(m2, unit, kind)` — "84㎡" / "33평" (평 = 공급면적 추정). */
    fun areaFmt(m2: Double?, unit: AreaUnit = AreaUnit.M2, kind: String? = null): String = when {
        m2 == null -> "—"
        unit == AreaUnit.PY -> "${jsNumber(jsRound(m2 / areaRatio(kind) / PY))}평"
        else -> "${jsNumber(floor(m2))}㎡"
    }

    /** `areaFmtBoth` — "33평(전용 84㎡)" / "전용 84㎡". Use where the value is shown big (평 is an estimate). */
    fun areaFmtBoth(m2: Double?, unit: AreaUnit = AreaUnit.M2, kind: String? = null, label: String = "전용"): String = when {
        m2 == null -> "—"
        unit == AreaUnit.PY -> "${jsNumber(jsRound(m2 / areaRatio(kind) / PY))}평($label ${jsNumber(floor(m2))}㎡)"
        else -> "$label ${jsNumber(floor(m2))}㎡"
    }

    // ── lib/rt.ts:3082, 3092 ──

    /** `stName` — adds '역' unless present. */
    fun stName(n: String?): String { val s = n.orEmpty(); return if (s.endsWith("역")) s else s + "역" }

    /** `distTxt` — 436 → "436m", 1234 → "1.2km". */
    fun distTxt(m: Double): String = if (m >= 1000) "${toFixed(m / 1000, 1)}km" else "${jsNumber(jsRound(m))}m"

    // ── lib/rt.ts:3151-3152 avatar colour ──

    /** `avatarColor(seed)` — `h = (h*31 + charCode) >>> 0`, PAL[h % 9]. Returns `#rrggbb` (lower case, as the web). */
    fun avatarColor(seed: String): String {
        var h = 0L
        for (c in seed) h = (h * 31 + c.code) and 0xFFFFFFFFL
        return AVATAR_PAL[(h % AVATAR_PAL.size).toInt()]
    }

    /** lib/rt.ts:3151 `const PAL` (same list as ReevlWeb.avatarPalette, kept as strings for the web's `#rrggbb`). */
    val AVATAR_PAL = listOf("#3182f6", "#f5a623", "#7b61ff", "#f04452", "#00c2b3", "#ff7a45", "#4c6ef5", "#e64980", "#12b886")

    // ── components/detail/KeyStats.tsx:33-34 (after A7 fix round 1) ──

    /** `approveYM` — "2017-02-24" → "2017.02"; no match → first 7 chars; empty → "". */
    fun approveYM(v: String?): String {
        if (v.isNullOrEmpty()) return ""
        val m = Regex("^(\\d{4})[-.]?(\\d{2})").find(v) ?: return v.take(7)
        return "${m.groupValues[1]}.${m.groupValues[2]}"
    }

    /** `yearsSince` — current year − first 4 digits (parseInt semantics: leading digits), null if not a number. */
    fun yearsSince(d: String?, currentYear: Int): Int? {
        if (d.isNullOrEmpty()) return null
        val digits = d.take(4).trimStart().let { s -> Regex("^[+-]?\\d+").find(s)?.value } ?: return null
        return currentYear - digits.toInt()
    }

    // ── components/detail/TradeHighlights.tsx ──

    /** `mdD` (:16) — "2026-07-08" → "26.07.08". */
    fun mdD(d: String): String = d.take(10).drop(2).replace("-", ".")

    /** Recent-trade row date (:164 `t[0].slice(5).replace('-', '.')`) — "2026-09-28" → "09.28". */
    fun recentRowDate(d: String): String = d.drop(5).replaceFirst("-", ".")

    /** Change vs the previous same-area trade (:178-180): "▲6,500만" / "▼4,000만" / "보합". */
    fun deltaText(delta: Double): String = when {
        delta == 0.0 -> "보합"
        else -> "${if (delta > 0) "▲" else "▼"}${wonShort(kotlin.math.abs(delta))}"
    }
}

/** Web `areaUnit` ('m2' | 'py'). The web default is 'm2' (components/common/settings.tsx:34). */
enum class AreaUnit { M2, PY }
