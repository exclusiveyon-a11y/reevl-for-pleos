package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.SearchCond
import kr.co.reevl.ontheway.data.SearchItem
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.nearby.NearbyEngine.Side

/**
 * AI 검색 — the car-side half of `POST /api/car/search` (pure, JVM-tested).
 *
 * The server reads the sentence, checks `applied` conditions against its data and returns the matches. Relative
 * direction words (`status:"car"`: 왼쪽/오른쪽/앞/방금 지나온) only make sense on the car, so the car classifies each
 * match with [NearbyEngine.classify] (the NEARBY rule: side = candidate, unknown when heading/accuracy are missing)
 * and keeps that side. Without a usable heading it filters nothing and says so — never a guessed side.
 */
object AiSearch {
    const val DEFAULT_RADIUS_M = 1000
    const val WIDE_RADIUS_M = 3000
    const val LIMIT = 10
    /** Longest sentence sent to the server (the field stops accepting more). */
    const val MAX_QUERY = 80

    /** Example chips (tap = fill + run). */
    val EXAMPLES = listOf(
        "오른쪽에 8억 이하로 거래된 단지",
        "근처 1,000세대 넘는 곳",
        "30평대 10억 이하",
        "방금 지나온 신축",
        "주변 큰 단지",
    )

    /** car-ds-v2 §9.2 idle suggestion rows (tap = fill + run). */
    val SUGGESTIONS = listOf("오른쪽 8억 이하", "1,000세대 넘는 곳", "30평대 10억 이하", "방금 지나온 신축")

    const val UNSUPPORTED_NOTE = "이 조건은 확인 못 했어요"
    const val NO_DIRECTION_NOTE = "방향을 몰라 좌우 구분 없이 보여요"
    const val NO_CONDITION_NOTE = "조건을 못 찾아 주변 단지를 보여 드려요"

    // ── condition chips ──

    enum class ChipKind { Applied, Direction, Unsupported }

    data class CondChip(val label: String, val kind: ChipKind)

    /** `applied` → normal chip, `car` → direction chip, anything else (incl. an unknown future status) → muted. */
    fun chipKind(status: String): ChipKind = when (status) {
        "applied" -> ChipKind.Applied
        "car" -> ChipKind.Direction
        else -> ChipKind.Unsupported
    }

    fun chips(conds: List<SearchCond>): List<CondChip> =
        conds.filter { it.label.isNotBlank() }.map { CondChip(it.label, chipKind(it.status)) }

    fun hasUnsupported(conds: List<SearchCond>): Boolean = chips(conds).any { it.kind == ChipKind.Unsupported }

    // ── direction (car-side) ──

    /**
     * The side a `car` condition asks for, from its key first, then its label. `방금 지나온` = behind the car.
     * null = not a direction we know (the condition is then shown but not applied).
     */
    fun sideOf(c: SearchCond): Side? {
        if (c.status != "car") return null
        val k = c.key.lowercase()
        val l = c.label
        return when {
            k in setOf("right", "side_right", "dir_right") || l.contains("오른") -> Side.RIGHT
            k in setOf("left", "side_left", "dir_left") || l.contains("왼") -> Side.LEFT
            k in setOf("behind", "passed", "back", "dir_behind", "just_passed") || l.contains("지나온") || l.contains("뒤") -> Side.BEHIND
            k in setOf("ahead", "front", "dir_ahead") || l.contains("앞") -> Side.AHEAD
            else -> null
        }
    }

    fun wantedSides(conds: List<SearchCond>): Set<Side> = conds.mapNotNull { sideOf(it) }.toSet()

    /** Same rule as [NearbyEngine.classify]: a side needs a heading and a fix with accuracy ≤ MAX_ACCURACY_M. */
    fun directionKnown(fix: NearbyEngine.Fix?, headingDeg: Double?): Boolean {
        val acc = fix?.accuracyM ?: return false
        return headingDeg != null && acc <= NearbyEngine.MAX_ACCURACY_M
    }

    data class Row(val item: SearchItem, val side: Side)

    /**
     * [rows] = what the list shows (server order = distance). [filtered] = a direction was asked AND applied;
     * [note] = one sentence about the direction (null when nothing needs saying).
     */
    data class Shown(val rows: List<Row>, val wanted: Set<Side>, val filtered: Boolean, val note: String?)

