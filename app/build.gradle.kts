import java.io.File

plugins {
    // Kotlin support is built into Android Gradle Plugin 9; no Kotlin plugin here.
    id("com.android.application")
}

// Add `wishy.includeX86=true` to gradle.properties (or pass -Pwishy.includeX86=true)
// to also build for x86_64 emulators. Real TVs and streaming sticks are ARM.
val includeX86 = providers.gradleProperty("wishy.includeX86").orNull == "true"

android {
    namespace = "org.wishy.browser"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.wishy.browser"
        minSdk = 26      // GeckoView 144+ requires Android 8.0
        targetSdk = 36
        versionCode = 4
        versionName = "2.0.0"

        // One universal APK: 32-bit ARM (most sticks and cheap boxes) and
        // 64-bit ARM (Shield, Google TV, newer TVs). Mozilla no longer ships
        // 32-bit x86.
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            if (includeX86) abiFilters.add("x86_64")
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
        viewBinding = true
    }

    packaging {
        jniLibs {
            // GeckoView's libxul.so is ~50 MB per CPU type. Stored compressed
            // the APK file is roughly half the size, which matters when
            // sideloading to a TV.
            useLegacyPackaging = true
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

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")
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
