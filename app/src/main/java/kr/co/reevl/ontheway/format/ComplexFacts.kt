package kr.co.reevl.ontheway.format

import java.time.LocalDate
import java.time.ZoneId
import kr.co.reevl.ontheway.data.CarComplex

/**
 * The 핵심 정보 rows of a complex exactly as the web KeyStats renders them (components/detail/KeyStats.tsx 84-86 and
 * 229-238 at reevl feat/contest after A7 fix round 1). Pure — one source for STOP_DETAIL and the list's
 * stop summary, unit-tested in ComplexFactsTest.
 */
data class FactRow(val key: String, val label: String, val value: String, val chip: String? = null, val note: String? = null)

object ComplexFacts {
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /** Web 준공 cell: A7 `completionText` verbatim; for an older server, rebuild it with the same KeyStats rule (KST). */
    fun completion(c: CarComplex, today: LocalDate = LocalDate.now(KST)): String? {
        c.completionText?.takeIf { it.isNotBlank() }?.let { return it }
        c.approveDate?.let { d ->
            val age = ReevlFormat.yearsSince(d, today.year)
            return "${ReevlFormat.approveYM(d)}${if (age != null) " · ${age}년차" else ""}"
        }
        return c.buildAgeYears?.takeIf { it > 0 }?.let { "${it}년차" }
    }

    /** KeyStats MakerRow label: `브랜드` / `브랜드 2` (and `시공사` / `시공사 2`). */
    fun countedLabel(base: String, n: Int) = if (n > 1) "$base $n" else base

    fun brandNames(c: CarComplex): List<String> = c.brand.names.ifEmpty { listOfNotNull(c.brand.name) }

    fun builderNames(c: CarComplex): List<String> = c.builders.ifEmpty { listOfNotNull(c.builder) }

    fun rows(c: CarComplex, today: LocalDate = LocalDate.now(KST)): List<FactRow> = buildList {
        c.households?.let { add(FactRow("households", "세대수", "${ReevlFormat.num(it.toDouble())}세대")) }
        completion(c, today)?.let { add(FactRow("built", "준공", it)) }
        c.nearestStation?.let { st ->
            val m = st.distanceM
            // KeyStats: '역세권' within 3 km, else '가장 가까운 역'; value `${lines.join('·')} ${stName} · ${distTxt}`
            val label = if (m != null && m <= 3_000) "역세권" else "가장 가까운 역"
            val lines = if (st.lines.isNotEmpty()) st.lines.joinToString("·") + " " else ""
            add(FactRow("station", label, "$lines${ReevlFormat.stName(st.name)}${m?.let { " · ${ReevlFormat.distTxt(it.toDouble())}" } ?: ""}"))
        }
        c.jeonseRatio?.let { jr ->
            // KeyStats: `${jr}%` + chip `전세 ${fmt.num(n_jeonse)}건`; the as-of date belongs to this number only.
            add(FactRow("jeonse", "전세가율", "${ReevlFormat.jsNumber(jr.value)}%",
                chip = jr.jeonseCount?.let { "전세 ${ReevlFormat.num(it.toDouble())}건" }, note = jr.asOf?.let { "$it 기준" }))
        }
        c.dongCount?.let { add(FactRow("dongs", "동수", "${it}개동")) }
        brandNames(c).takeIf { it.isNotEmpty() }?.let { add(FactRow("brand", countedLabel("브랜드", it.size), it.joinToString(" · "))) }
        builderNames(c).takeIf { it.isNotEmpty() }?.let { add(FactRow("builder", countedLabel("시공사", it.size), it.joinToString(" · "))) }
    }

    /** One glanceable line for list cards: station · 세대 · 준공 (same words as the rows). */
    fun summaryLine(c: CarComplex, today: LocalDate = LocalDate.now(KST)): String? = listOfNotNull(
        c.nearestStation?.let { st -> listOfNotNull(ReevlFormat.stName(st.name), st.distanceM?.let { ReevlFormat.distTxt(it.toDouble()) }).joinToString(" ") },
        c.households?.let { "${ReevlFormat.num(it.toDouble())}세대" },
        completion(c, today)?.let { "준공 $it" },
    ).joinToString(" · ").ifBlank { null }
}
