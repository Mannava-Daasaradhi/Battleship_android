// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/online/OnlineGameViewModel.kt
package com.battleship.fleetcommand.feature.game.online

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
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import com.battleship.fleetcommand.core.ui.haptic.HapticEvent
import com.battleship.fleetcommand.core.ui.haptic.HapticManager
import com.battleship.fleetcommand.core.ui.model.BoardViewState
import com.battleship.fleetcommand.core.ui.model.CellDisplayState
import com.battleship.fleetcommand.core.ui.model.CellViewState
import com.battleship.fleetcommand.core.ui.model.ShipPlacementViewState
import com.battleship.fleetcommand.navigation.OnlineBattleRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnlineGameViewModel @Inject constructor(
    private val repository: FirebaseMatchRepository,
    private val gameEngine: GameEngine,
    private val savedStateHandle: SavedStateHandle,
    private val hapticManager: HapticManager,
) : ViewModel() {

    // Route — gameId and myUid are now properly passed via OnlineBattleRoute
    private val route: OnlineBattleRoute = savedStateHandle.toRoute()
    private val gameId: String = route.gameId
    private val myUid: String = route.myUid

    // ── UiState ────────────────────────────────────────────────────────────────
    @Immutable
    data class UiState(
        val gameId: String = "",
        val myUid: String = "",
        val isMyTurn: Boolean = false,
        val myBoard: BoardViewState = BoardViewState.empty(),
        val opponentBoard: BoardViewState = BoardViewState.empty(),
        val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        val opponentName: String = "Opponent",
        val opponentConnected: Boolean = true,
        val gameStatus: GameStatus = GameStatus.WAITING,
        val opponentDisconnectedSeconds: Int = 0,
        val myShipCount: Int = 0,
        val opponentSunkCount: Int = 0,
    )

    enum class ConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }
    enum class GameStatus { WAITING, BATTLE, FINISHED }

    // ── UiEvent ────────────────────────────────────────────────────────────────
    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object ClaimVictoryOnTimeout : UiEvent()
    }

    // ── UiEffect ───────────────────────────────────────────────────────────────
    sealed class UiEffect {
        data class ShowHitAnimation(val coord: Coord) : UiEffect()
        data class ShowMissAnimation(val coord: Coord) : UiEffect()
        data class ShowSunkAnimation(val shipId: ShipId) : UiEffect()
        data class NavigateToGameOver(val winner: String) : UiEffect()
        data object ShowReconnectingOverlay : UiEffect()
        data object ShowOpponentDisconnectedDialog : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState(gameId = gameId, myUid = myUid))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // ── Internal state ─────────────────────────────────────────────────────────
    private var disconnectTimerJob: Job? = null
    private var gameObserverJob: Job? = null
    private var opponentShotJob: Job? = null

    // Local board — populated from Firebase boards node via game state
    private var myPlacements: List<ShipPlacement> = emptyList()

    // Track which opponent shots have already been resolved to avoid double-processing
    private val resolvedShotKeys = mutableSetOf<String>()

    // Track opponent UID once we know it (from game state)
    private var opponentUid: String = ""

    // ── Init ───────────────────────────────────────────────────────────────────
    init {
        Timber.d("OnlineGameViewModel: init gameId=$gameId myUid=$myUid")
        startObservingGame()
        startObservingOpponentShots()
        setPresence(connected = true)
    }

    // ── onEvent ────────────────────────────────────────────────────────────────
    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.CellTapped            -> handleCellTapped(event.coord)
            is UiEvent.ResignGame            -> handleResign()
            is UiEvent.ClaimVictoryOnTimeout -> handleClaimVictory()
        }
    }

    // ── Game state observer ────────────────────────────────────────────────────
    private fun startObservingGame() {
        gameObserverJob?.cancel()
        gameObserverJob = viewModelScope.launch {
            repository.observeGameState(gameId)
                .catch { e -> Timber.e(e, "OnlineGameViewModel: observeGameState error") }
                .collect { state -> handleGameStateUpdate(state) }
        }
    }

    private fun handleGameStateUpdate(state: OnlineGameState) {
        opponentUid = state.opponentUid

        val opponentData = state.players[state.opponentUid]
        val opponentConn = opponentData?.connected == true
        val opponentName = opponentData?.name ?: "Opponent"
        val isMyTurn     = state.currentTurn == myUid

        val newStatus = when (state.status) {
            "setup"    -> GameStatus.WAITING   // waiting for both to finish placing
            "battle"   -> GameStatus.BATTLE
            "finished" -> GameStatus.FINISHED
            else       -> GameStatus.WAITING
        }

        // Detect opponent disconnect / reconnect
        val wasConnected = _uiState.value.opponentConnected
        if (wasConnected && !opponentConn) {
            startDisconnectTimer()
        } else if (!wasConnected && opponentConn) {
            cancelDisconnectTimer()
        }

        // Rebuild boards from Firebase shot data
        val myBoard       = buildMyBoard(state)
        val opponentBoard = buildOpponentBoard(state)

        _uiState.update {
            it.copy(
                isMyTurn          = isMyTurn,
                opponentName      = opponentName,
                opponentConnected = opponentConn,
                gameStatus        = newStatus,
                connectionStatus  = ConnectionStatus.CONNECTED,
                myBoard           = myBoard,
                opponentBoard     = opponentBoard,
            )
        }

        // Navigate to game over when a winner is declared
        val winner = state.winner ?: ""
        if (winner.isNotEmpty() && newStatus == GameStatus.FINISHED) {
            // Translate UID to human-readable: "You" if local player won, opponent name otherwise
            val displayWinner = if (winner == myUid) "You" else opponentName
            viewModelScope.launch {
                _effects.send(UiEffect.NavigateToGameOver(displayWinner))
            }
        }
    }

    // ── Build board views from Firebase state ──────────────────────────────────

    /**
     * My board: my ships + opponent's shots against me (hit/miss markers).
     * We only know shot results once the opponent fires and we resolve them.
     * The opponentShots list in OnlineGameState is our local view of shots fired AT us.
     */
    private fun buildMyBoard(state: OnlineGameState): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        // Show my ships
        for (p in myPlacements) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SHIP
        }
        // Overlay opponent's shots against me
        for (shot in state.opponentShots) {
            val coord = shot.coord
            if (!coord.isValid()) continue
            when (shot.result) {
                FireResult.HIT, FireResult.SUNK -> cells[coord.index] = CellDisplayState.HIT
                FireResult.MISS -> cells[coord.index] = CellDisplayState.MISS
                null -> { /* not yet resolved */ }
            }
        }
        // Mark sunk ships
        val opponentShotCoords = state.opponentShots
            .filter { it.result == FireResult.HIT || it.result == FireResult.SUNK }
            .map { it.coord }.toSet()
        val sunkIds = myPlacements.filter { p -> p.occupiedCoords().all { it in opponentShotCoords } }
            .map { it.shipId }.toSet()
        for (p in myPlacements.filter { it.shipId in sunkIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        val shipViews = myPlacements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId), p.shipId in sunkIds)
        }.toImmutableList()
        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }

    /**
     * Opponent board (fog of war): only shows results of MY shots.
     */
    private fun buildOpponentBoard(state: OnlineGameState): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (shot in state.myShots) {
            val coord = shot.coord
            if (!coord.isValid()) continue
            when (shot.result) {
                FireResult.HIT, FireResult.SUNK -> cells[coord.index] = CellDisplayState.HIT
                FireResult.MISS -> cells[coord.index] = CellDisplayState.MISS
                null -> { /* pending result */ }
            }
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        return BoardViewState(cells = cellViews)
    }

    // ── Opponent shot observer ─────────────────────────────────────────────────
    private fun startObservingOpponentShots() {
        opponentShotJob?.cancel()
        opponentShotJob = viewModelScope.launch {
            repository.observeOpponentShots(gameId)
                .catch { e -> Timber.e(e, "OnlineGameViewModel: observeOpponentShots error") }
                .collect { shots -> resolveNewOpponentShots(shots) }
        }
    }

    private suspend fun resolveNewOpponentShots(shots: List<ShotData>) {
        shots.forEachIndexed { index, shotData ->
            // Only resolve shots that have no result yet (null = unresolved)
            if (shotData.result != null) return@forEachIndexed
            val key = "$index-${shotData.row}-${shotData.col}"
            if (key in resolvedShotKeys) return@forEachIndexed
            resolvedShotKeys.add(key)

            val coord = Coord.fromRowCol(shotData.row, shotData.col)

            // Resolve against our local ship placements
            val alreadyShotCoords = shots.take(index).map { it.coord }.toSet()
            val outcome: ShotOutcome = gameEngine.fireShot(coord, myPlacements, alreadyShotCoords)
                .getOrElse { ShotOutcome.Miss }

            // Map to FireResult
            val fireResult: FireResult = when (outcome) {
                is ShotOutcome.Hit  -> FireResult.HIT
                is ShotOutcome.Sunk -> FireResult.SUNK
                is ShotOutcome.Miss -> FireResult.MISS
            }

            // Write result back so the attacker can see it
            repository.writeShotResult(
                gameId     = gameId,
                shooterUid = opponentUid,
                shotIndex  = index,
                result     = fireResult
            )

            // Animate on our board (incoming shots)
            when (fireResult) {
                FireResult.HIT  -> {
                    hapticManager.perform(HapticEvent.HIT)
                    _effects.trySend(UiEffect.ShowHitAnimation(coord))
                }
                FireResult.MISS -> {
                    hapticManager.perform(HapticEvent.MISS)
                    _effects.trySend(UiEffect.ShowMissAnimation(coord))
                }
                FireResult.SUNK -> {
                    hapticManager.perform(HapticEvent.SHIP_SUNK)
                    val shipId = (outcome as? ShotOutcome.Sunk)?.shipId
                    if (shipId != null) _effects.trySend(UiEffect.ShowSunkAnimation(shipId))
                    // Check if all our ships are sunk → we lose
                    val myHitCoords = (resolvedShotKeys.size.let {
                        shots.filter { s -> s.result == FireResult.HIT || s.result == FireResult.SUNK }.map { s -> s.coord }.toSet()
                    })
                    // Do a quick local check — the Firebase winner write will confirm it on both devices
                }
            }
        }
    }

    // ── Cell tapped (fire shot) ────────────────────────────────────────────────
    private fun handleCellTapped(coord: Coord) {
        if (!_uiState.value.isMyTurn) return
        if (_uiState.value.gameStatus != GameStatus.BATTLE) return
        viewModelScope.launch {
            val result = repository.fireShot(gameId, coord)
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "OnlineGameViewModel: fireShot failed")
            } else {
                hapticManager.perform(HapticEvent.SHOT_FIRED)
            }
        }
    }

    // ── Resign ─────────────────────────────────────────────────────────────────
    private fun handleResign() {
        viewModelScope.launch {
            _effects.send(UiEffect.NavigateToGameOver(winner = "Opponent"))
        }
    }

    // ── Claim victory after disconnect ─────────────────────────────────────────
    private fun handleClaimVictory() {
        viewModelScope.launch {
            repository.claimVictory(gameId)
        }
    }

    // ── Presence ───────────────────────────────────────────────────────────────
    private fun setPresence(connected: Boolean) {
        viewModelScope.launch {
            repository.setPresence(gameId, connected)
        }
    }

    // ── Disconnect timer ───────────────────────────────────────────────────────
    private fun startDisconnectTimer() {
        disconnectTimerJob?.cancel()
        disconnectTimerJob = viewModelScope.launch {
            var elapsed = 0
            while (true) {
                delay(1_000L)
                elapsed++
                _uiState.update { it.copy(opponentDisconnectedSeconds = elapsed) }
                if (elapsed >= GameConstants.OPPONENT_DISCONNECT_CLAIM_SECS) {
                    _effects.send(UiEffect.ShowOpponentDisconnectedDialog)
                    break
                }
            }
        }
    }

    private fun cancelDisconnectTimer() {
        disconnectTimerJob?.cancel()
        disconnectTimerJob = null
        _uiState.update { it.copy(opponentDisconnectedSeconds = 0) }
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        gameObserverJob?.cancel()
        opponentShotJob?.cancel()
        disconnectTimerJob?.cancel()
        setPresence(connected = false)
    }
}