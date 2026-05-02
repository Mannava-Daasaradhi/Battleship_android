plugins { id("battleship.android.library") }

android { namespace = "com.battleship.fleetcommand.core.analytics" }

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.timber)
    // Hilt added when DI is wired in Phase 3
}