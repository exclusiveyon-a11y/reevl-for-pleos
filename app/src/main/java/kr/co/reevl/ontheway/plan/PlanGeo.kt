package kr.co.reevl.ontheway.plan

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop

/**
 * 계획 동선 (pure, unit-tested) — a port of the web's `reevl/frontend/lib/planGeo.ts` (2026-11-11) so the phone and the car
 * say the same numbers for the same plan.
 *
 * ★Every distance is a straight line (haversine). There is no route API: never a road path, never a drive time. The car's
 *  navigation (NaviHelper) is what knows the road.
 * ★순서 최적화 = nearest-neighbor → 2-opt + or-opt → exact Held-Karp up to [EXACT_MAX] points. Open path, stop 1 fixed
 *  (the place the user chose to start). The result is a **proposal**: the screen asks 적용 / 취소, never overwrites quietly.
 * ★A stop without usable coordinates is left out of the distance and goes to the end in its old order.
 */
object PlanGeo {
    data class LL(val lat: Double, val lng: Double)

    private const val R = 6371008.8 // mean Earth radius (m) — same value as the web
    private fun rad(d: Double) = d * Math.PI / 180

    fun haversineM(a: LL, b: LL): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * R * asin(min(1.0, sqrt(h)))
    }

    /** Usable coordinates: finite and roughly inside Korea (a flipped `[lon, lat]` is not). */
    fun hasLL(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in 33.0..39.0 && lng in 124.0..132.0

    fun ll(s: PlanStop): LL? = if (hasLL(s.lat, s.lng)) LL(s.lat!!, s.lng!!) else null

    /** legs[i] = point i → i+1; null when either side has no coordinates. */
    fun legsM(points: List<LL?>): List<Double?> = (0 until (points.size - 1).coerceAtLeast(0)).map { i ->
        val a = points[i]; val b = points[i + 1]
        if (a != null && b != null) haversineM(a, b) else null
    }

    /** Sum of the measurable legs (m); [Total.gaps] = legs that could not be measured. */
    data class Total(val m: Double, val gaps: Int)
    fun totalM(points: List<LL?>): Total = legsM(points).let { l -> Total(l.sumOf { it ?: 0.0 }, l.count { it == null }) }

    /** `50m 이내` · `850m` (10 m steps) · `1.2km` · `123km` — the web's fmtDist. */
    fun fmtDist(m: Double): String = when {
        !m.isFinite() || m < 0 -> ""
        m < 50 -> "50m 이내"
        m < 1000 -> "${(m / 10).roundToLong() * 10}m"
        m < 100_000 -> String.format(Locale.US, "%.1fkm", m / 1000)
        else -> "${(m / 1000).roundToLong()}km"
    }

    fun pathM(points: List<LL>, order: List<Int>): Double {
        var s = 0.0
        for (i in 0 until order.size - 1) s += haversineM(points[order[i]], points[order[i + 1]])
        return s
    }

    const val EXACT_MAX = 12

    /** Order of [points] (a permutation of indices): start = 0 fixed, [fixEnd] keeps the last index last. */
    fun optimizeOrder(points: List<LL>, fixEnd: Boolean = false): List<Int> {
        val n = points.size
        if (n <= 2) return points.indices.toList()
        val fe = fixEnd && n >= 3
        val d = { i: Int, j: Int -> haversineM(points[i], points[j]) }
        // ① nearest first (ties → the lower index)
        val left = linkedSetOf<Int>().apply { for (i in 1 until (if (fe) n - 1 else n)) add(i) }
        val order = mutableListOf(0)
        while (left.isNotEmpty()) {
            val cur = order.last()
            var best = -1; var bd = Double.POSITIVE_INFINITY
            for (j in left) { val x = d(cur, j); if (x < bd - 1e-9) { bd = x; best = j } }
            order.add(best); left.remove(best)
        }
        if (fe) order.add(n - 1)
        val lastMovable = if (fe) n - 2 else n - 1
        for (guard in 0 until 100) {
            var improved = false
            // ② 2-opt: reverse order[i..k]
            for (i in 1 until lastMovable) for (k in i + 1..lastMovable) {
                val a = order[i - 1]; val b = order[i]; val c = order[k]; val e = if (k + 1 < n) order[k + 1] else -1
                val before = d(a, b) + (if (e >= 0) d(c, e) else 0.0)
                val after = d(a, c) + (if (e >= 0) d(b, e) else 0.0)
                if (after < before - 1e-6) {
                    val seg = order.subList(i, k + 1).reversed()
                    for (x in seg.indices) order[i + x] = seg[x]
                    improved = true
                }
            }
            // ③ or-opt: move one point elsewhere (2-opt alone cannot send a middle point to the end of an open path)
            for (i in 1..lastMovable) {
                val x = order[i]
                val rest = order.subList(0, i) + order.subList(i + 1, order.size)
                val cur = pathLen(order, d)
                var bestAt = -1; var bestLen = cur - 1e-6
                for (at in 1..(if (fe) rest.size - 1 else rest.size)) {
                    if (at == i) continue
                    val len = pathLen(rest.subList(0, at) + x + rest.subList(at, rest.size), d)
                    if (len < bestLen) { bestLen = len; bestAt = at }
                }
                if (bestAt >= 0) {
                    val next = rest.subList(0, bestAt) + x + rest.subList(bestAt, rest.size)
                    order.clear(); order.addAll(next)
                    improved = true
                }
            }
            if (!improved) break
        }
        // ④ exact answer for small plans (the web measured the heuristic up to 11 % longer than the shortest)
        if (n <= EXACT_MAX) {
            val ex = exactOrder(n, d, fe)
            if (pathLen(ex, d) < pathLen(order, d) - 1e-6) return ex
        }
        return order
    }

    /** Held-Karp, open path from 0 ([fixEnd]: ending at n-1). Ties → the lower index (deterministic). */
    private fun exactOrder(n: Int, d: (Int, Int) -> Double, fixEnd: Boolean): List<Int> {
        val m = n - 1
        val full = (1 shl m) - 1
        val dp = DoubleArray((1 shl m) * m) { Double.POSITIVE_INFINITY }
        val par = IntArray((1 shl m) * m) { -1 }
        for (j in 0 until m) dp[(1 shl j) * m + j] = d(0, j + 1)
        for (mask in 1..full) for (j in 0 until m) {
            val v = dp[mask * m + j]
            if (mask and (1 shl j) == 0 || v == Double.POSITIVE_INFINITY) continue
            if (fixEnd && j == m - 1 && mask != full) continue
            for (k in 0 until m) {
                if (mask and (1 shl k) != 0) continue
                val nm = mask or (1 shl k); val c = v + d(j + 1, k + 1)
                if (c < dp[nm * m + k] - 1e-9) { dp[nm * m + k] = c; par[nm * m + k] = j }
            }
        }
        var end = if (fixEnd) m - 1 else 0
        if (!fixEnd) for (j in 1 until m) if (dp[full * m + j] < dp[full * m + end] - 1e-9) end = j
        val out = mutableListOf<Int>()
        var mask = full; var j = end
        while (j >= 0) {
            out.add(j + 1)
            val pj = par[mask * m + j]
            mask = mask and (1 shl j).inv()
            j = pj
        }
        return listOf(0) + out.reversed()
    }

    private fun pathLen(order: List<Int>, d: (Int, Int) -> Double): Double {
        var s = 0.0
        for (i in 0 until order.size - 1) s += d(order[i], order[i + 1])
        return s
    }

    /** [items] = the proposed order (stops without coordinates at the end, old order); [changed] = shorter by ≥ 0.5 m. */
    data class Optimized<T>(val items: List<T>, val beforeM: Double, val afterM: Double, val changed: Boolean, val skipped: Int)

    /** The web's optimizeStops. null = the first item has no coordinates (unknown start). */
    fun <T> optimizeStops(stops: List<T>, llOf: (T) -> LL?, fixEnd: Boolean = false): Optimized<T>? {
        if (stops.isEmpty() || llOf(stops[0]) == null) return null
        val withLL = stops.filter { llOf(it) != null }
        val without = stops.filter { llOf(it) == null }
        val fe = fixEnd && llOf(stops.last()) != null && withLL.size >= 3
        val ord = optimizeOrder(withLL.map { llOf(it)!! }, fe)
        val items = ord.map { withLL[it] } + without
        val beforeM = totalM(withLL.map(llOf)).m
        val afterM = totalM(items.mapNotNull(llOf)).m
        val changed = items.indices.any { items[it] !== stops[it] } && afterM < beforeM - 0.5
        return Optimized(if (changed) items else stops.toList(), beforeM, if (changed) afterM else beforeM, changed, without.size)
    }

    // ── car rules on top of the web's ──

    /**
     * The car's proposal for [plan]. Nothing visited yet → exactly the web's (stop 1 fixed). Mid-trip, the stops already
     * visited / skipped are history: everything up to the last of them stays where it is, that stop becomes the start, and
     * only the planned stops after it are reordered (the car does not send the driver back in time).
     * null = fewer than 2 movable stops with coordinates after the start, or the start has no coordinates.
     */
    data class Proposal(val planId: String, val version: Int, val before: List<PlanStop>, val after: List<PlanStop>,
                        val beforeM: Double, val afterM: Double, val changed: Boolean, val skipped: Int, val fixed: Int) {
        val order: List<String> get() = after.map { it.id }
        val savedM: Double get() = beforeM - afterM
    }

    fun proposal(plan: Plan): Proposal? {
        val ordered = PlanLogic.ordered(plan)
        val anchor = ordered.indexOfLast { it.visitStatus != PlanLogic.PLANNED }.coerceAtLeast(0)
        val seg = ordered.subList(anchor, ordered.size)
        if (seg.count { ll(it) != null } < 3) return null
        val r = optimizeStops(seg, ::ll) ?: return null
        val after = ordered.subList(0, anchor) + r.items
        return Proposal(plan.id, plan.version, ordered, after, r.beforeM, r.afterM, r.changed, r.skipped, anchor + 1)
    }

    /**
     * Straight-line distance still ahead: from the last visited / skipped stop (or stop 1) to the end, in order — the same span the
     * mid-trip proposal reorders. Nothing visited → the whole plan.
     */
    fun remainingM(plan: Plan): Total {
        val ordered = PlanLogic.ordered(plan)
        val anchor = ordered.indexOfLast { it.visitStatus != PlanLogic.PLANNED }.coerceAtLeast(0)
        return totalM(ordered.subList(anchor, ordered.size).map(::ll))
    }

    /**
     * Pins that would cover each other (centres closer than [minDist] px) are pushed apart along the line between them, a few rounds,
     * so every number stays readable; the true point keeps a small dot and a leader line (drawn by the map). Null stays null.
     * Deterministic (no randomness): two pins on the same point split horizontally, lower number to the left.
     */
    fun spread(points: List<Pair<Float, Float>?>, minDist: Float, rounds: Int = 40): List<Pair<Float, Float>?> {
        val xs = points.map { it?.first }.toMutableList()
        val ys = points.map { it?.second }.toMutableList()
        repeat(rounds) {
            var moved = false
            for (i in points.indices) for (j in i + 1 until points.size) {
                val xi = xs[i] ?: continue; val yi = ys[i]!!; val xj = xs[j] ?: continue; val yj = ys[j]!!
                var dx = xj - xi; var dy = yj - yi
                var d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d >= minDist) continue
                if (d < 0.01f) { dx = 1f; dy = 0f; d = 1f }
                val push = (minDist - d) / 2f + 0.5f
                val ux = dx / d; val uy = dy / d
                xs[i] = xi - ux * push; ys[i] = yi - uy * push
                xs[j] = xj + ux * push; ys[j] = yj + uy * push
                moved = true
            }
            if (!moved) return points.indices.map { k -> xs[k]?.let { it to ys[k]!! } }
        }
        return points.indices.map { k -> xs[k]?.let { it to ys[k]!! } }
    }

    /** Straight-line total of the plan in its order (the line under the map). */
    fun planTotalM(plan: Plan): Total = totalM(PlanLogic.ordered(plan).map(::ll))

    /**
     * Points → box coordinates: equirectangular (lng × cos lat), one scale for both axes (no stretching), centred, [pad] on every
     * side. One point / all the same → the centre. Null in = null out.
     */
    fun fit(points: List<LL?>, w: Float, h: Float, pad: Float): List<Pair<Float, Float>?> {
        val xs = points.filterNotNull()
        if (xs.isEmpty()) return points.map { null }
        val k = cos(rad(xs.sumOf { it.lat } / xs.size))
        val x0 = xs.minOf { it.lng * k }; val x1 = xs.maxOf { it.lng * k }
        val y0 = xs.minOf { it.lat }; val y1 = xs.maxOf { it.lat }
        val iw = (w - 2 * pad).coerceAtLeast(1f); val ih = (h - 2 * pad).coerceAtLeast(1f)
        val sx = if (x1 - x0 > 1e-12) iw / (x1 - x0) else Double.POSITIVE_INFINITY
        val sy = if (y1 - y0 > 1e-12) ih / (y1 - y0) else Double.POSITIVE_INFINITY
        val s = min(sx, sy).let { if (it.isInfinite()) 0.0 else it }
        val ox = pad + (iw - (x1 - x0) * s) / 2; val oy = pad + (ih - (y1 - y0) * s) / 2
        return points.map { p -> p?.let { ((ox + (it.lng * k - x0) * s).toFloat()) to ((oy + (y1 - it.lat) * s).toFloat()) } }
    }
}
