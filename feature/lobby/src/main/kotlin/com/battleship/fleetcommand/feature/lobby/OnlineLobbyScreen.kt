// ============================================================
// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/OnlineLobbyScreen.kt
// ============================================================
// FILE: feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/OnlineLobbyScreen.kt
package com.battleship.fleetcommand.feature.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.battleship.fleetcommand.core.ui.theme.NavyBackground
import com.battleship.fleetcommand.core.ui.theme.NavySurface

// Online Lobby — Phase 3 foundations present; full wiring in Phase 5 (Firebase integration)
@Composable
fun OnlineLobbyScreen(
    navController: NavController,
    viewModel: LobbyViewModel,
    route: com.battleship.fleetcommand.navigation.OnlineLobbyRoute,
) {
    Scaffold(modifier = Modifier.safeDrawingPadding()) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NavySurface, NavyBackground)))
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text("🌐", style = MaterialTheme.typography.displayLarge)
                Text("ONLINE LOBBY", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Online multiplayer — coming in the next phase.\nFirebase matchmaking will be wired here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                OutlinedButton(onClick = { navController.popBackStack() }) {
                    Text("BACK")
                }
            }
        }
    }
}