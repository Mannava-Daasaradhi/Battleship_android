// FILE: app/build.gradle.kts
// Section 18 (R8 full mode, ABI splits) + Section 20 (release signing config)
plugins {
    id("battleship.android.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baseline.profile)
}

android {
    namespace = "com.battleship.fleetcommand"

    defaultConfig {
        applicationId = "com.battleship.fleetcommand"
        versionCode = 1
        versionName = "1.0.0"
    }

    // Section 18 — ABI Splits (covers 99%+ of Android devices; smaller AAB per device)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        // Release signing — credentials injected via GitHub Secrets or local keystore.properties
        // NEVER commit the keystore file or passwords to the repository (Section 20).
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Section 18 — R8 full mode for maximum dead-code elimination
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // applicationIdSuffix removed: google-services.json only has the base
            // package name registered. Add a debug entry in Firebase Console and
            // re-download google-services.json before re-enabling this.
            isDebuggable = true
        }
    }
}

// Section 18 — R8 full mode (in gradle.properties or here via Android Gradle Plugin flag)
// Already set via gradle.properties: android.enableR8.fullMode=true
// Kept here as documentation — the gradle.properties entry is authoritative.

dependencies {
    // Feature modules
    implementation(project(":feature:menu"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:game"))
    implementation(project(":feature:lobby"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))

    // Core modules — explicit so Hilt/KSP can resolve DI provider return types
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:multiplayer"))
    implementation(project(":core:ui"))
    implementation(project(":core:analytics"))
    // :core:ads intentionally excluded — owner will integrate AdMob in a future update
    // ADS PLACEHOLDER — see Section 14; add :core:ads here when ready

    // DataStore
    implementation(libs.datastore.preferences)

    // Room runtime
    implementation(libs.room.runtime)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Navigation + Activity
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)

    // Hilt navigation
    implementation(libs.hilt.navigation.compose)

    // Serialization (required for @Serializable routes)
    implementation(libs.kotlinx.serialization.json)

    // Hilt root
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Logging + leak detection (debug only)
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)

    // Play Games (leaderboards/achievements — no ads involved)
    implementation(libs.play.services.games.v2)

    testImplementation(project(":core:testing"))
}