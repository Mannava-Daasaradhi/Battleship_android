// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/battle/BattleScreen.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/battle/BattleScreen.kt
package com.battleship.fleetcommand.feature.game.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.GameGrid
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.GameOverRoute
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleScreen(
    navController: NavController,
    viewModel: BattleViewModel,
    route: com.battleship.fleetcommand.navigation.BattleRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showResignDialog by remember { mutableStateOf(false) }

    // Section 12 BackHandler — shows resign dialog
    BackHandler { viewModel.onEvent(BattleViewModel.UiEvent.ResignGame) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is BattleViewModel.UiEffect.NavigateToGameOver ->
                    navController.navigate(GameOverRoute) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    }
                BattleViewModel.UiEffect.ShowResignDialog -> showResignDialog = true
                is BattleViewModel.UiEffect.ShowHitAnimation  -> { /* animation emitted, GameGrid handles via effect */ }
                is BattleViewModel.UiEffect.ShowMissAnimation -> { }
                is BattleViewModel.UiEffect.ShowSunkAnimation -> { }
            }
        }
    }

    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = { Text("Resign?") },
            text = { Text("Are you sure you want to forfeit this game?") },
            confirmButton = {
                TextButton(onClick = {
                    showResignDialog = false
                    navController.navigate(GameOverRoute) { popUpTo(0) { inclusive = false } }
                }) { Text("Resign") }
            },
            dismissButton = {
                TextButton(onClick = { showResignDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isMyTurn) "YOUR TURN" else "${uiState.opponentName}'s TURN",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(BattleViewModel.UiEvent.ResignGame) }) {
                        Icon(Icons.Default.Flag, contentDescription = "Resign")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Accuracy indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Shots: ${uiState.shotCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("Hits: ${uiState.hitCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            }

            // Enemy board label
            Text(
                "ENEMY WATERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // Opponent grid — fog of war
            Box {
                GameGrid(
                    board = uiState.opponentBoard,
                    showShips = false,
                    onCellTapped = { cell: CellViewState ->
                        if (uiState.isMyTurn && !uiState.isAnimating) {
                            viewModel.onEvent(BattleViewModel.UiEvent.CellTapped(cell.coord))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.isAiThinking) {
                    AiThinkingDotsOverlay(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
            }

            HorizontalDivider()

            // My board label
            Text("YOUR FLEET", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            // My board — full visibility
            GameGrid(
                board = uiState.myBoard,
                showShips = true,
                onCellTapped = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AiThinkingDotsOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiDots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 133),
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = offsetY }
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}