    /** Server side word → Side. */
    fun serverSide(s: String?): Side? = when (s) { "right" -> Side.RIGHT; "left" -> Side.LEFT; "ahead" -> Side.AHEAD; "behind" -> Side.BEHIND; else -> null }

    fun shown(items: List<SearchItem>, conds: List<SearchCond>, fix: NearbyEngine.Fix?, headingDeg: Double?, headingEstimated: Boolean = false): Shown {
        // The server judged the sides (we sent headingDeg): show them, never filter again.
        if (items.any { it.side != null }) {
            return Shown(items.map { Row(it, serverSide(it.side) ?: Side.UNKNOWN) }, emptySet(), filtered = false, note = null)
        }
        val sides: Map<String, Side> = if (fix == null) emptyMap() else
            NearbyEngine.classify(fix, headingDeg, items.map { NearbyEngine.Place(it.cid, it.lat, it.lng) }).associate { it.id to it.side }
        val all = items.map { Row(it, sides[it.cid] ?: Side.UNKNOWN) }
        val wanted = wantedSides(conds)
        if (wanted.isEmpty()) return Shown(all, wanted, filtered = false, note = null)
        if (!directionKnown(fix, headingDeg)) return Shown(all, wanted, filtered = false, note = NO_DIRECTION_NOTE)
        val note = if (headingEstimated) "멈추기 직전 진행 방향 기준(방향 추정)" else "현재 진행 방향 기준"
        return Shown(all.filter { it.side in wanted }, wanted, filtered = true, note = note)
    }

    // ── empty states ──

    sealed interface Empty {
        /** `no_candidates`: nothing in the radius. [canWiden] = a wider (3 km) search is still possible. */
        data class NoCandidates(val radiusM: Int, val canWiden: Boolean) : Empty
        /** `no_match` (or the direction removed every match). [failed] = the condition that failed most, if known. */
        data class NoMatch(val failed: String?) : Empty
    }

    /**
     * What the empty area says, or null when there are rows to show. `no_condition` with rows is not empty: the list
     * shows the nearby complexes under [NO_CONDITION_NOTE].
     */
    fun empty(r: SearchResponse, s: Shown): Empty? {
        // The server's reason wins: items sent with `no_match` are near misses (only used to name the failed check).
        when (r.emptyReason) {
            "no_candidates" -> return Empty.NoCandidates(r.radiusM, r.radiusM < WIDE_RADIUS_M)
            "no_match" -> return Empty.NoMatch(mostFailed(r))
        }
        if (s.rows.isNotEmpty()) return null
        return when (r.emptyReason) {
            "no_condition" -> Empty.NoCandidates(r.radiusM, r.radiusM < WIDE_RADIUS_M)
            else -> when {
                // The server matched some, the car's direction removed them all → the direction is what failed.
                r.items.isNotEmpty() && s.filtered -> Empty.NoMatch(directionLabel(r.conds))
                r.scanned == 0 -> Empty.NoCandidates(r.radiusM, r.radiusM < WIDE_RADIUS_M)
                else -> Empty.NoMatch(mostFailed(r))
            }
        }
    }

    /**
     * The check label with the most `ok:false` over the returned items (ties → first seen). The contract does not
     * promise non-matching items on `no_match`, so with no false check this is null and the screen lists the
     * applied conditions instead.
     */
    fun mostFailed(r: SearchResponse): String? {
        val counts = LinkedHashMap<String, Int>()
        r.items.forEach { it.checks.forEach { c -> if (c.ok == false && c.label.isNotBlank()) counts[c.label] = (counts[c.label] ?: 0) + 1 } }
        return counts.maxByOrNull { it.value }?.key
    }

    private fun directionLabel(conds: List<SearchCond>): String? = conds.firstOrNull { sideOf(it) != null }?.label

    /** `30평대 공급면적 기준` for each condition the server explained; null when none did. */
    fun basisLine(conds: List<SearchCond>): String? =
        conds.filter { !it.basis.isNullOrBlank() && it.label.isNotBlank() }.joinToString(" · ") { "${it.label} ${it.basis}" }.ifEmpty { null }

