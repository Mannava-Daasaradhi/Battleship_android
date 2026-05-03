// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentScreen.kt

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

private val NavyDeep   = Color(0xFF050E1A)
private val NavyMid    = Color(0xFF0A1930)
private val NavyAccent = Color(0xFF1A4A8A)
private val GoldAccent = Color(0xFFD4AF37)
private val TextWhite  = Color(0xFFF0F4FF)
private val TextMuted  = Color(0xFF8090B0)
private val ShimmerBase    = Color(0xFF112240)
private val ShimmerHighlight = Color(0xFF1E3A6E)

@Composable
fun WaitingForOpponentScreen(
    gameId: String,
    navController: NavController,
    viewModel: WaitingForOpponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialise with gameId on first composition
    LaunchedEffect(gameId) {
        viewModel.init(gameId)
    }

    // Navigate when both players are ready (status advances to "battle")
    LaunchedEffect(uiState.bothReady) {
        if (uiState.bothReady) {
            navController.navigate(ShipPlacementRoute(mode = "ONLINE", gameId = gameId)) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
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
            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text       = "⚓  BATTLE STATION",
                color      = GoldAccent,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (!uiState.opponentConnected) {
                // ── Shimmer placeholder rows ─────────────────────────────────
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
                    color  = NavyAccent,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                // ── Opponent connected ────────────────────────────────────────
                Text(
                    text      = "Opponent connected!",
                    color     = Color(0xFF4CAF50),
                    fontSize  = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text      = uiState.opponentName,
                    color     = TextWhite,
                    fontSize  = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
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
                    color  = GoldAccent,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick = { navController.popBackStack() },
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
            ) {
                Text("CANCEL")
            }
        }
    }
}

// ── Shimmer row ───────────────────────────────────────────────────────────────

@Composable
private fun ShimmerRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
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
            .background(
                Brush.horizontalGradient(
                    listOf(ShimmerBase, ShimmerHighlight, ShimmerBase)
                )
            )
    )
}