// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/ShipSunkAnimation.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/ShipSunkAnimation.kt
package com.battleship.fleetcommand.core.ui.animation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lottie fire-smoke loop + camera shake spring for ship sunk. Section 9.3. */
@Composable
fun ShipSunkAnimation(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    // TODO Phase 5: LottieAnimation + spring camera shake, 1200ms
    onComplete()
}