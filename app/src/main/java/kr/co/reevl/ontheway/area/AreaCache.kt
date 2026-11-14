package kr.co.reevl.ontheway.area

import kr.co.reevl.ontheway.data.AreaResponse

/**
 * 동네 answers kept per 동 (heat key `시도 구 동`) for [ttlMs] — driving-context-spec §2.4 request budget ("동네: 법정동, 6시간").
 * Memory only (no location history on disk), at most [max] 동 (oldest dropped).
 */
class AreaCache(private val ttlMs: Long = 6 * 3600_000L, private val max: Int = 20) {
    private val map = LinkedHashMap<String, Pair<AreaResponse, Long>>()

    fun put(r: AreaResponse, now: Long) {
        val key = r.area?.key ?: return
        map.remove(key)
        map[key] = r to now
        while (map.size > max) map.remove(map.keys.first())
    }

    /** Newest fresh answer whose key matches (e.g. the navigation's `수정구 시흥동` against `경기 성남시 수정구 시흥동`). */
    fun find(now: Long, match: (String) -> Boolean): AreaResponse? =
        map.keys.toList().asReversed().firstOrNull(match)?.let { get(it, now) }

    fun get(key: String, now: Long): AreaResponse? {
        val (r, at) = map[key] ?: return null
        return if (now - at <= ttlMs) r else { map.remove(key); null }
    }
}
