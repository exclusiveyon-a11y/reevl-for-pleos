package kr.co.reevl.ontheway.gleo

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToLong
import kr.co.reevl.ontheway.data.CarComplex
import kr.co.reevl.ontheway.data.FavToggleResponse
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.RepoError
import kr.co.reevl.ontheway.data.RepoResult
import kr.co.reevl.ontheway.data.SearchResponse
import kr.co.reevl.ontheway.data.Synced
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.plan.Insert
import kr.co.reevl.ontheway.plan.PlanLogic
import kr.co.reevl.ontheway.search.AiSearch

/** A complex the user may refer to by name or position. */
data class GleoRef(val cid: String, val name: String)

/** A change Gleo asked about and the user has not confirmed yet (one at a time, short-lived). */
@kotlinx.serialization.Serializable
data class PendingWrite(val action: String, val cid: String, val name: String, val expiresAt: Long)

/** The car's position for a voice request; [headingDeg] only when the car trusts it. */
data class GleoPosition(val lat: Double, val lng: Double, val headingDeg: Double?)

/** What the voice handler needs — the same repositories the screens use (search, complex, plan, favs). */
interface GleoBackend {
    fun position(): GleoPosition?
    suspend fun search(q: String, at: GleoPosition): RepoResult<SearchResponse>
    suspend fun complex(cid: String): RepoResult<Synced<CarComplex>>
    /** Today's first plan (TODAY_PLAN order); Ok(null) = no plan. */
    suspend fun todayPlan(): RepoResult<Plan?>
    suspend fun addStop(planId: String, cid: String, afterStopId: String?): RepoResult<Plan>
    suspend fun deleteStop(planId: String, stopId: String): RepoResult<Plan>
    /** `PATCH {order}` (순서 최적화); null = this backend cannot reorder. */
    suspend fun reorder(planId: String, order: List<String>): RepoResult<Plan>? = null
    suspend fun setFav(cid: String, on: Boolean): RepoResult<FavToggleResponse>
    /** Results of the last voice search (for "the first one"). */
    /** 지나가는 동네 for [at] (area-context-car-spec §2.3); null = this backend has no area source. */
    suspend fun area(at: GleoPosition): RepoResult<kr.co.reevl.ontheway.data.AreaResponse>? = null
    fun lastResults(): List<GleoRef>
    fun rememberResults(refs: List<GleoRef>)
    fun saved(): List<GleoRef>
    fun pending(): PendingWrite?
    fun setPending(p: PendingWrite?)
    fun now(): Long
}

/** How a spoken reference maps to a complex. */
sealed interface Resolved {
    data class One(val ref: GleoRef) : Resolved
    data class Many(val refs: List<GleoRef>) : Resolved
    data object None : Resolved
    data object Missing : Resolved
}

/**
 * The three Gleo functions (assets/schema.json). Every answer is one Callback whose `message` is a short ENGLISH sentence;
 * Korean appears only inside it for names, places and search terms, and in `data` values (Pleos Callback Schema guide
 * §2.1 "Message는 영어 문장이어야 하지만 … 검색어, 특정 위치, 항목 이름 등 포함 가능" — guides-2026-10-26/
 * pleos-only__app-integration-guide__guide__action_callback.md). Nothing is shown on screen — the request may come while driving, when this Lifestyle app may not draw (car-ds-v2 P-6). A change (add, remove, save) is reported
 * as success only after the server answered Ok.
 */
class GleoActions(private val b: GleoBackend) {

    suspend fun handle(function: String, params: Map<String, String>): GleoCallback = try {
        withTimeout(GleoProtocol.TIMEOUT_MS) {
            when (function) {
                GleoProtocol.SEARCH -> search(params["query"])
                // The area variant lives inside ANALYZE_PROPERTY (the schema allows 3 functions): "지금 지나는 동네 어때?".
                GleoProtocol.ANALYZE -> if (kr.co.reevl.ontheway.area.AreaLogic.isAreaRef(params["complexIdOrReference"]) ||
                    (params["complexIdOrReference"].isNullOrBlank() && kr.co.reevl.ontheway.area.AreaLogic.isAreaRef(params["query"]))) area()
                    else analyze(params["complexIdOrReference"])
                GleoProtocol.PLAN -> plan(params["action"], params["complexIdOrReference"], params["confirmed"]?.trim()?.lowercase() == "true")
                else -> GleoCallback(function, GleoStatus.Fail, "REEVL cannot handle this request.")
            }
        }
    } catch (e: TimeoutCancellationException) {
        GleoCallback(function, GleoStatus.Fail, "A timeout occurred while processing the request.")
    }

