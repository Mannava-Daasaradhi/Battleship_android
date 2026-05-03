package com.battleship.fleetcommand.core.data.mapper

import com.battleship.fleetcommand.core.data.local.entity.GameEntity
import com.battleship.fleetcommand.core.domain.model.Game
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * Bidirectional mapper between [GameEntity] (Room) and [Game] (domain).
 * Enum fields are stored as their .name String so no Room TypeConverters
 * are needed. Unknown values fall back to safe defaults.
 * Section 13 — Data Layer / Mappers.
 */

fun GameEntity.toDomain(): Game = Game(
    id          = id,
    mode        = GameMode.fromStorageKey(mode),
    startedAt   = startedAt,
    finishedAt  = finishedAt,
    winner      = winner?.let { runCatching { PlayerSlot.valueOf(it) }.getOrNull() },
    difficulty  = difficulty?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() },
    durationSecs = durationSecs,
    player1Name = player1Name,
    player2Name = player2Name
)

fun Game.toEntity(): GameEntity = GameEntity(
    id          = id,
    mode        = mode.toStorageKey(),
    startedAt   = startedAt,
    finishedAt  = finishedAt,
    winner      = winner?.name,
    difficulty  = difficulty?.name,
    durationSecs = durationSecs,
    player1Name = player1Name,
    player2Name = player2Name
)