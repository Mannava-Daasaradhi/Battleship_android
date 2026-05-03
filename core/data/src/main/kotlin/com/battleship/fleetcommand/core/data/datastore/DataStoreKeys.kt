// FILE: core/data/src/main/kotlin/com/battleship/fleetcommand/core/data/datastore/DataStoreKeys.kt
package com.battleship.fleetcommand.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * All DataStore preference keys for Battleship Fleet Command.
 * Key names are lowercase_snake_case per Section 24 naming conventions.
 *
 * Do NOT reference this object from :core:multiplayer — it may only be
 * imported by :core:data and :app.
 */
object DataStoreKeys {
    /** The player's display name shown in lobby and game screens. */
    val PLAYER_NAME = stringPreferencesKey("player_name")

    /** The current AI difficulty level (enum name, e.g. "MEDIUM"). */
    val DIFFICULTY = stringPreferencesKey("difficulty")

    /** Whether sound effects are enabled. */
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")

    /** Whether background music is enabled. */
    val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")

    /** Ship adjacency rule: "STRICT" or "RELAXED". */
    val ADJACENCY_MODE = stringPreferencesKey("adjacency_mode")

    /**
     * The Firebase game ID of the currently active or most recent online game.
     * Null after a game completes or is abandoned.
     * Used for reconnect logic in OnlineGameViewModel (Section 6.7).
     */
    val CURRENT_GAME_ID = stringPreferencesKey("current_game_id")

    /**
     * The Firebase anonymous UID for this device.
     * Written once on first anonymous sign-in; persists across sessions.
     * Section 6.1.
     */
    val ONLINE_PLAYER_UID = stringPreferencesKey("online_player_uid")
}