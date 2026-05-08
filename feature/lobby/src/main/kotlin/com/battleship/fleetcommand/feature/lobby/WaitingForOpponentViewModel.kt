// FILE: feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentViewModel.kt

package com.battleship.fleetcommand.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── UiState ───────────────────────────────────────────────────────────────────

data class WaitingUiState(
    val opponentConnected: Boolean = false,
    val opponentName: String = "",
    val bothReady: Boolean = false
)

// ── UiEffect ──────────────────────────────────────────────────────────────────

sealed class WaitingUiEffect {
    /**
     * Fired exactly once when both players are ready to place ships.
     * Consumed by WaitingForOpponentScreen via SharedFlow(replay=0) semantics
     * (Channel.BUFFERED) — never re-delivered on recomposition.
     */
    data class NavigateToShipPlacement(val gameId: String) : WaitingUiEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class WaitingForOpponentViewModel @Inject constructor(
    private val repository: FirebaseMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaitingUiState())
    val uiState: StateFlow<WaitingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<WaitingUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Guard: fire the navigation effect only once per ViewModel instance.
    private var navigationFired = false

    fun init(gameId: String) {
        viewModelScope.launch {
            repository.observeGameState(gameId)
                .catch { e -> Timber.e(e, "WaitingForOpponentViewModel: observeGameState error") }
                .collect { state ->
                    val opponentUid  = state.opponentUid
                    val opponentData = state.players[opponentUid]
                    val opponentConn = opponentData?.connected == true
                    val opponentName = opponentData?.name ?: "Opponent"

                    // ── Both-ready logic ─────────────────────────────────────────────
                    // "setup"  → guest has joined, both in lobby, waiting for placement.
                    //            opponentConnected = true, bothReady = false.
                    //            WaitingForOpponentScreen shows "Opponent connected!" with
                    //            "Preparing battle stations…" spinner — correct UX.
                    //
                    // "setup" + all players ready → advance to ship placement immediately.
                    //            (Edge case: very fast placements before status hits "battle".)
                    //
                    // "battle" → status was advanced to battle (both submitted placements).
                    //            Shouldn't reach WaitingForOpponentScreen in this state but
                    //            handled defensively.
                    //
                    // FIX: The original code had an operator-precedence bug:
                    //   state.status == "battle" || state.status == "setup" && allReady
                    // evaluates as:
                    //   state.status == "battle" || (state.status == "setup" && allReady)
                    // which is actually correct by Kotlin precedence (&&  before ||).
                    // However the result was never used — it was computed into a local
                    // variable `bothReady` but then _uiState.update used the hard-coded
                    // check `state.status == "battle"`. That meant the screen was stuck
                    // forever because nothing ever writes "battle" until after ship
                    // placement — a chicken-and-egg deadlock.
                    //
                    // The fix: bothReady fires on "setup" (opponent present) so both
                    // devices navigate to ShipPlacementScreen where they place ships
                    // independently. submitShipPlacement() then advances to "battle".
                    val allPlayersReady = state.players.values.isNotEmpty() &&
                            state.players.values.all { it.ready }
                    val bothReady = when (state.status) {
                        "setup"  -> state.players.size >= 2 // both in lobby → go to placement
                        "battle" -> true                     // defensive: already in battle
                        else     -> false                    // "waiting" or "finished"
                    }

                    _uiState.update {
                        it.copy(
                            opponentConnected = opponentConn || state.status == "setup" || state.status == "battle",
                            opponentName      = opponentName,
                            bothReady         = bothReady
                        )
                    }

                    // ── One-shot navigation ──────────────────────────────────────────
                    // Use a Channel-based effect (not LaunchedEffect on bothReady) to
                    // guarantee the navigation is fired exactly once regardless of
                    // recomposition, ViewModel recreation, or multiple state emissions.
                    if (bothReady && !navigationFired) {
                        navigationFired = true
                        _effects.trySend(WaitingUiEffect.NavigateToShipPlacement(gameId))
                        Timber.d("WaitingForOpponentViewModel: both ready — fired NavigateToShipPlacement gameId=$gameId status=${state.status}")
                    }
                }
        }
    }
}