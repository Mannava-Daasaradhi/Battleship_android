// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/animation/MissSplashAnimation.kt
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
fun MissSplashAnimation(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val animatable = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
        )
        onComplete()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val progress = animatable.value
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2f
        
        // Water splash ring 1
        drawCircle(
            color = Color(0xFF4FC3F7).copy(alpha = 1f - progress),
            radius = maxRadius * progress * 0.6f,
            center = center,
            style = Stroke(width = 16f * (1f - progress))
        )
        // Water splash ring 2
        drawCircle(
            color = Color(0xFFE1F5FE).copy(alpha = 1f - progress),
            radius = maxRadius * progress,
            center = center,
            style = Stroke(width = 8f * (1f - progress))
        )
    }
}