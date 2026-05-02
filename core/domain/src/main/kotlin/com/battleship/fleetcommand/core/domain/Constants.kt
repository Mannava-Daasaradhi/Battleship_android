// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/Constants.kt

package com.battleship.fleetcommand.core.domain

object GameConstants {
    const val BOARD_SIZE = 10
    const val TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE  // 100
    const val ROOM_CODE_LENGTH = 6
    const val HANDOFF_COUNTDOWN_SECS = 3
    const val RECONNECT_TIMEOUT_SECS = 10
    const val OPPONENT_DISCONNECT_CLAIM_SECS = 30
    const val MAX_CHAT_MESSAGE_LENGTH = 120
    const val SHOT_RATE_LIMIT_MS = 500L
}