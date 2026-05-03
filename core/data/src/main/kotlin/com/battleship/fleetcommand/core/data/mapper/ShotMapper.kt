package com.battleship.fleetcommand.core.data.mapper

import com.battleship.fleetcommand.core.data.local.entity.ShotEntity
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.model.Shot
import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * Bidirectional mapper between [ShotEntity] (Room) and [Shot] (domain).
 * [Coord] is stored as its raw Int index so no TypeConverter is needed.
 * Section 13 — Data Layer / Mappers.
 */

fun ShotEntity.toDomain(): Shot = Shot(
    gameId     = gameId,
    shotIndex  = shotIndex,
    coord      = Coord(coordIndex),
    result     = runCatching { FireResult.valueOf(result) }.getOrElse { FireResult.MISS },
    firedBy    = runCatching { PlayerSlot.valueOf(firedBy) }.getOrElse { PlayerSlot.ONE },
    timestamp  = timestamp
)

fun Shot.toEntity(): ShotEntity = ShotEntity(
    gameId     = gameId,
    shotIndex  = shotIndex,
    coordIndex = coord.index,
    result     = result.name,
    firedBy    = firedBy.name,
    timestamp  = timestamp
)