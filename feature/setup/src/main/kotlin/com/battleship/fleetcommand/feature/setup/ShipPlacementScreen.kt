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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
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
import com.battleship.fleetcommand.navigation.MainMenuRoute
import com.battleship.fleetcommand.navigation.OnlineBattleRoute
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// ── DRAG LONG-PRESS THRESHOLD ─────────────────────────────────────────────────
//
// BUG FIX: was 150L — far too short. The Android system long-press threshold is
// 500ms. At 150ms, any tap slower than 150ms (very common) accidentally activated
// drag instead of tap. Additionally LazyRow's scroll gesture runs at the Initial
// pass and consumed pointer events before our Main-pass handler saw them, causing
// the withTimeoutOrNull inner loop to see consumed/stale pointer state.
//
// Fix 1: Raised to 500ms — matches the system ViewConfiguration.getLongPressTimeout()
//         and is the value users intuitively understand as a long-press.
// Fix 2: The tracking while-loop inside withTimeoutOrNull now uses
//         PointerEventPass.Initial so we receive events BEFORE LazyRow's scroll
//         handler consumes them. This makes the "finger still down?" check reliable
//         regardless of scroll-gesture interception.
// Fix 3: After drag is activated, the drag-tracking while-loop also uses
//         PointerEventPass.Initial AND calls pointer.consume() to prevent LazyRow
//         from scrolling while the user is dragging a ship.
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

    // ── Screen-level drag state ──────────────────────────────────────────────
    var dragShipId     by remember { mutableStateOf<ShipId?>(null) }
    var dragOffset     by remember { mutableStateOf(Offset.Zero) }
    var gridTopLeft    by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx     by remember { mutableStateOf(0f) }
    var screenTopLeft  by remember { mutableStateOf(Offset.Zero) }

    // Convert an absolute screen position (root coords) into a grid Coord.
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
                    // MainMenuRoute is always the stack root for the online flow
                    // (MainMenu → OnlineLobby → WaitingForOpponent → ShipPlacement).
                    // ModeSelectRoute is NOT in the online back stack.
                    //
                    // BUG FIX: The previous code had a try/catch where the catch
                    // retried without popUpTo. That retry left Lobby+Waiting+Placement
                    // on the back stack, which then caused a crash in OnlineBattleScreen
                    // when NavigateToGameOver tried to popUpTo MainMenuRoute on a dirty
                    // stack. Fix: always use the graph's startDestinationId (which IS
                    // MainMenuRoute) rather than the typed MainMenuRoute reference, so
                    // the popUpTo never fails even if type-safe route serialisation has
                    // a mismatch. If even that fails, we still navigate but with a clean
                    // log rather than a crash.
                    try {
                        navController.navigate(
                            OnlineBattleRoute(gameId = effect.gameId, myUid = effect.myUid)
                        ) {
                            // Use startDestinationId (Int-based) — always present, never fails.
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "ShipPlacementScreen: NavigateToOnlineBattle popUpTo failed — navigating without popUpTo")
                        try {
                            navController.navigate(
                                OnlineBattleRoute(gameId = effect.gameId, myUid = effect.myUid)
                            )
                        } catch (e2: Exception) {
                            Timber.e(e2, "ShipPlacementScreen: NavigateToOnlineBattle retry also failed")
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
                is PlacementViewModel.UiEffect.ShowPlacementError -> { /* future snackbar */ }
                is PlacementViewModel.UiEffect.ShowError           -> { /* future snackbar */ }
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

        // ── Outer Box — coordinate origin for the ghost overlay ──────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> screenTopLeft = coords.positionInRoot() },
        ) {

            // ── Main content column ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                // ── Placement grid ───────────────────────────────────────────
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

                // ── Hint text ────────────────────────────────────────────────
                Text(
                    text = when {
                        uiState.isSubmitting           -> "Submitting fleet to server…"
                        dragShipId != null             -> "Drop on the grid to place"
                        uiState.selectedShipId != null ->
                            "Tap grid to place • Double-tap placed ship to rotate"
                        else ->
                            "Long-press & drag a ship onto the grid • Tap to select • Double-tap to rotate"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                // ── Ship tray ────────────────────────────────────────────────
                // NOTE: Drag is handled via raw pointerInput with awaitFirstDown +
                // delay(500ms) + pointer tracking. This bypasses LocalViewConfiguration
                // entirely and is reliably instant inside a LazyRow.
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

                // ── Action buttons ───────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { viewModel.onEvent(PlacementViewModel.UiEvent.ClearAll) },
                        enabled  = !uiState.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text("CLEAR") }
                    OutlinedButton(
                        onClick  = { viewModel.onEvent(PlacementViewModel.UiEvent.AutoPlace) },
                        enabled  = !uiState.isAutoPlacing && !uiState.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text("AUTO") }
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
            } // end Column

            // ── Floating ghost overlay ───────────────────────────────────────
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
        } // end outer Box
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
        // 1 column for row labels + 10 grid columns = 11 units across the full width
        val cellSizeDp  = (maxWidth / (GameConstants.BOARD_SIZE + 1))
        val cellSizePx  = with(density) { cellSizeDp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onMeasured(coords.positionInRoot(), cellSizePx)
                },
        ) {
            // ── Column labels 1–10 ────────────────────────────────────────────
            Row {
                // Corner spacer
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

            // ── Grid rows ────────────────────────────────────────────────────
            repeat(GameConstants.BOARD_SIZE) { row ->
                Row {
                    // Row label A–J
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
                                .pointerInput(coord) {
                                    detectTapGestures(
                                        onTap       = { onTapCell(coord) },
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

                // ── Gesture handler ───────────────────────────────────────────
                //
                // LONG PRESS BUG FIX — root cause and solution:
                //
                // PROBLEM 1 — threshold too short (150ms):
                //   A normal human tap is 80–200ms. At 150ms, slow or deliberate taps
                //   exceeded the threshold and triggered drag instead of selection.
                //   Users couldn't reliably tap to select ships.
                //
                // PROBLEM 2 — wrong PointerEventPass:
                //   The tracking while-loop used PointerEventPass.Main. LazyRow's internal
                //   horizontal scroll gesture handler runs at PointerEventPass.Initial and
                //   calls consume() on pointer events when it detects horizontal movement.
                //   By the time Main pass ran, pointer events were already consumed by the
                //   scroll handler. This made pointer.pressed unreliable (it could appear
                //   "not pressed" even though the finger was still down), causing
                //   withTimeoutOrNull to return true (tap) even on a genuine long-press.
                //
                // SOLUTION:
                //   - DRAG_LONGPRESS_MS raised to 500ms (Android system standard).
                //   - The tap-detection while-loop inside withTimeoutOrNull now uses
                //     PointerEventPass.Initial so we see events BEFORE LazyRow consumes them.
                //   - Once drag is activated (500ms elapsed), the drag-tracking while-loop
                //     also uses PointerEventPass.Initial AND calls pointer.consume() on every
                //     event, which prevents LazyRow from scrolling while a ship is being dragged.
                //
                // Gesture logic (unchanged):
                //   - Await finger DOWN.
                //   - Wait up to DRAG_LONGPRESS_MS (500ms) watching for finger UP → tap.
                //   - Finger lifts before 500ms → onSelectShip (tap).
                //   - 500ms elapses with finger still down → onDragStarted (drag).
                //   - Track MOVE events → onDragMoved.
                //   - Finger UP during drag → onDragEnded.
                //   - Gesture cancelled → onDragCancelled.
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
                                // Wait for initial finger contact.
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startAbs = (shipItemPositions[shipDef.id] ?: Offset.Zero) +
                                        down.position

                                var dragActivated = false
                                var currentAbs    = startAbs

                                // BUG FIX: Use PointerEventPass.Initial inside withTimeoutOrNull
                                // so we receive events before LazyRow's scroll handler consumes
                                // them. This makes the "finger still down?" check reliable.
                                //
                                // withTimeoutOrNull fires after DRAG_LONGPRESS_MS regardless of
                                // whether pointer events arrive (the coroutine dispatcher's delay
                                // mechanism runs independently of the event stream).
                                //
                                // Returns true  → finger lifted before 500ms timeout → tap
                                // Returns null  → 500ms elapsed with finger still down → drag
                                val wasTap = withTimeoutOrNull(DRAG_LONGPRESS_MS) {
                                    while (true) {
                                        val event: PointerEvent = awaitPointerEvent(
                                            // BUG FIX: Initial pass — before LazyRow scroll consumes events.
                                            pass = PointerEventPass.Initial
                                        )
                                        val pointer = event.changes.firstOrNull() ?: break
                                        currentAbs = (shipItemPositions[shipDef.id]
                                            ?: Offset.Zero) + pointer.position
                                        if (!pointer.pressed) {
                                            // Finger lifted before threshold → tap
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

                                // 500ms elapsed with finger still down → drag activated.
                                dragActivated = true
                                onDragStarted(shipDef.id, currentAbs)

                                // Track drag until finger lifts.
                                // BUG FIX: Use PointerEventPass.Initial AND consume() every event
                                // so LazyRow does not scroll while the user drags a ship.
                                while (true) {
                                    val event: PointerEvent = awaitPointerEvent(
                                        pass = PointerEventPass.Initial
                                    )
                                    val pointer = event.changes.firstOrNull() ?: break
                                    pointer.consume() // prevent LazyRow scroll during drag
                                    if (!pointer.pressed) {
                                        // Finger lifted → end drag
                                        currentAbs = (shipItemPositions[shipDef.id]
                                            ?: Offset.Zero) + pointer.position
                                        onDragEnded(currentAbs)
                                        return@awaitEachGesture
                                    }
                                    currentAbs = (shipItemPositions[shipDef.id] ?: Offset.Zero) +
                                            pointer.position
                                    onDragMoved(shipDef.id, currentAbs)
                                }

                                // Pointer stream ended unexpectedly → cancel
                                if (dragActivated) {
                                    onDragCancelled()
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Ship name
                    Text(
                        text       = shipDef.name,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (isPlaced) ValidGreen else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )

                    // Ship cell visualisation
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

                    // Orientation indicator + rotate button
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text  = if (orientation is Orientation.Horizontal) "H" else "V",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Icon(
                            imageVector        = Icons.Default.Refresh,
                            contentDescription = "Rotate ${shipDef.name}",
                            modifier           = Modifier
                                .size(14.dp)
                                .clickable { onRotateShip(shipDef.id) },
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Placed checkmark
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