    // ── 확인 필요 (ConfirmTemplate) ──
    const val DROP = "빼기"

    /** Area words (`30평대`) → the two readings + 빼기 (v9 C-2); anything else → 빼기 only. */
    fun confirmOptions(label: String): List<String> =
        if (label.contains("평")) listOf("전용 84㎡ 안팎", "공급 99~132㎡", DROP) else listOf(DROP)

    // ── lines ──

    /** `1km` / `1.5km` / `800m`. */
    fun radiusText(m: Int): String = when {
        m < 1000 -> "${m}m"
        m % 1000 == 0 -> "${m / 1000}km"
        else -> "${m / 100 / 10.0}km"
    }

    /** `반경 1km · 후보 42곳 중 3곳` (+ ` · 오른쪽 2곳` when the car filtered by side). */
    /** car-ds-v2 summary: `1km 안 60곳 중 4곳` — the radius actually covered (smaller when the server capped the pool). */
    fun summary(r: SearchResponse, s: Shown): String =
        "${radiusText(r.scannedRadiusM ?: r.radiusM)} 안 ${r.scanned}곳 중 ${s.rows.size}곳"

    fun countLine(r: SearchResponse, s: Shown): String {
        val base = "반경 ${radiusText(r.radiusM)} · 후보 ${r.scanned}곳 중 ${r.matched}곳" +
            (if (r.unknown > 0) " · 자료 없어 확인 못 한 ${r.unknown}곳" else "")
        if (!s.filtered) return base
        val side = s.wanted.joinToString("·") { sideWord(it) }
        return "$base · $side ${s.rows.size}곳"
    }

    fun sideWord(s: Side): String = when (s) {
        Side.RIGHT -> "오른쪽"
        Side.LEFT -> "왼쪽"
        Side.AHEAD -> "앞쪽"
        Side.BEHIND -> "지나온 쪽"
        Side.UNKNOWN -> "방향 모름"
    }

    /** Server notice + `기준일 기준` (the notice says 실거래 기준, 매물 호가 아님 — shown verbatim). */
    fun noticeLine(r: SearchResponse): String? =
        listOfNotNull(r.notice?.takeIf { it.isNotBlank() }, r.asOf?.let { "$it 기준" }).joinToString(" · ").ifEmpty { null }

    /**
     * The note line under the count (QA 2026-11-10: [Shown.note] and [NO_CONDITION_NOTE] were computed but never drawn —
     * `오른쪽 8억 이하` without a heading listed both sides under an `오른쪽` chip with nothing said).
     */
    fun noteLine(r: SearchResponse, s: Shown): String? = listOfNotNull(
        NO_DIRECTION_NOTE.takeIf { s.note == NO_DIRECTION_NOTE },
        // `학군 좋은` (unsupported) used to sit as a muted chip over an unfiltered list with nothing said (round 2).
        r.conds.filter { it.label.isNotBlank() && chipKind(it.status) == ChipKind.Unsupported }.map { it.label }
            .takeIf { it.isNotEmpty() && s.rows.isNotEmpty() }?.let { "${it.joinToString(", ")} 조건은 확인 못 했어요" },
        NO_CONDITION_NOTE.takeIf { r.emptyReason == "no_condition" && s.rows.isNotEmpty() },
    ).joinToString(" · ").ifEmpty { null }

    /** A direction chip whose side the car could not tell is not "applied": it is shown muted (확인 못 한 조건). */
    fun directionUnchecked(c: SearchCond, s: Shown?): Boolean = sideOf(c) != null && s?.note == NO_DIRECTION_NOTE

    /** 을/를 after [word] (last Hangul syllable's final consonant; digits/latin read as Korean: 0·1·3·6·7·8 → 을). */
    fun objParticle(word: String): String {
        val c = word.trim().lastOrNull() ?: return "를"
        val batchim = when {
            c in '가'..'힣' -> (c - '가') % 28 != 0
            c.isDigit() -> c in "013678"
            else -> false
        }
        return if (batchim) "을" else "를"
    }

    /** Normalised query: trimmed, inner whitespace collapsed, at most [MAX_QUERY] chars. Blank → null. */
    fun normalize(q: String): String? = q.trim().replace(Regex("\\s+"), " ").take(MAX_QUERY).ifEmpty { null }
}
