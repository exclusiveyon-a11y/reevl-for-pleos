plugins {
    id("com.android.application") // AGP 9: built-in Kotlin compiles src/*/java and src/*/kotlin
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "kr.co.reevl.ontheway"
    compileSdk = 34
    // D4 item 6: CarUxRestrictionsManager (public AAOS Car API, the SDK's optional `android.car` library).
    useLibrary("android.car")

    defaultConfig {
        applicationId = "kr.co.reevl.ontheway"
        minSdk = 28 // Pleos: minSdk >= 28 (signing key rotation / lineage requirement)
        targetSdk = 34
        versionCode = 2
        versionName = "0.3.0" // = assets/schema.json "version" (Gleo)
        // 고객 투어 모드 UI (Kia PBV tour build). The Pleos personal build hides it; `./gradlew … -PtourMode=true` brings
        // it back to reproduce the v7 tour evidence. Code is kept either way.
        // MapLibre ships libmaplibre.so for 4 ABIs (~41 MB stored together). Two are packaged: arm64-v8a (Pleos Connect cars and
        // the official arm64 emulator, ro.product.cpu.abilist = arm64-v8a) and x86_64 (the x86 PC emulator a reviewer may use,
        // 11-11 사용자). armeabi-v7a / x86 are dropped.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        buildConfigField("boolean", "TOUR_MODE", (project.findProperty("tourMode")?.toString() == "true").toString())
        // Gleo (App Schema) UI: hints, "글레오에게 말해 보세요", the lock's Gleo line. ON by default since 2026-11-14: the contest
        // office (운영사무국 메일 2026-11-14, notice '공모전 개발 범위 및 기술 문의 안내') says contest teams may use the Gleo
        // Interface without a separate review or approval. A Pleos App Market release still goes through deliver-schema review.
        // Turn off with `-PgleoEnabled=false`.
        buildConfigField("boolean", "GLEO_ENABLED", (project.findProperty("gleoEnabled")?.toString() != "false").toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release signing: the upload key lives OUTSIDE the repo (~/.reevl-pleos-keys/). Paths come from ~/.gradle/gradle.properties
    // (reevlPleosStoreFile / reevlPleosPasswordFile / reevlPleosKeyAlias) or the same names as environment variables.
    // Without them a release build is simply unsigned — nothing secret is ever read from the repository.
    fun signingProp(name: String): String? = (project.findProperty(name) as String?) ?: System.getenv(name)
    val storePath = signingProp("reevlPleosStoreFile")
    val passwordPath = signingProp("reevlPleosPasswordFile")
    signingConfigs {
        if (storePath != null && passwordPath != null && file(storePath).exists() && file(passwordPath).exists()) {
            create("pleosUpload") {
                val pw = file(passwordPath).readText().trim()
                storeFile = file(storePath)
                storePassword = pw
                keyAlias = signingProp("reevlPleosKeyAlias") ?: "reevl-pleos-upload"
                keyPassword = pw
                enableV1Signing = false // minSdk 28: v2 + v3 (v3 = key rotation / lineage, Pleos asks for API 28+ for it)
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Contest dev server port (controller ruling 3). 10.0.2.2 = host loopback from the emulator.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3300\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://reevl.co.kr\"")
            // Stability first: no R8 (kotlinx.serialization + Compose + the Pleos SDK would each need keep rules to verify).
            isMinifyEnabled = false
            signingConfigs.findByName("pleosUpload")?.let { signingConfig = it }
        }
    }

    flavorDimensions += "navi"
    productFlavors {
        create("pleos") {
            dimension = "navi"
            buildConfigField("String", "NAVI_ADAPTER", "\"pleos\"")
            // No in-app speech on Pleos: the car's only RecognitionService is Gleo's private one (exported=false), so RECORD_AUDIO
            // is not declared (src/pleos/AndroidManifest.xml removes it) and the 말하기 path never asks for it.
            buildConfigField("boolean", "VOICE_INPUT", "false")
        }
        create("mock") {
            dimension = "navi"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("String", "NAVI_ADAPTER", "\"mock\"")
            buildConfigField("boolean", "VOICE_INPUT", "true")
        }
    }

    compileOptions {
        // Kotlin jvmTarget follows targetCompatibility under AGP 9 built-in Kotlin.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Pleos Connect NaviHelper — only the real-SDK flavor links it, so the mock build can never call the SDK.
    "pleosImplementation"("ai.pleos.playground:NaviHelper:3.0.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Reevl car API (/api/car/*): HTTP + JSON.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // QR 연결: the link QR is drawn on the device (Apache-2.0, pure Java, no Android/camera parts) — no network QR service.
    implementation("com.google.zxing:core:3.5.3")

    // 동선 지도 바탕 (2026-11-11): MapLibre Native, OpenGL ES build (the default `android-sdk` artifact is the Vulkan renderer,
    // which the Pleos emulator image does not guarantee). BSD-2; tiles = OpenFreeMap (keyless, free) — same source as web /plan.
    // 13.7.1 = 2026-10-15 (≥ 2 weeks old at adoption).
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
