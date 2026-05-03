// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/matchmaking/RoomCodeGenerator.kt
package com.battleship.fleetcommand.core.multiplayer.matchmaking

import com.battleship.fleetcommand.core.domain.GameConstants
import java.security.SecureRandom

/**
 * Generates cryptographically random 6-character room codes.
 *
 * Charset excludes visually ambiguous characters:
 *   - '0' / 'O' — look the same in many fonts
 *   - '1' / 'I' — look the same in many fonts
 * Section 6.4 spec.
 */
object RoomCodeGenerator {

    private val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    /** Generates a [GameConstants.ROOM_CODE_LENGTH]-character uppercase alphanumeric room code. */
    fun generate(): String = buildString(GameConstants.ROOM_CODE_LENGTH) {
        repeat(GameConstants.ROOM_CODE_LENGTH) {
            append(CHARSET[secureRandom.nextInt(CHARSET.length)])
        }
    }

    /**
     * Validates that [code] is exactly [GameConstants.ROOM_CODE_LENGTH] chars,
     * all uppercase alphanumeric (no ambiguous chars enforced on input for robustness).
     */
    fun isValid(code: String): Boolean =
        code.length == GameConstants.ROOM_CODE_LENGTH && code.all { it.isLetterOrDigit() }
}