// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/LobbyViewModel.kt

package com.battleship.fleetcommand.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import com.battleship.fleetcommand.core.domain.multiplayer.GameCreationResult
import com.battleship.fleetcommand.core.domain.multiplayer.JoinResult
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── UiState ───────────────────────────────────────────────────────────────────

data class LobbyUiState(
    val mode: LobbyMode = LobbyMode.CHOOSE,
    val roomCode: String = "",
    val roomCodeInput: String = "",
    val hostName: String = "",
    val guestName: String = "",
    val opponentConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class LobbyMode { CHOOSE, HOSTING, JOINING }

// ── UiEvent ───────────────────────────────────────────────────────────────────

sealed class LobbyUiEvent {
    data object HostGame : LobbyUiEvent()
    data object JoinGame : LobbyUiEvent()
    data class RoomCodeInputChanged(val code: String) : LobbyUiEvent()
    data object ConfirmJoin : LobbyUiEvent()
    data object CancelLobby : LobbyUiEvent()
}

// ── UiEffect ──────────────────────────────────────────────────────────────────

sealed class LobbyUiEffect {
    data class NavigateToWaiting(val gameId: String) : LobbyUiEffect()
    data class ShowError(val message: String) : LobbyUiEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class LobbyViewModel @Inject constructor(
    private val repository: FirebaseMatchRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LobbyUiState())
    val uiState: StateFlow<LobbyUiState> = _uiState.asStateFlow()

    private val _effects = Channel<LobbyUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: LobbyUiEvent) {
        when (event) {
            is LobbyUiEvent.HostGame             -> handleHostGame()
            is LobbyUiEvent.JoinGame             -> handleJoinGame()
            is LobbyUiEvent.RoomCodeInputChanged -> handleRoomCodeInputChanged(event.code)
            is LobbyUiEvent.ConfirmJoin          -> handleConfirmJoin()
            is LobbyUiEvent.CancelLobby          -> handleCancelLobby()
        }
    }

    // ── HostGame ──────────────────────────────────────────────────────────────

    private fun handleHostGame() {
        viewModelScope.launch {
            val playerName = preferencesRepository.observePlayerName().first().ifBlank { "Host" }

            _uiState.update { it.copy(isLoading = true, error = null, mode = LobbyMode.HOSTING) }

            repository.createGame(playerName)
                .catch { e ->
                    val msg = e.message ?: "Failed to create game. Check your connection."
                    Timber.e(e, "LobbyViewModel: createGame flow error")
                    _uiState.update { it.copy(isLoading = false, error = msg, mode = LobbyMode.CHOOSE) }
                    _effects.send(LobbyUiEffect.ShowError(msg))
                }
                .collect { result ->
                    when (result) {
                        is GameCreationResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    roomCode  = result.roomCode,
                                    hostName  = playerName
                                )
                            }
                            _effects.send(LobbyUiEffect.NavigateToWaiting(result.gameId))
                        }
                        is GameCreationResult.Failure -> {
                            Timber.e("LobbyViewModel: createGame failed reason=${result.reason}")
                            _uiState.update {
                                it.copy(isLoading = false, error = result.reason, mode = LobbyMode.CHOOSE)
                            }
                            _effects.send(LobbyUiEffect.ShowError(result.reason))
                        }
                    }
                }
        }
    }

    // ── JoinGame (switch to JOIN mode) ────────────────────────────────────────

    private fun handleJoinGame() {
        _uiState.update { it.copy(mode = LobbyMode.JOINING, error = null) }
    }

    // ── RoomCodeInputChanged ──────────────────────────────────────────────────

    private fun handleRoomCodeInputChanged(code: String) {
        val normalised = code.uppercase()
            .filter { it.isLetterOrDigit() }
            .take(6)
        _uiState.update { it.copy(roomCodeInput = normalised) }
    }

    // ── ConfirmJoin ───────────────────────────────────────────────────────────

    private fun handleConfirmJoin() {
        val roomCode = _uiState.value.roomCodeInput
        if (roomCode.length != 6) {
            viewModelScope.launch {
                _effects.send(LobbyUiEffect.ShowError("Room code must be exactly 6 characters"))
            }
            return
        }

        viewModelScope.launch {
            val playerName = preferencesRepository.observePlayerName().first().ifBlank { "Guest" }

            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.joinGame(roomCode, playerName)
                .catch { e ->
                    val msg = e.message ?: "Network error. Check your connection."
                    Timber.e(e, "LobbyViewModel: joinGame flow error")
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                    _effects.send(LobbyUiEffect.ShowError(msg))
                }
                .collect { result ->
                    when (result) {
                        is JoinResult.Success -> {
                            _uiState.update { it.copy(isLoading = false, guestName = playerName) }
                            _effects.send(LobbyUiEffect.NavigateToWaiting(result.gameId))
                        }
                        is JoinResult.NotFound -> {
                            val msg = "Room code not found. Check the code and try again."
                            _uiState.update { it.copy(isLoading = false, error = msg) }
                            _effects.send(LobbyUiEffect.ShowError(msg))
                        }
                        is JoinResult.GameAlreadyStarted -> {
                            val msg = "This game has already started."
                            _uiState.update { it.copy(isLoading = false, error = msg) }
                            _effects.send(LobbyUiEffect.ShowError(msg))
                        }
                        is JoinResult.Failure -> {
                            val msg = result.reason.ifBlank { "Network error. Check your connection." }
                            _uiState.update { it.copy(isLoading = false, error = msg) }
                            _effects.send(LobbyUiEffect.ShowError(msg))
                        }
                    }
                }
        }
    }

    // ── CancelLobby ───────────────────────────────────────────────────────────

    private fun handleCancelLobby() {
        _uiState.update { LobbyUiState() }
    }
}