    // ── SEARCH_PROPERTIES ──

    private suspend fun search(query: String?): GleoCallback {
        val f = GleoProtocol.SEARCH
        val q = query?.let { AiSearch.normalize(it) } ?: return GleoCallback(f, GleoStatus.Asking, "Search conditions are required to search for complexes.", listOf(mapOf("missingField" to "query")))
        val at = b.position() ?: return GleoCallback(f, GleoStatus.Asking, "The car's current location is required to search nearby.", listOf(mapOf("missingField" to "location")))
        return when (val r = b.search(q, at)) {
            is RepoResult.Err -> fail(f, r.error)
            is RepoResult.Ok -> {
                val v = r.value
                val shown = AiSearch.shown(v.items, v.conds, null, null)
                val rows = shown.rows.take(3)
                b.rememberResults(shown.rows.map { GleoRef(it.item.cid, it.item.name) })
                val radius = distEn((v.scannedRadiusM ?: v.radiusM).toDouble())
                val sideNote = if (v.conds.any { it.status == "car" }) " The driving direction is unknown, so left or right was ignored." else ""
                if (rows.isEmpty()) GleoCallback(f, GleoStatus.Success, "The search was completed, but no matching complexes were found within $radius.$sideNote")
                else GleoCallback(f, GleoStatus.Success, "Found ${plural(shown.rows.size, "complex", "complexes")} within $radius.$sideNote", rows.mapIndexed { i, row ->
                    buildMap {
                        put("rank", "${i + 1}")
                        put("name", row.item.name)
                        put("distance", ReevlFormat.distTxt(row.item.distanceM.toDouble()))
                        (row.item.evidenceTrade ?: row.item.latestTrade)?.let { put("latestPrice", ReevlFormat.wonShort(it.price.toDouble())); put("latestDate", it.date) }
                        AiSearch.sideWord(row.side).takeIf { row.side != kr.co.reevl.ontheway.nearby.NearbyEngine.Side.UNKNOWN }?.let { put("side", it) }
                    }
                })
            }
        }
    }

    // ── ANALYZE_PROPERTY · 동네 ──

    /** The server's own Korean summary (≤ 120 chars: 등락 → 주목 단지 → 이 동네 소식) goes in `data.summary` for Gleo to speak; the message is English. */
    private suspend fun area(): GleoCallback {
        val f = GleoProtocol.ANALYZE
        val at = b.position() ?: return GleoCallback(f, GleoStatus.Asking, "The car's current location is required to describe the area.", listOf(mapOf("missingField" to "location")))
        return when (val r = b.area(at)) {
            null -> GleoCallback(f, GleoStatus.Fail, "REEVL cannot handle this request.")
            is RepoResult.Err -> fail(f, r.error)
            is RepoResult.Ok -> {
                val v = r.value
                // The server's summary is Korean: it goes in `data` (any language), the message stays English.
                val a = v.area ?: return GleoCallback(f, GleoStatus.Success, "The search was completed, but REEVL knows no apartment complexes near here.",
                    v.summary.takeIf { it.isNotBlank() }?.let { listOf(mapOf("summary" to it)) }.orEmpty())
                val place = kr.co.reevl.ontheway.area.AreaLogic.placeName(a)
                GleoCallback(f, GleoStatus.Success, "Here is the latest summary for $place.", listOf(buildMap {
                    if (v.summary.isNotBlank()) put("summary", v.summary)
                    put("area", place)
                    put("region", "${a.sido} ${a.gu}")
                    a.ret.y1?.let { put("change1y", kr.co.reevl.ontheway.area.AreaLogic.pct(it)) }
                    v.notable.firstOrNull()?.let { put("notable", it.name) }
                    v.news.firstOrNull { it.scope != "sido" }?.let { put("news", it.title); put("newsSource", it.source) }
                }))
            }
        }
    }

    // ── ANALYZE_PROPERTY ──

