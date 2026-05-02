plugins {
    id("battleship.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.battleship.fleetcommand.core.data"
    defaultConfig {
        // Room schema export path — checked into version control
        ksp { arg("room.schemaLocation", "$projectDir/../app/schemas") }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.room.testing)
    testImplementation(project(":core:testing"))
}