// ============================================================
// feature/settings/src/main/kotlin/com/battleship/fleetcommand/feature/settings/SettingsViewModel.kt
// ============================================================
// FILE: feature/settings/src/main/kotlin/com/battleship/fleetcommand/feature/settings/SettingsViewModel.kt
package com.battleship.fleetcommand.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
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
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    @Immutable
    data class UiState(
        val playerName: String = "",
        val soundEnabled: Boolean = true,
        val musicEnabled: Boolean = true,
    )

    sealed class UiEvent {
        data class SetPlayerName(val name: String) : UiEvent()
        data class SetSoundEnabled(val enabled: Boolean) : UiEvent()
        data class SetMusicEnabled(val enabled: Boolean) : UiEvent()
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
            preferencesRepository.observePlayerName().collect { name ->
                _uiState.update { it.copy(playerName = name) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.observeSoundEnabled().collect { enabled ->
                _uiState.update { it.copy(soundEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.observeMusicEnabled().collect { enabled ->
                _uiState.update { it.copy(musicEnabled = enabled) }
            }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.SetPlayerName    -> preferencesRepository.setPlayerName(event.name)
                is UiEvent.SetSoundEnabled  -> preferencesRepository.setSoundEnabled(event.enabled)
                is UiEvent.SetMusicEnabled  -> preferencesRepository.setMusicEnabled(event.enabled)
                UiEvent.NavigateBack        -> _uiEffect.emit(UiEffect.PopBackStack)
            }
        }
    }
}