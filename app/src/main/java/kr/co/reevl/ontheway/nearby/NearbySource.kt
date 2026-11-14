package kr.co.reevl.ontheway.nearby

import kotlin.math.cos
import kotlin.math.floor
import kr.co.reevl.ontheway.data.NearbyComplexes
import kr.co.reevl.ontheway.data.PlanRepository
import kr.co.reevl.ontheway.data.RepoResult

/**
 * Where nearby complexes come from — one seam (pleos-signals-and-cost.md §3: an on-device index `LocalIndex` slots in here later,
 * once the pipeline ships it; not in scope now). Today: the server, `GET /api/car/complexes/nearby`.
 */
interface NearbySource {
    suspend fun complexes(lat: Double, lng: Double, radiusM: Int): RepoResult<NearbyComplexes>
}

class ServerNearbySource(private val repo: PlanRepository) : NearbySource {
    override suspend fun complexes(lat: Double, lng: Double, radiusM: Int) = repo.nearbyComplexes(lat, lng, radiusM)
}

/**
 * Request budget for 주변 (driving-context-spec §2.2): one answer per ~100 m grid cell is reused for [ttlMs] (10 min); a forced
 * refresh (⟳, unlock reload) of the same cell within [debounceMs] (10 s) also reuses it. Memory only. Pure apart from [source].
 */
class CachedNearbySource(
    private val source: NearbySource,
    private val ttlMs: Long = 10 * 60_000L,
    private val debounceMs: Long = 10_000L,
    private val max: Int = 32,
) {
    data class Answer(val result: RepoResult<NearbyComplexes>, val cached: Boolean, val cell: String)

    private val map = LinkedHashMap<String, Pair<NearbyComplexes, Long>>()

    /** Returns a cached answer when allowed, else asks [source] once. [force] = a user/unlock refresh. */
    suspend fun get(lat: Double, lng: Double, radiusM: Int, now: Long, force: Boolean = false): Answer {
        val cell = cellOf(lat, lng) + "@" + radiusM
        cached(cell, now, force)?.let { return Answer(RepoResult.Ok(it), cached = true, cell = cell) }
        val r = source.complexes(lat, lng, radiusM)
        if (r is RepoResult.Ok) put(cell, r.value, now)
        return Answer(r, cached = false, cell = cell)
    }

    fun cached(cell: String, now: Long, force: Boolean): NearbyComplexes? {
        val (v, at) = map[cell] ?: return null
        val age = now - at
        return when {
            age > ttlMs -> { map.remove(cell); null }
            force && age > debounceMs -> null
            else -> v
        }
    }

    fun put(cell: String, v: NearbyComplexes, now: Long) {
        map.remove(cell); map[cell] = v to now
        while (map.size > max) map.remove(map.keys.first())
    }

    fun clear() = map.clear()

    companion object {
        const val CELL_M = 100.0

        /** ~100 m square cell key (`i:j`), longitude step widened by 1/cos(lat). */
        fun cellOf(lat: Double, lng: Double): String {
            val dLat = CELL_M / 110_574.0
            val dLng = CELL_M / (111_320.0 * cos(Math.toRadians(lat)))
            return "${floor(lat / dLat).toLong()}:${floor(lng / dLng).toLong()}"
        }
    }
}
