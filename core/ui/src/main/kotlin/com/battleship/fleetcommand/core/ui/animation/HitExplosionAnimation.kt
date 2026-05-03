// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/HitExplosionAnimation.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/HitExplosionAnimation.kt
package com.battleship.fleetcommand.core.ui.animation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lottie explosion animation for direct hits. Requires lottie/hit_explosion.json asset. Section 9.3. */
@Composable
fun HitExplosionAnimation(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    // TODO Phase 5: LottieAnimation(LottieCompositionSpec.Asset("lottie/hit_explosion.json"), iterations=1)
    onComplete()
}