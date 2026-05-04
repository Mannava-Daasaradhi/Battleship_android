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

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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

    // Core modules
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:multiplayer"))
    implementation(project(":core:ui"))
    implementation(project(":core:analytics"))
    // :core:ads intentionally excluded — owner will integrate AdMob in a future update

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

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Hilt root
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Logging
    implementation(libs.timber)
    // LeakCanary removed — installs unwanted companion "Leaks" app on device.
    // Re-add only when actively debugging memory leaks locally.

    // Play Games
    implementation(libs.play.services.games.v2)

    testImplementation(project(":core:testing"))
}