// ============================================================
// feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/DifficultyScreen.kt
// ============================================================
// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/DifficultyScreen.kt
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
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.navigation.ShipPlacementRoute
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DifficultyScreen(
    navController: NavController,
    viewModel: DifficultyViewModel,
    route: com.battleship.fleetcommand.navigation.DifficultyRoute,
) {
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is DifficultyViewModel.UiEffect.NavigateToPlacement ->
                    navController.navigate(ShipPlacementRoute(mode = effect.mode))
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
                Text("SELECT DIFFICULTY", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                DifficultyCard("EASY", "AI fires randomly — good for beginners") { viewModel.selectDifficulty(Difficulty.EASY) }
                DifficultyCard("MEDIUM", "Hunt-and-target tactics after first hit") { viewModel.selectDifficulty(Difficulty.MEDIUM) }
                DifficultyCard("HARD", "Probability heat-map — Admiral AI") { viewModel.selectDifficulty(Difficulty.HARD) }
            }
        }
    }
}

@Composable
private fun DifficultyCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
