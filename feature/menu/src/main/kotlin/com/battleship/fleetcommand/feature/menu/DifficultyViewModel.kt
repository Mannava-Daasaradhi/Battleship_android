// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/DifficultyViewModel.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/DifficultyViewModel.kt
package com.battleship.fleetcommand.feature.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.navigation.DifficultyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DifficultyViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val route: DifficultyRoute = savedStateHandle.toRoute()

    sealed class UiEffect {
        data class NavigateToPlacement(val mode: String, val difficulty: String) : UiEffect()
    }

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    fun selectDifficulty(difficulty: Difficulty) {
        viewModelScope.launch {
            _uiEffect.emit(UiEffect.NavigateToPlacement(route.mode, difficulty.name))
        }
    }
}