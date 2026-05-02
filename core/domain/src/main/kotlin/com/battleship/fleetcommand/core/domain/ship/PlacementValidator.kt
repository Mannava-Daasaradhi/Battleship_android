// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/ship/PlacementValidator.kt

package com.battleship.fleetcommand.core.domain.ship

import com.battleship.fleetcommand.core.domain.Coord

/**
 * Controls whether ships may touch each other.
 * STRICT  — no touching, not even diagonals.
 * RELAXED — ships may be adjacent but not overlap.
 * Configurable in Settings screen (Section 1).
 */
enum class AdjacencyMode { STRICT, RELAXED }

/**
 * All reasons a ship placement can be rejected.
 * Section 24 error naming pattern: sealed class suffixed with Error,
 * noun/adjective subclasses only.
 */
sealed class PlacementError {
    /** One or more cells fall outside the 10×10 grid. */
    data object OutOfBounds : PlacementError()

    /** Placement overlaps an already-placed ship. */
    data class OverlapsExistingShip(val conflictingShipId: ShipId) : PlacementError()

    /** Placement is adjacent/diagonal to an existing ship (STRICT mode only). */
    data class AdjacentToExistingShip(val conflictingShipId: ShipId) : PlacementError()
}

/**
 * Pure stateless validator for ship placement.
 * No Android imports. No coroutines. Section 4.2 rules.
 */
object PlacementValidator {

    /**
     * Validates a proposed placement against existing placements.
     * Returns an empty list when placement is legal.
     * Returns early on the first fatal error (OutOfBounds, then Overlap).
     */
    fun validate(
        placement: ShipPlacement,
        existingPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): List<PlacementError> {
        val coords = placement.occupiedCoords()
        val errors = mutableListOf<PlacementError>()

        // 1. Boundary check — fail fast, no point checking further
        if (coords.any { !it.isValid() }) {
            errors.add(PlacementError.OutOfBounds)
            return errors
        }

        // 2. Overlap and adjacency check against every existing placement
        val coordSet = coords.toSet()

        for (existing in existingPlacements) {
            val existingCoords = existing.occupiedCoords().toSet()

            // Direct overlap
            if (coordSet.any { it in existingCoords }) {
                errors.add(PlacementError.OverlapsExistingShip(existing.shipId))
                return errors   // overlap is fatal — return immediately
            }

            // Strict adjacency (cardinal + diagonal neighbours)
            if (adjacencyMode == AdjacencyMode.STRICT) {
                val forbidden: Set<Coord> = existingCoords
                    .flatMap { it.adjacentCoords() + it.diagonalCoords() }
                    .toSet()
                if (coordSet.any { it in forbidden }) {
                    errors.add(PlacementError.AdjacentToExistingShip(existing.shipId))
                    // Don't return early — collect all adjacency violations
                }
            }
        }

        return errors
    }

    /**
     * Convenience wrapper — returns true only when placement is fully legal.
     */
    fun isValid(
        placement: ShipPlacement,
        existingPlacements: List<ShipPlacement>,
        adjacencyMode: AdjacencyMode = AdjacencyMode.RELAXED
    ): Boolean = validate(placement, existingPlacements, adjacencyMode).isEmpty()
}