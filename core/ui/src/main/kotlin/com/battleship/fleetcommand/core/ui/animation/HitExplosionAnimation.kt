// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/HitExplosionAnimation.kt
package com.battleship.fleetcommand.core.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun HitExplosionAnimation(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val animatable = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        onComplete()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val progress = animatable.value
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2f
        
        // Expanding inner core
        drawCircle(
            color = Color(0xFFFF5722).copy(alpha = 1f - progress),
            radius = maxRadius * progress * 0.8f,
            center = center
        )
        
        // Expanding outer shockwave ring
        drawCircle(
            color = Color(0xFFFFC107).copy(alpha = 1f - progress),
            radius = maxRadius * progress * 1.2f,
            center = center,
            style = Stroke(width = 12f * (1f - progress))
        )
    }
}