import java.io.File

plugins {
    // Kotlin support is built into Android Gradle Plugin 9; no Kotlin plugin here.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.wishy.browser"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.wishy.browser"
        minSdk = 24          // system WebView needs no engine, so older TV boxes work too
        targetSdk = 36
        versionCode = 10
        versionName = "4.1.0"
        // English only: fewer strings and resources in the APK.
        androidResources.localeFilters += "en"
    }

    // ABI splits to produce 3 lightweight, architecture-optimized release APKs (ARM64, ARMv7, x86_64)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // Minified and shrunk release build for maximum stability and minimal size
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/kotlin/**",
                "DebugProbesKt.bin", "kotlin-tooling-metadata.json"
            )
        }
    }

    // Lint warnings must never stop a build.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Document-start scripts for the privacy layer (Apache-2.0).
    implementation("androidx.webkit:webkit:1.14.0")
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

// Copy finished APKs to <project>/output/ so they are easy to find and ignored by git.
val apkDir = layout.buildDirectory.dir("outputs/apk")
val outputDir = rootProject.layout.projectDirectory.dir("output")
val distDir = rootProject.layout.projectDirectory.dir("dist")
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    doLast {
        val out = outputDir.asFile
        out.mkdirs()
        distDir.asFile.mkdirs()
        apkDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "apk" }
            .forEach {
                it.copyTo(File(out, it.name), overwrite = true)
                it.copyTo(File(distDir.asFile, it.name), overwrite = true)
            }
    }
}
