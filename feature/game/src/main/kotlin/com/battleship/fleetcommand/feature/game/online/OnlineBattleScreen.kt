// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/online/OnlineBattleScreen.kt
package com.battleship.fleetcommand.feature.game.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.battleship.fleetcommand.navigation.MainMenuRoute
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineBattleScreen(
    navController: NavController,
    viewModel: OnlineGameViewModel,
    route: com.battleship.fleetcommand.navigation.OnlineBattleRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showResignDialog by remember { mutableStateOf(false) }

    BackHandler {
        showResignDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is OnlineGameViewModel.UiEffect.NavigateToGameOver -> {
                    try {
                        navController.navigate(GameOverRoute(gameId = "", winner = effect.winner)) {
                            popUpTo(MainMenuRoute) { inclusive = false }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "OnlineBattleScreen: NavigateToGameOver failed — winner=${effect.winner}")
                    }
                }
                is OnlineGameViewModel.UiEffect.ShowHitAnimation  -> { }
                is OnlineGameViewModel.UiEffect.ShowMissAnimation -> { }
                is OnlineGameViewModel.UiEffect.ShowSunkAnimation -> { }
                is OnlineGameViewModel.UiEffect.ShowReconnectingOverlay -> { }
                is OnlineGameViewModel.UiEffect.ShowOpponentDisconnectedDialog -> { }
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
        
        // 3. FIXED LOGIC: Removed weight/mini restrictions. Standard scrollable column
        // with two beautiful, full-sized grids so the game looks grand again.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Status info row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            Spacer(modifier = Modifier.height(8.dp))

            val canFire = uiState.isMyTurn &&
                    uiState.gameStatus == OnlineGameViewModel.GameStatus.BATTLE &&
                    uiState.opponentConnected

            GameGrid(
                board = uiState.opponentBoard,
                showShips = false,
                onCellTapped = if (canFire) { cell: CellViewState ->
                    viewModel.onEvent(OnlineGameViewModel.UiEvent.CellTapped(cell.coord))
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            Spacer(Modifier.height(32.dp))

            Text(
                "YOUR FLEET",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            
            GameGrid(
                board = uiState.myBoard,
                showShips = true,
                onCellTapped = null,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(32.dp))
        }
    }
}