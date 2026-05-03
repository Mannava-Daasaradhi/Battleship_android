// FILE: core/domain/src/main/kotlin/com/battleship/fleetcommand/core/domain/model/Shot.kt
package com.battleship.fleetcommand.core.domain.model

import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.player.PlayerSlot

/**
 * A single fired shot — the domain model returned by [GameRepository.getShots]
 * and [GameRepository.observeShots].
 *
 * Maps 1-to-1 with ShotEntity (core:data) but carries zero Room annotations.
 * The ShotMapper in :core:data handles ShotEntity ↔ Shot conversion.
 *
 * Fields mirror ShotEntity from Section 4.5 / Section 13 exactly.
 */
data class Shot(
    val gameId: String,
    val shotIndex: Int,
    val coord: Coord,
    val result: FireResult,
    val firedBy: PlayerSlot,
    val timestamp: Long
)