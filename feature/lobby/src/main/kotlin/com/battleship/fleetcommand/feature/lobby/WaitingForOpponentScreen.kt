// ============================================================
// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentScreen.kt
// ============================================================
// FILE: feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/WaitingForOpponentScreen.kt
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

@Composable
fun WaitingForOpponentScreen(
    navController: NavController,
    viewModel: LobbyViewModel,
    route: com.battleship.fleetcommand.navigation.WaitingForOpponentRoute,
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
            ) {
                CircularProgressIndicator()
                Text(
                    "Waiting for opponent…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                OutlinedButton(onClick = { navController.popBackStack() }) {
                    Text("CANCEL")
                }
            }
        }
    }
}