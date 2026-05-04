// ============================================================
// feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/ShipPlacementScreen.kt
// ============================================================
// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/ShipPlacementScreen.kt
package com.battleship.fleetcommand.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.components.GameGrid
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.BattleRoute
import com.battleship.fleetcommand.navigation.HandOffRoute
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipPlacementScreen(
    navController: NavController,
    viewModel: PlacementViewModel,
    route: com.battleship.fleetcommand.navigation.ShipPlacementRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedShipId by remember { mutableStateOf<ShipId?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is PlacementViewModel.UiEffect.NavigateToBattle ->
                    navController.navigate(BattleRoute(gameId = effect.gameId)) {
                        popUpTo(com.battleship.fleetcommand.navigation.ModeSelectRoute) { inclusive = false }
                    }
                is PlacementViewModel.UiEffect.NavigateToHandOff ->
                    navController.navigate(
                        HandOffRoute(
                            gameId = effect.gameId,
                            mode = route.mode,
                            isP1HandOff = effect.isP1HandOff,
                        )
                    )
                is PlacementViewModel.UiEffect.ShowPlacementError -> { /* snackbar / shake — future */ }
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("PLACE YOUR FLEET") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Grid
            GameGrid(
                board = uiState.board,
                showShips = true,
                onCellTapped = { cell: CellViewState ->
                    val ship = selectedShipId ?: return@GameGrid
                    viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(ship, cell.coord))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Ship tray
            Text("TAP SHIP → TAP CELL TO PLACE   |   TAP PLACED SHIP TO ROTATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ShipRegistry.ALL) { shipDef ->
                    val isPlaced = uiState.placements.any { it.shipId == shipDef.id }
                    val isSelected = selectedShipId == shipDef.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isPlaced) {
                                viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(shipDef.id))
                            } else {
                                selectedShipId = if (isSelected) null else shipDef.id
                            }
                        },
                        label = { Text("${shipDef.name} (${shipDef.size})") },
                    )
                }
            }

            // No rewarded ad button — ads are a future addition

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(PlacementViewModel.UiEvent.ClearAll) },
                    modifier = Modifier.weight(1f),
                ) { Text("CLEAR") }
                OutlinedButton(
                    onClick = { viewModel.onEvent(PlacementViewModel.UiEvent.AutoPlace) },
                    enabled = !uiState.isAutoPlacing,
                    modifier = Modifier.weight(1f),
                ) { Text("AUTO") }
            }

            BattleshipButton(
                text = "CONFIRM FLEET",
                onClick = { viewModel.onEvent(PlacementViewModel.UiEvent.ConfirmPlacement) },
                enabled = uiState.canConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}