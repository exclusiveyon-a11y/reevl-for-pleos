// Toolchain (2026-10-26): Gradle 9.3.1 wrapper on Android Studio's bundled JBR 25,
// AGP 9.1.1 with built-in Kotlin (no org.jetbrains.kotlin.android plugin), Kotlin/Compose compiler 2.3.20.
// Gradle 8.x cannot run on JDK 25, which is why this is not the older AGP 8.x line.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}
