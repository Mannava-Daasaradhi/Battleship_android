plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.setup" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(project(":core:testing"))
}