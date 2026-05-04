// FILE: app/src/main/kotlin/com/battleship/fleetcommand/games/AchievementIds.kt
// Section 7.2 — Achievements Spec — all 12 achievement IDs as constants
package com.battleship.fleetcommand.games

/**
 * All 12 Google Play Games achievement IDs for Battleship Fleet Command.
 *
 * ⚠️ PLACEHOLDER IDs — replace every "CgkIxxxxxxxxxxxxEAI_" value with the
 *    real IDs generated in the Google Play Console under:
 *    Play Games Services → Achievements → [Create Achievement] → Copy ID
 *
 * Section 7.2 — Achievement Spec
 * Standard achievements (one-time unlock): ACH_FIRST_BLOOD … ACH_ONLINE_WIN
 * Incremental achievements (progress counter): ACH_WINS_10 … ACH_HARD_WINS_25
 */
object AchievementIds {

    // ── Standard (one-time unlock) ────────────────────────────────────────

    /** "First Blood" — Win your first game (any mode / difficulty). */
    const val ACH_FIRST_BLOOD = "CgkIxxxxxxxxxxxxEAIA"

    /** "Carrier Down" — Sink an opponent's Carrier in any game. */
    const val ACH_CARRIER_KILL = "CgkIxxxxxxxxxxxxEAIB"

    /** "Perfect Admiral" — Win a Hard game with zero misses. */
    const val ACH_PERFECT_GAME = "CgkIxxxxxxxxxxxxEAIC"

    /** "Lightning Strike" — Win a Hard game in under 2 minutes (< 120 seconds). */
    const val ACH_SPEED_RUN = "CgkIxxxxxxxxxxxxEAID"

    /** "Last Ship Standing" — Win when only the Destroyer (size 2) remains on your board. */
    const val ACH_SURVIVOR = "CgkIxxxxxxxxxxxxEAIE"

    /** "Local Legend" — Win 5 Pass & Play games. */
    const val ACH_LOCAL_LEGEND = "CgkIxxxxxxxxxxxxEAIF"

    /** "Connected Commander" — Win your first online multiplayer game. */
    const val ACH_ONLINE_WIN = "CgkIxxxxxxxxxxxxEAIG"

    // ── Incremental (progress counter) ───────────────────────────────────

    /** "Fleet Captain" — Win 10 games total. Max steps = 10. */
    const val ACH_WINS_10 = "CgkIxxxxxxxxxxxxEAIH"

    /** "Commodore" — Win 50 games total. Max steps = 50. */
    const val ACH_WINS_50 = "CgkIxxxxxxxxxxxxEAII"

    /** "Admiral" — Win 100 games total. Max steps = 100. */
    const val ACH_WINS_100 = "CgkIxxxxxxxxxxxxEAIJ"

    /** "Thousand Shots" — Fire 1000 total shots. Max steps = 1000. */
    const val ACH_SHOTS_1000 = "CgkIxxxxxxxxxxxxEAIK"

    /** "Master Tactician" — Win 25 Hard difficulty games. Max steps = 25. */
    const val ACH_HARD_WINS_25 = "CgkIxxxxxxxxxxxxEAIL"
}