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
    private val soundManager: com.battleship.fleetcommand.core.ui.sound.SoundManager,
    private val hapticManager: com.battleship.fleetcommand.core.ui.haptic.HapticManager,
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
        val shotCount: Int = 0,      // player shots fired
        val aiShotCount: Int = 0,    // AI / opponent shots fired
        val hitCount: Int = 0,
        val isAiThinking: Boolean = false,
    )

    enum class GamePhase { BATTLE, GAME_OVER }

    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object AnimationComplete : UiEvent()
        /** Pass & Play only — called when returning from HandOff screen for P2's turn. */
        data object PassAndPlayResumeP2Turn : UiEvent()
        /** Pass & Play only — called when returning from HandOff screen for P1's turn. */
        data object PassAndPlayResumeP1Turn : UiEvent()
    }

    sealed class UiEffect {
        data class ShowHitAnimation(val coord: Coord) : UiEffect()
        data class ShowMissAnimation(val coord: Coord) : UiEffect()
        data class ShowSunkAnimation(val coord: Coord, val shipId: ShipId) : UiEffect()
        data class NavigateToGameOver(val gameId: String, val winner: String) : UiEffect()
        data object ShowResignDialog : UiEffect()
        /** Pass & Play — navigate to HandOff before switching active player. */
        data class NavigateToPassAndPlayHandOff(
            val gameId: String,
            val toPlayerName: String,
            val isP1Turn: Boolean,   // true = it will be P1's turn after handoff
        ) : UiEffect()
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
    // Atomic guard — set synchronously before any coroutine launch to prevent tap-spam
    private var isProcessingShot = false
    private var shotIndex = 0

    // Pass & Play state — tracks which player's board is "mine" on this device turn
    private var gameMode: GameMode = GameMode.AI
    private var p1Name: String = "Player 1"
    private var p2Name: String = "Player 2"
    // In LOCAL mode: p1Placements = Player 1's ships, p2Placements = Player 2's ships
    private var p1Placements: List<ShipPlacement> = emptyList()
    private var p2Placements: List<ShipPlacement> = emptyList()
    private var p1ShotHistory: MutableSet<Coord> = mutableSetOf()
    private var p2ShotHistory: MutableSet<Coord> = mutableSetOf()
    // true = it is currently P1's turn to fire at P2's board
    private var isP1ActiveTurn: Boolean = true

    init {
        viewModelScope.launch {
            loadGame()
        }
    }

    private suspend fun loadGame() {
        val game = gameRepository.getGame(gameId)
        gameMode = game?.mode ?: GameMode.AI
        p1Name = game?.player1Name ?: "Player 1"
        p2Name = game?.player2Name ?: "Player 2"

        when (gameMode) {
            GameMode.LOCAL -> {
                p1Placements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
                p2Placements = gameRepository.getBoardState(gameId, PlayerSlot.TWO) ?: emptyList()
                // Start with P1's turn: P1 fires at P2's board
                isP1ActiveTurn = true
                myPlacements = p1Placements
                aiPlacements = p2Placements
                _uiState.update {
                    it.copy(
                        mode = GameMode.LOCAL,
                        myName = p1Name,
                        opponentName = p2Name,
                        isMyTurn = true,
                    )
                }
                refreshPassAndPlayBoards()
            }
            GameMode.AI -> {
                myPlacements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
                val storedAi = gameRepository.getBoardState(gameId, PlayerSlot.TWO)
                aiPlacements = storedAi ?: generateAiPlacements().also { placements ->
                    gameRepository.saveBoardState(gameId, PlayerSlot.TWO, placements)
                }
                _uiState.update {
                    it.copy(mode = GameMode.AI, myName = "Player", opponentName = "AI")
                }
                refreshBoards()
            }
            GameMode.ONLINE -> {
                myPlacements = gameRepository.getBoardState(gameId, PlayerSlot.ONE) ?: emptyList()
                aiPlacements = gameRepository.getBoardState(gameId, PlayerSlot.TWO) ?: emptyList()
                refreshBoards()
            }
        }
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
            UiEvent.PassAndPlayResumeP2Turn -> resumePassAndPlayTurn(p1JustFired = true)
            UiEvent.PassAndPlayResumeP1Turn -> resumePassAndPlayTurn(p1JustFired = false)
        }
    }

    /** Called when HandOff is dismissed and the next player's turn begins in LOCAL mode. */
    private fun resumePassAndPlayTurn(p1JustFired: Boolean) {
        // Flip active player
        isP1ActiveTurn = !p1JustFired
        if (isP1ActiveTurn) {
            myPlacements = p1Placements
            aiPlacements = p2Placements
            myShotHistory = p1ShotHistory
            aiShotHistory = p2ShotHistory
            _uiState.update {
                it.copy(myName = p1Name, opponentName = p2Name, isMyTurn = true, isAnimating = false)
            }
        } else {
            myPlacements = p2Placements
            aiPlacements = p1Placements
            myShotHistory = p2ShotHistory
            aiShotHistory = p1ShotHistory
            _uiState.update {
                it.copy(myName = p2Name, opponentName = p1Name, isMyTurn = true, isAnimating = false)
            }
        }
        refreshPassAndPlayBoards()
        isProcessingShot = false
    }

    private fun handlePlayerShot(coord: Coord) {
        val state = _uiState.value
        // Guard checked synchronously on the main thread — prevents tap spam races
        if (!state.isMyTurn || state.isAnimating || isProcessingShot || coord in myShotHistory) return
        isProcessingShot = true  // set BEFORE launching coroutine

        viewModelScope.launch {
            _uiState.update { it.copy(isAnimating = true, isMyTurn = false, shotCount = it.shotCount + 1) }
            myShotHistory.add(coord)

            // Keep P&P histories in sync
            if (gameMode == GameMode.LOCAL) {
                if (isP1ActiveTurn) p1ShotHistory.add(coord) else p2ShotHistory.add(coord)
            }

            val outcome = withContext(Dispatchers.Default) {
                gameEngine.fireShot(coord, aiPlacements, myShotHistory - coord)
            }.getOrElse { ShotOutcome.Miss }

            // Persist the shot so GameOverScreen can show accuracy stats
            val fireResult = outcome.toFireResult()
            val firedBySlot = when {
                gameMode == GameMode.LOCAL && !isP1ActiveTurn -> PlayerSlot.TWO
                else -> PlayerSlot.ONE
            }
            gameRepository.saveShot(
                gameId,
                com.battleship.fleetcommand.core.domain.model.Shot(
                    gameId     = gameId,
                    shotIndex  = shotIndex++,
                    coord      = coord,
                    result     = fireResult,
                    firedBy    = firedBySlot,
                    timestamp  = System.currentTimeMillis(),
                )
            )

            when (outcome) {
                is ShotOutcome.Miss -> {
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.FIRE_CANNON)
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.MISS_SPLASH)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHOT_FIRED)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.MISS)
                    _uiEffect.emit(UiEffect.ShowMissAnimation(coord))
                }
                is ShotOutcome.Hit -> {
                    _uiState.update { it.copy(hitCount = it.hitCount + 1) }
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.FIRE_CANNON)
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.HIT_EXPLOSION)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHOT_FIRED)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.HIT)
                    _uiEffect.emit(UiEffect.ShowHitAnimation(coord))
                }
                is ShotOutcome.Sunk -> {
                    _uiState.update { it.copy(hitCount = it.hitCount + ShipRegistry.sizeOf(outcome.shipId)) }
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.FIRE_CANNON)
                    soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.SHIP_SUNK)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHOT_FIRED)
                    hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.SHIP_SUNK)
                    _uiEffect.emit(UiEffect.ShowSunkAnimation(coord, outcome.shipId))
                }
            }

            if (gameMode == GameMode.LOCAL) refreshPassAndPlayBoards() else refreshBoards()
            delay(700) // let animation finish

            // Check win
            if (checkAllSunk(aiPlacements, myShotHistory)) {
                soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.VICTORY)
                hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.VICTORY)
                isProcessingShot = false
                val winnerName = if (gameMode == GameMode.LOCAL) {
                    if (isP1ActiveTurn) p1Name else p2Name
                } else "Player"
                // Record the finished game in the DB (used by StatsRepository)
                runCatching {
                    val winnerSlot = if (gameMode == GameMode.LOCAL && !isP1ActiveTurn) PlayerSlot.TWO else PlayerSlot.ONE
                    gameRepository.finishGame(gameId, winnerSlot, 0L)
                }
                _uiEffect.emit(UiEffect.NavigateToGameOver(gameId, winnerName))
                return@launch
            }

            // After shot — AI responds or Pass & Play hands off
            if (gameMode == GameMode.LOCAL) {
                // Hand off to the other player
                val nextPlayerName = if (isP1ActiveTurn) p2Name else p1Name
                _uiState.update { it.copy(isAnimating = false) }
                isProcessingShot = false
                _uiEffect.emit(
                    UiEffect.NavigateToPassAndPlayHandOff(
                        gameId = gameId,
                        toPlayerName = nextPlayerName,
                        isP1Turn = !isP1ActiveTurn,
                    )
                )
            } else {
                isProcessingShot = false
                _uiState.update { it.copy(isAnimating = false, isAiThinking = true) }
                delay(600) // AI "thinking" delay
                performAiShot()
            }
        }
    }

    private suspend fun performAiShot() {
        val coord = withContext(Dispatchers.Default) {
            var candidate: Coord
            do {
                val idx = (0 until GameConstants.TOTAL_CELLS).random()
                candidate = Coord(idx)
            } while (candidate in aiShotHistory)
            candidate
        }

        aiShotHistory.add(coord)
        // Increment AI shot count atomically with the shot
        _uiState.update { it.copy(aiShotCount = it.aiShotCount + 1) }

        val outcome = withContext(Dispatchers.Default) {
            gameEngine.fireShot(coord, myPlacements, aiShotHistory - coord)
        }.getOrElse { ShotOutcome.Miss }

        // Persist AI shot so GameOverScreen stats are accurate
        gameRepository.saveShot(
            gameId,
            com.battleship.fleetcommand.core.domain.model.Shot(
                gameId     = gameId,
                shotIndex  = shotIndex++,
                coord      = coord,
                result     = outcome.toFireResult(),
                firedBy    = PlayerSlot.TWO,
                timestamp  = System.currentTimeMillis(),
            )
        )

        refreshBoards()

        if (checkAllSunk(myPlacements, aiShotHistory)) {
            _uiState.update { it.copy(isAiThinking = false) }
            soundManager.play(com.battleship.fleetcommand.core.ui.sound.GameSound.DEFEAT)
            hapticManager.perform(com.battleship.fleetcommand.core.ui.haptic.HapticEvent.DEFEAT)
            // Record the finished game in the DB (used by StatsRepository)
            runCatching { gameRepository.finishGame(gameId, PlayerSlot.TWO, 0L) }
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

    /**
     * In LOCAL mode the "active player" sees their own fleet and fires at the opponent's fog board.
     * P1 active: myBoard = P1 ships hit by P2 shots; opponentBoard = P2 fog hit by P1 shots.
     * P2 active: myBoard = P2 ships hit by P1 shots; opponentBoard = P1 fog hit by P2 shots.
     */
    private fun refreshPassAndPlayBoards() {
        val activeShots   = if (isP1ActiveTurn) p1ShotHistory else p2ShotHistory
        val incomingShots = if (isP1ActiveTurn) p2ShotHistory else p1ShotHistory
        val activePlacements   = if (isP1ActiveTurn) p1Placements else p2Placements
        val opponentPlacements = if (isP1ActiveTurn) p2Placements else p1Placements
        val myBoard       = buildPlayerBoard(activePlacements, incomingShots, showShips = true)
        val opponentBoard = buildFogBoard(opponentPlacements, activeShots)
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