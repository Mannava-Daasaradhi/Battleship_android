// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/ShipSunkAnimation.kt
package com.battleship.fleetcommand.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Ship-sunk explosion — a self-contained, dependency-free Compose animation (Section 9.3).
 *
 * A fireball core flashes and expands while a shockwave ring races outward and fades, all over
 * ~1200ms, then [onComplete] fires so the caller can dismiss the overlay. Deliberately avoids a
 * Lottie asset so it has zero asset cost and never blocks on a missing raw resource.
 */
@Composable
fun ShipSunkAnimation(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
        onComplete()
    }

    Canvas(modifier = modifier.size(160.dp)) {
        val p = progress.value
        val maxRadius = size.minDimension / 2f

        // Shockwave ring — expands to full radius and thins out as it fades.
        val fade = (1f - p).coerceIn(0f, 1f)
        drawCircle(
            color = Color(0xFFFF7043).copy(alpha = fade),
            radius = maxRadius * p,
            style = Stroke(width = 6.dp.toPx() * fade + 1f),
        )

        // Fireball core — bright flash that grows slightly then burns out.
        val coreRadius = maxRadius * (0.20f + 0.55f * p)
        drawCircle(color = Color(0xFFFFCA28).copy(alpha = fade * 0.9f), radius = coreRadius)
        drawCircle(color = Color(0xFFFF5722).copy(alpha = fade * 0.7f), radius = coreRadius * 0.6f)
    }
}
