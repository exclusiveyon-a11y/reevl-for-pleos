package kr.co.reevl.ontheway.plan

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kr.co.reevl.ontheway.data.MeetingPoint
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.navi.RouteSummary

/**
 * 고객 투어 모드 (v7 PBV; brand 2026-10-28 renamed from 고객 동행 투어) — pure rules, JVM-tested. Sources:
 *  · v7 master §3-B "약속 지연 알림 문구 규칙" (sentences used verbatim), §9-6-A "ETA 이벤트 계약"
 *    (`delay = max(0, eta − appointment)` only when both exist and `eta_source = navi_verified`; no appointment →
 *    only the next stop), §3-C 기능 2–4, V7-contract §1 (fields).
 *  · Brief D4 item 1: a plan whose date is not today shows the appointment time without any ETA comparison.
 */
object Tour {
    const val MODE_TOUR = "tour"
    const val MODE_LABEL = "고객 투어 모드"

    fun isTour(p: Plan?): Boolean = p?.mode == MODE_TOUR

    /** The pseudonym only in tour mode (V7-contract: "only meaningful when mode = tour"). */
    fun clientLabel(p: Plan?): String? = p?.takeIf { isTour(it) }?.clientLabel?.trim()?.takeIf { it.isNotEmpty() }

    /** `고객 투어 모드 · 고객 A` (or just the mode label without a pseudonym). */
    fun modeLine(p: Plan?): String? = if (!isTour(p)) null else listOfNotNull(MODE_LABEL, clientLabel(p)).joinToString(" · ")
}

object Schedule {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    /** Initial threshold (v7 master §3-B step 6: "알림 임계치(예: 5분)는 사용자 테스트로 조정할 초기값"). */
    const val LATE_THRESHOLD_MIN = 5

    /** V7-contract `YYYY-MM-DDTHH:mm:00+09:00`. Anything unreadable → null (shown as "no appointment"). */
    fun parse(s: String?): OffsetDateTime? = s?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    fun appointment(stop: PlanStop?): OffsetDateTime? = parse(stop?.appointmentAt)

    /** `14:00` (KST wall clock). */
    fun hhmm(t: OffsetDateTime): String = t.atZoneSameInstant(KST).format(HHMM)

    fun hhmm(t: Instant): String = t.atZone(KST).format(HHMM)

