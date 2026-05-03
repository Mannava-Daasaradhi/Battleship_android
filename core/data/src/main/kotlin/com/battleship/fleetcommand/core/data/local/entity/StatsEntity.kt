package com.battleship.fleetcommand.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for career statistics.
 * This table always contains exactly one row (id = 1).
 * Maps 1-to-1 with the domain model [PlayerStats].
 * Section 13 — Data Layer / Room entities.
 */
@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val id: Int = 1,   // singleton row — never changes
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val bestTimeSeconds: Long = Long.MAX_VALUE,
    val totalShots: Int = 0,
    val totalHits: Int = 0,
    val winStreak: Int = 0,
    val currentStreak: Int = 0,
    val onlineWins: Int = 0,
    val localWins: Int = 0,
    val aiWins: Int = 0
)