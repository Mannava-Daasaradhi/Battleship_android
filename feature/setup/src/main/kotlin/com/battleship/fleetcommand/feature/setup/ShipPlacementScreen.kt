// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/ShipPlacementScreen.kt
package com.battleship.fleetcommand.feature.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.battleship.fleetcommand.core.ui.theme.GridLine
import com.battleship.fleetcommand.core.ui.theme.InvalidRed
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavyPrimary
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.core.ui.theme.ValidGreen
import com.battleship.fleetcommand.navigation.BattleRoute
import com.battleship.fleetcommand.navigation.HandOffRoute
import com.battleship.fleetcommand.navigation.OnlineBattleRoute
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val DRAG_LONGPRESS_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipPlacementScreen(
    navController: NavController,
    viewModel: PlacementViewModel,
    route: com.battleship.fleetcommand.navigation.ShipPlacementRoute,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    var dragShipId     by remember { mutableStateOf<ShipId?>(null) }
    var dragOffset     by remember { mutableStateOf(Offset.Zero) }
    var gridTopLeft    by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx     by remember { mutableStateOf(0f) }
    var screenTopLeft  by remember { mutableStateOf(Offset.Zero) }

    fun offsetToGridCoord(abs: Offset): Coord? {
        if (cellSizePx <= 0f) return null
        val rel = abs - gridTopLeft
        val col = ((rel.x - cellSizePx) / cellSizePx).toInt()
        val row = ((rel.y - cellSizePx) / cellSizePx).toInt()
        if (row !in 0 until GameConstants.BOARD_SIZE ||
            col !in 0 until GameConstants.BOARD_SIZE) return null
        return Coord.fromRowCol(row, col)
    }

    val onGridMeasured: (Offset, Float) -> Unit = remember {
        { topLeft, size ->
            gridTopLeft = topLeft
            cellSizePx  = size
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is PlacementViewModel.UiEffect.NavigateToBattle ->
                    navController.navigate(BattleRoute(gameId = effect.gameId)) {
                        popUpTo(com.battleship.fleetcommand.navigation.ModeSelectRoute) { inclusive = false }
                    }
                is PlacementViewModel.UiEffect.NavigateToOnlineBattle -> {
                    try {
                        navController.navigate(
                            OnlineBattleRoute(gameId = effect.gameId, myUid = effect.myUid)
                        ) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "ShipPlacementScreen: NavigateToOnlineBattle popUpTo failed")
                        try {
                            navController.navigate(
                                OnlineBattleRoute(gameId = effect.gameId, myUid = effect.myUid)
                            )
                        } catch (e2: Exception) {
                            Timber.e(e2, "ShipPlacementScreen: retry failed")
                        }
                    }
                }
                is PlacementViewModel.UiEffect.NavigateToHandOff ->
                    navController.navigate(
                        HandOffRoute(
                            gameId      = effect.gameId,
                            mode        = route.mode,
                            isP1HandOff = effect.isP1HandOff,
                            phase       = effect.phase,
                        )
                    )
                is PlacementViewModel.UiEffect.ShowPlacementError -> { }
                is PlacementViewModel.UiEffect.ShowError           -> { }
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    val label = if (route.playerSlot == 1) "PLAYER 2 — PLACE FLEET" else "PLACE YOUR FLEET"
                    Text(label, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> screenTopLeft = coords.positionInRoot() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlacementGridWithDrag(
                    uiState         = uiState,
                    onTapCell       = { coord ->
                        val selected = uiState.selectedShipId ?: return@PlacementGridWithDrag
                        viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(selected, coord))
                    },
                    onDoubleTapCell = { coord ->
                        val placement = uiState.placements.firstOrNull { p ->
                            coord in p.occupiedCoords()
                        }
                        if (placement != null) {
                            viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(placement.shipId))
                        }
                    },
                    onMeasured      = onGridMeasured,
                    modifier        = Modifier.fillMaxWidth(),
                )

                Text(
                    text = when {
                        uiState.isSubmitting           -> "Submitting fleet to server…"
                        dragShipId != null             -> "Drop on the grid to place"
                        uiState.selectedShipId != null ->
                            "Tap grid to place • Tap ROTATE below to flip ship"
                        else ->
                            "Long-press & drag a ship onto the grid • Tap to select"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                ShipSelectionTray(
                    uiState           = uiState,
                    currentDragShipId = dragShipId,
                    onSelectShip      = { shipId ->
                        viewModel.onEvent(PlacementViewModel.UiEvent.SelectShip(shipId))
                    },
                    onRotateShip      = { shipId ->
                        viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(shipId))
                    },
                    onDragStarted     = { shipId, absOffset ->
                        dragShipId = shipId
                        dragOffset = absOffset
                        viewModel.onEvent(PlacementViewModel.UiEvent.SetDragging(shipId))
                    },
                    onDragMoved       = { shipId, absOffset ->
                        dragOffset = absOffset
                        val coord  = offsetToGridCoord(absOffset)
                        viewModel.onEvent(
                            PlacementViewModel.UiEvent.HoverShip(shipId, coord ?: Coord(-1))
                        )
                    },
                    onDragEnded       = { absOffset ->
                        val id    = dragShipId
                        val coord = offsetToGridCoord(absOffset)
                        if (id != null && coord != null) {
                            viewModel.onEvent(PlacementViewModel.UiEvent.PlaceShip(id, coord))
                        } else {
                            viewModel.onEvent(PlacementViewModel.UiEvent.ClearHover)
                        }
                        dragShipId = null
                    },
                    onDragCancelled   = {
                        dragShipId = null
                        viewModel.onEvent(PlacementViewModel.UiEvent.ClearHover)
                    },
                )

                // ── The new 3-button layout ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = { viewModel.onEvent(PlacementViewModel.UiEvent.ClearAll) },
                        enabled  = !uiState.isSubmitting,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { 
                        Text("CLEAR", style = MaterialTheme.typography.labelMedium) 
                    }
                    
                    OutlinedButton(
                        onClick  = { 
                            uiState.selectedShipId?.let { shipId ->
                                viewModel.onEvent(PlacementViewModel.UiEvent.RotateShip(shipId))
                            }
                        },
                        enabled  = uiState.selectedShipId != null && !uiState.isSubmitting,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { 
                        Text("ROTATE", style = MaterialTheme.typography.labelMedium) 
                    }

                    OutlinedButton(
                        onClick  = { viewModel.onEvent(PlacementViewModel.UiEvent.AutoPlace) },
                        enabled  = !uiState.isAutoPlacing && !uiState.isSubmitting,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { 
                        Text("AUTO", style = MaterialTheme.typography.labelMedium) 
                    }
                }

                if (uiState.isSubmitting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    BattleshipButton(
                        text     = "CONFIRM FLEET",
                        onClick  = { viewModel.onEvent(PlacementViewModel.UiEvent.ConfirmPlacement) },
                        enabled  = uiState.canConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } 

            val ghostId = dragShipId
            if (ghostId != null && cellSizePx > 0f) {
                val ship        = ShipRegistry.ALL.find { it.id == ghostId }
                val orientation = uiState.orientations[ghostId] ?: Orientation.Horizontal
                if (ship != null) {
                    FloatingShipGhost(
                        shipSize         = ship.size,
                        orientation      = orientation,
                        absOffset        = dragOffset,
                        containerTopLeft = screenTopLeft,
                        cellSizePx       = cellSizePx,
                        density          = density,
                    )
                }
            }
        } 
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlacementGridWithDrag
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlacementGridWithDrag(
    uiState: PlacementViewModel.UiState,
    onTapCell: (Coord) -> Unit,
    onDoubleTapCell: (Coord) -> Unit,
    onMeasured: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density     = LocalDensity.current
        val cellSizeDp  = (maxWidth / (GameConstants.BOARD_SIZE + 1))
        val cellSizePx  = with(density) { cellSizeDp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onMeasured(coords.positionInRoot(), cellSizePx)
                },
        ) {
            Row {
                Box(Modifier.size(cellSizeDp))
                repeat(GameConstants.BOARD_SIZE) { col ->
                    Box(Modifier.size(cellSizeDp), contentAlignment = Alignment.Center) {
                        Text(
                            text  = (col + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            repeat(GameConstants.BOARD_SIZE) { row ->
                Row {
                    Box(Modifier.size(cellSizeDp), contentAlignment = Alignment.Center) {
                        Text(
                            text  = ('A' + row).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }

                    repeat(GameConstants.BOARD_SIZE) { col ->
                        val coord    = Coord.fromRowCol(row, col)
                        val cellState = uiState.board.cells.getOrNull(coord.index)
                        val isShip   = cellState?.state == CellDisplayState.SHIP
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
                            isShip                     -> NavyPrimary
                            else                       -> NavySurface
                        }
                        val animatedBg by animateColorAsState(
                            targetValue   = bgColor,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label         = "cellBg$row$col",
                        )

                        Box(
                            modifier = Modifier
                                .size(cellSizeDp)
                                .background(animatedBg)
                                .border(
                                    width = if (isSelectedShipHere) 2.dp else 0.5.dp,
                                    color = if (isSelectedShipHere)
                                        MaterialTheme.colorScheme.primary
                                    else GridLine,
                                )
                                .clickable { onTapCell(coord) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FloatingShipGhost
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FloatingShipGhost(
    shipSize: Int,
    orientation: Orientation,
    absOffset: Offset,
    containerTopLeft: Offset,
    cellSizePx: Float,
    density: androidx.compose.ui.unit.Density,
) {
    val isVertical    = orientation is Orientation.Vertical
    val cellDp        = with(density) { cellSizePx.toDp() }

    val ghostWidthPx  = if (isVertical) cellSizePx            else shipSize * cellSizePx
    val ghostHeightPx = if (isVertical) shipSize * cellSizePx else cellSizePx

    val ghostX = absOffset.x - containerTopLeft.x - ghostWidthPx / 2f
    val ghostY = absOffset.y - containerTopLeft.y - ghostHeightPx / 2f

    Box(
        modifier = Modifier
            .offset { IntOffset(ghostX.toInt(), ghostY.toInt()) }
            .graphicsLayer { alpha = 0.55f },
    ) {
        if (isVertical) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(shipSize) {
                    Box(
                        Modifier
                            .size(cellDp)
                            .background(NavyPrimary, RoundedCornerShape(3.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(shipSize) {
                    Box(
                        Modifier
                            .size(cellDp)
                            .background(NavyPrimary, RoundedCornerShape(3.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ShipSelectionTray
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShipSelectionTray(
    uiState: PlacementViewModel.UiState,
    currentDragShipId: ShipId?,
    onSelectShip: (ShipId) -> Unit,
    onRotateShip: (ShipId) -> Unit,
    onDragStarted: (shipId: ShipId, absOffset: Offset) -> Unit,
    onDragMoved: (shipId: ShipId, absOffset: Offset) -> Unit,
    onDragEnded: (absOffset: Offset) -> Unit,
    onDragCancelled: () -> Unit,
) {
    val shipItemPositions = remember { mutableStateMapOf<ShipId, Offset>() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text       = "YOUR SHIPS",
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShipRegistry.ALL) { shipDef ->
                val isPlaced    = uiState.placements.any { it.shipId == shipDef.id }
                val isSelected  = uiState.selectedShipId == shipDef.id
                val isDragging  = currentDragShipId == shipDef.id
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

                val itemAlpha by animateFloatAsState(
                    targetValue   = if (isDragging) 0.25f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "shipAlpha${shipDef.id}",
                )

                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha  = itemAlpha
                            scaleX = if (isDragging) 0.9f else 1f
                            scaleY = if (isDragging) 0.9f else 1f
                        }
                        .border(
                            width  = if (isSelected || isDragging) 2.dp else 1.dp,
                            color  = borderColor,
                            shape  = RoundedCornerShape(8.dp),
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
                        .pointerInput(shipDef.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startAbs = (shipItemPositions[shipDef.id] ?: Offset.Zero) +
                                        down.position

                                var dragActivated = false
                                var currentAbs    = startAbs

                                val wasTap = withTimeoutOrNull(DRAG_LONGPRESS_MS) {
                                    while (true) {
                                        val event: PointerEvent = awaitPointerEvent(
                                            pass = PointerEventPass.Initial
                                        )
                                        val pointer = event.changes.firstOrNull() ?: break
                                        currentAbs = (shipItemPositions[shipDef.id]
                                            ?: Offset.Zero) + pointer.position
                                        if (!pointer.pressed) {
                                            pointer.consume()
                                            return@withTimeoutOrNull true
                                        }
                                    }
                                    false
                                }

                                if (wasTap == true) {
                                    onSelectShip(shipDef.id)
                                    return@awaitEachGesture
                                }

                                dragActivated = true
                                onDragStarted(shipDef.id, currentAbs)

                                while (true) {
                                    val event: PointerEvent = awaitPointerEvent(
                                        pass = PointerEventPass.Initial
                                    )
                                    val pointer = event.changes.firstOrNull() ?: break
                                    pointer.consume()
                                    if (!pointer.pressed) {
                                        currentAbs = (shipItemPositions[shipDef.id]
                                            ?: Offset.Zero) + pointer.position
                                        onDragEnded(currentAbs)
                                        return@awaitEachGesture
                                    }
                                    currentAbs = (shipItemPositions[shipDef.id] ?: Offset.Zero) +
                                            pointer.position
                                    onDragMoved(shipDef.id, currentAbs)
                                }

                                if (dragActivated) {
                                    onDragCancelled()
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text       = shipDef.name,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (isPlaced) ValidGreen else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )

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

                    // Kept just the H/V indicator without the tiny button
                    Text(
                        text  = if (orientation is Orientation.Horizontal) "Horizontal" else "Vertical",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )

                    if (isPlaced) {
                        Text(
                            text  = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = ValidGreen,
                        )
                    }
                }
            }
        }
    }
}