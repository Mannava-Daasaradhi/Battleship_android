package com.battleship.fleetcommand.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity that persists a player's ship placement grid.
 * One row per (gameId, slot) pair.
 *
 * [placementsData] is a pipe-separated list of ship placement tokens:
 *   "CARRIER:0:0:H|BATTLESHIP:2:3:V|CRUISER:5:1:H|SUBMARINE:7:4:V|DESTROYER:9:8:H"
 * Each token: "<ShipId.name>:<headRow>:<headCol>:<H|V>"
 *
 * Section 13 — Data Layer / Room entities.
 */
@Entity(
    tableName = "board_states",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class BoardStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val slot: String,            // PlayerSlot.name: "ONE" | "TWO"
    val placementsData: String   // pipe-separated placement tokens (see class doc)
)