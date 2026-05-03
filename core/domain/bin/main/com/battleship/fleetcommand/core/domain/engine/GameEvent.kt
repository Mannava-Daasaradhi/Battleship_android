// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameEvent.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement

sealed class GameEvent {

    data class ShipPlaced(val placement: ShipPlacement) : GameEvent()
    data object AllShipsPlaced : GameEvent()
    data object PlacementConfirmed : GameEvent()

    data object OpponentJoined : GameEvent()
    data object OpponentReady : GameEvent()

    data class CellFired(val coord: Coord) : GameEvent()

    data class ShotResolved(
        val coord: Coord,
        val result: FireResult,
        val firedBy: PlayerSlot
    ) : GameEvent()

    data object AnimationComplete : GameEvent()
    data class GameEnded(val winner: PlayerSlot) : GameEvent()

    data object Resign : GameEvent()
    data object Restart : GameEvent()
}

enum class FireResult { HIT, MISS, SUNK }