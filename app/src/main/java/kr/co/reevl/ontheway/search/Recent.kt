package kr.co.reevl.ontheway.search

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kr.co.reevl.ontheway.data.Memo
import kr.co.reevl.ontheway.data.RecentItem
import kr.co.reevl.ontheway.data.RecentPutItem
import kr.co.reevl.ontheway.data.RecentResponse
import kr.co.reevl.ontheway.data.Screener

/**
 * 휴대폰 ↔ 차 동기화 rules on the car (pure, JVM-tested in RecentTest). User 2026-11-10: "휴대폰에서 최근 본 단지나 찜 단지 등등이 다 연동되게".
 *
 *  · 최근 본 단지 = the server's merged list (phone + car, reevl user_recent) + what this car opened and has not sent yet.
 *    A complex opened here shows at once (from the car's own facts); it reaches the server with the next account sync.
 *  · Requests: one account sync = recent (PUT when something is pending, else GET) + memos + screeners — only while parked, at
 *    most once per [SYNC_EVERY_MS] (app open / ⟳ force it), never polled. 0 while driving-locked (the caller checks).
 */
object Recent {
    /** The server keeps 30 per account (lib/recent.ts RECENT_MAX). */
    const val KEEP = 30
    /** HOME / AI 검색: at most 3 rows, then `더보기` (1:1 window budget). */
    const val HOME_ROWS = 3
    /** Account sync (recent · memos · screeners · favs on resume): at most once per 5 minutes unless forced. */
    const val SYNC_EVERY_MS = 5 * 60_000L

    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun syncDue(lastAtMs: Long?, nowMs: Long, force: Boolean = false): Boolean =
        force || lastAtMs == null || nowMs - lastAtMs >= SYNC_EVERY_MS || nowMs < lastAtMs

    /** Server list ∪ not-yet-sent car views; one row per complex (the newer view wins), newest first, ≤ [KEEP]. Apartments only. */
    fun merged(server: RecentResponse?, pending: List<RecentItem>): List<RecentItem> {
        val by = LinkedHashMap<String, RecentItem>()
        for (it in server?.items.orEmpty() + pending) {
            if (!it.cid.contains('|') || it.cid.contains('~')) continue
            val cur = by[it.cid]
            by[it.cid] = when {
                cur == null -> it
                it.viewedAt > cur.viewedAt -> it.copy(name = it.name ?: cur.name, brand = it.brand ?: cur.brand, households = it.households ?: cur.households,
                    latestTrade = it.latestTrade ?: cur.latestTrade, address = it.address ?: cur.address, lat = it.lat ?: cur.lat, lng = it.lng ?: cur.lng)
                else -> cur
            }
        }
        return by.values.sortedWith(compareByDescending<RecentItem> { it.viewedAt }.thenBy { it.cid }).take(KEEP)
    }

    /** This car opened [item] at [nowMs] → the pending list (newest first, one per complex, ≤ [KEEP]). Not a real complex → unchanged. */
    fun note(pending: List<RecentItem>, item: RecentItem, nowMs: Long): List<RecentItem> {
        if (!item.cid.contains('|') || item.name == null) return pending
        return (listOf(item.copy(viewedAt = nowMs, source = CAR, approx = false)) + pending.filter { it.cid != item.cid }).take(KEEP)
    }

    fun toPut(pending: List<RecentItem>): List<RecentPutItem> = pending.map { RecentPutItem(it.cid, it.viewedAt) }

    /** After the server took [sent]: drop those (a complex opened again meanwhile has a newer time and stays). */
    fun afterPush(pending: List<RecentItem>, sent: List<RecentPutItem>): List<RecentItem> {
        val at = sent.associate { it.cid to it.viewedAt }
        return pending.filter { p -> at[p.cid]?.let { p.viewedAt > it } ?: true }
    }

    const val CAR = "car"

    /** `휴대폰에서 · 어제` / `이 차에서 · 방금`; a time we do not know (approx) is never invented → `휴대폰에서`. */
    fun sourceLabel(item: RecentItem, nowMs: Long, zone: ZoneId = KST): String {
        val where = if (item.source == CAR) "이 차에서" else "휴대폰에서"
        if (item.approx || item.viewedAt <= 0) return where
        return "$where · ${ago(item.viewedAt, nowMs, zone)}"
    }

    /** `방금` · `n분 전` · `n시간 전` (same day) · `어제` · `n일 전` (≤ 6) · `MM.dd`. */
    fun ago(atMs: Long, nowMs: Long, zone: ZoneId = KST): String {
        val d = nowMs - atMs
        val day = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return when {
            d < 60_000L -> "방금"
            d < 3_600_000L && day == today -> "${d / 60_000L}분 전"
            day == today -> "${d / 3_600_000L}시간 전"
            day == today.minusDays(1) -> "어제"
            day.isAfter(today.minusDays(7)) -> "${java.time.temporal.ChronoUnit.DAYS.between(day, today)}일 전"
            else -> DateTimeFormatter.ofPattern("MM.dd").format(day)
        }
    }

    // ── 내 메모 ──

    /** The memo of [cid], or null. Blank text = no memo (the web deletes on empty). */
    fun memoOf(memos: List<Memo>?, cid: String): Memo? = memos?.firstOrNull { it.cid == cid && it.text.isNotBlank() }

    /** Second line of the memo row: `휴대폰에서 적은 메모 · 11.09`. */
    fun memoLine(m: Memo, zone: ZoneId = KST): String =
        listOfNotNull("휴대폰에서 적은 메모", m.updatedAt.takeIf { it > 0 }?.let {
            DateTimeFormatter.ofPattern("MM.dd").format(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())
        }).joinToString(" · ")

    /** The memo itself on one or two lines (the row wraps; longer text is cut — the full memo lives on the phone). */
    fun memoText(m: Memo, max: Int = 80): String {
        val t = m.text.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
    }

    // ── 저장한 조건 (screeners) ──

    /** Screeners the car can run (q != null), newest first, ≤ [max]. */
    fun runnable(items: List<Screener>?, max: Int = 3): List<Screener> = items.orEmpty().filter { !it.q.isNullOrBlank() }.take(max)

    /** One-tap chips under the field: the user's own saved conditions first, then the voice suggestions (not one that repeats a saved
     *  condition's sentence); [total] chips at most. */
    fun chips(screeners: List<Screener>?, voice: List<String>, total: Int): List<Chip> {
        val own = runnable(screeners, total).map { Chip(it.name, it.q!!, it.id) }
        return own + voice.filter { v -> own.none { it.label == v || it.q == v } }.take((total - own.size).coerceAtLeast(0)).map { Chip(it, it, null) }
    }

    /** AI 검색 idle rows: the saved conditions + voice suggestions, ≤ [total] rows in all (but always 2 voice rows), so 최근 본 단지
     *  stays on the first screen of the 1:1 window. */
    fun idleVoice(screens: List<Chip>, voice: List<String>, total: Int = 4): List<String> =
        voice.filter { v -> screens.none { it.label == v || it.q == v } }.take(maxOf(2, total - screens.size))

    /** [screenerId] != null = a saved condition from the phone (tap runs [q], the row title is [label]). */
    data class Chip(val label: String, val q: String, val screenerId: String?)

    /** After running a saved condition: what stays on the phone (`역세권·지역은 휴대폰에서만 볼 수 있어요`), or null. */
    fun skippedNote(s: Screener): String? = s.skipped.takeIf { it.isNotEmpty() }?.let { "${it.take(3).joinToString("·")}${if (it.size > 3) " 등" else ""}은 휴대폰에서만 볼 수 있어요" }

}
