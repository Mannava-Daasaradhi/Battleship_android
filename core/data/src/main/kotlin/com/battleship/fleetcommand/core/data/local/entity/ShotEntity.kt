package com.battleship.fleetcommand.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single fired shot.
 * Maps 1-to-1 with the domain model [Shot] via ShotMapper.
 * Cascades on game deletion so orphaned shots are never left behind.
 * Section 13 — Data Layer / Room entities.
 */
@Entity(
    tableName = "shots",
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
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val shotIndex: Int,
    val coordIndex: Int,   // Coord.index — single-Int representation of (row, col)
    val result: String,    // FireResult.name: "HIT" | "MISS" | "SUNK"
    val firedBy: String,   // PlayerSlot.name: "ONE" | "TWO"
    val timestamp: Long    // epoch millis
)