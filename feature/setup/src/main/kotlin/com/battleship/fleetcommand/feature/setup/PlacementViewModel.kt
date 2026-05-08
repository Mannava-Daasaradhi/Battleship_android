// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/PlacementViewModel.kt
package com.battleship.fleetcommand.feature.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import com.battleship.fleetcommand.core.domain.ship.PlacementError
import com.battleship.fleetcommand.core.domain.ship.PlacementValidator
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.ShipPlacementViewState
import com.battleship.fleetcommand.navigation.ShipPlacementRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlacementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val firebaseMatchRepository: FirebaseMatchRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val route: ShipPlacementRoute = savedStateHandle.toRoute()
    private val gameMode = GameMode.fromStorageKey(route.mode)

    // FIX: playerSlot was inverted — route.playerSlot == 0 means P1, == 1 means P2.
    private val playerSlot = if (route.playerSlot == 0) PlayerSlot.ONE else PlayerSlot.TWO

    // FIX: read player names from route instead of hardcoding "Player 1" / "Player 2"
    private val p1Name = route.player1Name.ifBlank { "Player 1" }
    private val p2Name = route.player2Name.ifBlank { "Player 2" }

    @Immutable
    data class UiState(
        val board: BoardViewState = BoardViewState.empty(),
        val placements: List<ShipPlacement> = emptyList(),
        val orientations: Map<ShipId, Orientation> = ShipRegistry.ALL.associate { it.id to Orientation.Horizontal },
        val canConfirm: Boolean = false,
        val isAutoPlacing: Boolean = false,
        val isSubmitting: Boolean = false,
        val playerName: String = "Player",
        val mode: GameMode = GameMode.AI,
        val selectedShipId: ShipId? = null,
        val hoverCoords: Set<Coord> = emptySet(),
        val hoverValid: Boolean = false,
        val draggingShipId: ShipId? = null,
    )

    sealed class UiEvent {
        data class PlaceShip(val shipId: ShipId, val coord: Coord) : UiEvent()
        data class RotateShip(val shipId: ShipId) : UiEvent()
        data class SelectShip(val shipId: ShipId) : UiEvent()
        data class HoverShip(val shipId: ShipId, val coord: Coord) : UiEvent()
        data class SetDragging(val shipId: ShipId?) : UiEvent()
        data object ClearHover : UiEvent()
        data object AutoPlace : UiEvent()
        data object ClearAll : UiEvent()
        data object ConfirmPlacement : UiEvent()
    }

    sealed class UiEffect {
        data class NavigateToBattle(val gameId: String) : UiEffect()
        data class NavigateToOnlineBattle(val gameId: String, val myUid: String) : UiEffect()
        data class NavigateToHandOff(
            val gameId: String,
            val isP1HandOff: Boolean = false,
            val phase: String = "SETUP",
        ) : UiEffect()
        data class ShowPlacementError(val error: PlacementError) : UiEffect()
        data class ShowError(val message: String) : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState(mode = gameMode))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.PlaceShip       -> placeShip(event.shipId, event.coord)
                is UiEvent.RotateShip      -> rotateShip(event.shipId)
                is UiEvent.SelectShip      -> selectShip(event.shipId)
                is UiEvent.HoverShip       -> hoverShip(event.shipId, event.coord)
                is UiEvent.SetDragging     -> _uiState.update { it.copy(draggingShipId = event.shipId) }
                UiEvent.ClearHover         -> _uiState.update { it.copy(hoverCoords = emptySet(), hoverValid = false, draggingShipId = null) }
                UiEvent.AutoPlace          -> autoPlace()
                UiEvent.ClearAll           -> clearAll()
                UiEvent.ConfirmPlacement   -> confirmPlacement()
            }
        }
    }

    private fun placeShip(shipId: ShipId, coord: Coord) {
        val orientation = _uiState.value.orientations[shipId] ?: Orientation.Horizontal
        val newPlacement = ShipPlacement(shipId, coord, orientation)
        val existing = _uiState.value.placements.filter { it.shipId != shipId }
        val errors = PlacementValidator.validate(newPlacement, existing, AdjacencyMode.RELAXED)
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _uiEffect.emit(UiEffect.ShowPlacementError(errors.first())) }
            return
        }
        val newPlacements = existing + newPlacement
        _uiState.update {
            it.copy(
                placements = newPlacements,
                canConfirm = newPlacements.size == ShipRegistry.ALL.size,
                board = buildBoard(newPlacements),
                selectedShipId = null,
                draggingShipId = null,
                hoverCoords = emptySet(),
                hoverValid = false,
            )
        }
    }

    private fun selectShip(shipId: ShipId) {
        val current = _uiState.value.selectedShipId
        _uiState.update {
            it.copy(selectedShipId = if (current == shipId) null else shipId)
        }
    }

    private fun hoverShip(shipId: ShipId, coord: Coord) {
        if (!coord.isValid()) return  // sentinel from tray drag — ignore
        val orientation = _uiState.value.orientations[shipId] ?: Orientation.Horizontal
        val candidate = ShipPlacement(shipId, coord, orientation)
        val others = _uiState.value.placements.filter { it.shipId != shipId }
        val isValid = PlacementValidator.validate(candidate, others, AdjacencyMode.RELAXED).isEmpty()
        val hoverCoords = candidate.occupiedCoords().filter { it.isValid() }.toSet()
        _uiState.update { it.copy(hoverCoords = hoverCoords, hoverValid = isValid) }
    }

    private fun rotateShip(shipId: ShipId) {
        val current = _uiState.value.orientations[shipId] ?: Orientation.Horizontal
        val toggled = if (current is Orientation.Horizontal) Orientation.Vertical else Orientation.Horizontal
        val newOrientations = _uiState.value.orientations + (shipId to toggled)
        val existingPlacement = _uiState.value.placements.firstOrNull { it.shipId == shipId }
        if (existingPlacement != null) {
            val rotated = existingPlacement.copy(orientation = toggled)
            val others = _uiState.value.placements.filter { it.shipId != shipId }
            val errors = PlacementValidator.validate(rotated, others, AdjacencyMode.RELAXED)
            if (errors.isEmpty()) {
                val newPlacements = others + rotated
                _uiState.update {
                    it.copy(
                        orientations = newOrientations,
                        placements = newPlacements,
                        board = buildBoard(newPlacements),
                    )
                }
                return
            }
        }
        _uiState.update { it.copy(orientations = newOrientations) }
    }

    private suspend fun autoPlace() {
        _uiState.update { it.copy(isAutoPlacing = true) }
        val placements = withContext(Dispatchers.Default) { generateRandomPlacements() }
        val newOrientations = placements.associate { it.shipId to it.orientation }
        _uiState.update {
            it.copy(
                placements = placements,
                orientations = newOrientations,
                isAutoPlacing = false,
                canConfirm = true,
                board = buildBoard(placements),
            )
        }
    }

    private fun generateRandomPlacements(): List<ShipPlacement> {
        val result = mutableListOf<ShipPlacement>()
        for (shipDef in ShipRegistry.ALL) {
            var placed = false
            var attempts = 0
            while (!placed && attempts < 1000) {
                attempts++
                val orientation = if ((0..1).random() == 0) Orientation.Horizontal else Orientation.Vertical
                val row = (0 until GameConstants.BOARD_SIZE).random()
                val col = (0 until GameConstants.BOARD_SIZE).random()
                val coord = Coord.fromRowCol(row, col)
                val placement = ShipPlacement(shipDef.id, coord, orientation)
                if (PlacementValidator.validate(placement, result, AdjacencyMode.RELAXED).isEmpty()) {
                    result.add(placement); placed = true
                }
            }
        }
        return result
    }

    private fun clearAll() {
        _uiState.update {
            it.copy(
                placements = emptyList(),
                canConfirm = false,
                board = buildBoard(emptyList()),
                selectedShipId = null,
                draggingShipId = null,
                hoverCoords = emptySet(),
                hoverValid = false,
                orientations = ShipRegistry.ALL.associate { def -> def.id to Orientation.Horizontal },
            )
        }
    }

    private fun confirmPlacement() {
        if (!_uiState.value.canConfirm) return
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            when (gameMode) {
                GameMode.AI -> {
                    val gameId = gameRepository.createGame(
                        com.battleship.fleetcommand.core.domain.model.Game(
                            mode = gameMode,
                            player1Name = "Player", player2Name = "AI",
                            id = java.util.UUID.randomUUID().toString(),
                            startedAt = System.currentTimeMillis(),
                        )
                    )
                    gameRepository.saveBoardState(gameId, PlayerSlot.ONE, _uiState.value.placements)
                    _uiEffect.emit(UiEffect.NavigateToBattle(gameId))
                }

                GameMode.LOCAL -> {
                    if (playerSlot == PlayerSlot.ONE) {
                        val gameId = gameRepository.createGame(
                            com.battleship.fleetcommand.core.domain.model.Game(
                                mode = gameMode,
                                player1Name = p1Name,
                                player2Name = p2Name,
                                id = java.util.UUID.randomUUID().toString(),
                                startedAt = System.currentTimeMillis(),
                            )
                        )
                        gameRepository.saveBoardState(gameId, PlayerSlot.ONE, _uiState.value.placements)
                        _uiEffect.emit(UiEffect.NavigateToHandOff(gameId, isP1HandOff = true, phase = "SETUP"))
                    } else {
                        val gameId = route.gameId
                        gameRepository.saveBoardState(gameId, PlayerSlot.TWO, _uiState.value.placements)
                        _uiEffect.emit(UiEffect.NavigateToHandOff(gameId, isP1HandOff = false, phase = "SETUP"))
                    }
                }

                GameMode.ONLINE -> {
                    // ── Online placement confirmed ────────────────────────────────
                    // 1. Get the Firebase gameId from the route (passed from WaitingForOpponentScreen)
                    // 2. Submit ships to Firebase Realtime Database
                    // 3. Also save to Room so GameOverScreen can display board history
                    // 4. Navigate to OnlineBattleRoute (dedicated online battle screen)
                    //
                    // We do NOT go through HandOffScreen for online — there is no local handoff needed.
                    val firebaseGameId = route.gameId
                    if (firebaseGameId.isBlank()) {
                        Timber.e("PlacementViewModel: ONLINE confirmPlacement — gameId is blank!")
                        _uiEffect.emit(UiEffect.ShowError("Game session lost. Please restart."))
                        return@launch
                    }

                    _uiState.update { it.copy(isSubmitting = true) }

                    val myUid = preferencesRepository.getOnlinePlayerUid() ?: run {
                        _uiState.update { it.copy(isSubmitting = false) }
                        _uiEffect.emit(UiEffect.ShowError("Authentication lost. Please restart."))
                        return@launch
                    }

                    // Submit to Firebase
                    val result = firebaseMatchRepository.submitShipPlacement(
                        gameId = firebaseGameId,
                        ships  = _uiState.value.placements
                    )

                    _uiState.update { it.copy(isSubmitting = false) }

                    if (result.isFailure) {
                        val msg = result.exceptionOrNull()?.message ?: "Failed to submit ships. Check your connection."
                        Timber.e(result.exceptionOrNull(), "PlacementViewModel: submitShipPlacement failed")
                        _uiEffect.emit(UiEffect.ShowError(msg))
                        return@launch
                    }

                    Timber.d("PlacementViewModel: ONLINE ships submitted — navigating to OnlineBattle gameId=$firebaseGameId myUid=$myUid")
                    _uiEffect.emit(UiEffect.NavigateToOnlineBattle(firebaseGameId, myUid))
                }
            }
        }
    }

    private fun buildBoard(placements: List<ShipPlacement>): BoardViewState {
        val cellStates = Array(GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE) { CellDisplayState.WATER }
        for (placement in placements) {
            for (coord in placement.occupiedCoords()) {
                if (coord.isValid()) cellStates[coord.index] = CellDisplayState.SHIP
            }
        }
        val cells = cellStates.mapIndexed { i, state -> CellViewState(Coord(i), state) }.toImmutableList()
        val shipViews = placements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId))
        }.toImmutableList()
        return BoardViewState(cells = cells, ownShips = shipViews)
    }
}