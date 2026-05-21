package com.battleship.fleetcommand.feature.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battleship.fleetcommand.core.domain.ship.ShipDefinition
import com.battleship.fleetcommand.core.ui.theme.ShipColors

/** Drag-to-place ship item per Section 8.1. Now uses per-ship accent colours. */
@Composable
fun DraggableShipItem(
    shipDef: ShipDefinition,
    isPlaced: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ShipColors.forShip(shipDef.id)
    val boxColor = if (isPlaced) palette.hull.copy(alpha = 0.5f) else palette.hull

    Row(
        modifier = modifier
            .padding(4.dp)
            .background(
                if (isPlaced) palette.hull.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(shipDef.size) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(boxColor, MaterialTheme.shapes.small)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            shipDef.name,
            style = MaterialTheme.typography.labelSmall,
            color = palette.accent,
        )
    }
}
