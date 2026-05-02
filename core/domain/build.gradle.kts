// Pure Kotlin — zero Android runtime (battleship.kotlin.library)
plugins {
    id("battleship.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
}