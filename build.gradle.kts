plugins {
    id("com.android.application") version "9.4.1" apply false
    // Not applied to any module: Android Gradle Plugin 9 has Kotlin built in.
    // Declaring it here only pins the Kotlin compiler version, which must be
    // new enough to read GeckoView (built with Kotlin 2.4).
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
