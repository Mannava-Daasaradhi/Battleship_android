// FILE: app/build.gradle.kts
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // applicationIdSuffix removed: google-services.json only has the base
            // package name registered. Add a debug entry in Firebase Console and
            // re-download google-services.json before re-enabling this.
            isDebuggable = true
        }
    }
}

dependencies {
    // Feature modules
    implementation(project(":feature:menu"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:game"))
    implementation(project(":feature:lobby"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))

    // Core modules visible to :app
    implementation(project(":core:ui"))
    implementation(project(":core:analytics"))
    // :core:ads intentionally excluded until ads phase

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Navigation + Activity
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)

    // Serialization — required for @Serializable routes in Routes.kt
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