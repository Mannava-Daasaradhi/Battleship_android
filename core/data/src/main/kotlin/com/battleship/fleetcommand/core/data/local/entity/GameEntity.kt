package com.battleship.fleetcommand.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a single game session.
 * Maps 1-to-1 with the domain model [Game] via GameMapper.
 * All enum fields stored as their .name String — no Room TypeConverters needed.
 * Section 13 — Data Layer / Room entities.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val mode: String,           // GameMode.name: "AI" | "LOCAL" | "ONLINE"
    val startedAt: Long,        // epoch millis
    val finishedAt: Long?,      // null while game is in progress
    val winner: String?,        // PlayerSlot.name or null
    val difficulty: String?,    // Difficulty.name or null (AI mode only)
    val durationSecs: Long?,    // null while game is in progress
    val player1Name: String,
    val player2Name: String
)