// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/OpponentThinkingOverlay.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/OpponentThinkingOverlay.kt
package com.battleship.fleetcommand.feature.game.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.battleship.fleetcommand.core.ui.theme.WaterDeep

/** Shimmer effect overlaid on the grid when it's the opponent's turn. Section 9.3. */
@Composable
fun OpponentThinkingOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "shimmerX",
    )
    Canvas(modifier = modifier) {
        val width = size.width
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, WaterDeep.copy(alpha = 0.7f), Color.Transparent),
                startX = shimmerX * width,
                endX = (shimmerX + 0.5f) * width,
            ),
        )
    }
}