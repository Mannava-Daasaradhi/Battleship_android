// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/player/Difficulty.kt
package com.battleship.fleetcommand.core.domain.player

/**
 * AI difficulty tier for single-player mode.
 *
 * - EASY   — fires randomly (EasyAI).
 * - MEDIUM — hunt-and-target with checkerboard parity (MediumAI).
 * - HARD   — full probability-density heat map recalculated every shot (HardAI).
 *
 * Section 1 / Section 4.4. Stored as name string in DataStore and Room.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD;

    companion object {
        fun fromStorageKey(key: String): Difficulty =
            entries.firstOrNull { it.name == key } ?: MEDIUM
    }
}