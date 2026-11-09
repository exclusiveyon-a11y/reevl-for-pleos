package kr.co.reevl.ontheway.plan

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlanSummary
import kr.co.reevl.ontheway.navi.CoordsGate
import kr.co.reevl.ontheway.navi.Stop

/** Pure rules behind the plan screens (no Android, unit-tested). */
object PlanLogic {
    const val PLANNED = "planned"
    const val VISITED = "visited"
    const val SKIPPED = "skipped"

    const val LABEL_VISITED = "방문 완료"
    const val LABEL_NEXT = "다음 방문"
    const val LABEL_PLANNED = "방문 예정"
    const val LABEL_SKIPPED = "건너뜀"
    const val GUIDE_FIRST = "첫 장소 안내"
    const val GUIDE_NEXT = "다음 장소 안내"
    const val NEEDS_LOCATION = "위치 확인 필요"

    fun ordered(plan: Plan): List<PlanStop> = plan.stops.sortedBy { it.position }

    /** Same rule as the server's `nextStop`: first `planned` stop by position. */
    fun nextStop(plan: Plan): PlanStop? = ordered(plan).firstOrNull { it.visitStatus == PLANNED }

    fun statusLabel(stop: PlanStop, next: PlanStop?): String = when (stop.visitStatus) {
        VISITED -> LABEL_VISITED
        SKIPPED -> LABEL_SKIPPED
        else -> if (stop.id == next?.id) LABEL_NEXT else LABEL_PLANNED
    }

    /** `첫 장소 안내` until something was visited or skipped, then `다음 장소 안내`. */
    fun guideLabel(plan: Plan): String =
        if (plan.stops.all { it.visitStatus == PLANNED }) GUIDE_FIRST else GUIDE_NEXT

    /** The navi layer's view of a stop. lat/lng come from the server already as latitude/longitude. */
    fun toNaviStop(s: PlanStop): Stop = Stop(
        id = s.id,
        position = s.position,
        kind = s.kind,
        name = s.name,
        address = s.address.orEmpty(),
        lat = s.lat,
        lng = s.lng,
        locationVerified = s.locationVerified,
        visitStatus = s.visitStatus,
    )

    /** The guide button is enabled only when the shared [CoordsGate] would let the stop through. */
    fun canGuide(s: PlanStop): Boolean = CoordsGate.check(toNaviStop(s)) == null

    fun kindLabel(kind: String): String = when (kind) {
        "apartment_complex" -> "아파트 단지"
        "real_estate_office" -> "공인중개사무소"
        else -> "방문지"
    }

    /** Plans for today first, then upcoming dates (soonest first), then undated / past (server order kept). */
    fun sortForToday(plans: List<PlanSummary>, today: LocalDate): List<PlanSummary> {
        fun date(p: PlanSummary) = p.plannedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val indexed = plans.withIndex()
        return indexed.sortedWith(
            compareBy<IndexedValue<PlanSummary>>(
                { iv -> date(iv.value).let { d -> if (d == today) 0 else if (d != null && d.isAfter(today)) 1 else 2 } },
                { iv -> date(iv.value).let { d -> if (d != null && d.isAfter(today)) d.toEpochDay() else 0L } },
                { iv -> iv.index },
            ),
        ).map { it.value }
    }

    /**
     * `오늘 갈 곳 N곳` only when the top plan is dated today; a dated plan names its date (`11월 7일 (토) 갈 곳 3곳`);
     * an undated plan says `갈 곳 N곳` — never "오늘" for a plan that has no date (D2 review M-10).
     */
    fun headline(top: PlanSummary?, today: LocalDate): String {
        if (top == null) return "갈 곳이 아직 없어요"
        val d = top.plannedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val prefix = when {
            d == null -> ""
            d == today -> "오늘 "
            else -> shortDate(d) + " "
        }
        return "${prefix}갈 곳 ${top.stopCount}곳"
    }

    /** `2026-11-07` → `2026년 11월 7일 (토)`; unparsable → as is; null → `날짜 미정`. */
    fun dateLabel(plannedDate: String?): String {
        if (plannedDate == null) return "날짜 미정"
        val d = runCatching { LocalDate.parse(plannedDate) }.getOrNull() ?: return plannedDate
        return "${d.year}년 ${shortDate(d)}"
    }

    private fun shortDate(d: LocalDate) =
        "${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    /** 24000 m → `24km`, 8500 → `8.5km`, 800 → `800m`. */
    fun distance(m: Int?): String? = when {
        m == null || m < 0 -> null
        m < 1000 -> "${m}m"
        m < 10_000 -> String.format(Locale.US, "%.1fkm", m / 1000.0).replace(".0km", "km")
        else -> "${Math.round(m / 1000.0)}km"
    }

    /** 2220 s → `37분`, 3900 → `1시간 5분`. */
    fun duration(s: Int?): String? {
        if (s == null || s < 0) return null
        val min = Math.round(s / 60.0).toInt()
        return if (min < 60) "${min}분" else if (min % 60 == 0) "${min / 60}시간" else "${min / 60}시간 ${min % 60}분"
    }
}
