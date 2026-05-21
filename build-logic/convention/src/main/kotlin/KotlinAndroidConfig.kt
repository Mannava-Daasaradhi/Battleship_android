// build-logic/convention/src/main/kotlin/KotlinAndroidConfig.kt

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig {
            minSdk = 26
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            // Treat all Kotlin warnings as errors in CI
            allWarningsAsErrors = providers.gradleProperty("battleship.warningsAsErrors")
                .map(String::toBoolean)
                .getOrElse(false)
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

/**
 * Adds Compose-specific opt-ins to compiler args.
 * Must only be called from plugins that have Compose on the classpath
 * (BattleshipAndroidLibraryComposeConventionPlugin, Feature, Application).
 * Calling this from a non-Compose module causes "opt-in marker is unresolved" warnings.
 */
internal fun Project.configureKotlinCompose() {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            )
        }
    }
}