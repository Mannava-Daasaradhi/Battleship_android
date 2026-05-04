// FILE: feature/menu/src/main/kotlin/com/battleship/fleetcommand/feature/menu/MainMenuScreen.kt
package com.battleship.fleetcommand.feature.menu

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.components.BattleshipButton
import com.battleship.fleetcommand.core.ui.theme.GoldAccent
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import com.battleship.fleetcommand.core.ui.theme.NavySurfaceVariant
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

    // Entrance animation
    var revealed by remember { mutableStateOf(false) }
    val titleScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "titleScale",
    )
    LaunchedEffect(Unit) { revealed = true }

    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to NavySurface,
                            0.5f to NavyBackground,
                            1.0f to NavyBackground,
                        )
                    )
                )
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Animated title block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { scaleX = titleScale; scaleY = titleScale },
                ) {
                    Text(
                        text = "⚓",
                        fontSize = 56.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "BATTLESHIP",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                        ),
                        color = GoldAccent,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "FLEET COMMAND",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }

                // Stats summary card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = NavySurfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatChip(label = "Wins", value = uiState.stats.wins.toString())
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        StatChip(label = "Win Rate", value = "${uiState.stats.winRatePercent}%")
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        StatChip(label = "Accuracy", value = "${uiState.stats.accuracyPercent}%")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                BattleshipButton(
                    text = "▶  PLAY",
                    onClick = { viewModel.onEvent(MenuViewModel.UiEvent.PlayVsAi) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MenuViewModel.UiEvent.OpenStatistics) },
                        modifier = Modifier.weight(1f),
                    ) { Text("STATS") }
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MenuViewModel.UiEvent.OpenSettings) },
                        modifier = Modifier.weight(1f),
                    ) { Text("SETTINGS") }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = GoldAccent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}