    private suspend fun analyze(ref: String?): GleoCallback {
        val f = GleoProtocol.ANALYZE
        val target = when (val res = resolve(ref, b.lastResults() + b.saved())) {
            is Resolved.One -> res.ref
            is Resolved.Missing -> return GleoCallback(f, GleoStatus.Asking, "A complex name is required.", listOf(mapOf("missingField" to "complexIdOrReference")))
            is Resolved.None -> return GleoCallback(f, GleoStatus.Asking, "That complex was not found. Please name the complex.")
            is Resolved.Many -> return askChoose(f, res.refs)
        }
        return when (val r = b.complex(target.cid)) {
            is RepoResult.Err -> fail(f, r.error)
            is RepoResult.Ok -> {
                val c = r.value.value
                val facts = buildMap {
                    put("name", c.name)
                    c.households?.let { put("households", "$it") }
                    c.completionYear?.let { put("completionYear", "$it") }
                    c.recentTrades?.firstOrNull()?.let { put("latestPrice", ReevlFormat.wonShort(it.price.toDouble())); put("latestDate", it.date); put("areaM2", "${it.areaM2}") }
                    c.tradeAsOf?.let { put("asOf", it) }
                }
                val trades = c.recentTrades.orEmpty().drop(1).take(2).map { mapOf("price" to ReevlFormat.wonShort(it.price.toDouble()), "date" to it.date, "areaM2" to "${it.areaM2}") }
                GleoCallback(f, GleoStatus.Success, "These are the recorded transactions for ${c.name}, not current listing prices.", listOf(facts) + trades)
            }
        }
    }

    // ── MANAGE_VISIT_PLAN ──

    /**
     * Writes (add · remove · save) need two turns, as the guide's `asking` describes ("Confirmation … is required"): the first
     * call only answers `asking` and remembers the change for [CONFIRM_TTL_MS]; it runs only when Gleo calls again with
     * `confirmed=true` for the same action (and target, if one is given) before that expires. The guide defines no session or
     * request id, so the pending change itself is the token.
     */
    private fun confirmedPending(action: String, cid: String?, confirmed: Boolean): PendingWrite? {
        val p = b.pending() ?: return null
        if (!confirmed || p.action != action || p.expiresAt < b.now() || (cid != null && cid != p.cid)) return null
        return p
    }

    private fun ask(f: String, action: String, t: GleoRef, question: String): GleoCallback {
        b.setPending(PendingWrite(action, t.cid, t.name, b.now() + CONFIRM_TTL_MS))
        return GleoCallback(f, GleoStatus.Asking, question, listOf(mapOf("pendingAction" to action, "name" to t.name, "needs" to "confirmed")))
    }

    private suspend fun plan(action: String?, ref: String?, confirmed: Boolean = false): GleoCallback {
        val f = GleoProtocol.PLAN
        val act = action?.trim()?.lowercase()
        // A confirmation may come without the reference: take it from the change we asked about.
        val pend = if (act in WRITES && ref.isNullOrBlank()) confirmedPending(act!!, null, confirmed) else null
        val refOrPending = if (pend != null) pend.cid else ref
        return when (act) {
            "list" -> when (val r = b.todayPlan()) {
                is RepoResult.Err -> fail(f, r.error)
                is RepoResult.Ok -> r.value?.let { p ->
                    val stops = PlanLogic.ordered(p)
                    GleoCallback(f, GleoStatus.Success, "Today's visit plan \"${p.title}\" has ${plural(stops.size, "stop", "stops")}.",
                        stops.map { mapOf("order" to "${it.position}", "name" to it.name, "status" to it.visitStatus) })
                } ?: GleoCallback(f, GleoStatus.Success, "There is no visit plan for today.")
            }
            "add" -> {
                val t = target(f, refOrPending) ?: return pending!!
                if (confirmedPending("add", t.cid, confirmed) == null) return ask(f, "add", t, "Confirmation is required to add ${t.name} to today's visit plan.")
                b.setPending(null)
                val p = when (val r = b.todayPlan()) { is RepoResult.Err -> return fail(f, r.error); is RepoResult.Ok -> r.value }
                    ?: return GleoCallback(f, GleoStatus.Fail, "There is no visit plan to add to. Plans can be created on the phone.")
                if (p.stops.any { it.cid == t.cid }) return GleoCallback(f, GleoStatus.Fail, "${t.name} is already in today's visit plan.")
                val anchor = Insert.anchor(p)
                when (val r = b.addStop(p.id, t.cid, anchor?.id)) {
                    is RepoResult.Err -> fail(f, r.error)
                    is RepoResult.Ok -> GleoCallback(f, GleoStatus.Success,
                        if (anchor != null) "${t.name} was added after ${anchor.name}." else "${t.name} was added to the end of today's visit plan.",
                        listOf(mapOf("name" to t.name, "planStops" to "${r.value.stops.size}")))
                }
            }
            "remove" -> {
                val p = when (val r = b.todayPlan()) { is RepoResult.Err -> return fail(f, r.error); is RepoResult.Ok -> r.value }
                    ?: return GleoCallback(f, GleoStatus.Fail, "There is no visit plan for today.")
                val inPlan = p.stops.map { GleoRef(it.cid ?: "stop:${it.id}", it.name) }
                val t = when (val res = resolve(refOrPending, inPlan)) {
                    is Resolved.One -> res.ref
                    is Resolved.Missing -> return GleoCallback(f, GleoStatus.Asking, "The place to remove from the plan is required.", listOf(mapOf("missingField" to "complexIdOrReference")))
                    is Resolved.None -> return GleoCallback(f, GleoStatus.Asking, "That place is not in today's plan. Please name the place.")
                    is Resolved.Many -> return askChoose(f, res.refs)
                }
                if (confirmedPending("remove", t.cid, confirmed) == null) return ask(f, "remove", t, "Confirmation is required to remove ${t.name} from today's visit plan.")
                b.setPending(null)
                val stop = p.stops.first { (it.cid ?: "stop:${it.id}") == t.cid }
                when (val r = b.deleteStop(p.id, stop.id)) {
                    is RepoResult.Err -> fail(f, r.error)
                    is RepoResult.Ok -> GleoCallback(f, GleoStatus.Success, "${stop.name} was removed from today's visit plan.")
                }
            }
            "save" -> {
                val t = target(f, refOrPending) ?: return pending!!
                if (confirmedPending("save", t.cid, confirmed) == null) return ask(f, "save", t, "Confirmation is required to save ${t.name}.")
                b.setPending(null)
                when (val r = b.setFav(t.cid, true)) {
                    is RepoResult.Err -> fail(f, r.error)
                    // success only now that the server confirmed it
                    is RepoResult.Ok -> GleoCallback(f, GleoStatus.Success, "${t.name} was saved. It also appears on the phone and the web.",
                        listOf(mapOf("name" to t.name, "savedCount" to "${r.value.count}")))
                }
            }
            "reorder" -> reorder(f, confirmed)
            null, "" -> GleoCallback(f, GleoStatus.Asking, ACTION_CHOICES, listOf(mapOf("missingField" to "action")))
            else -> GleoCallback(f, GleoStatus.Asking, ACTION_CHOICES)
        }
    }

