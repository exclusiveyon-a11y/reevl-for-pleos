package kr.co.reevl.ontheway.legal

/**
 * 개인정보·위치 안내 (first run, parked) and the open-source notice — one place for every URL, version and line.
 *
 * Sources: sdk-docs-2026-10-19/pleos-only_distribute_app-review-guide_review-user-privacy-security.txt — 투명성 (항목·사용 방식·
 * 공유를 "앱 자체에서 확실하게 명시"), 동의 (동의 버튼 외의 행동은 동의 아님, 동의/거부가 명확), 철회 (취소 시 경로를 끊고 저장 데이터 삭제);
 * …vehicle-app-planning-guide_security-privacy.txt (최소 수집).
 */
object Consent {
    /** Bump when the notice changes in substance → everyone is asked again. */
    const val VERSION = 1

    /** REEVL's privacy policy, car section (계획·차 연결) — the QR (web: reevl/frontend/app/privacy, anchor #car). */
    const val PRIVACY_URL = "https://reevl.co.kr/privacy#car"
    const val PRIVACY_TEXT = "reevl.co.kr/privacy#car"
    /** 차 위치 section of the same policy (how the car's position is used and kept). */
    const val LOCATION_URL = "https://reevl.co.kr/privacy#car-location"
    const val LOCATION_TEXT = "차 위치 · reevl.co.kr/privacy#car-location"

    /**
     * 위치기반서비스 이용약관 — PLACEHOLDER: no page yet (2026-11-11). null = hidden everywhere. Fill with the real URL and it
     * appears as one more line under the policy link in the notice (ui/screens/LegalScreens.kt).
     */
    val LOCATION_TERMS_URL: String? = null

    const val TITLE = "시작 전에 알려 드려요"

    /** Three rows (car-ds: primary + ≤ 2-line secondary): what · why · how long, and who else receives it. */
    data class Item(val title: String, val body: String)
    val ITEMS = listOf(
        Item("위치·차 속도", "앱을 쓰는 동안만 · 주변 단지 찾기와 도착 확인에 써요"),
        Item("계정 연결", "연결 토큰, 휴대폰의 계획·저장 단지 · 연결을 끊으면 지워요"),
        Item("보내는 곳", "리블 서버(Cloudflare)·지도(OpenFreeMap)에 접속 IP가 가요"),
    )
    const val ACCEPT = "동의하고 시작"
    const val EXIT = "나가기"
}

/** Open-source notice (설정 › 오픈소스 라이선스). From `./gradlew :app:dependencies --configuration pleosReleaseRuntimeClasspath`. */
object OssLicenses {
    data class Lib(val name: String, val license: String, val file: String)

    private const val APACHE = "Apache-2.0"
    val LIBS = listOf(
        Lib("MapLibre Native Android · Gestures", "BSD-2-Clause", "licenses/MapLibre-BSD-2-Clause.txt"),
        Lib("MapLibre Java (GeoJSON · Turf)", APACHE, "licenses/Apache-2.0.txt"),
        Lib("AndroidX · Jetpack Compose", APACHE, "licenses/Apache-2.0.txt"),
        Lib("Kotlin · kotlinx.coroutines · kotlinx.serialization", APACHE, "licenses/Apache-2.0.txt"),
        Lib("OkHttp · Okio", APACHE, "licenses/Apache-2.0.txt"),
        Lib("ZXing core", APACHE, "licenses/Apache-2.0.txt"),
        Lib("Gson · Timber · Guava ListenableFuture · JetBrains Annotations", APACHE, "licenses/Apache-2.0.txt"),
        Lib("Pretendard 글꼴", "SIL OFL 1.1", "licenses/Pretendard-OFL.txt"),
    )

    /** Map data: shown on the map itself too (PlanMapStyle.ATTRIBUTION). */
    const val MAP_DATA = "지도: OpenFreeMap © OpenMapTiles © OpenStreetMap contributors (ODbL)"
}
