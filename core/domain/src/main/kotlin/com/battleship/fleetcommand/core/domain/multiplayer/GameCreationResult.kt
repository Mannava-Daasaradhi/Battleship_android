// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/multiplayer/GameCreationResult.kt
package com.battleship.fleetcommand.core.domain.multiplayer

/**
 * Result of a host creating an online game room.
 * Section 6.10 / Section 6.4 — MatchmakingRepository.createGame().
 * Section 24: sealed class, noun subclasses.
 */
sealed class GameCreationResult {
    data class Success(val gameId: String, val roomCode: String) : GameCreationResult()
    data class Failure(val reason: String) : GameCreationResult()
}