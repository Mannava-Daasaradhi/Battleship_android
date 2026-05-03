// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/modifier/PressScaleModifier.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/modifier/PressScaleModifier.kt
package com.battleship.fleetcommand.core.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/** Reusable scale-on-press modifier. Applied to all interactive buttons per Section 9. */
fun Modifier.pressScaleModifier(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { isPressed = true; tryAwaitRelease(); isPressed = false }
            )
        }
}
