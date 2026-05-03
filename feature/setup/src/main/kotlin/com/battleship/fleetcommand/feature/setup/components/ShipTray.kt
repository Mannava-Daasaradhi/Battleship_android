// ============================================================
// feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/components/ShipTray.kt
// ============================================================
// FILE: feature/setup/src/main/kotlin/com/battleship/fleetcommand/feature/setup/components/ShipTray.kt
package com.battleship.fleetcommand.feature.setup.components

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.battleship.fleetcommand.core.domain.ship.ShipDefinition
import com.battleship.fleetcommand.core.domain.ship.ShipId

/** Horizontal tray of ship items for placement. Section 8.1. */
@Composable
fun ShipTray(ships: List<ShipDefinition>, placedIds: Set<ShipId>, onShipSelected: (ShipId) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier) {
        items(ships) { ship ->
            DraggableShipItem(shipDef = ship, isPlaced = ship.id in placedIds, onTap = { onShipSelected(ship.id) })
        }
    }
}