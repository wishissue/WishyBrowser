import java.io.File

plugins {
    // Kotlin support is built into Android Gradle Plugin 9; no Kotlin plugin here.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Add `wishy.includeX86=true` to gradle.properties (or pass -Pwishy.includeX86=true)
// to also build for x86_64 emulators. Real TVs and streaming sticks are ARM.
val includeX86 = providers.gradleProperty("wishy.includeX86").orNull == "true"

// The uncompressed option (wishy.uncompressedLibs=true) stores native libraries
// uncompressed in the APK so they can be page-aligned and memory-mapped directly
// by Android 6.0+. This is experimental for GeckoView.
val uncompressedLibs = providers.gradleProperty("wishy.uncompressedLibs").orNull == "true"

android {
    namespace = "org.wishy.browser"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.wishy.browser"
        minSdk = 26      // GeckoView 144+ requires Android 8.0
        targetSdk = 36
        versionCode = 8
        versionName = "3.1.0"
    }

    // Three flavor options: arm32, arm64 and universal. Universal includes both
    // and is the easiest to sideload, but flavor-specific APKs are half the
    // size on disk. x86_64 is only for emulators and added to universal.
    flavorDimensions += "abi"
    productFlavors {
        create("arm32") {
            dimension = "abi"
            ndk {
                abiFilters.clear()
                abiFilters.add("armeabi-v7a")
            }
        }
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
            }
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
                if (includeX86) abiFilters.addAll(listOf("x86", "x86_64"))
            }
        }
    }

    // Every build type is shrunk and signed, so whichever one you build gives
    // a small APK you can install directly (an unsigned APK cannot be
    // installed at all). The local debug key is used; use your own keystore
    // if you ever publish through a store.
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // GeckoView's libxul.so is ~50 MB per CPU type. Stored compressed
            // the APK file is roughly half the size, which matters when
            // sideloading to a TV. Setting wishy.uncompressedLibs=true overrides this.
            useLegacyPackaging = !uncompressedLibs
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/kotlin/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    // Lint warnings must never stop a one-click build.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // No dependency-metadata blob inside the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Open source and privacy: GeckoView pulls in Google Play Services (a
// proprietary library) only for passkey / security-key login. The app leaves
// it out and turns WebAuthn off (see BrowserApplication), so nothing
// proprietary ships in the APK.
configurations.configureEach {
    exclude(group = "com.google.android.gms")
}

dependencies {
    // Mozilla GeckoView (Firefox's engine), MPL-2.0, from Mozilla's Maven repo.
    // Exact version pinned for reproducible builds. Firefox 155 release.
    implementation("org.mozilla.geckoview:geckoview:155.0.20260903215306")

    // Jetpack Compose + Material 3 Expressive: the exact versions Orion Store uses
    // with this same Android Gradle Plugin and Kotlin version.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "org.mozilla.geckoview.ExperimentalGeckoViewApi",
        )
    }
}

// After building an APK, put a copy in <project>/dist/ so it is easy to find.
val apkDir = layout.buildDirectory.dir("outputs/apk")
val distDir = rootProject.layout.projectDirectory.dir("dist")
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    doLast {
        val out = distDir.asFile
        out.mkdirs()
        apkDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .forEach { it.copyTo(File(out, it.name), overwrite = true) }
    }
}
