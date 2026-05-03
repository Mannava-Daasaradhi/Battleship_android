package com.battleship.fleetcommand.core.domain.model

/**
 * The three game modes supported by Battleship Fleet Command.
 *
 * - AI          — Single player vs computer (Easy / Medium / Hard difficulty).
 * - LOCAL       — Pass & Play on a single device between two human players.
 * - ONLINE      — Real-time Firebase match between two players on separate devices.
 *
 * String serialisation mirrors the Room/DataStore storage values defined in
 * Section 4.5 of the instruction document ("AI" | "LOCAL" | "ONLINE").
 */
enum class GameMode {
    AI,
    LOCAL,
    ONLINE;

    /** Returns the string token stored in Room and DataStore for this mode. */
    fun toStorageKey(): String = name  // "AI", "LOCAL", "ONLINE"

    companion object {
        /** Parses a storage token back to a [GameMode], defaulting to [AI] on unknown values. */
        fun fromStorageKey(key: String): GameMode =
            entries.firstOrNull { it.name == key } ?: AI
    }
}