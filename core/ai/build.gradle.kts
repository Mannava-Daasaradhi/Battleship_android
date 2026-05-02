// Pure Kotlin — zero Android runtime
plugins { id("battleship.kotlin.library") }

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.coroutines.core)
}