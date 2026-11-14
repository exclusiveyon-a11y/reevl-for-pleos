package kr.co.reevl.ontheway.area

import kr.co.reevl.ontheway.data.AreaResponse
import kr.co.reevl.ontheway.data.PlanRepository
import kr.co.reevl.ontheway.data.RepoResult

/**
 * Where 동네 comes from — one seam so the source can switch without touching screens or voice (docs/design/pleos-signals-and-cost.md,
 * area-context-car-spec §2.5). Today: the server (`GET /api/car/area`). Next (P1): [hint] = NaviHelper `CurrentLocationInfo.address`
 * (`수정구 시흥동`, seen on the AVD during guidance) + a daily, versioned, CDN-cached 동 pack computed on the device, the server
 * kept for news/insights only.
 */
interface AreaSource {
    suspend fun area(lat: Double, lng: Double, headingDeg: Double?, hint: String? = null): RepoResult<AreaResponse>
}

/** The current source: one server request per call (the caller enforces the request budget and [AreaCache]). */
class ServerAreaSource(private val repo: PlanRepository) : AreaSource {
    override suspend fun area(lat: Double, lng: Double, headingDeg: Double?, hint: String?) = repo.area(lat, lng, headingDeg)
}
