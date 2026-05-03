// build-logic/convention/src/main/kotlin/BattleshipKotlinLibraryConventionPlugin.kt
// Pure Kotlin JVM plugin — NO Android runtime. Used by :core:domain and :core:ai.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class BattleshipKotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
            }

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            configureKotlin()

            dependencies {
                // JUnit 5 for pure Kotlin modules
                "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.11.4")
                "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.11.4")
                "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.11.4")
                "testImplementation"("io.mockk:mockk:1.13.13")
                "testImplementation"("app.cash.turbine:turbine:1.2.0")
                "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}