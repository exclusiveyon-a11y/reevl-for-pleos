package kr.co.reevl.ontheway.plan

import kr.co.reevl.ontheway.data.Plan

/**
 * D2 review I-2: an async answer may only be applied if it still belongs to what the screen shows.
 * Pure (no Android) so the races are unit-tested.
 */
class SessionEpoch {
    @Volatile private var n = 0L

    /** Capture before launching a call. */
    fun current(): Long = n

    /** Logout, a new link, or a 401: every answer captured before this is dropped. */
    fun bump() { n++ }

    fun isCurrent(captured: Long): Boolean = captured == n
}

object PlanGate {
    /**
     * Apply [incoming] only when it is the plan the screen asked for ([wantedId]) and not older than the plan already
     * shown (a GET issued before a PATCH can return after it — the PATCH answer is newer and must win).
     */
    fun accept(wantedId: String?, shown: Plan?, incoming: Plan): Boolean {
        if (incoming.id != wantedId) return false
        if (shown == null || shown.id != incoming.id) return true
        return incoming.version >= shown.version
    }
}

/** M-10: never show a raw error code on screen — the user sentence for a failed guide request. */
object GuideFailureText {
    const val GENERIC = "길 안내를 연결하지 못했습니다. 다시 시도해 주세요."
    fun detail(code: String): String? = when (code) {
        "NO_VERIFIED_COORDS" -> "이 장소는 위치 확인이 필요해요. 휴대폰 리블에서 위치를 확인해 주세요."
        "NOT_CONNECTED" -> "차량 내비게이션에 연결하지 못했어요."
        "TIMEOUT" -> "차량 내비게이션이 응답하지 않았어요."
        "DEST_MISMATCH" -> "내비게이션에 다른 목적지가 설정됐어요."
        else -> null
    }
}
