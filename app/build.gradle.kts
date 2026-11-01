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
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "4.0.0"
        // The browser engine is the TV's own WebView, so the app has no native libraries and a
        // single APK runs on every CPU type (no ABI flavours needed any more).
    }

    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("WISHY_KEYSTORE").orNull
                ?: "/home/denji/Documents/WishyBrowser_Keystore/wishy.jks"
            val storeFileObj = File(storePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = providers.gradleProperty("WISHY_KEYSTORE_PASSWORD").orNull ?: "wishisashi13"
                keyAlias = providers.gradleProperty("WISHY_KEY_ALIAS").orNull ?: "wishy"
                keyPassword = providers.gradleProperty("WISHY_KEY_PASSWORD").orNull ?: "wishisashi13"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/kotlin/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Keeps proprietary Google Play Services out of the APK should any library ever pull it in.
configurations.configureEach {
    exclude(group = "com.google.android.gms")
}

dependencies {
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
        )
    }
}

// After building an APK, put a copy in <project>/dist/ so it is easy to find, and print its size.
val apkDir = layout.buildDirectory.dir("outputs/apk")
val distDir = rootProject.layout.projectDirectory.dir("dist")
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    doLast {
        val out = distDir.asFile
        out.mkdirs()
        apkDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "apk" }.forEach {
            it.copyTo(File(out, it.name), overwrite = true)
            println("APK: ${it.name} = ${"%.2f".format(it.length() / 1048576.0)} MB")
        }
    }
}
