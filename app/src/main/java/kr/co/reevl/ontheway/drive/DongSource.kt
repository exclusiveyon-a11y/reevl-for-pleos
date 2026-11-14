package kr.co.reevl.ontheway.drive

/**
 * Which 동 the car is in, without a server call (pleos-signals-and-cost.md §3.3, driving-context-spec §2.4).
 * Order: the car's navigation (Pleos NaviHelper `CurrentLocationInfo.address`, e.g. `수정구 시흥동`) → the 동 of the cached
 * 동네 answer. A future on-device index (`LocalIndex`, pending the pipeline) slots in as another [DongSource].
 */
data class Dong(
    /** Last token, e.g. `시흥동`. */
    val name: String,
    /** The token before it when there is one (`수정구`); the navigation gives no 시도. */
    val gu: String?,
    /** `navi` | `area-cache` */
    val via: String,
) {
    /** `수정구 시흥동` — matched against the area key `시도 구 동` by suffix (only the last token of [gu]). */
    val guDong: String get() = listOfNotNull(gu?.split(' ')?.last(), name).joinToString(" ")
}

fun interface DongSource {
    suspend fun current(): Dong?
}

object DongLogic {
    private val ENDS = listOf("동", "읍", "면", "가", "리")

    /**
     * `수정구 시흥동` → Dong(시흥동, 수정구). Accepts longer addresses (`경기 성남시 수정구 시흥동 123-4`): the last token that
     * looks like a 동/읍/면/가/리 name wins. Null when nothing looks like one.
     */
    fun parse(address: String?, via: String): Dong? {
        val toks = address?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        val i = toks.indexOfLast { t -> t.length >= 2 && ENDS.any { t.endsWith(it) } && t.first().isLetter() }
        if (i < 0) return null
        return Dong(toks[i], toks.getOrNull(i - 1)?.takeIf { it.endsWith("구") || it.endsWith("시") || it.endsWith("군") }, via)
    }

    /** Same 동? Compares the name, and the 구 when both sides have one. */
    fun same(a: Dong?, b: Dong?): Boolean {
        if (a == null || b == null) return false
        if (a.name != b.name) return false
        val ga = a.gu?.split(' ')?.last(); val gb = b.gu?.split(' ')?.last() // `성남시 수정구` vs the navigation's `수정구`
        return ga == null || gb == null || ga == gb
    }

    /** Does area key `경기 성남시 수정구 시흥동` / `서울 종로구 송월동` belong to [d]? */
    fun matchesKey(d: Dong, key: String): Boolean {
        val toks = key.split(' ')
        if (toks.lastOrNull() != d.name) return false
        return d.gu == null || toks.contains(d.gu.split(' ').last())
    }
}

/** First source that answers wins. */
class ChainDongSource(private val sources: List<DongSource>) : DongSource {
    override suspend fun current(): Dong? {
        for (s in sources) s.current()?.let { return it }
        return null
    }
}
