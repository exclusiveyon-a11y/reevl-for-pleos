package kr.co.reevl.ontheway.data

/**
 * THE one place where Reevl coordinates are converted.
 *
 * Reevl coords files (`web/data/coords/<sigungu>.json`) map `cid -> [lon, lat]` — LONGITUDE FIRST.
 * Pleos NaviHelper 3.0.5 `RouteInfo` takes `(latitude, longitude)`. Never index a Reevl pair anywhere else.
 */
object ReevlCoords {
    data class LatLng(val lat: Double, val lng: Double)

    /** @param lonLat a Reevl `[lon, lat]` pair exactly as stored in the coords file. */
    fun fromReevlPair(lonLat: DoubleArray): LatLng {
        require(lonLat.size == 2) { "Reevl coords pair must be [lon, lat], got size ${lonLat.size}" }
        val lon = lonLat[0]
        val lat = lonLat[1]
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "out of range: lon=$lon lat=$lat" }
        return LatLng(lat = lat, lng = lon)
    }
}