    private var pending: GleoCallback? = null

    /** Resolves [ref] for add/save among recent results, saved homes and the plan; on failure leaves the asking callback in [pending]. */
    private suspend fun target(f: String, ref: String?): GleoRef? {
        val plan = (b.todayPlan() as? RepoResult.Ok)?.value?.stops.orEmpty().filter { it.cid != null }.map { GleoRef(it.cid!!, it.name) }
        return when (val res = resolve(ref, b.lastResults() + b.saved() + plan)) {
            is Resolved.One -> res.ref
            is Resolved.Missing -> { pending = GleoCallback(f, GleoStatus.Asking, "A complex name is required.", listOf(mapOf("missingField" to "complexIdOrReference"))); null }
            is Resolved.None -> { pending = GleoCallback(f, GleoStatus.Asking, "That complex was not found. Please name the complex."); null }
            is Resolved.Many -> { pending = askChoose(f, res.refs); null }
        }
    }

    /**
     * "동선 줄여줘" → 순서 최적화 by voice (plan/PlanGeo, the same proposal the screen shows). First call: the numbers and the new
     * order, then a question; only `confirmed` with the SAME plan version sends the order (a phone edit in between → ask again).
     * Straight-line distances only — never a drive time.
     */
    private suspend fun reorder(f: String, confirmed: Boolean): GleoCallback {
        val p = when (val r = b.todayPlan()) { is RepoResult.Err -> return fail(f, r.error); is RepoResult.Ok -> r.value }
            ?: return GleoCallback(f, GleoStatus.Success, "There is no visit plan for today.")
        val prop = kr.co.reevl.ontheway.plan.PlanGeo.proposal(p)
            ?: return GleoCallback(f, GleoStatus.Success, "At least three stops with known locations are needed to reorder the plan.")
        val fmt = ::distEn
        if (!prop.changed) return GleoCallback(f, GleoStatus.Success, "The current order is already the shortest.")
        val key = "plan:${p.id}:${p.version}"
        // The new order stays in the data (Gleo may read it on request); the sentence is one line.
        if (confirmedPending("reorder", key, confirmed) == null) {
            b.setPending(PendingWrite("reorder", key, p.title, b.now() + CONFIRM_TTL_MS))
            return GleoCallback(f, GleoStatus.Asking,
                "Confirmation is required to reorder the ${if (prop.fixed > 1) "remaining " else ""}stops, which shortens the straight-line distance from ${fmt(prop.beforeM)} to ${fmt(prop.afterM)}.",
                listOf(mapOf("pendingAction" to "reorder", "needs" to "confirmed", "beforeDistance" to fmt(prop.beforeM), "afterDistance" to fmt(prop.afterM),
                    "order" to prop.after.drop(prop.fixed - 1).joinToString(", ") { it.name })))
        }
        b.setPending(null)
        val r = b.reorder(p.id, prop.order) ?: return GleoCallback(f, GleoStatus.Fail, "REEVL cannot handle this request.")
        return when (r) {
            is RepoResult.Err -> fail(f, r.error)
            is RepoResult.Ok -> GleoCallback(f, GleoStatus.Success, "The stops were reordered, and the straight-line distance is ${fmt(prop.savedM)} shorter.",
                PlanLogic.ordered(r.value).map { mapOf("order" to "${it.position}", "name" to it.name) })
        }
    }

