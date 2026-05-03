// ============================================================
// feature/stats/src/main/kotlin/com/battleship/fleetcommand/feature/stats/StatisticsScreen.kt
// ============================================================
// FILE: feature/stats/src/main/kotlin/com/battleship/fleetcommand/feature/stats/StatisticsScreen.kt
package com.battleship.fleetcommand.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface
import kotlinx.coroutines.flow.collectLatest

// ADS PLACEHOLDER — owner will integrate AdMob here in a future update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: StatsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                StatsViewModel.UiEffect.PopBackStack -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("STATISTICS") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(StatsViewModel.UiEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                StatisticsContent(stats = uiState.stats)
            }
        }
    }
}

@Composable
private fun StatisticsContent(stats: PlayerStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatSection(title = "OVERALL") {
            StatRow("Games Played", stats.totalGames.toString())
            StatRow("Wins", stats.wins.toString())
            StatRow("Losses", stats.losses.toString())
            StatRow("Win Rate", "${stats.winRatePercent}%")
            StatRow("Accuracy", "${stats.accuracyPercent}%")
            StatRow("Win Streak", stats.currentStreak.toString())
            StatRow("Best Streak", stats.winStreak.toString())
            if (stats.hasBestTime) {
                val mins = stats.bestTimeSeconds / 60
                val secs = stats.bestTimeSeconds % 60
                StatRow("Best Time (Hard)", "%d:%02d".format(mins, secs))
            }
        }
        StatSection(title = "BY MODE") {
            StatRow("vs AI Wins", stats.aiWins.toString())
            StatRow("Local Wins", stats.localWins.toString())
            StatRow("Online Wins", stats.onlineWins.toString())
        }
        StatSection(title = "SHOTS") {
            StatRow("Total Shots", stats.totalShots.toString())
            StatRow("Total Hits", stats.totalHits.toString())
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}