    /** `오후 2시`, `오후 2시 30분`, `오전 9시` — the spoken form the §3-B sentences use. */
    fun spoken(t: OffsetDateTime): String {
        val z = t.atZoneSameInstant(KST)
        val h = z.hour
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
        return if (z.minute == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${z.minute}분"
    }

    /** §9-6-A `eta_source`. Only [NAVI_VERIFIED] may be compared with an appointment. */
    enum class EtaSource(val wire: String) { NAVI_VERIFIED("navi_verified"), UNAVAILABLE("unavailable") }

    data class Eta(val at: Instant, val source: EtaSource)

    /**
     * ETA from the car's navigation: the time of its route report + the remaining time it reported — and only when
     * the route was verified (Ruling 17: destination read back = request). Anything else is [EtaSource.UNAVAILABLE].
     */
    fun etaFromNavi(route: RouteSummary?, verified: Boolean): Eta? {
        val at = route?.reportedAtMs ?: return null
        val dur = route.durationS ?: return null
        if (!verified || dur < 0) return null
        return Eta(Instant.ofEpochMilli(at).plusSeconds(dur.toLong()), EtaSource.NAVI_VERIFIED)
    }

    /** §9-6-A: `delay_minutes = max(0, eta_at − appointment_at)` only when both exist and the ETA is navi_verified. */
    fun delayMinutes(appointment: OffsetDateTime?, eta: Eta?): Int? {
        if (appointment == null || eta == null || eta.source != EtaSource.NAVI_VERIFIED) return null
        // Whole minutes of the ETA as shown (`도착 예정 13:40`), so the sentence matches the two times on screen.
        val etaShown = eta.at.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val min = Duration.between(appointment.toInstant(), etaShown).toMinutes()
        return if (min <= 0) 0 else min.toInt()
    }

    // ── §3-B sentences, verbatim patterns ──
    fun lateSentence(appointment: OffsetDateTime, minutes: Int) = "${spoken(appointment)} 약속에 약 ${minutes}분 늦을 수 있습니다."
    fun unknownEtaSentence(appointment: OffsetDateTime) = "약속은 ${spoken(appointment)}입니다. 도착 예정시간을 확인할 수 없습니다."
    fun clashSentence(appointment: OffsetDateTime) = "${spoken(appointment)}에 방문 일정이 두 개 있습니다. 어느 곳을 먼저 방문할까요?"

    /** Is the plan (or, without a plan date, the appointment) on [today] in KST? */
    fun isToday(plan: Plan, appointment: OffsetDateTime?, today: LocalDate): Boolean {
        val d = plan.plannedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: appointment?.atZoneSameInstant(KST)?.toLocalDate()
        return d == today
    }

    /** What the 다음 약속 card shows. Built only for a tour plan whose next stop has an appointment. */
    data class AppointmentCard(
        val stop: PlanStop,
        val appointment: OffsetDateTime,
        /** `다음 약속 14:00 · 경희궁자이(2단지)` */
        val title: String,
        /** `만남 장소 · …` */
        val meetingLine: String,
        /** `도착 예정 14:12` — only with a navi_verified ETA on the plan's own day. */
        val etaLine: String?,
        /** The §3-B sentence for the situation, or null when there is nothing to warn about. */
        val sentence: String?,
        /** true = the sentence is a warning (late / clash). */
        val warn: Boolean,
        /** Not today: `11월 7일 (토) 약속` — the time is shown, no ETA comparison. */
        val dayNote: String?,
        val delayMinutes: Int?,
        val etaSource: EtaSource,
    )

    /**
     * The next stop's appointment card. [eta] must be the navigation's ETA **for this stop** (to the stop or to its
     * meeting point), or null. No appointment on the next stop → null (the list shows only the next stop, §9-6-A).
     */
    fun appointmentCard(plan: Plan, eta: Eta?, today: LocalDate): AppointmentCard? {
        if (!Tour.isTour(plan)) return null
        val next = PlanLogic.nextStop(plan) ?: return null
        val appt = appointment(next) ?: return null
        val onDay = isToday(plan, appt, today)
        val usableEta = eta?.takeIf { onDay && it.source == EtaSource.NAVI_VERIFIED }
        val delay = delayMinutes(appt, usableEta)
        val clash = PlanLogic.ordered(plan).any { it.id != next.id && it.visitStatus == PlanLogic.PLANNED && appointment(it)?.toInstant() == appt.toInstant() }
        val (sentence, warn) = when {
            clash -> clashSentence(appt) to true
            !onDay -> null to false
            usableEta == null -> unknownEtaSentence(appt) to false
            delay != null && delay >= LATE_THRESHOLD_MIN -> lateSentence(appt, delay) to true
            else -> null to false
        }
        return AppointmentCard(
            stop = next,
            appointment = appt,
            title = "다음 약속 ${hhmm(appt)} · ${next.name}",
            meetingLine = meetingLine(next.meetingPoint),
            etaLine = usableEta?.let { "도착 예정 ${hhmm(it.at)}" },
            sentence = sentence,
            warn = warn,
            dayNote = if (onDay) null else "${PlanLogic.dateLabel(plan.plannedDate ?: appt.atZoneSameInstant(KST).toLocalDate().toString()).substringAfter("년 ")} 약속",
            delayMinutes = delay,
            etaSource = usableEta?.source ?: EtaSource.UNAVAILABLE,
        )
    }

    /** `만남 장소 · OO공인중개사사무소` / `만남 장소 · 단지 대표 위치` (no point set = the complex's own point). */
    fun meetingLine(mp: MeetingPoint?): String = when {
        mp == null || mp.label.isBlank() -> "만남 장소 · 단지 대표 위치"
        mp.verified -> "만남 장소 · ${mp.label}"
        else -> "만남 장소 · ${mp.label} (위치 확인 필요)"
    }

    /** `만남 장소로 안내` is offered only for a server-verified point with coordinates (Brief item 4, CoordsGate). */
    fun canGuideToMeeting(mp: MeetingPoint?): Boolean =
        mp != null && mp.verified && mp.lat != null && mp.lng != null && mp.lat in -90.0..90.0 && mp.lng in -180.0..180.0

    /** Per-stop time in tour lists: `14:00 약속`, or null. */
    fun stopTimeLabel(plan: Plan?, stop: PlanStop): String? =
        if (!Tour.isTour(plan)) null else appointment(stop)?.let { "${hhmm(it)} 약속" }

    /** First appointment of a tour plan (TODAY_PLAN card: `첫 약속 14:00`). */
    fun firstAppointment(plan: Plan): OffsetDateTime? =
        if (!Tour.isTour(plan)) null else PlanLogic.ordered(plan).mapNotNull { appointment(it) }.minOrNull()
}

/**
 * 현장에서 추가 (v7 §3, §3-C 기능 2): the new place goes right after the current/last-visited stop; visited stops are
 * never moved (`A → 새 장소 → B → C`).
 */
object Insert {
    data class Row(val position: Int, val name: String, val isNew: Boolean, val visitStatus: String?)

