// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentViewModel.kt

package com.battleship.fleetcommand.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class WaitingUiState(
    val opponentConnected: Boolean = false,
    val opponentName: String = "",
    val bothReady: Boolean = false
)

@HiltViewModel
class WaitingForOpponentViewModel @Inject constructor(
    private val repository: FirebaseMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaitingUiState())
    val uiState: StateFlow<WaitingUiState> = _uiState.asStateFlow()

    fun init(gameId: String) {
        viewModelScope.launch {
            repository.observeGameState(gameId)
                .catch { e -> Timber.e(e, "WaitingForOpponentViewModel: observeGameState error") }
                .collect { state ->
                    val myUid         = state.myUid
                    val opponentUid   = state.opponentUid
                    val opponentData  = state.players[opponentUid]
                    val opponentConn  = opponentData?.connected == true
                    val opponentName  = opponentData?.name ?: "Opponent"
                    val bothReady     = state.status == "battle" || state.status == "setup" &&
                            state.players.values.all { it.ready }

                    _uiState.update {
                        it.copy(
                            opponentConnected = opponentConn,
                            opponentName      = opponentName,
                            bothReady         = state.status == "battle"
                        )
                    }
                }
        }
    }
}