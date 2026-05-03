plugins { id("battleship.android.feature") }

android { namespace = "com.battleship.fleetcommand.feature.settings" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    testImplementation(project(":core:testing"))
}