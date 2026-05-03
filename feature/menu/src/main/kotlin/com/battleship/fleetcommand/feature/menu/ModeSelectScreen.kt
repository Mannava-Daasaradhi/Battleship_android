// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/ModeSelectScreen.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/ModeSelectScreen.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.DifficultyRoute
import com.battleship.fleetcommand.navigation.OnlineLobbyRoute
import com.battleship.fleetcommand.navigation.PlayerNamesRoute
import kotlinx.coroutines.flow.collectLatest

// ADS PLACEHOLDER — owner will integrate AdMob here in a future update

@Composable
fun ModeSelectScreen(
    navController: NavController,
    viewModel: ModeSelectViewModel,
) {
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is ModeSelectViewModel.UiEffect.NavigateToDifficulty ->
                    navController.navigate(DifficultyRoute(mode = effect.mode))
                is ModeSelectViewModel.UiEffect.NavigateToPlayerNames ->
                    navController.navigate(PlayerNamesRoute(mode = effect.mode))
                ModeSelectViewModel.UiEffect.NavigateToOnlineLobby ->
                    navController.navigate(OnlineLobbyRoute)
            }
        }
    }

    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "SELECT MODE",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ModeCard(
                    emoji = "🤖",
                    title = "vs AI",
                    subtitle = "Easy · Medium · Hard",
                    onClick = { viewModel.onEvent(ModeSelectViewModel.UiEvent.SelectAi) },
                )
                ModeCard(
                    emoji = "👥",
                    title = "Pass & Play",
                    subtitle = "Local two-player on this device",
                    onClick = { viewModel.onEvent(ModeSelectViewModel.UiEvent.SelectLocal) },
                )
                ModeCard(
                    emoji = "🌐",
                    title = "Online",
                    subtitle = "Firebase real-time matchmaking",
                    onClick = { viewModel.onEvent(ModeSelectViewModel.UiEvent.SelectOnline) },
                )
            }
        }
    }
}

@Composable
private fun ModeCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineLarge)
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}