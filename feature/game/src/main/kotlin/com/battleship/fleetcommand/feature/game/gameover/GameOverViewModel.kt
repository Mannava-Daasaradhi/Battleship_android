// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/GameOverViewModel.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/gameover/GameOverViewModel.kt
package com.battleship.fleetcommand.feature.game.gameover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.ShipPlacementViewState
import com.battleship.fleetcommand.navigation.GameOverRoute
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
class GameOverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val route: GameOverRoute = savedStateHandle.toRoute()

    @Immutable
    data class UiState(
        val winner: String = "",
        val isPlayerWin: Boolean = false,
        val myBoard: BoardViewState = BoardViewState.empty(),
        val opponentBoard: BoardViewState = BoardViewState.empty(),
        val accuracy: Int = 0,
        val totalShots: Int = 0,
        val isLoading: Boolean = true,
    )

    sealed class UiEvent {
        data object PlayAgain : UiEvent()
        data object MainMenu : UiEvent()
        data object ViewStats : UiEvent()
    }

    sealed class UiEffect {
        // ADS PLACEHOLDER — owner will integrate AdMob interstitial here in a future update
        data object NavigateToMainMenu : UiEffect()
        data object NavigateToStatistics : UiEffect()
        data object NavigateToModeSelect : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            loadGameResult()
        }
    }

    private suspend fun loadGameResult() {
        val gameId = route.gameId
        val winner = route.winner

        // winner == "AI" means AI won in single-player; any other value is a player name
        _uiState.update { it.copy(winner = winner, isPlayerWin = winner != "AI") }

        if (gameId.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        // Wrap all DB access in try/catch — a Room exception (migration gap, missing row, etc.)
        // must NEVER crash the process. Worst case we show the result card without board details.
        try {
            val myPlacements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
            val aiPlacements = gameRepository.getBoardState(gameId, PlayerSlot.TWO) ?: emptyList()
            val shots        = gameRepository.getShots(gameId)
            val myShots      = shots.filter { it.firedBy == PlayerSlot.ONE }
            val aiShots      = shots.filter { it.firedBy == PlayerSlot.TWO }

            val myShotCoords  = myShots.map { it.coord }.toSet()
            val aiShotCoords  = aiShots.map { it.coord }.toSet()
            val hits = myShots.count { s ->
                aiPlacements.any { p -> s.coord in p.occupiedCoords() }
            }
            val accuracy = if (myShots.isEmpty()) 0 else (hits * 100) / myShots.size

            val myBoard       = buildRevealedBoard(myPlacements, aiShotCoords)
            val opponentBoard = buildRevealedBoard(aiPlacements, myShotCoords)

            _uiState.update {
                it.copy(
                    myBoard       = myBoard,
                    opponentBoard = opponentBoard,
                    accuracy      = accuracy,
                    totalShots    = myShots.size,
                    isLoading     = false,
                )
            }
        } catch (e: Exception) {
            // DB read failed — still dismiss the loading spinner so the result card is shown
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.PlayAgain  -> _uiEffect.emit(UiEffect.NavigateToModeSelect)
                UiEvent.MainMenu   -> _uiEffect.emit(UiEffect.NavigateToMainMenu)
                UiEvent.ViewStats  -> _uiEffect.emit(UiEffect.NavigateToStatistics)
            }
        }
    }

    private fun buildRevealedBoard(placements: List<ShipPlacement>, shots: Set<Coord>): BoardViewState {
        val sunkIds = placements.filter { p -> p.occupiedCoords().all { it in shots } }.map { it.shipId }.toSet()
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        // Show all ships (revealed board)
        for (p in placements) {
            for (c in p.occupiedCoords()) {
                if (c.isValid()) cells[c.index] = when {
                    p.shipId in sunkIds -> CellDisplayState.SUNK
                    else                -> CellDisplayState.SHIP
                }
            }
        }
        // Overlay shots
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