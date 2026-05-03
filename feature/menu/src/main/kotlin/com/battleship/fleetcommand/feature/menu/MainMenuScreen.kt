// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/MainMenuScreen.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/MainMenuScreen.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.ModeSelectRoute
import com.battleship.fleetcommand.navigation.SettingsRoute
import com.battleship.fleetcommand.navigation.StatisticsRoute
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainMenuScreen(
    navController: NavController,
    viewModel: MenuViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                MenuViewModel.UiEffect.NavigateToModeSelect   -> navController.navigate(ModeSelectRoute)
                MenuViewModel.UiEffect.NavigateToSettings     -> navController.navigate(SettingsRoute)
                MenuViewModel.UiEffect.NavigateToStatistics   -> navController.navigate(StatisticsRoute)
            }
        }
    }

    // ADS PLACEHOLDER — owner will integrate AdMob here in a future update

    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(NavySurface, NavyBackground))
                )
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Title
                Text(
                    text = "⚓",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "BATTLESHIP\nFLEET COMMAND",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stats summary
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatChip(label = "Wins", value = uiState.stats.wins.toString())
                        StatChip(label = "Win Rate", value = "${uiState.stats.winRatePercent}%")
                        StatChip(label = "Accuracy", value = "${uiState.stats.accuracyPercent}%")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                BattleshipButton(
                    text = "PLAY",
                    onClick = { viewModel.onEvent(MenuViewModel.UiEvent.PlayVsAi) },
                    modifier = Modifier.fillMaxWidth(),
                )
                BattleshipButton(
                    text = "STATISTICS",
                    onClick = { viewModel.onEvent(MenuViewModel.UiEvent.OpenStatistics) },
                    modifier = Modifier.fillMaxWidth(),
                )
                BattleshipButton(
                    text = "SETTINGS",
                    onClick = { viewModel.onEvent(MenuViewModel.UiEvent.OpenSettings) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}