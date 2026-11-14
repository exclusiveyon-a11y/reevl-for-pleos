package kr.co.reevl.ontheway.area

import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kr.co.reevl.ontheway.data.AreaInfo
import kr.co.reevl.ontheway.data.AreaNeighbor
import kr.co.reevl.ontheway.data.AreaNews
import kr.co.reevl.ontheway.data.AreaNotable
import kr.co.reevl.ontheway.data.AreaRet
import kr.co.reevl.ontheway.format.ReevlFormat

/**
 * 지나가는 동네 — pure rules (JVM-tested, AreaLogicTest). Spec: docs/design/area-context-car-spec.md §3.
 *
 * Colour = REEVL web heatmap exactly (reevl/frontend/lib/rt.ts heatColor: blue ↔ neutral ↔ red, Korean up = red), the same
 * light/dark stops and the same text-colour rule (luminance > 0.62 → ink, else white). Saturation caps per period: 1년 = web
 * FLOW_CAP.dong 0.20 (Treemap.tsx:55, "상위 5~10 % 포화"; 2026-11-09 동 |1년| p90 0.170 · p95 0.221). 1개월·3개월 are not
 * coloured on the web — 0.08 / 0.10 are this app's choice at the same share (동 |1개월| p90 0.067 · p95 0.090, |3개월| p90 0.087
 * · p95 0.116, heat_index.json 2026-11-09); the legend shows each cap.
 */
object AreaLogic {
    enum class Period(val label: String, val cap: Double) { M1("1개월", 0.08), M3("3개월", 0.10), Y1("1년", 0.20) }

    fun retOf(r: AreaRet, p: Period): Double? = when (p) { Period.M1 -> r.m1; Period.M3 -> r.m3; Period.Y1 -> r.y1 }

    /** `+3.2%` · `−1.0%` · `0.0%` (minus sign U+2212 like the web's fmt.pct). */
    fun pct(x: Double?): String {
        if (x == null || !x.isFinite()) return "—"
        val v = (x * 1000).roundToInt() / 10.0
        val sign = if (v > 0) "+" else if (v < 0) "−" else ""
        return sign + String.format(java.util.Locale.US, "%.1f%%", abs(v))
    }

    /** Web heatColor stops (rt.ts:1361–1374). [t] 0..1 (0.5 = 0 %). Returns ARGB background and foreground. */
    data class Heat(val bg: Int, val fg: Int)

