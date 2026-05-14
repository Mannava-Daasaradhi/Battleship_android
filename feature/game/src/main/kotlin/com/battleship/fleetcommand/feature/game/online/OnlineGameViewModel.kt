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
    )

    enum class ConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }
    enum class GameStatus { WAITING, BATTLE, FINISHED }

    sealed class UiEvent {
        data class CellTapped(val coord: Coord) : UiEvent()
        data object ResignGame : UiEvent()
        data object ClaimVictoryOnTimeout : UiEvent()
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

    // ── Sunk-ship haptic dedup tracking ───────────────────────────────────
    // Tracks which of MY shots resulted in a SUNK that we already fired haptic for.
    // Key = shipId string from ShotData. Prevents re-firing on every Firebase update.
    private val attackerSunkHapticFiredFor = mutableSetOf<String>()

    // Tracks which opponent ships sunk US that we already fired haptic for (defender side).
    // Key = "row-col" of the sunk-triggering shot.
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
        val isMyTurn     = state.currentTurn == myUid

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

        // ── Build boards ───────────────────────────────────────────────────
        val newMyBoard       = buildMyBoard(state)
        val newOpponentBoard = buildOpponentBoard(state)

        _uiState.update {
            it.copy(
                isMyTurn          = isMyTurn,
                isAnimating       = if (isMyTurn) false else it.isAnimating,
                opponentName      = opponentName,
                opponentConnected = opponentConn,
                gameStatus        = newStatus,
                connectionStatus  = ConnectionStatus.CONNECTED,
                myBoard           = newMyBoard,
                opponentBoard     = newOpponentBoard,
            )
        }

        // ── Attacker haptic: fire SHIP_SUNK once per newly-sunk enemy ship ─
        // We look at myShots for any SUNK result whose shipId we haven't yet
        // fired haptic for. shipId in ShotData identifies which enemy ship sank.
        viewModelScope.launch {
            state.myShots
                .filter { shot -> shot.result == FireResult.SUNK && shot.shipId != null }
                .forEach { shot ->
                    val key = shot.shipId!! // non-null guarded above
                    if (attackerSunkHapticFiredFor.add(key)) {
                        // New sunk event — fire waveform haptic for attacker
                        hapticManager.perform(HapticEvent.SHIP_SUNK)
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

    // ── MY FLEET board ─────────────────────────────────────────────────────
    // Shows my ships; opponent's hits on them. Sunk ships turn orange.
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

        // Detect which of my ships are fully sunk by opponent hits
        val opponentShotCoords = state.opponentShots
            .filter { it.result == FireResult.HIT || it.result == FireResult.SUNK }
            .map { it.coord }.toSet()
        val sunkIds = myPlacements
            .filter { p -> p.occupiedCoords().all { it in opponentShotCoords } }
            .map { it.shipId }.toSet()

        // Colour all cells of sunk ships orange on my fleet board
        for (p in myPlacements.filter { it.shipId in sunkIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }

        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        val shipViews = myPlacements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId), p.shipId in sunkIds)
        }.toImmutableList()
        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }

    // ── ENEMY WATERS board ─────────────────────────────────────────────────
    // Shows fog of war; my hits/misses on opponent. Sunk enemy ships turn orange.
    //
    // How we detect sunk cells without knowing opponent's placements:
    // Firebase stores result=SUNK and shipId on the shot that triggered the sunk.
    // All prior shots whose shipId matches that SUNK shot are part of the same ship.
    // We colour ALL cells that share a shipId with any SUNK-result shot as SUNK.
    private fun buildOpponentBoard(state: OnlineGameState): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }

        // Collect all shipIds that have been fully sunk (result == SUNK)
        val sunkShipIds: Set<String> = state.myShots
            .filter { it.result == FireResult.SUNK && it.shipId != null }
            .map { it.shipId!! }
            .toSet()

        for (shot in state.myShots) {
            val coord = shot.coord
            if (!coord.isValid()) continue
            when (shot.result) {
                FireResult.SUNK -> {
                    // The sinking shot itself — mark SUNK (orange)
                    cells[coord.index] = CellDisplayState.SUNK
                }
                FireResult.HIT -> {
                    // This HIT might belong to a ship that was later sunk;
                    // check if its shipId is in the sunkShipIds set.
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
        // No ownShips on enemy board — ships stay hidden (showShips=false in UI)
        return BoardViewState(cells = cellViews)
    }

    private fun startObservingOpponentShots() {
        opponentShotJob?.cancel()
        opponentShotJob = viewModelScope.launch {
            repository.observeOpponentShots(gameId).collect { shots -> resolveNewOpponentShots(shots) }
        }
    }

    // ── Resolve shots fired at ME by the opponent ──────────────────────────
    // This runs on the DEFENDER's device. We:
    //   1. Check each unresolved shot against our placements
    //   2. Write the result back to Firebase
    //   3. Fire haptic feedback for HIT / MISS / SHIP_SUNK
    //      SHIP_SUNK haptic deduped via defenderSunkHapticFiredFor to avoid
    //      re-firing on every Firebase snapshot update.
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

            // Resolve the shipId to persist alongside the result.
            // For HIT and SUNK, shipId identifies which enemy ship was struck so the
            // ATTACKER's device can group hits by ship and render SUNK (orange) once
            // all cells of that ship are hit. MISS has no associated ship.
            val shipIdString: String? = when (outcome) {
                is ShotOutcome.Hit  -> outcome.shipId.name
                is ShotOutcome.Sunk -> outcome.shipId.name
                is ShotOutcome.Miss -> null
            }

            // Atomically write result + shipId + flip turn in ONE Firebase multi-path update.
            // Replaces the old two-call pattern (writeShotResult + flipTurn) that caused:
            //   1. Turn lag — attacker saw intermediate state with result written but turn not yet flipped.
            //   2. Partial SUNK rendering — board rebuilt between result-write and shipId-write,
            //      leaving shipId=null in the snapshot so all sunk cells rendered as HIT (red).
            // nextTurnUid = myUid because turns ALTERNATE — after the defender resolves the
            // attacker's shot, it becomes the DEFENDER's turn to fire. The defender is myUid
            // on this device. (opponentUid here is the attacker — the person who fired.)
            repository.commitShotAndFlipTurn(
                gameId      = gameId,
                shooterUid  = opponentUid,
                shotIndex   = index,
                result      = fireResult,
                shipId      = shipIdString,
                nextTurnUid = myUid,
            )

            // ── Defender haptic ────────────────────────────────────────────
            // SHIP_SUNK deduped: only fires once per sunk event.
            // For SUNK we use the shot key (each sunk has exactly one triggering shot).
            when (fireResult) {
                FireResult.HIT  -> hapticManager.perform(HapticEvent.HIT)
                FireResult.MISS -> hapticManager.perform(HapticEvent.MISS)
                FireResult.SUNK -> {
                    if (defenderSunkHapticFiredFor.add(key)) {
                        hapticManager.perform(HapticEvent.SHIP_SUNK)
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