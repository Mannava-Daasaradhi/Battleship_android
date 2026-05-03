// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/ModeSelectViewModel.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/ModeSelectViewModel.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.model.GameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeSelectViewModel @Inject constructor() : ViewModel() {

    sealed class UiEvent {
        data object SelectAi : UiEvent()
        data object SelectLocal : UiEvent()
        data object SelectOnline : UiEvent()
    }

    sealed class UiEffect {
        data class NavigateToDifficulty(val mode: String) : UiEffect()
        data class NavigateToPlayerNames(val mode: String) : UiEffect()
        data object NavigateToOnlineLobby : UiEffect()
    }

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.SelectAi     -> _uiEffect.emit(UiEffect.NavigateToDifficulty(GameMode.AI.name))
                UiEvent.SelectLocal  -> _uiEffect.emit(UiEffect.NavigateToPlayerNames(GameMode.LOCAL.name))
                UiEvent.SelectOnline -> _uiEffect.emit(UiEffect.NavigateToOnlineLobby)
            }
        }
    }
}