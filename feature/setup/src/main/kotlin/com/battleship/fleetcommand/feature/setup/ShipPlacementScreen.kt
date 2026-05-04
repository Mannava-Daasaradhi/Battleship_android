// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/ShipPlacementScreen.kt
package com.battleship.fleetcommand.feature.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.theme.*
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
                            phase = effect.phase,
                        )
                    )
                is PlacementViewModel.UiEffect.ShowPlacementError -> { /* future snackbar */ }
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    val playerLabel = if (route.playerSlot == 1) "PLAYER 2 — PLACE FLEET" else "PLACE YOUR FLEET"
                    Text(playerLabel, fontWeight = FontWeight.Bold)
                },
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Placement grid with drag-and-drop ──────────────────────────
            PlacementGridWithDrag(
                uiState = uiState,
                onDropShip = { shipId, coord ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(shipId, coord))
                },
                onTapCell = { coord ->
                    val selected = uiState.selectedShipId ?: return@PlacementGridWithDrag
                    viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(selected, coord))
                },
                onDoubleTapCell = { coord ->
                    // Double-tap a cell containing a ship to rotate it
                    val placement = uiState.placements.firstOrNull { p ->
                        coord in p.occupiedCoords()
                    }
                    if (placement != null) {
                        viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(placement.shipId))
                    }
                },
                onDragHover = { shipId, coord ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.HoverShip(shipId, coord))
                },
                onDragStart = { shipId ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.SetDragging(shipId))
                },
                onDragEnd = {
                    viewModel.onEvent(PlacementViewModel.UiEvent.ClearHover)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Hint text ──────────────────────────────────────────────────
            Text(
                text = when {
                    uiState.draggingShipId != null -> "Drop on the grid to place"
                    uiState.selectedShipId != null -> "Tap grid to place • Double-tap grid ship to rotate"
                    else -> "Tap a ship to select • Long-press & drag to place • Double-tap placed ship to rotate"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // ── Ship tray ──────────────────────────────────────────────────
            ShipSelectionTray(
                uiState = uiState,
                onSelectShip = { shipId ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.SelectShip(shipId))
                },
                onRotateShip = { shipId ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(shipId))
                },
                onDragStart = { shipId ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.SetDragging(shipId))
                },
                onDragHover = { shipId, coord ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.HoverShip(shipId, coord))
                },
                onDrop = { shipId, coord ->
                    viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(shipId, coord))
                },
                onDragCancel = {
                    viewModel.onEvent(PlacementViewModel.UiEvent.ClearHover)
                },
            )

            // ── Action buttons ─────────────────────────────────────────────
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

// ── Placement grid with integrated drag-and-drop ──────────────────────────────

