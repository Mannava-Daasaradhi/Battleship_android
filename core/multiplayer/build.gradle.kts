plugins { id("battleship.android.library") }

android { namespace = "com.battleship.fleetcommand.core.multiplayer" }

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Hilt added in Phase 3 when DI is wired
}