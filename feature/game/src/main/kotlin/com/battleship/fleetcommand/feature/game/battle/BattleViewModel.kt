// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/battle/BattleViewModel.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/battle/BattleViewModel.kt
package com.battleship.fleetcommand.feature.game.battle

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.engine.GameEngine
import com.battleship.fleetcommand.core.domain.engine.ShotOutcome
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.ShipPlacementViewState
import com.battleship.fleetcommand.navigation.BattleRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BattleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val gameEngine: GameEngine,
) : ViewModel() {

    private val route: BattleRoute = savedStateHandle.toRoute()
    private val gameId: String = route.gameId

    @Immutable
    data class UiState(
        val mode: GameMode = GameMode.AI,
        val myBoard: BoardViewState = BoardViewState.empty(),
        val opponentBoard: BoardViewState = BoardViewState.empty(),
        val isMyTurn: Boolean = true,
        val currentTurnName: String = "",
        val isAnimating: Boolean = false,
        val gamePhase: GamePhase = GamePhase.BATTLE,
        val myName: String = "Player",
        val opponentName: String = "AI",
        val shotCount: Int = 0,
        val hitCount: Int = 0,
        val isAiThinking: Boolean = false,
    )

    enum class GamePhase { BATTLE, GAME_OVER }

    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object AnimationComplete : UiEvent()
    }

    sealed class UiEffect {
        data class ShowHitAnimation(val coord: Coord) : UiEffect()
        data class ShowMissAnimation(val coord: Coord) : UiEffect()
        data class ShowSunkAnimation(val coord: Coord, val shipId: ShipId) : UiEffect()
        data class NavigateToGameOver(val gameId: String, val winner: String) : UiEffect()
        data object ShowResignDialog : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    // Game state
    private var myPlacements: List<ShipPlacement> = emptyList()
    private var aiPlacements: List<ShipPlacement> = emptyList()
    private var myShotHistory: MutableSet<Coord> = mutableSetOf()
    private var aiShotHistory: MutableSet<Coord> = mutableSetOf()

    init {
        viewModelScope.launch {
            loadGame()
        }
    }

    private suspend fun loadGame() {
        myPlacements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
        // AI placements — generate if not stored
        val storedAi = gameRepository.getBoardState(gameId, PlayerSlot.TWO)
        aiPlacements = storedAi ?: generateAiPlacements().also { placements ->
            gameRepository.saveBoardState(gameId, PlayerSlot.TWO, placements)
        }
        refreshBoards()
    }

    private suspend fun generateAiPlacements(): List<ShipPlacement> = withContext(Dispatchers.Default) {
        val result = mutableListOf<ShipPlacement>()
        for (shipDef in ShipRegistry.ALL) {
            var placed = false
            while (!placed) {
                val orientation = if ((0..1).random() == 0)
                    com.battleship.fleetcommand.core.domain.Orientation.Horizontal
                else com.battleship.fleetcommand.core.domain.Orientation.Vertical
                val row = (0 until GameConstants.BOARD_SIZE).random()
                val col = (0 until GameConstants.BOARD_SIZE).random()
                val coord = Coord.fromRowCol(row, col)
                val placement = ShipPlacement(shipDef.id, coord, orientation)
                val errors = com.battleship.fleetcommand.core.domain.ship.PlacementValidator
                    .validate(placement, result, com.battleship.fleetcommand.core.domain.ship.AdjacencyMode.RELAXED)
                if (errors.isEmpty()) { result.add(placement); placed = true }
            }
        }
        result
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.CellTapped    -> handlePlayerShot(event.coord)
            UiEvent.ResignGame       -> viewModelScope.launch { _uiEffect.emit(UiEffect.ShowResignDialog) }
            UiEvent.AnimationComplete -> _uiState.update { it.copy(isAnimating = false) }
        }
    }

    private fun handlePlayerShot(coord: Coord) {
        val state = _uiState.value
        if (!state.isMyTurn || state.isAnimating || coord in myShotHistory) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnimating = true, shotCount = it.shotCount + 1) }
            myShotHistory.add(coord)

            val outcome = withContext(Dispatchers.Default) {
                gameEngine.fireShot(coord, aiPlacements, myShotHistory - coord)
            }.getOrElse { ShotOutcome.Miss }

            when (outcome) {
                is ShotOutcome.Miss -> {
                    _uiEffect.emit(UiEffect.ShowMissAnimation(coord))
                }
                is ShotOutcome.Hit -> {
                    _uiState.update { it.copy(hitCount = it.hitCount + 1) }
                    _uiEffect.emit(UiEffect.ShowHitAnimation(coord))
                }
                is ShotOutcome.Sunk -> {
                    _uiState.update { it.copy(hitCount = it.hitCount + ShipRegistry.sizeOf(outcome.shipId)) }
                    _uiEffect.emit(UiEffect.ShowSunkAnimation(coord, outcome.shipId))
                }
            }

            refreshBoards()
            delay(700) // let animation finish

            // Check win
            if (checkAllSunk(aiPlacements, myShotHistory)) {
                _uiEffect.emit(UiEffect.NavigateToGameOver(gameId, "Player"))
                return@launch
            }

            _uiState.update { it.copy(isAnimating = false, isMyTurn = false, isAiThinking = true) }
            delay(600) // AI "thinking" delay
            performAiShot()
        }
    }

    private suspend fun performAiShot() {
        val coord = withContext(Dispatchers.Default) {
            // Simple random AI for now; real AI injected via AiTurnProcessor in full integration
            var candidate: Coord
            do {
                val idx = (0 until GameConstants.TOTAL_CELLS).random()
                candidate = Coord(idx)
            } while (candidate in aiShotHistory)
            candidate
        }

        aiShotHistory.add(coord)
        val outcome = withContext(Dispatchers.Default) {
            gameEngine.fireShot(coord, myPlacements, aiShotHistory - coord)
        }.getOrElse { ShotOutcome.Miss }

        refreshBoards()

        if (checkAllSunk(myPlacements, aiShotHistory)) {
            _uiState.update { it.copy(isAiThinking = false) }
            _uiEffect.emit(UiEffect.NavigateToGameOver(gameId, "AI"))
            return
        }

        _uiState.update { it.copy(isMyTurn = true, isAiThinking = false) }
    }

    private fun checkAllSunk(placements: List<ShipPlacement>, shots: Set<Coord>): Boolean {
        return placements.all { placement ->
            placement.occupiedCoords().all { it in shots }
        }
    }

    private fun refreshBoards() {
        val myBoard = buildPlayerBoard(myPlacements, aiShotHistory, showShips = true)
        val opponentBoard = buildFogBoard(aiPlacements, myShotHistory)
        _uiState.update { it.copy(myBoard = myBoard, opponentBoard = opponentBoard) }
    }

    private fun buildPlayerBoard(placements: List<ShipPlacement>, incomingShots: Set<Coord>, showShips: Boolean): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        if (showShips) {
            for (p in placements) {
                for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SHIP
            }
        }
        for (shot in incomingShots) {
            if (!shot.isValid()) continue
            val hit = placements.any { shot in it.occupiedCoords() }
            cells[shot.index] = if (hit) CellDisplayState.HIT else CellDisplayState.MISS
        }
        val sunkShipIds = placements.filter { p -> p.occupiedCoords().all { it in incomingShots } }.map { it.shipId }.toSet()
        for (p in placements.filter { it.shipId in sunkShipIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        val shipViews = placements.map { p -> ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId), p.shipId in sunkShipIds) }.toImmutableList()
        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }

    private fun buildFogBoard(placements: List<ShipPlacement>, shots: Set<Coord>): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (shot in shots) {
            if (!shot.isValid()) continue
            val hit = placements.any { shot in it.occupiedCoords() }
            cells[shot.index] = if (hit) CellDisplayState.HIT else CellDisplayState.MISS
        }
        val sunkShipIds = placements.filter { p -> p.occupiedCoords().all { it in shots } }.map { it.shipId }.toSet()
        for (p in placements.filter { it.shipId in sunkShipIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        return BoardViewState(cells = cellViews)
    }
}