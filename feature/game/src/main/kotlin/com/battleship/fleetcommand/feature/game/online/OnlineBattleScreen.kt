// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/online/OnlineBattleScreen.kt
package com.battleship.fleetcommand.feature.game.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.GameGrid
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.GameOverRoute
import com.battleship.fleetcommand.navigation.OnlineBattleRoute
import kotlinx.coroutines.flow.collectLatest

/**
 * Online battle screen — wired to [OnlineGameViewModel] which uses Firebase Realtime DB.
 * Separate from BattleScreen (which uses local Room / AI) to keep concerns isolated.
 * Section 6 — Online Multiplayer.
 *
 * BUG 4 FIX: Both GameGrid composables now use Modifier.weight(1f) so they share
 * available vertical space equally and the "YOUR FLEET" grid is never clipped off-screen.
 * The outer Column uses Modifier.fillMaxSize() which is required for weight() to work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineBattleScreen(
    navController: NavController,
    viewModel: OnlineGameViewModel,
    route: OnlineBattleRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showResignDialog by remember { mutableStateOf(false) }

    BackHandler {
        showResignDialog = true
    }

    // Consume one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is OnlineGameViewModel.UiEffect.NavigateToGameOver ->
                    navController.navigate(GameOverRoute(gameId = "", winner = effect.winner)) {
                        popUpTo(com.battleship.fleetcommand.navigation.MainMenuRoute) { inclusive = false }
                    }
                is OnlineGameViewModel.UiEffect.ShowHitAnimation  -> { /* future animation */ }
                is OnlineGameViewModel.UiEffect.ShowMissAnimation -> { /* future animation */ }
                is OnlineGameViewModel.UiEffect.ShowSunkAnimation -> { /* future animation */ }
                is OnlineGameViewModel.UiEffect.ShowReconnectingOverlay -> { /* future overlay */ }
                is OnlineGameViewModel.UiEffect.ShowOpponentDisconnectedDialog -> {
                    // TODO: show dialog offering to claim victory
                }
            }
        }
    }

    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = { Text("Resign?") },
            text = { Text("Are you sure you want to forfeit this online game?") },
            confirmButton = {
                TextButton(onClick = {
                    showResignDialog = false
                    viewModel.onEvent(OnlineGameViewModel.UiEvent.ResignGame)
                }) { Text("Resign") }
            },
            dismissButton = {
                TextButton(onClick = { showResignDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Waiting for battle to start (still in "setup" status — opponent hasn't placed ships yet)
    if (uiState.gameStatus == OnlineGameViewModel.GameStatus.WAITING) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    "Waiting for ${uiState.opponentName} to place ships…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Your fleet is ready for battle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    val turnText = when {
                        uiState.gameStatus == OnlineGameViewModel.GameStatus.FINISHED -> "GAME OVER"
                        uiState.isMyTurn -> "YOUR TURN"
                        else -> "${uiState.opponentName}'s TURN"
                    }
                    Text(turnText, style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(onClick = { showResignDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Resign")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { paddingValues ->
        // BUG 4 FIX: Column uses fillMaxSize() so child weight() modifiers have a
        // bounded height to share. Without fillMaxSize(), weight() has no effect and
        // the bottom grid overflows the screen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "vs ${uiState.opponentName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!uiState.opponentConnected) {
                    Text(
                        "Opponent disconnected (${uiState.opponentDisconnectedSeconds}s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                "ENEMY WATERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // Opponent board — tappable only on my turn during battle
            val canFire = uiState.isMyTurn &&
                    uiState.gameStatus == OnlineGameViewModel.GameStatus.BATTLE &&
                    uiState.opponentConnected

            // BUG 4 FIX: Added weight(1f) so this grid shares vertical space equally
            // with the "YOUR FLEET" grid below. Previously both grids had no height
            // constraint and the bottom one was cut off.
            GameGrid(
                board = uiState.opponentBoard,
                showShips = false,
                onCellTapped = if (canFire) { cell: CellViewState ->
                    viewModel.onEvent(OnlineGameViewModel.UiEvent.CellTapped(cell.coord))
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            HorizontalDivider()

            Text(
                "YOUR FLEET",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // BUG 4 FIX: Added weight(1f) so this grid shares vertical space equally
            // with the "ENEMY WATERS" grid above. Previously this grid was clipped off-screen.
            GameGrid(
                board = uiState.myBoard,
                showShips = true,
                onCellTapped = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}