    /**
     * The stop the new place follows: the last stop (by position) that is no longer `planned` (visited/skipped);
     * when nothing was visited yet, the stop we are at/heading to (the first planned one). Empty plan → null (= end).
     */
    fun anchor(plan: Plan): PlanStop? {
        val ordered = PlanLogic.ordered(plan)
        return ordered.lastOrNull { it.visitStatus != PlanLogic.PLANNED } ?: ordered.firstOrNull()
    }

    /** The order the user is asked to confirm, with the new place marked. */
    fun preview(plan: Plan, newName: String): List<Row> {
        val ordered = PlanLogic.ordered(plan)
        val a = anchor(plan)
        val at = if (a == null) ordered.size else ordered.indexOfFirst { it.id == a.id } + 1
        val names = ordered.map { Row(0, it.name, false, it.visitStatus) }.toMutableList()
        names.add(at, Row(0, newName, true, null))
        return names.mapIndexed { i, r -> r.copy(position = i + 1) }
    }

    /** `경희궁자이(2단지) → 경희궁자이(4단지) → 경희궁자이(3단지) → 경희궁롯데캐슬` */
    fun previewLine(plan: Plan, newName: String): String = preview(plan, newName).joinToString(" → ") { it.name }

    /** CHANGE_CONFIRM question: `<A> 다음에 <새 장소>를 방문할까요?` (을/를 by the last syllable). */
    fun question(plan: Plan, newName: String): String {
        val a = anchor(plan)
        val obj = "$newName${Josa.eulReul(newName)}"
        return if (a == null) "${obj} 방문 순서 끝에 추가할까요?" else "${a.name} 다음에 $obj 방문할까요?"
    }

    /** After the server saved the insert: the stop whose id was not in [before]. */
    fun addedStop(before: Plan, after: Plan): PlanStop? {
        val ids = before.stops.map { it.id }.toSet()
        return after.stops.firstOrNull { it.id !in ids }
    }
}

/** Korean object particle 을/를 chosen by how the name is read (last Hangul syllable or digit; brackets ignored). */
object Josa {
    fun eulReul(word: String): String {
        val c = word.trimEnd().trimEnd(')', ']', '}', '>', '.', ' ', '"', '\'').lastOrNull() ?: return "를"
        val batchim = when {
            c in '가'..'힣' -> (c.code - 0xAC00) % 28 != 0
            c.isDigit() -> c in "013678" // 영 일 삼 육 칠 팔 end in a consonant; 이 사 오 구 do not
            else -> false
        }
        return if (batchim) "을" else "를"
    }
}

/**
 * 고객 보기 (v7 PBV 03, slide 11): what a client may see — order, names, brand avatars (cid), public complex facts.
 * By construction it carries no client label, appointment, meeting point, note or other plan.
 */
object CustomerView {
    data class Item(val position: Int, val name: String, val cid: String?, val kind: String, val visitStatus: String)

    fun items(plan: Plan): List<Item> = PlanLogic.ordered(plan).filter { it.visitStatus != PlanLogic.SKIPPED }
        .mapIndexed { i, s -> Item(i + 1, s.name, s.cid, s.kind, s.visitStatus) }

    /** Header line of the client view — no plan title (a broker's title may name the client). */
    fun headline(plan: Plan): String = "함께 볼 곳 ${items(plan).size}곳"
}
