plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.menu" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    // :core:analytics added when event tracking is implemented
    testImplementation(project(":core:testing"))
}