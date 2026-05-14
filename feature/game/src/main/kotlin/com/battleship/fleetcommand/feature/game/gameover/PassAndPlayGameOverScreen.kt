// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/PassAndPlayGameOverScreen.kt
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
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.MainMenuRoute
import com.battleship.fleetcommand.navigation.ModeSelectRoute
import com.battleship.fleetcommand.navigation.StatisticsRoute
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PassAndPlayGameOverScreen(
    navController: NavController,
    viewModel: PassAndPlayGameOverViewModel,
    route: com.battleship.fleetcommand.navigation.PassAndPlayGameOverRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onEvent(PassAndPlayGameOverViewModel.UiEvent.MainMenu)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                PassAndPlayGameOverViewModel.UiEffect.NavigateToMainMenu ->
                    navController.navigate(MainMenuRoute) { popUpTo(0) { inclusive = true } }
                PassAndPlayGameOverViewModel.UiEffect.NavigateToStatistics ->
                    navController.navigate(StatisticsRoute)
                PassAndPlayGameOverViewModel.UiEffect.NavigateToModeSelect ->
                    navController.navigate(ModeSelectRoute) { popUpTo(MainMenuRoute) }
            }
        }
    }

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
                                text = "🏆",
                                style = MaterialTheme.typography.displayLarge,
                            )
                            Text(
                                text = "${uiState.winner.uppercase()} WINS!",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "A hard-fought battle!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PLAYER 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    StatChip("Shots", uiState.p1Shots.toString())
                                    StatChip("Accuracy", "${uiState.p1Accuracy}%")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PLAYER 2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    StatChip("Shots", uiState.p2Shots.toString())
                                    StatChip("Accuracy", "${uiState.p2Accuracy}%")
                                }
                            }
                        }
                    }

                    if (uiState.p1Board.cells.isNotEmpty()) {
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
                                Text("P1 FLEET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                                GameGrid(
                                    board = uiState.p1Board,
                                    showShips = true,
                                    onCellTapped = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("P2 FLEET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                                GameGrid(
                                    board = uiState.p2Board,
                                    showShips = true,
                                    onCellTapped = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    BattleshipButton(
                        text = "PLAY AGAIN",
                        onClick = { viewModel.onEvent(PassAndPlayGameOverViewModel.UiEvent.PlayAgain) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(PassAndPlayGameOverViewModel.UiEvent.ViewStats) },
                            modifier = Modifier.weight(1f),
                        ) { Text("STATS") }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(PassAndPlayGameOverViewModel.UiEvent.MainMenu) },
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