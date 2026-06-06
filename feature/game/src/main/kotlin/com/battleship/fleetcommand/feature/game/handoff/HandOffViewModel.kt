// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/handoff/HandOffViewModel.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/handoff/HandOffViewModel.kt
package com.battleship.fleetcommand.feature.game.handoff

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.ui.haptic.HapticEvent
import com.battleship.fleetcommand.core.ui.haptic.HapticManager
import com.battleship.fleetcommand.navigation.HandOffRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
class HandOffViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val hapticManager: HapticManager,
) : ViewModel() {

    private val route: HandOffRoute = savedStateHandle.toRoute()

    @Immutable
    data class UiState(
        val countdown: Int = GameConstants.HANDOFF_COUNTDOWN_SECS,
        val canProceed: Boolean = false,
        val fromPlayer: String = "Player 1",
        val toPlayer: String = "Player 2",
    )

    sealed class UiEvent {
        data object Proceed : UiEvent()
    }

    sealed class UiEffect {
        data object NavigateToNextScreen : UiEffect()
    }

    private val _uiState = MutableStateFlow(
        UiState(toPlayer = route.toPlayerName.ifBlank { "the next player" })
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    init {
        startCountdown()
    }

    private fun startCountdown() {
        viewModelScope.launch {
            var remaining = GameConstants.HANDOFF_COUNTDOWN_SECS
            while (remaining > 0) {
                _uiState.update { it.copy(countdown = remaining) }
                hapticManager.perform(HapticEvent.COUNTDOWN_TICK)
                delay(1_000L)
                remaining--
            }
            _uiState.update { it.copy(countdown = 0, canProceed = true) }
            hapticManager.perform(HapticEvent.HAND_OFF_GO)
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.Proceed -> if (_uiState.value.canProceed) {
                    _uiEffect.emit(UiEffect.NavigateToNextScreen)
                }
            }
        }
    }
}