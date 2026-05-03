plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.game" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:ai"))
    implementation(project(":core:multiplayer"))
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(project(":core:testing"))
}