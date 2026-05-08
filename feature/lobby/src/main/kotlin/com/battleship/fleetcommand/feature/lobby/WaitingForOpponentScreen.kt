// FILE: feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentScreen.kt

package com.battleship.fleetcommand.feature.lobby

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.battleship.fleetcommand.navigation.ShipPlacementRoute

private val NavyDeep         = Color(0xFF050E1A)
private val NavyMid          = Color(0xFF0A1930)
private val NavyAccent       = Color(0xFF1A4A8A)
private val GoldAccent       = Color(0xFFD4AF37)
private val TextWhite        = Color(0xFFF0F4FF)
private val TextMuted        = Color(0xFF8090B0)
private val ShimmerBase      = Color(0xFF112240)
private val ShimmerHighlight = Color(0xFF1E3A6E)

@Composable
fun WaitingForOpponentScreen(
    gameId: String,
    roomCode: String,                            // host's code to display; empty for guest
    navController: NavController,
    viewModel: WaitingForOpponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Initialise Firebase observer (idempotent — only runs once per gameId) ─
    LaunchedEffect(gameId) {
        viewModel.init(gameId)
    }

    // ── One-shot navigation via UiEffect channel ──────────────────────────────
    // Using a Channel-based effect (not LaunchedEffect(uiState.bothReady)) avoids
    // the double-navigation bug that occurred when Compose re-ran the LaunchedEffect
    // on recomposition while bothReady was still true after the first navigation.
    // Channel(BUFFERED) + receiveAsFlow() guarantees delivery exactly once.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WaitingUiEffect.NavigateToShipPlacement -> {
                    navController.navigate(ShipPlacementRoute(mode = "ONLINE", gameId = effect.gameId)) {
                        // Remove WaitingForOpponentScreen and OnlineLobbyScreen from
                        // back stack so pressing Back from ShipPlacement doesn't loop
                        // back into the lobby. Keep MainMenuRoute so the user can
                        // ultimately return to the main menu.
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDeep, NavyMid)))
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text          = "⚓  BATTLE STATION",
                color         = GoldAccent,
                fontSize      = 22.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Room code block — only shown to the host ──────────────────────
            if (roomCode.isNotBlank()) {
                Text(
                    text          = "ROOM CODE",
                    color         = TextMuted,
                    fontSize      = 11.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roomCode.forEach { char ->
                        Box(
                            modifier         = Modifier
                                .size(width = 42.dp, height = 52.dp)
                                .background(NavyAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = char.toString(),
                                color      = GoldAccent,
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text      = "Share this code with your opponent",
                    color     = TextMuted,
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Status area ───────────────────────────────────────────────────
            if (!uiState.opponentConnected) {
                Text(
                    text      = "Waiting for opponent…",
                    color     = TextMuted,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                repeat(3) {
                    ShimmerRow()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    color       = NavyAccent,
                    modifier    = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text       = "Opponent connected!",
                    color      = Color(0xFF4CAF50),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text       = uiState.opponentName,
                    color      = TextWhite,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text      = "Preparing battle stations…",
                    color     = TextMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    color       = GoldAccent,
                    modifier    = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick = { navController.popBackStack() },
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
            ) { Text("CANCEL") }
        }
    }
}

// ── Shimmer row ───────────────────────────────────────────────────────────────

@Composable
private fun ShimmerRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(ShimmerBase, ShimmerHighlight, ShimmerBase)))
    )
}