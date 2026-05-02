// build-logic/convention/src/main/kotlin/BattleshipAndroidFeatureConventionPlugin.kt

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class BattleshipAndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("com.google.dagger.hilt.android")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = platform("androidx.compose:compose-bom:2024.12.01")
                "implementation"(bom)
                "implementation"("androidx.compose.ui:ui")
                "implementation"("androidx.compose.material3:material3")
                "implementation"("androidx.compose.ui:ui-tooling-preview")
                "implementation"("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
                "implementation"("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
                "implementation"("androidx.hilt:hilt-navigation-compose:1.2.0")
                "implementation"("com.google.dagger:hilt-android:2.53")
                "ksp"("com.google.dagger:hilt-android-compiler:2.53")

                // Testing
                "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.11.4")
                "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.11.4")
                "testImplementation"("io.mockk:mockk:1.13.13")
                "testImplementation"("app.cash.turbine:turbine:1.2.0")
                "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

                "debugImplementation"("androidx.compose.ui:ui-tooling")
                "debugImplementation"("androidx.compose.ui:ui-test-manifest")
            }
        }
    }
}