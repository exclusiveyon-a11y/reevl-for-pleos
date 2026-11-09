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
        versionCode = 1
        versionName = "0.2.0-p0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // Contest dev server port (controller ruling 3). 10.0.2.2 = host loopback from the emulator.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3300\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://reevl.co.kr\"")
            isMinifyEnabled = false
        }
    }

    flavorDimensions += "navi"
    productFlavors {
        create("pleos") {
            dimension = "navi"
            buildConfigField("String", "NAVI_ADAPTER", "\"pleos\"")
        }
        create("mock") {
            dimension = "navi"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("String", "NAVI_ADAPTER", "\"mock\"")
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
