// Shared test utilities — never included in production source sets
plugins { id("battleship.android.library") }

android { namespace = "com.battleship.fleetcommand.core.testing" }

dependencies {
    // Can see all modules but only via test/androidTest configurations
    api(project(":core:domain"))
    api(libs.coroutines.test)
    api(libs.junit5.api)
    api(libs.turbine)
    api(libs.mockk)
    implementation(libs.junit5.engine)
}