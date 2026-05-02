plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.lobby" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:multiplayer"))
    testImplementation(project(":core:testing"))
}