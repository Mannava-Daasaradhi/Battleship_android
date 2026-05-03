// ============================================================
// core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/NavyCard.kt
// ============================================================
// FILE: core/ui/src/main/kotlin/com/battleship/fleetcommand/core/ui/components/NavyCard.kt
package com.battleship.fleetcommand.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = modifier) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}