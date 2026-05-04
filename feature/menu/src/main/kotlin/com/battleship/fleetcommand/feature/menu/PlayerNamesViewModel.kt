// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/PlayerNamesViewModel.kt
package com.battleship.fleetcommand.feature.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.navigation.PlayerNamesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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
class PlayerNamesViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val route: PlayerNamesRoute = savedStateHandle.toRoute()

    data class UiState(val player1: String = "Player 1", val player2: String = "Player 2")

    sealed class UiEffect {
        // FIX: pass player names through to ShipPlacementRoute so the Game record stores real names
        data class NavigateToPlacement(
            val mode: String,
            val player1Name: String,
            val player2Name: String,
        ) : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    fun setPlayer1(name: String) = _uiState.update { it.copy(player1 = name) }
    fun setPlayer2(name: String) = _uiState.update { it.copy(player2 = name) }

    fun confirm() {
        viewModelScope.launch {
            _uiEffect.emit(
                UiEffect.NavigateToPlacement(
                    mode = route.mode,
                    player1Name = _uiState.value.player1.trim().ifBlank { "Player 1" },
                    player2Name = _uiState.value.player2.trim().ifBlank { "Player 2" },
                )
            )
        }
    }
}