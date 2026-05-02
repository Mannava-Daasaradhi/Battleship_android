plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.game" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:ai"))
    implementation(project(":core:multiplayer"))
    testImplementation(project(":core:testing"))
}