    private fun askChoose(f: String, refs: List<GleoRef>) =
        GleoCallback(f, GleoStatus.Asking, "Several complexes match. It is necessary to select one of them.", refs.take(3).mapIndexed { i, r -> mapOf("rank" to "${i + 1}", "name" to r.name) })

    private fun fail(f: String, e: RepoError) = GleoCallback(f, GleoStatus.Fail, failText(e))

    companion object {
        private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        /** English distance for a message (PlanGeo.fmtDist is the Korean screen form): "850 m", "1.2 km", "less than 50 m". */
        internal fun distEn(m: Double): String = when {
            !m.isFinite() || m < 0 -> "an unknown distance"
            m < 50 -> "less than 50 m"
            m < 1000 -> "${(m / 10).roundToLong() * 10} m"
            m < 100_000 -> String.format(java.util.Locale.US, "%.1f", m / 1000).removeSuffix(".0") + " km"
            else -> "${(m / 1000).roundToLong()} km"
        }
        private const val ACTION_CHOICES = "Please choose an action: list, add, remove, reorder or save."

        const val CONFIRM_TTL_MS = 60_000L
        private val WRITES = setOf("add", "remove", "save", "reorder")

        fun failText(e: RepoError): String = when (e) {
            RepoError.Expired, RepoError.NotLinked -> "This car is not linked to REEVL. Please link it with the code from the phone."
            is RepoError.Offline -> "The car is not connected to the network."
            RepoError.RateLimited -> "There are too many requests. Please try again shortly."
            RepoError.ComplexNotFound -> "REEVL has no information about that complex."
            RepoError.PlanNotFound -> "The visit plan was not found."
            RepoError.FavLimit -> "The saved list is full. Please remove some on the phone."
            else -> "An unexpected error occurred on the REEVL server."
        }

        private val ORDINALS = mapOf("첫" to 1, "1" to 1, "first" to 1, "두" to 2, "2" to 2, "second" to 2, "세" to 3, "3" to 3, "third" to 3)

        private fun norm(s: String) = s.lowercase().replace(Regex("[\\s()·]"), "")

        /** cid → itself; "첫 번째" / "2번" / "third" → that rank of [candidates]; else unique name match. */
        fun resolve(ref: String?, candidates: List<GleoRef>): Resolved {
            val r = ref?.trim().orEmpty()
            if (r.isEmpty()) return Resolved.Missing
            val uniq = candidates.distinctBy { it.cid }
            if (r.count { it == '|' } >= 3) return Resolved.One(uniq.firstOrNull { it.cid == r } ?: GleoRef(r, r.split('|')[1]))
            val ord = Regex("^(첫|두|세|[123]|first|second|third)\\s*(번째|번|one)?").find(r.lowercase())
            if (ord != null && ord.value.isNotBlank() && (r.length <= 8)) {
                val i = ORDINALS[ord.groupValues[1]] ?: 0
                return uniq.getOrNull(i - 1)?.let { Resolved.One(it) } ?: Resolved.None
            }
            val n = norm(r)
            val exact = uniq.filter { norm(it.name) == n }
            if (exact.size == 1) return Resolved.One(exact[0])
            val part = uniq.filter { norm(it.name).contains(n) || n.contains(norm(it.name)) }
            return when (part.size) { 0 -> Resolved.None; 1 -> Resolved.One(part[0]); else -> Resolved.Many(part) }
        }
    }
}
