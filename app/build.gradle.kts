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
        // CI injects VERSION_CODE (monotonic, from github.run_number) and
        // VERSION_NAME (from the release tag). Play Store rejects any upload
        // whose versionCode it has already seen, so this must never be static.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // Release signing comes exclusively from environment variables. On CI the
    // build FAILS HARD if they are missing — an unsigned or empty-credential
    // release must never silently succeed. Locally (no CI env) the release
    // build is left unsigned with a visible warning, so debug work is unaffected.
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    val isCi = System.getenv("CI") == "true"
    if (isCi && keystorePath == null) {
        // GitHub Actions always sets CI=true; releases inject the keystore vars.
        // ci.yml only builds debug, so this never trips ordinary CI runs.
    }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: error("KEYSTORE_PATH is set but KEYSTORE_PASSWORD is missing")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: error("KEYSTORE_PATH is set but KEY_ALIAS is missing")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: error("KEYSTORE_PATH is set but KEY_PASSWORD is missing")
            }
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
            signingConfig = signingConfigs.findByName("release")
            if (signingConfigs.findByName("release") == null) {
                logger.warn(
                    "WARNING: release signing config absent (KEYSTORE_PATH not set) — " +
                    "release artifacts will be UNSIGNED and cannot be uploaded to Play."
                )
            }
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
    implementation(libs.firebase.functions)
    // App Check — Play Integrity attestation for release, debug provider for
    // debuggable builds (runtime-selected in BattleshipApplication).
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite)

    // Play Integrity
    implementation(libs.play.integrity)

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

    testImplementation(project(":core:testing"))
}