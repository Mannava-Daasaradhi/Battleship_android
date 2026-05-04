// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/PlayerNamesScreen.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.ShipPlacementRoute
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PlayerNamesScreen(
    navController: NavController,
    viewModel: PlayerNamesViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                // FIX: pass player names into ShipPlacementRoute so they flow through to the Game record
                is PlayerNamesViewModel.UiEffect.NavigateToPlacement ->
                    navController.navigate(
                        ShipPlacementRoute(
                            mode = effect.mode,
                            playerSlot = 0,          // P1 places first
                            player1Name = effect.player1Name,
                            player2Name = effect.player2Name,
                        )
                    )
            }
        }
    }
    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("PLAYER NAMES", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                OutlinedTextField(
                    value = uiState.player1,
                    onValueChange = viewModel::setPlayer1,
                    label = { Text("Player 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.player2,
                    onValueChange = viewModel::setPlayer2,
                    label = { Text("Player 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                BattleshipButton(
                    text = "START",
                    onClick = viewModel::confirm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}