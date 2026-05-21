package com.battleship.fleetcommand.core.ui.model

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import com.battleship.fleetcommand.core.domain.ship.ShipRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Shared board-building utilities used by PlacementViewModel, BattleViewModel,
 * and OnlineGameViewModel to produce BoardViewState with per-cell ship identity.
 */
object BoardBuilder {

    /**
     * Metadata for each cell: which ship (if any), the cell's index within the ship,
     * the ship's total size, and orientation.
     */
    data class CellShipInfo(
        val shipId: ShipId? = null,
        val cellIndex: Int = -1,
        val shipSize: Int = 0,
        val orientation: Orientation? = null,
    )

    /**
     * Build a lookup array mapping each board index → CellShipInfo.
     * Call once per board refresh, then use when constructing CellViewState.
     */
    fun buildShipInfoMap(placements: List<ShipPlacement>): Array<CellShipInfo> {
        val map = Array(GameConstants.TOTAL_CELLS) { CellShipInfo() }
        for (placement in placements) {
            val size = ShipRegistry.sizeOf(placement.shipId)
            val coords = placement.occupiedCoords()
            coords.forEachIndexed { cellIndex, coord ->
                if (coord.isValid()) {
                    map[coord.index] = CellShipInfo(
                        shipId = placement.shipId,
                        cellIndex = cellIndex,
                        shipSize = size,
                        orientation = placement.orientation,
                    )
                }
            }
        }
        return map
    }

    /**
     * Build a placement screen board (ships visible, no shots).
     */
    fun buildPlacementBoard(placements: List<ShipPlacement>): BoardViewState {
        val shipInfo = buildShipInfoMap(placements)
        val cellStates = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (placement in placements) {
            for (coord in placement.occupiedCoords()) {
                if (coord.isValid()) cellStates[coord.index] = CellDisplayState.SHIP
            }
        }
        val cells = cellStates.mapIndexed { i, state ->
            val info = shipInfo[i]
            CellViewState(
                coord = Coord(i),
                state = state,
                shipId = info.shipId,
                shipCellIndex = info.cellIndex,
                shipSize = info.shipSize,
                shipOrientation = info.orientation,
            )
        }.toImmutableList()

        val shipViews = placements.map { p ->
            ShipPlacementViewState(p.shipId, p.headCoord, p.orientation, ShipRegistry.sizeOf(p.shipId))
        }.toImmutableList()

        return BoardViewState(cells = cells, ownShips = shipViews)
    }

    /**
     * Build the player's own board during battle (ships visible + incoming shots).
     */
    fun buildPlayerBoard(
        placements: List<ShipPlacement>,
        incomingShots: Set<Coord>,
        showShips: Boolean = true,
    ): BoardViewState {
        val shipInfo = buildShipInfoMap(placements)
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }

        if (showShips) {
            for (p in placements) {
                for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SHIP
            }
        }
        for (shot in incomingShots) {
            if (!shot.isValid()) continue
            val hit = placements.any { shot in it.occupiedCoords() }
            cells[shot.index] = if (hit) CellDisplayState.HIT else CellDisplayState.MISS
        }
        val sunkShipIds = placements
            .filter { p -> p.occupiedCoords().all { it in incomingShots } }
            .map { it.shipId }.toSet()
        for (p in placements.filter { it.shipId in sunkShipIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }

        val cellViews = cells.mapIndexed { i, s ->
            val info = shipInfo[i]
            CellViewState(
                coord = Coord(i),
                state = s,
                shipId = info.shipId,
                shipCellIndex = info.cellIndex,
                shipSize = info.shipSize,
                shipOrientation = info.orientation,
            )
        }.toImmutableList()

        val shipViews = placements.map { p ->
            ShipPlacementViewState(
                p.shipId, p.headCoord, p.orientation,
                ShipRegistry.sizeOf(p.shipId), p.shipId in sunkShipIds,
            )
        }.toImmutableList()

        return BoardViewState(cells = cellViews, ownShips = shipViews)
    }

    /**
     * Build the fog-of-war opponent board (ships hidden, only shot results visible).
     */
    fun buildFogBoard(
        placements: List<ShipPlacement>,
        shots: Set<Coord>,
    ): BoardViewState {
        val cells = Array(GameConstants.TOTAL_CELLS) { CellDisplayState.WATER }
        for (shot in shots) {
            if (!shot.isValid()) continue
            val hit = placements.any { shot in it.occupiedCoords() }
            cells[shot.index] = if (hit) CellDisplayState.HIT else CellDisplayState.MISS
        }
        val sunkShipIds = placements
            .filter { p -> p.occupiedCoords().all { it in shots } }
            .map { it.shipId }.toSet()
        for (p in placements.filter { it.shipId in sunkShipIds }) {
            for (c in p.occupiedCoords()) if (c.isValid()) cells[c.index] = CellDisplayState.SUNK
        }
        // No ship info on fog board — ships are hidden
        val cellViews = cells.mapIndexed { i, s -> CellViewState(Coord(i), s) }.toImmutableList()
        return BoardViewState(cells = cellViews)
    }
}
