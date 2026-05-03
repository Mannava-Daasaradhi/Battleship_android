// ============================================================
// feature/stats/src/main/kotlin/com/battleship/fleetcommand/feature/stats/StatsViewModel.kt
// ============================================================
// FILE: feature/stats/src/main/kotlin/com/battleship/fleetcommand/feature/stats/StatsViewModel.kt
package com.battleship.fleetcommand.feature.stats

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
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    @Immutable
    data class UiState(
        val stats: PlayerStats = PlayerStats.empty(),
        val isLoading: Boolean = true,
    )

    sealed class UiEvent {
        data object NavigateBack : UiEvent()
    }

    sealed class UiEffect {
        data object PopBackStack : UiEffect()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<UiEffect>(replay = 0)
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            statsRepository.observeStats().collect { stats ->
                _uiState.update { it.copy(stats = stats, isLoading = false) }
            }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                UiEvent.NavigateBack -> _uiEffect.emit(UiEffect.PopBackStack)
            }
        }
    }
}