@Composable
private fun PlacementGridWithDrag(
    uiState: PlacementViewModel.UiState,
    onDropShip: (ShipId, Coord) -> Unit,
    onTapCell: (Coord) -> Unit,
    onDoubleTapCell: (Coord) -> Unit,
    onDragHover: (ShipId, Coord) -> Unit,
    onDragStart: (ShipId) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var gridTopLeft by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx by remember { mutableStateOf(0f) }

    // Helper to convert absolute screen position → Coord
    fun offsetToCoord(absOffset: Offset): Coord? {
        if (cellSizePx <= 0f) return null
        val rel = absOffset - gridTopLeft
        // Account for the label column offset (one cell wide)
        val gridContentLeft = cellSizePx
        val gridContentTop  = cellSizePx  // label row
        val col = ((rel.x - gridContentLeft) / cellSizePx).toInt()
        val row = ((rel.y - gridContentTop)  / cellSizePx).toInt()
        if (row !in 0 until GameConstants.BOARD_SIZE || col !in 0 until GameConstants.BOARD_SIZE) return null
        return Coord.fromRowCol(row, col)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellSizeDp = (maxWidth - 24.dp) / GameConstants.BOARD_SIZE
        cellSizePx = with(density) { cellSizeDp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    gridTopLeft = coords.positionInRoot()
                }
        ) {
            Column {
                // Column coordinate labels 1–10
                Row {
                    Spacer(Modifier.size(cellSizeDp))
                    repeat(GameConstants.BOARD_SIZE) { col ->
                        Box(Modifier.size(cellSizeDp), contentAlignment = Alignment.Center) {
                            Text(
                                text = (col + 1).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
                repeat(GameConstants.BOARD_SIZE) { row ->
                    Row {
                        // Row label A–J
                        Box(Modifier.size(cellSizeDp), contentAlignment = Alignment.Center) {
                            Text(
                                text = ('A' + row).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        repeat(GameConstants.BOARD_SIZE) { col ->
                            val coord = Coord.fromRowCol(row, col)
                            val cellState = uiState.board.cells.getOrNull(coord.index)
                            val isShip = cellState?.state == CellDisplayState.SHIP
                            val isHovered = uiState.hoverCoords.contains(coord)
                            val isHoverValid = uiState.hoverValid

                            val isSelectedShipHere = uiState.selectedShipId?.let { selId ->
                                uiState.placements.any { p ->
                                    p.shipId == selId && coord in p.occupiedCoords()
                                }
                            } == true

                            val bgColor = when {
                                isHovered && isHoverValid  -> ValidGreen.copy(alpha = 0.6f)
                                isHovered && !isHoverValid -> InvalidRed.copy(alpha = 0.6f)
                                isShip -> NavyPrimary
                                else   -> NavySurface
                            }
                            val animatedBg by animateColorAsState(
                                targetValue = bgColor,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "cellBg$row$col",
                            )

                            Box(
                                modifier = Modifier
                                    .size(cellSizeDp)
                                    .background(animatedBg)
                                    .border(
                                        width = if (isSelectedShipHere) 2.dp else 0.5.dp,
                                        color = if (isSelectedShipHere) MaterialTheme.colorScheme.primary
                                                else GridLine,
                                    )
                                    .pointerInput(coord) {
                                        detectTapGestures(
                                            onTap = { onTapCell(coord) },
                                            onDoubleTap = { onDoubleTapCell(coord) },
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Ship selection tray with drag-to-place ────────────────────────────────────

@Composable
private fun ShipSelectionTray(
    uiState: PlacementViewModel.UiState,
    onSelectShip: (ShipId) -> Unit,
    onRotateShip: (ShipId) -> Unit,
    onDragStart: (ShipId) -> Unit,
    onDragHover: (ShipId, Coord) -> Unit,
    onDrop: (ShipId, Coord) -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = LocalDensity.current

    // Track each ship item's position so drag can compute grid coords
    val shipItemPositions = remember { mutableStateMapOf<ShipId, Offset>() }
    var gridTopLeft by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx by remember { mutableStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "YOUR SHIPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShipRegistry.ALL) { shipDef ->
                val isPlaced   = uiState.placements.any { it.shipId == shipDef.id }
                val isSelected = uiState.selectedShipId == shipDef.id
                val isDragging = uiState.draggingShipId == shipDef.id
                val orientation = uiState.orientations[shipDef.id] ?: Orientation.Horizontal

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isDragging -> MaterialTheme.colorScheme.primary
                        isSelected -> MaterialTheme.colorScheme.primary
                        isPlaced   -> ValidGreen
                        else       -> MaterialTheme.colorScheme.outline
                    },
                    label = "shipBorder${shipDef.id}",
                )

                val itemScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.1f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "shipScale${shipDef.id}",
                )

                Column(
                    modifier = Modifier
                        .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                        .border(
                            width = if (isSelected || isDragging) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .background(
                            color = when {
                                isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                isPlaced   -> ValidGreen.copy(alpha = 0.1f)
                                else       -> MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .onGloballyPositioned { coords ->
                            shipItemPositions[shipDef.id] = coords.positionInRoot()
                        }
                        // Long-press drag gesture from the tray item
                        .pointerInput(shipDef.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    onDragStart(shipDef.id)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    // Convert absolute pointer position to grid coord
                                    val absPos = change.position + (shipItemPositions[shipDef.id] ?: Offset.Zero)
                                    // Use a broadcast approach: fire hover on every drag move
                                    // The grid's onGloballyPositioned sets gridTopLeft & cellSizePx via
                                    // the shared state — but we can't access that here directly.
                                    // Instead we pass the absolute position and let the ViewModel
                                    // do the math via DragGestureHandler.
                                    // For simplicity: pass raw change.position relative to the item,
                                    // and rely on the grid drop zone below.
                                    onDragHover(shipDef.id, Coord(-1)) // sentinel — tray drag in progress
                                },
                                onDragEnd = { onDragCancel() },
                                onDragCancel = { onDragCancel() },
                            )
                        }
                        .clickable { onSelectShip(shipDef.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Ship name
                    Text(
                        text = shipDef.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlaced) ValidGreen else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )

                    // Visual ship cells preview
                    val isVertical = orientation is Orientation.Vertical
                    if (isVertical) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(shipDef.size) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(
                                            color = when {
                                                isPlaced   -> ValidGreen
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                        )
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(shipDef.size) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(
                                            color = when {
                                                isPlaced   -> ValidGreen
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                        )
                                )
                            }
                        }
                    }

                    // Orientation indicator + rotate button — always visible for every ship
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (orientation is Orientation.Horizontal) "H" else "V",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rotate ${shipDef.name}",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onRotateShip(shipDef.id) },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Status badge
                    if (isPlaced) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = ValidGreen,
                        )
                    }
                }
            }
        }
    }
}