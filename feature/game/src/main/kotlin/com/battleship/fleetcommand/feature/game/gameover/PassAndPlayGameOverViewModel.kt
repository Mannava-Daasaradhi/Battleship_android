// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/PassAndPlayGameOverViewModel.kt
package com.battleship.fleetcommand.feature.game.gameover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.ShipPlacementViewState
import com.battleship.fleetcommand.navigation.PassAndPlayGameOverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PassAndPlayGameOverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val route: PassAndPlayGameOverRoute = savedStateHandle.toRoute()

    @Immutable
    data class UiState(
        val winner: String = "",
        val p1Board: BoardViewState = BoardViewState.empty(),
        val p2Board: BoardViewState = BoardViewState.empty(),
        val p1Accuracy: Int = 0,
        val p1Shots: Int = 0,
        val p2Accuracy: Int = 0,
        val p2Shots: Int = 0,
        val isLoading: Boolean = true,
    )

    sealed class UiEvent {
        data object PlayAgain : UiEvent()
        data object MainMenu : UiEvent()
        data object ViewStats : UiEvent()
    }

    sealed class UiEffect {
        data object NavigateToMainMenu : UiEffect()
        data object NavigateToStatistics : UiEffect()
        data object NavigateToModeSelect : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState(winner = route.winner))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            loadGameResult()
        }
    }

    private suspend fun loadGameResult() {
        try {
            val gameId = route.gameId
            val p1Placements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
            val p2Placements = gameRepository.getBoardState(gameId, PlayerSlot.TWO) ?: emptyList()
            val shots = gameRepository.getShots(gameId)
            
            val p1ShotsList = shots.filter { it.firedBy == PlayerSlot.ONE }
            val p2ShotsList = shots.filter { it.firedBy == PlayerSlot.TWO }

            val p1ShotCoords = p1ShotsList.map { it.coord }.toSet()
            val p2ShotCoords = p2ShotsList.map { it.coord }.toSet()
            
            val p1Hits = p1ShotsList.count { s -> p2Placements.any { p -> s.coord in p.occupiedCoords() } }
            val p2Hits = p2ShotsList.count { s -> p1Placements.any { p -> s.coord in p.occupiedCoords() } }
            
            val p1Acc = if (p1ShotsList.isEmpty()) 0 else (p1Hits * 100) / p1ShotsList.size
            val p2Acc = if (p2ShotsList.isEmpty()) 0 else (p2Hits * 100) / p2ShotsList.size

            val p1Board = buildRevealedBoard(p1Placements, p2ShotCoords)
            val p2Board = buildRevealedBoard(p2Placements, p1ShotCoords)

            _uiState.update {
                it.copy(
                    p1Board = p1Board,
                    p2Board = p2Board,
                    p1Accuracy = p1Acc,
                    p1Shots = p1ShotsList.size,
                    p2Accuracy = p2Acc,
                    p2Shots = p2ShotsList.size,
                    isLoading = false,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.PlayAgain -> _uiEffect.emit(UiEffect.NavigateToModeSelect)
                UiEvent.MainMenu -> _uiEffect.emit(UiEffect.NavigateToMainMenu)
                UiEvent.ViewStats -> _uiEffect.emit(UiEffect.NavigateToStatistics)
            }
        }
    }

    private fun buildRevealedBoard(placements: List<ShipPlacement>, shots: Set<Coord>): BoardViewState {
        val sunkIds = placements.filter { p -> p.occupiedCoords().all { it in shots } }.map { it.shipId }.toSet()
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (p in placements) {
            for (c in p.occupiedCoords()) {
                if (c.isValid()) cells[c.index] = when {
                    p.shipId in sunkIds -> CellDisplayState.SUNK
                    else -> CellDisplayState.SHIP
                }
            }
        }
        for (shot in shots) {
            if (!shot.isValid()) continue
            val hit = placements.any { shot in it.occupiedCoords() }
            if (!hit) cells[shot.index] = CellDisplayState.MISS
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        val shipViews = placements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId), p.shipId in sunkIds)
        }.toImmutableList()
        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }
}