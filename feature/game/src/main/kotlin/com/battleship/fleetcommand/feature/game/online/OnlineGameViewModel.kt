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
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.model.GameResult
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.OnlineGameState
import com.battleship.fleetcommand.core.domain.multiplayer.ShotData
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnlineGameViewModel @Inject constructor(
    private val repository: FirebaseMatchRepository,
    private val gameRepository: GameRepository,
    private val statsRepository: StatsRepository,
    private val gameEngine: GameEngine,
    private val savedStateHandle: SavedStateHandle,
    private val hapticManager: HapticManager,
) : ViewModel() {

    private val route: OnlineBattleRoute = savedStateHandle.toRoute()
    private val gameId: String = route.gameId
    private val myUid: String = route.myUid

    @Immutable
    data class UiState(
        val gameId: String = "",
        val myUid: String = "",
        val isMyTurn: Boolean = false,
        val isAnimating: Boolean = false,
        val myBoard: BoardViewState = BoardViewState.empty(),
        val opponentBoard: BoardViewState = BoardViewState.empty(),
        val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        val opponentName: String = "Opponent",
        val opponentConnected: Boolean = true,
        val gameStatus: GameStatus = GameStatus.WAITING,
        val opponentDisconnectedSeconds: Int = 0,
        val myShipCount: Int = 0,
        val opponentSunkCount: Int = 0,
        val sunkNotificationMessage: String? = null,
    )

    enum class ConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }
    enum class GameStatus { WAITING, BATTLE, FINISHED }

    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object ClaimVictoryOnTimeout : UiEvent()
        data object SunkNotificationShown : UiEvent()
    }

    sealed class UiEffect {
        data class ShowHitAnimation(val coord: Coord) : UiEffect()
        data class ShowMissAnimation(val coord: Coord) : UiEffect()
        data class ShowSunkAnimation(val shipId: ShipId) : UiEffect()
        data class NavigateToGameOver(val winner: String, val totalShots: Int, val accuracy: Int) : UiEffect()
        data object ShowReconnectingOverlay : UiEffect()
        data object ShowOpponentDisconnectedDialog : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState(gameId = gameId, myUid = myUid))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var disconnectTimerJob: Job? = null
    private var gameObserverJob: Job? = null
    private var opponentShotJob: Job? = null
    private var myPlacements: List<ShipPlacement> = emptyList()

    private val resolvedShotKeys = mutableSetOf<String>()

    private var opponentUid: String = ""
    private var navigatedToGameOver = false

    private val attackerSunkHapticFiredFor = mutableSetOf<String>()
    private val defenderSunkHapticFiredFor = mutableSetOf<String>()

    init {
        startObservingGame()
        startObservingOpponentShots()
        setPresence(connected = true)
        loadMyPlacements()
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.CellTapped            -> handleCellTapped(event.coord)
            is UiEvent.ResignGame            -> handleResign()
            is UiEvent.ClaimVictoryOnTimeout -> handleClaimVictory()
            is UiEvent.SunkNotificationShown -> _uiState.update { it.copy(sunkNotificationMessage = null) }
        }
    }

    private fun loadMyPlacements() {
        viewModelScope.launch {
            try {
                var placements: List<ShipPlacement>? = null
                repeat(LOAD_MAX_RETRIES) { attempt ->
                    if (placements != null) return@repeat
                    val loaded = gameRepository.getBoardState(gameId, PlayerSlot.ONE)
                    if (!loaded.isNullOrEmpty()) {
                        placements = loaded
                    } else if (attempt < LOAD_MAX_RETRIES - 1) {
                        delay(LOAD_RETRY_DELAY_MS)
                    }
                }

                if (!placements.isNullOrEmpty()) {
                    myPlacements = placements!!
                    _uiState.update { it.copy(myBoard = buildMyBoardFromPlacements(placements!!)) }
                }
            } catch (e: Exception) {}
        }
    }

    private fun buildMyBoardFromPlacements(placements: List<ShipPlacement>): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (p in placements) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SHIP
        }
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        val shipViews = placements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId), false)
        }.toImmutableList()
        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }

    private fun startObservingGame() {
        gameObserverJob?.cancel()
        gameObserverJob = viewModelScope.launch {
            repository.observeGameState(gameId)
                .catch { e -> Timber.e(e, "observeGameState error") }
                .collect { state -> handleGameStateUpdate(state) }
        }
    }

    private fun handleGameStateUpdate(state: OnlineGameState) {
        opponentUid = state.opponentUid

        val opponentData = state.players[state.opponentUid]
        val opponentConn = opponentData?.connected == true
        val opponentName = opponentData?.name ?: "Opponent"
        
        val isMyTurnFromState = state.currentTurn == myUid

        val resolvedIsMyTurn = if (_uiState.value.isAnimating && isMyTurnFromState) {
            false
        } else {
            isMyTurnFromState
        }

        val newStatus = when (state.status) {
            "setup"    -> GameStatus.WAITING
            "battle"   -> GameStatus.BATTLE
            "finished" -> GameStatus.FINISHED
            else       -> GameStatus.WAITING
        }

        val wasConnected = _uiState.value.opponentConnected
        if (wasConnected && !opponentConn) {
            startDisconnectTimer()
        } else if (!wasConnected && opponentConn) {
            cancelDisconnectTimer()
        }

        val newMyBoard       = buildMyBoard(state)
        val newOpponentBoard = buildOpponentBoard(state)

        _uiState.update {
            it.copy(
                isMyTurn          = resolvedIsMyTurn,
                isAnimating       = if (!isMyTurnFromState) false else it.isAnimating,
                opponentName      = opponentName,
                opponentConnected = opponentConn,
                gameStatus        = newStatus,
                connectionStatus  = ConnectionStatus.CONNECTED,
                myBoard           = newMyBoard,
                opponentBoard     = newOpponentBoard,
            )
        }

        viewModelScope.launch {
            state.myShots
                .filter { shot -> shot.result == FireResult.SUNK && shot.shipId != null }
                .forEach { shot ->
                    val key = shot.shipId!! 
                    if (attackerSunkHapticFiredFor.add(key)) {
                        hapticManager.perform(HapticEvent.SHIP_SUNK)
                        val shipDisplayName = key.lowercase().replaceFirstChar { it.uppercase() }
                        _uiState.update {
                            it.copy(sunkNotificationMessage = "You sunk the $shipDisplayName!")
                        }
                    }
                }
        }

        val myTotalHits = state.myShots.count {
            it.result == FireResult.HIT || it.result == FireResult.SUNK
        }

        if (myTotalHits == 17 && state.status != "finished") {
            viewModelScope.launch { repository.claimVictory(gameId) }
        }

        val winner = state.winner ?: ""
        if (winner.isNotEmpty() && newStatus == GameStatus.FINISHED && !navigatedToGameOver) {
            navigatedToGameOver = true

            val isWin = (winner == myUid)
            val displayWinner = if (isWin) "You" else opponentName

            val totalShots = state.myShots.size
            val accuracy = if (totalShots == 0) 0 else (myTotalHits * 100) / totalShots

            val matchResult = GameResult(
                winner = if (isWin) PlayerSlot.ONE else PlayerSlot.TWO,
                mode = GameMode.ONLINE,
                totalShots = totalShots,
                totalHits = myTotalHits,
                durationSeconds = 0L
            )

            viewModelScope.launch {
                try {
                    statsRepository.recordGameResult(matchResult)
                } catch (e: Exception) {}
                _effects.send(UiEffect.NavigateToGameOver(displayWinner, totalShots, accuracy))
            }
        }
    }

    private fun buildMyBoard(state: OnlineGameState): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (p in myPlacements) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SHIP
        }
        for (shot in state.opponentShots) {
            val coord = shot.coord
            if (!coord.isValid()) continue
            when (shot.result) {
                FireResult.HIT, FireResult.SUNK -> cells[coord.index] = CellDisplayState.HIT
                FireResult.MISS -> cells[coord.index] = CellDisplayState.MISS
                null -> { }
            }
        }

        val opponentShotCoords = state.opponentShots
            .filter { it.result == FireResult.HIT || it.result == FireResult.SUNK }
            .map { it.coord }.toSet()
        val sunkIds = myPlacements
            .filter { p -> p.occupiedCoords().all { it in opponentShotCoords } }
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

    private fun buildOpponentBoard(state: OnlineGameState): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }

        val sunkShipIds: Set<String> = state.myShots
            .filter { it.result == FireResult.SUNK && it.shipId != null }
            .map { it.shipId!! }
            .toSet()

        for (shot in state.myShots) {
            val coord = shot.coord
            if (!coord.isValid()) continue
            when (shot.result) {
                FireResult.SUNK -> cells[coord.index] = CellDisplayState.SUNK
                FireResult.HIT -> {
                    val displayState = if (shot.shipId != null && shot.shipId in sunkShipIds) {
                        CellDisplayState.SUNK
                    } else {
                        CellDisplayState.HIT
                    }
                    cells[coord.index] = displayState
                }
                FireResult.MISS -> cells[coord.index] = CellDisplayState.MISS
                null -> { }
            }
        }

        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        return BoardViewState(cells = cellViews)
    }

    private fun startObservingOpponentShots() {
        opponentShotJob?.cancel()
        opponentShotJob = viewModelScope.launch {
            repository.observeOpponentShots(gameId).collect { shots -> resolveNewOpponentShots(shots) }
        }
    }

    private suspend fun resolveNewOpponentShots(shots: List<ShotData>) {
        shots.forEachIndexed { index, shotData ->
            if (shotData.result != null) return@forEachIndexed
            
            val key = "$index-${shotData.row}-${shotData.col}"
            if (key in resolvedShotKeys) return@forEachIndexed
            resolvedShotKeys.add(key)

            val coord = Coord.fromRowCol(shotData.row, shotData.col)

            val alreadyShotCoords = shots.take(index).map { it.coord }.toSet()
            val outcome: ShotOutcome = gameEngine.fireShot(coord, myPlacements, alreadyShotCoords)
                .getOrElse { ShotOutcome.Miss }

            val fireResult = when (outcome) {
                is ShotOutcome.Hit  -> FireResult.HIT
                is ShotOutcome.Sunk -> FireResult.SUNK
                is ShotOutcome.Miss -> FireResult.MISS
            }

            val shipIdString: String? = when (outcome) {
                is ShotOutcome.Hit  -> outcome.shipId.name
                is ShotOutcome.Sunk -> outcome.shipId.name
                is ShotOutcome.Miss -> null
            }

            val commitResult = repository.commitShotAndFlipTurn(
                gameId      = gameId,
                shooterUid  = opponentUid,
                shotIndex   = index,
                result      = fireResult,
                shipId      = shipIdString,
                nextTurnUid = myUid,
            )

            // CRITICAL FIX: If the write fails, we must remove it from the set so it 
            // can be retried on the next snapshot, otherwise the game locks permanently.
            if (commitResult.isFailure) {
                resolvedShotKeys.remove(key)
                Timber.e(commitResult.exceptionOrNull(), "commitShotAndFlipTurn failed for key: $key")
                return@forEachIndexed
            }

            when (fireResult) {
                FireResult.HIT  -> hapticManager.perform(HapticEvent.HIT)
                FireResult.MISS -> hapticManager.perform(HapticEvent.MISS)
                FireResult.SUNK -> {
                    if (defenderSunkHapticFiredFor.add(key)) {
                        hapticManager.perform(HapticEvent.SHIP_SUNK)
                        val shipDisplayName = (shipIdString ?: "Ship")
                            .lowercase().replaceFirstChar { it.uppercase() }
                        _uiState.update {
                            it.copy(sunkNotificationMessage = "Your $shipDisplayName was sunk!")
                        }
                    }
                }
            }
        }
    }

    private fun handleCellTapped(coord: Coord) {
        if (!_uiState.value.isMyTurn || _uiState.value.isAnimating) return
        if (_uiState.value.gameStatus != GameStatus.BATTLE) return

        _uiState.update { it.copy(isMyTurn = false, isAnimating = true) }

        viewModelScope.launch {
            val result = repository.fireShot(gameId, coord)
            if (result.isFailure) {
                _uiState.update { it.copy(isMyTurn = true, isAnimating = false) }
            } else {
                hapticManager.perform(HapticEvent.SHOT_FIRED)
            }
        }
    }

    private fun handleResign() {
        if (navigatedToGameOver) return
        _uiState.update { it.copy(isMyTurn = false) }
        viewModelScope.launch {
            repository.forfeit(gameId, opponentUid)
        }
    }

    private fun handleClaimVictory() {
        viewModelScope.launch { repository.claimVictory(gameId) }
    }

    private fun setPresence(connected: Boolean) {
        viewModelScope.launch { repository.setPresence(gameId, connected) }
    }

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

    override fun onCleared() {
        super.onCleared()
        gameObserverJob?.cancel()
        opponentShotJob?.cancel()
        disconnectTimerJob?.cancel()
        setPresence(connected = false)
    }

    companion object {
        private const val LOAD_MAX_RETRIES    = 5
        private const val LOAD_RETRY_DELAY_MS = 300L
    }
}