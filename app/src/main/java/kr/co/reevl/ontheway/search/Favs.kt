package kr.co.reevl.ontheway.search

import kr.co.reevl.ontheway.data.FavsResponse
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.UserMessages

/** 관심 단지 rules on the car (pure, JVM-tested). */
object Favs {
    /** GET /api/car/favs returns at most this many (newest first). */
    const val LIST_MAX = 100

    /**
     * Saved or not, only from what the server said: a toggle it confirmed wins, else membership in the last list.
     * No list → null (unknown: the star waits instead of guessing). A list capped at [LIST_MAX] cannot prove "not
     * saved" for a cid outside it → null too.
     */
    fun state(cid: String, list: FavsResponse?, confirmed: Map<String, Boolean>): Boolean? {
        confirmed[cid]?.let { return it }
        val l = list ?: return null
        if (l.favs.any { it.cid == cid }) return true
        return if (l.favs.size >= LIST_MAX) null else false
    }

    /** `3곳` / `100곳+` (capped list without a count from a toggle) / null (unknown). */
    fun countLabel(serverCount: Int?, list: FavsResponse?): String? = when {
        serverCount != null -> "${serverCount}곳"
        list == null -> null
        list.favs.size >= LIST_MAX -> "${LIST_MAX}곳+"
        else -> "${list.favs.size}곳"
    }

    /** One sentence after a failed toggle; the star stays as it was. */
    fun failText(wantOn: Boolean, e: RepoError): String {
        val head = if (wantOn) "저장하지 못했어요." else "저장을 해제하지 못했어요."
        return when (e) {
            RepoError.ComplexNotFound -> "$head 리블에 이 단지 정보가 없어요."
            RepoError.FavLimit -> UserMessages.FAV_LIMIT
            else -> "$head ${UserMessages.of(e)}"
        }
    }

    /** `저장 26.11.09` from epoch ms (KST). */
    fun savedLabel(savedAt: Long): String? = if (savedAt <= 0) null else
        "저장 " + java.time.format.DateTimeFormatter.ofPattern("yy.MM.dd").withZone(java.time.ZoneId.of("Asia/Seoul"))
            .format(java.time.Instant.ofEpochMilli(savedAt))
}
