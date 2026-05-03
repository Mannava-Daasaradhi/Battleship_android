// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/multiplayer/JoinResult.kt
package com.battleship.fleetcommand.core.domain.multiplayer

/**
 * Result of a guest joining an online game room by room code.
 * Section 6.10 / Section 6.4 — MatchmakingRepository.joinGame().
 * Section 24: sealed class, noun subclasses.
 */
sealed class JoinResult {
    data class Success(val gameId: String) : JoinResult()
    data object NotFound : JoinResult()
    data object GameAlreadyStarted : JoinResult()
    data class Failure(val reason: String) : JoinResult()
}