    private fun rgb(c: androidx.compose.ui.graphics.Color) = intArrayOf((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    // Heat ends: red = the `--up` chart mark (charts keep up/down), the blue and middle stops are the web heatColor scale (no token).
    private val BLUE_L = intArrayOf(77, 159, 255); private val MID_L = intArrayOf(244, 246, 249); private val RED_L = rgb(kr.co.reevl.design.ReevlPalette.Light.up)
    private val BLUE_D = intArrayOf(61, 126, 255); private val MID_D = intArrayOf(43, 46, 55); private val RED_D = rgb(kr.co.reevl.design.ReevlPalette.Dark.up)
    private val INK = kr.co.reevl.design.ReevlPalette.Light.ink.toArgb()
    private val WHITE = kr.co.reevl.design.ReevlPalette.Light.bg.toArgb()

    fun tOf(v: Double, cap: Double): Double = ((v / cap).coerceIn(-1.0, 1.0) + 1) / 2

    fun heat(t: Double, dark: Boolean): Heat {
        val k = t.coerceIn(0.0, 1.0)
        val (a, b, f) = if (k < 0.5) Triple(if (dark) BLUE_D else BLUE_L, if (dark) MID_D else MID_L, k * 2)
        else Triple(if (dark) MID_D else MID_L, if (dark) RED_D else RED_L, (k - 0.5) * 2)
        val c = IntArray(3) { (a[it] + (b[it] - a[it]) * f).roundToInt() }
        val lum = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
        return Heat((0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2], if (lum > 0.62) INK else WHITE)
    }

    fun heatOf(v: Double?, p: Period, dark: Boolean): Heat? = v?.let { heat(tOf(it, p.cap), dark) }

    /** One 동 on the grid: [col]/[row] in −R..R, row −R = top (ahead when [headingUp]). */
    data class Cell(val n: AreaNeighbor, val col: Int, val row: Int)
    data class Grid(val cells: List<Cell>, val radius: Int, val headingUp: Boolean)

    const val GRID_R = 2
    const val CELL_M = 700.0

    /**
     * Places up to 12 동 on a 5×5 grid around the car (the current 동 always in the centre cell). With a heading the grid is
     * turned so ahead = up and right = right (same side rule as 주변's tabs); without one, north = up (stated under the map).
     * A 동 takes the free cell nearest to its true position (no overlap, nothing dropped while cells remain).
     */
    fun grid(neighbors: List<AreaNeighbor>, r: Int = GRID_R, cellM: Double = CELL_M): Grid {
        val headingUp = neighbors.isNotEmpty() && neighbors.all { it.relDeg != null }
        val pos = neighbors.associateWith { n ->
            if (headingUp) {
                val a = Math.toRadians(n.relDeg!!.toDouble())
                Pair(n.distanceM * sin(a), n.distanceM * cos(a))      // x right, y ahead
            } else Pair(n.eastM.toDouble(), n.northM.toDouble())
        }
        val taken = HashSet<Pair<Int, Int>>()
        val out = ArrayList<Cell>()
        val order = neighbors.sortedWith(compareByDescending<AreaNeighbor> { it.current }.thenBy { it.distanceM })
        for (n in order) {
            val (x, y) = pos.getValue(n)
            val tx = x / cellM; val ty = -y / cellM                     // row grows downwards
            val best = if (n.current && (0 to 0) !in taken) 0 to 0 else {
                var b: Pair<Int, Int>? = null; var bd = Double.MAX_VALUE
                for (c in -r..r) for (w in -r..r) {
                    if ((c to w) in taken) continue
                    val d = hypot(c - tx, w - ty)
                    if (d < bd - 1e-9) { bd = d; b = c to w }
                }
                b
            } ?: break
            taken += best
            out += Cell(n, best.first, best.second)
        }
        return Grid(out, r, headingUp)
    }

    /** Area title: the 동, or the 구 when the 동 has no index of its own. */
    fun placeName(a: AreaInfo): String = if (a.level == "dong") a.dong else a.gu.split(' ').last()

    /** Home / menu value: `신흥동 · 1년 +26.4%`. */
    fun homeValue(a: AreaInfo?): String? = a?.let { "${placeName(it)} · 1년 ${pct(it.ret.y1)}" }

    /** Left-menu value (400 dp menu): `신흥동 +26.4%`. */
    fun railValue(a: AreaInfo): String = "${placeName(a)} ${pct(a.ret.y1)}"

    /** `경기 성남시 수정구` (+ `· 구 기준` when the numbers are the 구's). */
    fun regionLine(a: AreaInfo): String = listOf(a.sido, a.gu).filter { it.isNotBlank() }.joinToString(" ") +
        (if (a.level == "dong") "" else " · ${a.dong}은 구 지수로 봐요")

    fun factsLine(a: AreaInfo): String? = listOfNotNull(
        a.complexes?.let { "단지 ${ReevlFormat.num(it.toDouble())}곳" },
        a.households?.let { "${ReevlFormat.num(it.toDouble())}세대" },
        a.trades12?.takeIf { it > 0 }?.let { "최근 1년 거래 ${ReevlFormat.num(it.toDouble())}건" },
    ).joinToString(" · ").ifEmpty { null }

    fun placesLine(a: AreaInfo): String? = listOfNotNull(
        a.stations.takeIf { it.isNotEmpty() }?.joinToString("·"),
        a.schools.takeIf { it.isNotEmpty() }?.joinToString("·"),
    ).joinToString(" · ").ifEmpty { null }

    fun notableLine(n: AreaNotable): String = listOfNotNull(
        n.ret1y?.let { "1년 ${pct(it)}" },
        n.households?.let { "${ReevlFormat.num(it.toDouble())}세대" },
        n.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) },
    ).joinToString(" · ")

    /** `한국경제 · 11.09 · 셀레스빌` — date `2026.11.09 18:07` → `11.09`. */
    fun newsLine(n: AreaNews): String = listOfNotNull(
        n.source.ifBlank { null },
        n.date.takeIf { it.length >= 10 }?.let { it.substring(5, 7) + "." + it.substring(8, 10) },
        when (n.scope) { "complex" -> "${n.about} 기사"; "gu" -> "${n.about} 소식"; else -> "${n.about} 전체" },
    ).joinToString(" · ")

    /** Voice: does the spoken reference mean "the area I'm driving through" rather than a complex? */
    private val AREA_WORDS = listOf("동네", "지나는 곳", "지나가는 곳", "여기", "이 근처", "이 지역", "지금 지나", "이곳", "neighborhood", "this area", "around here")
    fun isAreaRef(ref: String?): Boolean = ref != null && AREA_WORDS.any { ref.contains(it, ignoreCase = true) }
}
