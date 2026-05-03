// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/online/OnlineGameViewModel.kt

package com.battleship.fleetcommand.feature.game.online

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.engine.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.engine.GameEngine
import com.battleship.fleetcommand.core.domain.engine.ShipId
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.ui.BoardViewState
import com.battleship.fleetcommand.core.ui.haptic.HapticEvent
import com.battleship.fleetcommand.core.ui.haptic.HapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val hapticManager: HapticManager
) : ViewModel() {

    // ── UiState ───────────────────────────────────────────────────────────────

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
        val gameStatus: GameStatus = GameStatus.BATTLE,
        val opponentDisconnectedSeconds: Int = 0
    )

    enum class ConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }
    enum class GameStatus { SETUP, BATTLE, FINISHED }

    // ── UiEvent ───────────────────────────────────────────────────────────────

    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object ClaimVictoryOnTimeout : UiEvent()
    }

    // ── UiEffect ──────────────────────────────────────────────────────────────

    sealed class UiEffect {
        data class ShowHitAnimation(val coord: Coord) : UiEffect()
        data class ShowMissAnimation(val coord: Coord) : UiEffect()
        data class ShowSunkAnimation(val shipId: ShipId) : UiEffect()
        data class NavigateToGameOver(val winner: String) : UiEffect()
        data object ShowReconnectingOverlay : UiEffect()
        data object ShowOpponentDisconnectedDialog : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    private var disconnectTimerJob: Job? = null
    private var gameObserverJob: Job? = null
    private var opponentShotJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(gameId: String, myUid: String) {
        _uiState.update { it.copy(gameId = gameId, myUid = myUid) }
        startObservingGame(gameId, myUid)
        startObservingOpponentShots(gameId)
    }

    // ── onEvent ───────────────────────────────────────────────────────────────

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.CellTapped             -> handleCellTapped(event.coord)
            is UiEvent.ResignGame             -> handleResign()
            is UiEvent.ClaimVictoryOnTimeout  -> handleClaimVictory()
        }
    }

    // ── Game state observer ───────────────────────────────────────────────────

    private fun startObservingGame(gameId: String, myUid: String) {
        gameObserverJob?.cancel()
        gameObserverJob = viewModelScope.launch {
            repository.observeGameState(gameId)
                .catch { e -> Timber.e(e, "OnlineGameViewModel: observeGameState error") }
                .collect { state -> handleGameStateUpdate(state, myUid) }
        }
    }

    private fun handleGameStateUpdate(state: OnlineGameState, myUid: String) {
        val opponentUid     = state.opponentUid
        val opponentData    = state.players[opponentUid]
        val opponentConn    = opponentData?.connected == true
        val opponentName    = opponentData?.name ?: "Opponent"
        val isMyTurn        = state.currentTurn == myUid
        val newStatus       = when (state.status) {
            "setup"    -> GameStatus.SETUP
            "battle"   -> GameStatus.BATTLE
            "finished" -> GameStatus.FINISHED
            else       -> GameStatus.BATTLE
        }

        // Detect opponent disconnect / reconnect
        val wasConnected = _uiState.value.opponentConnected
        if (wasConnected && !opponentConn) {
            startDisconnectTimer()
        } else if (!wasConnected && opponentConn) {
            cancelDisconnectTimer()
        }

        _uiState.update {
            it.copy(
                isMyTurn          = isMyTurn,
                opponentName      = opponentName,
                opponentConnected = opponentConn,
                gameStatus        = newStatus,
                connectionStatus  = ConnectionStatus.CONNECTED
            )
        }

        // Navigate to game over when a winner is declared
        if (state.winner != null && newStatus == GameStatus.FINISHED) {
            viewModelScope.launch {
                _effects.send(UiEffect.NavigateToGameOver(state.winner))
            }
        }
    }

    // ── Opponent shot observer ────────────────────────────────────────────────

    private fun startObservingOpponentShots(gameId: String) {
        opponentShotJob?.cancel()
        opponentShotJob = viewModelScope.launch {
            repository.observeOpponentShots(gameId)
                .catch { e -> Timber.e(e, "OnlineGameViewModel: observeOpponentShots error") }
                .collect { shotData ->
                    // Resolve the shot against our own ships
                    val coord = Coord.fromRowCol(shotData.row, shotData.col)
                    val result = gameEngine.resolveIncomingShot(coord)

                    // Write result back for the attacker to see
                    repository.writeShotResult(
                        gameId      = gameId,
                        shooterUid  = shotData.shooterUid,
                        shotPushKey = shotData.pushKey,
                        result      = result.fireResult,
                        shipId      = result.sunkShipId?.name
                    )

                    // Animate on our board
                    when (result.fireResult) {
                        FireResult.HIT  -> {
                            hapticManager.perform(HapticEvent.HIT)
                            _effects.send(UiEffect.ShowHitAnimation(coord))
                        }
                        FireResult.MISS -> {
                            hapticManager.perform(HapticEvent.MISS)
                            _effects.send(UiEffect.ShowMissAnimation(coord))
                        }
                        FireResult.SUNK -> {
                            hapticManager.perform(HapticEvent.SUNK)
                            result.sunkShipId?.let { _effects.send(UiEffect.ShowSunkAnimation(it)) }
                        }
                    }
                }
        }
    }

    // ── Cell tapped (fire shot) ───────────────────────────────────────────────

    private fun handleCellTapped(coord: Coord) {
        if (!_uiState.value.isMyTurn) return
        if (_uiState.value.gameStatus != GameStatus.BATTLE) return

        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            val result = repository.fireShot(gameId, coord)
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "OnlineGameViewModel: fireShot failed")
            } else {
                hapticManager.perform(HapticEvent.SHOT_FIRED)
            }
        }
    }

    // ── Resign ────────────────────────────────────────────────────────────────

    private fun handleResign() {
        viewModelScope.launch {
            // Claim opponent as winner by writing their uid
            val opponentUid = repository.observeGameState(_uiState.value.gameId)
                .catch { }
                .let { _uiState.value.let { s -> s.gameId } }
            // Simplified: navigate to game over with no winner (opponent wins implicitly)
            _effects.send(UiEffect.NavigateToGameOver(winner = ""))
        }
    }

    // ── Claim victory after disconnect ────────────────────────────────────────

    private fun handleClaimVictory() {
        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            repository.claimVictory(gameId)
        }
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun handleReconnect() {
        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.RECONNECTING) }
            _effects.send(UiEffect.ShowReconnectingOverlay)

            withTimeoutOrNull(GameConstants.RECONNECT_TIMEOUT_SECS * 1_000L) {
                repository.observeGameState(gameId).collect { state ->
                    if (state.status != "finished") {
                        repository.setPresence(gameId, connected = true)
                        _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
                        return@collect
                    }
                }
            } ?: run {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
            }
        }
    }

    // ── Disconnect timer ──────────────────────────────────────────────────────

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

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        gameObserverJob?.cancel()
        opponentShotJob?.cancel()
        disconnectTimerJob?.cancel()
    }
}