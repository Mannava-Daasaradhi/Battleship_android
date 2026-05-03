// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/handoff/HandOffScreen.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/handoff/HandOffScreen.kt
package com.battleship.fleetcommand.feature.game.handoff

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import kotlinx.coroutines.flow.collectLatest

/**
 * HandOffScreen — CANNOT be skipped. Full opaque overlay. 3-second mandatory countdown.
 * Back gesture completely disabled. Screen orientation locked. Section 8.4, Section 12.
 */
@Composable
fun HandOffScreen(
    navController: NavController,
    viewModel: HandOffViewModel,
    route: com.battleship.fleetcommand.navigation.HandOffRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Section 8.4: Back gesture completely disabled during hand-off
    BackHandler(enabled = true) { /* no-op — cannot navigate back during hand-off */ }

    // Section 8.4: Lock orientation during hand-off
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                HandOffViewModel.UiEffect.NavigateToNextScreen -> navController.popBackStack()
            }
        }
    }

    // Full opaque background — no see-through allowed
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = "⚓",
                style = MaterialTheme.typography.displayLarge,
            )

            Text(
                text = "HAND OFF DEVICE",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Pass the device to ${uiState.toPlayer}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            // Section 9 countdown animation — key(countdown) resets scale each tick
            key(uiState.countdown) {
                CountdownNumber(countdown = uiState.countdown)
            }

            Button(
                onClick = { viewModel.onEvent(HandOffViewModel.UiEvent.Proceed) },
                enabled = uiState.canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp),
            ) {
                Text(
                    text = if (uiState.countdown > 0) uiState.countdown.toString() else "TAP TO START",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CountdownNumber(countdown: Int) {
    var triggered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (triggered) 1.0f else 1.5f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "countdownScale",
    )
    LaunchedEffect(Unit) { triggered = true }

    Text(
        text = if (countdown > 0) countdown.toString() else "GO!",
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}