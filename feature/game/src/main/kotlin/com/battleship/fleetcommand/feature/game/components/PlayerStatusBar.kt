// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/PlayerStatusBar.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/components/PlayerStatusBar.kt
package com.battleship.fleetcommand.feature.game.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlayerStatusBar(name: String, isActive: Boolean, shotCount: Int, hitCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = if (isActive) "▶ $name" else name, style = MaterialTheme.typography.titleLarge, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text("Shots: $shotCount  Hits: $hitCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}