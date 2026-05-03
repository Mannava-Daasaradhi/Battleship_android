// ============================================================
// feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/RoomCodeDisplay.kt
// ============================================================
// FILE: feature/lobby/src/main/kotlin/com/battleship/fleetcommand/feature/lobby/RoomCodeDisplay.kt
package com.battleship.fleetcommand.feature.lobby

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RoomCodeDisplay(code: String, modifier: Modifier = Modifier) {
    Text(text = code, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}