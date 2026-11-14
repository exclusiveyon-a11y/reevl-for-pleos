package kr.co.reevl.ontheway

/** Build-time switches the UI reads (one place, so a screen never decides on its own). */
object Features {
    /**
     * Gleo voice integration shown in the UI. true by default since 2026-11-14 — the contest office lets contest teams use
     * the Gleo Interface without review or approval. Gradle `-PgleoEnabled=false` turns it off (app/build.gradle.kts).
     */
    val GLEO: Boolean = BuildConfig.GLEO_ENABLED

    /** In-app speech recognition may be offered (mock flavor only; Pleos has no recogniser a third-party app may bind). */
    val VOICE_INPUT: Boolean = BuildConfig.VOICE_INPUT
}
