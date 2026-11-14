package kr.co.reevl.ontheway.link

import java.time.LocalDate
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.plan.PlanLogic

/**
 * 휴대폰으로 넘기기 (user 2026-11-10 "휴대폰에서 찾기 해서 qr 줘서 휴대폰에서 가능하게"): the car is voice-first, so anything that needs
 * typing or setup is handed to the phone by a QR. The car and the phone are already the same account (car token), so what the
 * user makes on the phone comes back through the normal `GET /api/car/plans` · `/api/car/favs` — no new server endpoint.
 *
 * Rules kept free of Android (unit-tested in PhoneHandoffTest):
 *  · URL = public route + `from=car`, and for PLAN `&add=<cid>` when a complex is in context (2026-11-11: the web /plan
 *    screen shows a 확인 card — "차에서 보낸 단지" — and adds it to today's plan, making today's plan if there is none).
 *    No token, no request id, nothing personal: a cid is a public complex id (the same one in /complex/<cid>), URL-encoded.
 *    The car still keeps "which complex" itself — if the phone adds it, the result offers 안내 to it; if not, `추가`.
 *  · Refresh plans + saved every [POLL_MS] for at most [MAX_MS], only while the QR screen is in front, the app is in the
 *    foreground and the car is not driving-locked; once more when the user leaves without a change. Nothing else polls.
 *  · A change (a plan to add to / a new plan, or — for 찾기 — a new saved complex) closes the QR and says what came.
 */
object PhoneHandoff {
    /** Production web: a phone cannot open the emulator's debug server (same as [QrLink.WEB]). */
    const val WEB = QrLink.WEB
    /** 10 s between checks while the QR is visible. */
    const val POLL_MS = 10_000L
    /** Stop checking 10 minutes after the QR appeared (the screen then offers `다시 확인`). */
    const val MAX_MS = 10 * 60_000L

    enum class Kind {
        /** `추가` with no plan for today → make the plan on the phone (reevl /plan, the web 임장 계획 screen). */
        PLAN,
        /** `휴대폰으로 찾기` on HOME / AI 검색 → type the long query on the phone (/ai). */
        SEARCH,
    }

    /**
     * The QR text. `/plan` = the web 임장 계획 screen (feat/contest, behind NEXT_PUBLIC_VISIT_PLAN; the old
     * `/account?from=car#plan` link still lands there); `/ai` = the web AI 검색 (live). `from=car` tags the source.
     * [cid] (PLAN only) = the complex the user pressed `추가` on → `&add=<cid>`, percent-encoded (cids hold `|`, Korean, `()`).
     * A blank cid or SEARCH ignores it.
     */
    fun url(kind: Kind, cid: String? = null): String = when (kind) {
        Kind.PLAN -> "$WEB/plan?from=car" + (cid?.takeIf { it.isNotBlank() }?.let { "&add=" + encode(it) } ?: "")
        Kind.SEARCH -> "$WEB/ai?from=car"
    }

    /** RFC 3986 percent-encoding (UTF-8; space → %20, not +) — what the web reads back with URLSearchParams. */
    fun encode(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20").replace("%7E", "~")

    /** Check now? Only on the QR screen, in front, parked/unrestricted, nothing found yet, within [MAX_MS]. */
    fun shouldPoll(onScreen: Boolean, foreground: Boolean, driving: Boolean, found: Boolean, startedAtMs: Long, nowMs: Long): Boolean =
        onScreen && foreground && !driving && !found && !timedOut(startedAtMs, nowMs)

    fun timedOut(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs >= MAX_MS

    /** Leaving the QR screen without a change → one last refresh (not while locked: the lock means 0 calls). */
    fun refreshOnLeave(found: Boolean, driving: Boolean): Boolean = !found && !driving

    /** What the account looked like when the QR appeared. [plans] = id → updatedAt of the plan HOME would use (and the rest). */
    data class Baseline(val plans: Map<String, Long>, val favCids: Set<String>)

    fun baseline(plans: List<PlanSummary>?, favCids: Collection<String>?): Baseline =
        Baseline(plans.orEmpty().associate { it.id to it.updatedAt }, favCids.orEmpty().toSet())

    sealed interface Change {
        data class PlanMade(val planId: String) : Change
        data class Saved(val cid: String) : Change
    }

    /**
     * The first change worth closing the QR for. [plans] / [favCids] = a fresh server answer (null = not asked or not fresh).
     *  · PLAN and SEARCH: the plan `추가` would use (PlanLogic.addTarget — today's, else the next, never a past one) is new or
     *    changed since the QR appeared.
     *  · SEARCH only: a complex saved on the phone (in PLAN the user may star complexes on the way to building the plan —
     *    that is not the end of what they went to do).
     */
    fun detect(kind: Kind, base: Baseline, plans: List<PlanSummary>?, favCids: Collection<String>?, today: LocalDate): Change? {
        plans?.let { PlanLogic.addTarget(it, today) }?.let { t ->
            val was = base.plans[t.id]
            if (was == null || t.updatedAt > was) return Change.PlanMade(t.id)
        }
        if (kind == Kind.SEARCH) favCids?.firstOrNull { it !in base.favCids }?.let { return Change.Saved(it) }
        return null
    }

    /** The one action after a plan arrived. */
    enum class PlanAction {
        /** The complex the user wanted to add is not in it yet → 넣을 자리 (PLAN_INSERT). */
        ADD,
        /** Guide to [planStop] (the wanted complex if it is in the plan, else the next stop). */
        GUIDE,
        /** Nothing guidable → open the plan. */
        OPEN,
    }

    /** The stop the result line names: the wanted complex if the plan has it, else the next planned stop, else the first. */
    fun planStop(plan: Plan, wantedCid: String?): PlanStop? =
        wantedCid?.let { c -> plan.stops.firstOrNull { it.cid == c } } ?: PlanLogic.nextStop(plan) ?: PlanLogic.ordered(plan).firstOrNull()

    fun planAction(plan: Plan, wantedCid: String?, canGuide: (PlanStop) -> Boolean): PlanAction {
        if (wantedCid != null && plan.stops.none { it.cid == wantedCid }) return PlanAction.ADD
        val s = planStop(plan, wantedCid) ?: return PlanAction.OPEN
        return if (canGuide(s)) PlanAction.GUIDE else PlanAction.OPEN
    }
}
