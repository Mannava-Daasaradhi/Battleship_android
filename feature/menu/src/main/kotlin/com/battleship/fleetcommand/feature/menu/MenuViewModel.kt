// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/MenuViewModel.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/MenuViewModel.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
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
class MenuViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    @Immutable
    data class UiState(
        val stats: PlayerStats = PlayerStats.empty(),
        val playerName: String = "Admiral",
    )

    sealed class UiEvent {
        data object PlayVsAi : UiEvent()
        data object PlayLocal : UiEvent()
        data object PlayOnline : UiEvent()
        data object OpenSettings : UiEvent()
        data object OpenStatistics : UiEvent()
    }

    sealed class UiEffect {
        data object NavigateToModeSelect : UiEffect()
        data object NavigateToSettings : UiEffect()
        data object NavigateToStatistics : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            statsRepository.observeStats().collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.PlayVsAi,
                UiEvent.PlayLocal,
                UiEvent.PlayOnline  -> _uiEffect.emit(UiEffect.NavigateToModeSelect)
                UiEvent.OpenSettings   -> _uiEffect.emit(UiEffect.NavigateToSettings)
                UiEvent.OpenStatistics -> _uiEffect.emit(UiEffect.NavigateToStatistics)
            }
        }
    }
}