plugins { id("battleship.android.library.compose") }

android { namespace = "com.battleship.fleetcommand.core.ui" }

dependencies {
    implementation(project(":core:domain"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.material3.adaptive)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    androidTestImplementation(libs.compose.ui.test.junit4)
}