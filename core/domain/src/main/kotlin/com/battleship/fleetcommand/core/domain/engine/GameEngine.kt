// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameEngine.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.PlacementError
import com.battleship.fleetcommand.core.domain.ship.PlacementValidator
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry

/**
 * Pure game logic class — no Android imports, no coroutines, no DI.
 */
class GameEngine(
    private val random: Random = DefaultRandom
) {

    fun interface Random {
        fun nextInt(bound: Int): Int
    }

    private object DefaultRandom : Random {
        private val jRandom = java.util.Random()
        override fun nextInt(bound: Int): Int = jRandom.nextInt(bound)
    }

    // ── Placement ─────────────────────────────────────────────────────────

    fun validatePlacement(
        placement: ShipPlacement,
        existingPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): Result<Unit> {
        val errors = PlacementValidator.validate(placement, existingPlacements, adjacencyMode)
        return if (errors.isEmpty()) Result.success(Unit)
        else Result.failure(errors.first().toException())
    }

    fun placeShip(
        placement: ShipPlacement,
        currentPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): Result<List<ShipPlacement>> {
        val withoutThis = currentPlacements.filter { it.shipId != placement.shipId }
        val errors = PlacementValidator.validate(placement, withoutThis, adjacencyMode)
        if (errors.isNotEmpty()) return Result.failure(errors.first().toException())
        return Result.success(withoutThis + placement)
    }

    fun isPlacementComplete(placements: List<ShipPlacement>): Boolean {
        val placedIds = placements.map { it.shipId }.toSet()
        return ShipRegistry.ALL.all { def -> def.id in placedIds }
    }

    // ── Shot resolution ───────────────────────────────────────────────────

    fun fireShot(
        coord: Coord,
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Result<ShotOutcome> {
        if (coord in shotHistory) {
            return Result.failure(
                IllegalArgumentException("Coord ${coord.toDisplayLabel()} already fired at")
            )
        }

        // occupiedCoords() takes no args — size is resolved internally via ShipRegistry
        val hitPlacement = placements.firstOrNull { placement ->
            coord in placement.occupiedCoords()
        }

        if (hitPlacement == null) return Result.success(ShotOutcome.Miss)

        val shipCoords = hitPlacement.occupiedCoords().toSet()
        val newShotHistory = shotHistory + coord
        val remaining = shipCoords - newShotHistory

        return if (remaining.isEmpty()) Result.success(ShotOutcome.Sunk(hitPlacement.shipId))
        else Result.success(ShotOutcome.Hit(hitPlacement.shipId))
    }

    // ── Game-over detection ───────────────────────────────────────────────

    fun isGameOver(
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Boolean = placements.all { placement ->
        placement.occupiedCoords().all { it in shotHistory }
    }

    fun sunkenShips(
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Set<ShipId> = placements
        .filter { placement -> placement.occupiedCoords().all { it in shotHistory } }
        .map { it.shipId }
        .toSet()

    // ── Auto-placement ────────────────────────────────────────────────────

    fun autoPlace(
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): List<ShipPlacement> {
        val placements = mutableListOf<ShipPlacement>()
        val jRandom = java.util.Random()

        for (def in ShipRegistry.ALL) {
            var placed = false
            val orientations = listOf(Orientation.Horizontal, Orientation.Vertical).shuffled(jRandom)
            val rows = (0 until Board.DIMENSION).shuffled(jRandom)
            val cols = (0 until Board.DIMENSION).shuffled(jRandom)

            outer@ for (orientation in orientations) {
                for (row in rows) {
                    for (col in cols) {
                        val candidate = ShipPlacement(
                            shipId = def.id,
                            headCoord = Coord.fromRowCol(row, col),
                            orientation = orientation
                        )
                        if (PlacementValidator.isValid(candidate, placements, adjacencyMode)) {
                            placements.add(candidate)
                            placed = true
                            break@outer
                        }
                    }
                }
            }

            if (!placed) placed = exhaustivePlace(def.id, placements, adjacencyMode)
            check(placed) { "autoPlace: could not place ${def.id} — this should never happen" }
        }

        return placements
    }

    private fun exhaustivePlace(
        shipId: ShipId,
        currentPlacements: MutableList<ShipPlacement>,
        adjacencyMode: AdjacencyMode
    ): Boolean {
        for (row in 0 until Board.DIMENSION) {
            for (col in 0 until Board.DIMENSION) {
                for (orientation in listOf(Orientation.Horizontal, Orientation.Vertical)) {
                    val candidate = ShipPlacement(
                        shipId = shipId,
                        headCoord = Coord.fromRowCol(row, col),
                        orientation = orientation
                    )
                    if (PlacementValidator.isValid(candidate, currentPlacements, adjacencyMode)) {
                        currentPlacements.add(candidate)
                        return true
                    }
                }
            }
        }
        return false
    }

    // ── Board snapshots ───────────────────────────────────────────────────

    fun buildFogBoard(
        defenderPlacements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Board {
        var board = Board.empty()
        val sunk = sunkenShips(defenderPlacements, shotHistory)
        for (coord in shotHistory) {
            val hitPlacement = defenderPlacements.firstOrNull { coord in it.occupiedCoords() }
            val state = when {
                hitPlacement == null -> CellState.Miss
                hitPlacement.shipId in sunk -> CellState.Sunk(hitPlacement.shipId)
                else -> CellState.Hit
            }
            board = board.withCell(coord, state)
        }
        return board
    }

    fun buildOwnBoard(
        ownPlacements: List<ShipPlacement>,
        incomingShotHistory: Set<Coord>
    ): Board {
        var board = Board.empty()
        val sunk = sunkenShips(ownPlacements, incomingShotHistory)
        for (placement in ownPlacements) {
            for (coord in placement.occupiedCoords()) {
                board = board.withCell(coord, CellState.Ship(placement.shipId, placement.orientation))
            }
        }
        for (coord in incomingShotHistory) {
            val hitPlacement = ownPlacements.firstOrNull { coord in it.occupiedCoords() }
            val state = when {
                hitPlacement == null -> CellState.Miss
                hitPlacement.shipId in sunk -> CellState.Sunk(hitPlacement.shipId)
                else -> CellState.Hit
            }
            board = board.withCell(coord, state)
        }
        return board
    }
}

// ── Shot outcome ──────────────────────────────────────────────────────────────

sealed class ShotOutcome {
    data object Miss : ShotOutcome()
    data class Hit(val shipId: ShipId) : ShotOutcome()
    data class Sunk(val shipId: ShipId) : ShotOutcome()

    fun toFireResult(): FireResult = when (this) {
        is Miss -> FireResult.MISS
        is Hit  -> FireResult.HIT
        is Sunk -> FireResult.SUNK
    }
}

// ── PlacementError → Exception bridge ────────────────────────────────────────

private fun PlacementError.toException(): Exception = when (this) {
    is PlacementError.OutOfBounds ->
        IllegalArgumentException("Ship placement is out of bounds")
    is PlacementError.OverlapsExistingShip ->
        IllegalArgumentException("Ship overlaps existing ship: $conflictingShipId")
    is PlacementError.AdjacentToExistingShip ->
        IllegalArgumentException("Ship is adjacent to existing ship: $conflictingShipId")
}