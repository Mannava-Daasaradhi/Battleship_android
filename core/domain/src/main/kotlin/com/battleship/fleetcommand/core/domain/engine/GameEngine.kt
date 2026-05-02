// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/engine/GameEngine.kt
package com.battleship.fleetcommand.core.domain.engine

import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
import com.battleship.fleetcommand.core.domain.board.Coord
import com.battleship.fleetcommand.core.domain.ship.AdjacencyMode
import com.battleship.fleetcommand.core.domain.ship.Orientation
import com.battleship.fleetcommand.core.domain.ship.PlacementError
import com.battleship.fleetcommand.core.domain.ship.PlacementValidator
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry

/**
 * Pure game logic class — no Android imports, no coroutines, no DI.
 *
 * Responsibilities:
 *  - Validate and apply ship placements
 *  - Resolve shots (Hit / Miss / Sunk)
 *  - Detect game-over condition
 *  - Auto-place all ships randomly
 *
 * All methods are deterministic given the same inputs.
 * Random behaviour is isolated to [autoPlace] via an injectable [Random].
 */
class GameEngine(
    private val random: Random = DefaultRandom
) {

    // ── Random abstraction (no kotlin.random import needed at call sites) ─

    fun interface Random {
        fun nextInt(bound: Int): Int
    }

    private object DefaultRandom : Random {
        private val jRandom = java.util.Random()
        override fun nextInt(bound: Int): Int = jRandom.nextInt(bound)
    }

    // ── Placement ─────────────────────────────────────────────────────────

    /**
     * Validates [placement] against [existingPlacements] under [adjacencyMode].
     * Returns [Result.success] with Unit on success,
     * or [Result.failure] wrapping the first [PlacementError] on failure.
     */
    fun validatePlacement(
        placement: ShipPlacement,
        existingPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): Result<Unit> {
        val errors = PlacementValidator.validate(placement, existingPlacements, adjacencyMode)
        return if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(errors.first().toException())
        }
    }

    /**
     * Applies [placement] to [currentPlacements] if valid.
     * Returns the new list of placements on success,
     * or [Result.failure] wrapping the first [PlacementError] on failure.
     *
     * If a ship with the same [ShipId] is already placed, it is replaced.
     */
    fun placeShip(
        placement: ShipPlacement,
        currentPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): Result<List<ShipPlacement>> {
        // Remove any existing placement for this ship (allow re-placement)
        val withoutThis = currentPlacements.filter { it.shipId != placement.shipId }

        val errors = PlacementValidator.validate(placement, withoutThis, adjacencyMode)
        if (errors.isNotEmpty()) {
            return Result.failure(errors.first().toException())
        }

        return Result.success(withoutThis + placement)
    }

    /**
     * Returns true when all ships in [placements] have been placed
     * (one entry per [ShipId] in [ShipRegistry.ALL]).
     */
    fun isPlacementComplete(placements: List<ShipPlacement>): Boolean {
        val placedIds = placements.map { it.shipId }.toSet()
        return ShipRegistry.ALL.all { def -> def.id in placedIds }
    }

    // ── Shot resolution ───────────────────────────────────────────────────

    /**
     * Resolves a shot at [coord] against the defender's [placements] and
     * the current [shotHistory] (already-fired coords on this board).
     *
     * Returns:
     *  - [ShotOutcome.Miss]        — coord is water or already shot
     *  - [ShotOutcome.Hit]         — coord hits a ship (not yet sunk)
     *  - [ShotOutcome.Sunk]        — this hit sinks the last cell of a ship
     *
     * Returns [Result.failure] if [coord] was already fired at.
     */
    fun fireShot(
        coord: Coord,
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Result<ShotOutcome> {
        if (coord in shotHistory) {
            return Result.failure(IllegalArgumentException("Coord ${coord.toDisplayLabel()} already fired at"))
        }

        // Find which ship (if any) occupies this coord
        val hitPlacement = placements.firstOrNull { placement ->
            val def = ShipRegistry.byId(placement.shipId)
            coord in placement.occupiedCoords(def.size)
        }

        if (hitPlacement == null) {
            return Result.success(ShotOutcome.Miss)
        }

        // Check if this shot sinks the ship
        val def = ShipRegistry.byId(hitPlacement.shipId)
        val shipCoords = hitPlacement.occupiedCoords(def.size).toSet()
        val newShotHistory = shotHistory + coord
        val remainingUnshotCells = shipCoords - newShotHistory

        return if (remainingUnshotCells.isEmpty()) {
            Result.success(ShotOutcome.Sunk(hitPlacement.shipId))
        } else {
            Result.success(ShotOutcome.Hit(hitPlacement.shipId))
        }
    }

    // ── Game-over detection ───────────────────────────────────────────────

    /**
     * Returns true when every cell of every ship in [placements]
     * has been fired at (i.e. is in [shotHistory]).
     */
    fun isGameOver(
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Boolean {
        return placements.all { placement ->
            val def = ShipRegistry.byId(placement.shipId)
            placement.occupiedCoords(def.size).all { it in shotHistory }
        }
    }

    /**
     * Returns the set of [ShipId]s that have been fully sunk
     * given [placements] and [shotHistory].
     */
    fun sunkenShips(
        placements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Set<ShipId> {
        return placements
            .filter { placement ->
                val def = ShipRegistry.byId(placement.shipId)
                placement.occupiedCoords(def.size).all { it in shotHistory }
            }
            .map { it.shipId }
            .toSet()
    }

    // ── Auto-placement ────────────────────────────────────────────────────

    /**
     * Randomly places all ships from [ShipRegistry.ALL] on an empty board.
     * Respects [adjacencyMode] — defaults to RELAXED for auto-place.
     *
     * Guaranteed to succeed: falls back to brute-force retry per ship.
     * Returns the complete list of [ShipPlacement]s.
     */
    fun autoPlace(
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): List<ShipPlacement> {
        val placements = mutableListOf<ShipPlacement>()

        for (def in ShipRegistry.ALL) {
            var placed = false
            // Shuffle candidate positions until one is valid
            val orientations = listOf(Orientation.Horizontal, Orientation.Vertical)
            val rows = (0 until Board.DIMENSION).toMutableList().also { it.shuffle(java.util.Random()) }
            val cols = (0 until Board.DIMENSION).toMutableList().also { it.shuffle(java.util.Random()) }

            outer@ for (orientation in orientations.shuffled(java.util.Random())) {
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

            // Safety fallback: exhaustive search if shuffle missed
            if (!placed) {
                placed = exhaustivePlace(def.id, def.size, placements, adjacencyMode)
            }

            check(placed) { "autoPlace: could not place ${def.id} — this should never happen" }
        }

        return placements
    }

    private fun exhaustivePlace(
        shipId: ShipId,
        size: Int,
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

    // ── Board snapshot ────────────────────────────────────────────────────

    /**
     * Builds a fog-of-war [Board] for the attacker — only Hit/Miss/Sunk cells
     * are revealed; ship positions are hidden (shown as Water).
     */
    fun buildFogBoard(
        defenderPlacements: List<ShipPlacement>,
        shotHistory: Set<Coord>
    ): Board {
        var board = Board.empty()
        val sunk = sunkenShips(defenderPlacements, shotHistory)

        for (coord in shotHistory) {
            val hitPlacement = defenderPlacements.firstOrNull { placement ->
                val def = ShipRegistry.byId(placement.shipId)
                coord in placement.occupiedCoords(def.size)
            }
            val state = when {
                hitPlacement == null -> CellState.Miss
                hitPlacement.shipId in sunk -> CellState.Sunk(hitPlacement.shipId)
                else -> CellState.Hit
            }
            board = board.withCell(coord, state)
        }

        return board
    }

    /**
     * Builds the owner's own [Board] — all ship positions visible,
     * plus hit/miss/sunk overlaid from [incomingShotHistory].
     */
    fun buildOwnBoard(
        ownPlacements: List<ShipPlacement>,
        incomingShotHistory: Set<Coord>
    ): Board {
        var board = Board.empty()
        val sunk = sunkenShips(ownPlacements, incomingShotHistory)

        // Paint all ship cells first
        for (placement in ownPlacements) {
            val def = ShipRegistry.byId(placement.shipId)
            for (coord in placement.occupiedCoords(def.size)) {
                board = board.withCell(coord, CellState.Ship(placement.shipId, placement.orientation))
            }
        }

        // Overlay shot results
        for (coord in incomingShotHistory) {
            val hitPlacement = ownPlacements.firstOrNull { placement ->
                val def = ShipRegistry.byId(placement.shipId)
                coord in placement.occupiedCoords(def.size)
            }
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

/**
 * The resolved result of a single [GameEngine.fireShot] call.
 * Distinct from [FireResult] (the enum used in events) — this carries richer data.
 */
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
    is PlacementError.Overlap ->
        IllegalArgumentException("Ship overlaps existing ship: $conflictingShipId")
    is PlacementError.TooClose ->
        IllegalArgumentException("Ship is too close to existing ship: $conflictingShipId")
}