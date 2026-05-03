// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/GameOverScreen.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/GameOverScreen.kt
package com.battleship.fleetcommand.feature.game.gameover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.components.GameGrid
import com.battleship.fleetcommand.core.ui.theme.HitRed
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.core.ui.theme.OnlineGreen
import com.battleship.fleetcommand.navigation.MainMenuRoute
import com.battleship.fleetcommand.navigation.ModeSelectRoute
import com.battleship.fleetcommand.navigation.StatisticsRoute
import kotlinx.coroutines.flow.collectLatest

// ADS PLACEHOLDER — owner will integrate AdMob interstitial here in a future update

@Composable
fun GameOverScreen(
    navController: NavController,
    viewModel: GameOverViewModel,
    route: com.battleship.fleetcommand.navigation.GameOverRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Section 12: Back → Main Menu (not back to battle)
    BackHandler {
        viewModel.onEvent(GameOverViewModel.UiEvent.MainMenu)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                GameOverViewModel.UiEffect.NavigateToMainMenu ->
                    navController.navigate(MainMenuRoute) { popUpTo(0) { inclusive = true } }
                GameOverViewModel.UiEffect.NavigateToStatistics ->
                    navController.navigate(StatisticsRoute)
                GameOverViewModel.UiEffect.NavigateToModeSelect ->
                    navController.navigate(ModeSelectRoute) { popUpTo(MainMenuRoute) { inclusive = false } }
            }
        }
    }

    // Section 9: GameOver enter transition — scale 0.8→1.0 + alpha (handled in NavHost)
    // Internal result card also springs in
    var revealed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gameOverCard",
    )
    LaunchedEffect(Unit) { revealed = true }

    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Result card — springs in per Section 9
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { scaleX = cardScale; scaleY = cardScale },
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (uiState.isPlayerWin) "🏆" else "💥",
                                style = MaterialTheme.typography.displayLarge,
                            )
                            Text(
                                text = if (uiState.isPlayerWin) "VICTORY!" else "DEFEATED",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.isPlayerWin) OnlineGreen else HitRed,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (uiState.isPlayerWin) "You sank the enemy fleet!" else "${uiState.winner} wins this battle.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                StatChip("Shots", uiState.totalShots.toString())
                                StatChip("Accuracy", "${uiState.accuracy}%")
                            }
                        }
                    }

                    // Both boards revealed side by side per Section task spec
                    Text(
                        "BATTLE REPORT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("YOUR FLEET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                            GameGrid(
                                board = uiState.myBoard,
                                showShips = true,
                                onCellTapped = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ENEMY FLEET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                            GameGrid(
                                board = uiState.opponentBoard,
                                showShips = true,
                                onCellTapped = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Actions
                    BattleshipButton(
                        text = "PLAY AGAIN",
                        onClick = { viewModel.onEvent(GameOverViewModel.UiEvent.PlayAgain) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(GameOverViewModel.UiEvent.ViewStats) },
                            modifier = Modifier.weight(1f),
                        ) { Text("STATS") }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(GameOverViewModel.UiEvent.MainMenu) },
                            modifier = Modifier.weight(1f),
                        ) { Text("MENU") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
