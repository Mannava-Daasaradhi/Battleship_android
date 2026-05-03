// ============================================================
// feature/settings/src/main/kotlin/com/battleship/fleetcommand/feature/settings/SettingsScreen.kt
// ============================================================
// FILE: feature/settings/src/main/kotlin/com/battleship/fleetcommand/feature/settings/SettingsScreen.kt
package com.battleship.fleetcommand.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var nameInput by remember(uiState.playerName) { mutableStateOf(uiState.playerName) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                SettingsViewModel.UiEffect.PopBackStack -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(SettingsViewModel.UiEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Player name
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PLAYER NAME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { viewModel.onEvent(SettingsViewModel.UiEvent.SetPlayerName(nameInput)) }) {
                                    Text("SAVE")
                                }
                            },
                        )
                    }
                }

                // Audio toggles
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AUDIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingsToggleRow(
                            label = "Sound Effects",
                            checked = uiState.soundEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsViewModel.UiEvent.SetSoundEnabled(it)) },
                        )
                        SettingsToggleRow(
                            label = "Background Music",
                            checked = uiState.musicEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsViewModel.UiEvent.SetMusicEnabled(it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}