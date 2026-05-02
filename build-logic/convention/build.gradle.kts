// build-logic/convention/build.gradle.kts

plugins {
    `kotlin-dsl`
}

group = "com.battleship.fleetcommand.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.plugins.android.application.map { plugin ->
        "${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:${plugin.version}"
    }.get().let { "$it" })

    // Use direct artifact coordinates — cleaner than the map trick above
    compileOnly("com.android.tools.build:gradle:8.5.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:2.53")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.21-1.0.28")
}

gradlePlugin {
    plugins {
        // battleship.android.application
        register("battleshipAndroidApplication") {
            id = "battleship.android.application"
            implementationClass = "BattleshipAndroidApplicationConventionPlugin"
        }
        // battleship.android.feature
        register("battleshipAndroidFeature") {
            id = "battleship.android.feature"
            implementationClass = "BattleshipAndroidFeatureConventionPlugin"
        }
        // battleship.android.library.compose
        register("battleshipAndroidLibraryCompose") {
            id = "battleship.android.library.compose"
            implementationClass = "BattleshipAndroidLibraryComposeConventionPlugin"
        }
        // battleship.android.library
        register("battleshipAndroidLibrary") {
            id = "battleship.android.library"
            implementationClass = "BattleshipAndroidLibraryConventionPlugin"
        }
        // battleship.kotlin.library
        register("battleshipKotlinLibrary") {
            id = "battleship.kotlin.library"
            implementationClass = "BattleshipKotlinLibraryConventionPlugin"
        }
    }
}