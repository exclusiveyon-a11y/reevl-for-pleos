package kr.co.reevl.ontheway.ui.state

import java.time.ZoneId
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.data.UserMessages

/** car-ds-v2 §5.8 banner words: `오프라인 · 21:36 기준` / `불러오지 못했어요`. */
object Notices {
    fun offline(at: Long, zone: ZoneId = ZoneId.systemDefault()) = Notice(NoticeKind.Offline, "오프라인 · ${UserMessages.hhmm(at, zone)} 기준")

    /** Cached data → offline line with its time; fresh → none; nothing + an error → error line with 다시. */
    fun of(data: Synced<*>?, message: String?): Notice? = when {
        // A cached copy shown while the fresh one loads is not "offline": only a failed refresh (notice set) says so.
        data != null && data.fromCache && data.notice != null -> offline(data.syncedAt)
        data == null && message != null -> Notice(NoticeKind.Error, message, retry = true)
        else -> null
    }

    /**
     * A list that failed to refresh but still shows its last answer: offline → `오프라인 · 20:30 기준` (when that answer
     * arrived) so the rows are not read as now (QA round 2: 주변 said only `오프라인이에요` over the old rows).
     */
    fun forList(message: String?, hasRows: Boolean, syncedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Notice? = when {
        message == null -> null
        hasRows && syncedAt > 0 && message.startsWith(UserMessages.OFFLINE) -> offline(syncedAt, zone).copy(retry = true)
        else -> Notice(if (message.startsWith(UserMessages.OFFLINE)) NoticeKind.Offline else NoticeKind.Error, short(message), retry = true)
    }

    /** Short failure words (the long D2 sentences stay for logs). */
    fun failed(e: RepoError): String = when (e) {
        is RepoError.Offline -> "오프라인이에요"
        RepoError.RateLimited -> "잠시 후 다시 해 주세요"
        RepoError.ComplexNotFound -> "단지 정보가 없어요"
        RepoError.PlanNotFound -> "계획을 찾을 수 없어요"
        RepoError.FavLimit -> "저장이 너무 많아요"
        else -> "불러오지 못했어요"
    }

    /** A long D2 sentence (UserMessages) → the short banner word. */
    fun short(message: String): String = when {
        message.startsWith(UserMessages.OFFLINE) -> "오프라인이에요"
        else -> "불러오지 못했어요"
    }
}
