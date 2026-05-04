package com.battleship.fleetcommand.feature.game.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
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
import com.battleship.fleetcommand.navigation.HandOffRoute
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

    // Pass & Play: observe saved state set by HandOffScreen when returning to this screen
    val passAndPlayResumeP1 = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<Boolean?>("passAndPlayResumeP1", null)
        ?.collectAsState()
    LaunchedEffect(passAndPlayResumeP1?.value) {
        val resumeP1 = passAndPlayResumeP1?.value ?: return@LaunchedEffect
        // Clear the key so it doesn't fire again on recomposition
        navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("passAndPlayResumeP1")
        if (resumeP1) {
            viewModel.onEvent(BattleViewModel.UiEvent.PassAndPlayResumeP1Turn)
        } else {
            viewModel.onEvent(BattleViewModel.UiEvent.PassAndPlayResumeP2Turn)
        }
    }

    // Section 12 BackHandler — shows resign dialog
    BackHandler { viewModel.onEvent(BattleViewModel.UiEvent.ResignGame) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is BattleViewModel.UiEffect.NavigateToGameOver ->
                    navController.navigate(GameOverRoute(gameId = effect.gameId, winner = effect.winner)) {
                        popUpTo<com.battleship.fleetcommand.navigation.MainMenuRoute> { inclusive = false }
                    }
                is BattleViewModel.UiEffect.NavigateToPassAndPlayHandOff -> {
                    // Navigate to HandOff; on return the resumed player's turn begins via onEvent
                    navController.navigate(
                        HandOffRoute(
                            gameId = effect.gameId,
                            mode = "LOCAL",
                            isP1HandOff = effect.isP1Turn,
                        )
                    )
                }
                BattleViewModel.UiEffect.ShowResignDialog -> showResignDialog = true
                is BattleViewModel.UiEffect.ShowHitAnimation  -> { }
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
                    navController.navigate(GameOverRoute(gameId = "", winner = "AI")) {
                        popUpTo(0) { inclusive = false }
                    }
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
                        // Flag icon is in extended icons only — Close is semantically "exit/resign"
                        Icon(Icons.Default.Close, contentDescription = "Resign")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Your shots: ${uiState.shotCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("${uiState.opponentName}: ${uiState.aiShotCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("Hits: ${uiState.hitCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "ENEMY WATERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Box {
                GameGrid(
                    board = uiState.opponentBoard,
                    showShips = false,
                    onCellTapped = { cell: CellViewState ->
                        // ViewModel has the authoritative synchronous guard (isProcessingShot).
                        // UI guard here is a best-effort pre-filter only.
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
            Text("YOUR FLEET", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                    initialStartOffset = StartOffset(index * 133),
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = offsetY }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}