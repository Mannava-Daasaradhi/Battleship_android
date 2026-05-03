// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/multiplayer/MatchmakingError.kt
package com.battleship.fleetcommand.core.domain.multiplayer

/**
 * Error types for online matchmaking operations.
 * Section 24: sealed class suffixed with Error, noun/adjective subclasses.
 */
sealed class MatchmakingError {
    data object RoomNotFound : MatchmakingError()
    data object RoomFull : MatchmakingError()
    data object NetworkUnavailable : MatchmakingError()
    data class Unknown(val cause: Throwable) : MatchmakingError()
}