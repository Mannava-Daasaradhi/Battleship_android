package com.battleship.fleetcommand.core.data.repository

import com.battleship.fleetcommand.core.data.local.BattleshipDatabase
import com.battleship.fleetcommand.core.data.local.entity.BoardStateEntity
import com.battleship.fleetcommand.core.data.mapper.toDomain
import com.battleship.fleetcommand.core.data.mapper.toEntity
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.Orientation
import com.battleship.fleetcommand.core.domain.model.Game
import com.battleship.fleetcommand.core.domain.model.Shot
import com.battleship.fleetcommand.core.domain.player.PlayerSlot
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.battleship.fleetcommand.core.domain.ship.ShipPlacement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [GameRepository].
 * Bound in :app's RepositoryModule via @Binds.
 *
 * [ShipPlacement] lists are persisted as a pipe-separated string
 * (see [encodePlacements] / [decodePlacements]) so no custom TypeConverter
 * or JSON library dependency is required in :core:data.
 *
 * Section 13 — Data Layer / Repository Implementations.
 */
@Singleton
class GameRepositoryImpl @Inject constructor(
    private val db: BattleshipDatabase
) : GameRepository {

    // ── Game CRUD ─────────────────────────────────────────────────────────────

    override suspend fun createGame(game: Game): String {
        db.gameDao().insert(game.toEntity())
        return game.id
    }

    override suspend fun saveGame(game: Game) {
        db.gameDao().insert(game.toEntity())
    }

    override suspend fun getGame(gameId: String): Game? =
        db.gameDao().getById(gameId)?.toDomain()

    override suspend fun getUnfinishedGame(): Game? =
        db.gameDao().getUnfinishedGame()?.toDomain()

    override suspend fun finishGame(
        gameId: String,
        winner: PlayerSlot,
        durationSecs: Long
    ) {
        db.gameDao().finishGame(
            gameId      = gameId,
            finishedAt  = System.currentTimeMillis(),
            winner      = winner.name,
            durationSecs = durationSecs
        )
    }

    // ── Board state (ship placements) ─────────────────────────────────────────

    override suspend fun saveBoardState(
        gameId: String,
        slot: PlayerSlot,
        placements: List<ShipPlacement>
    ) {
        db.boardStateDao().upsert(
            BoardStateEntity(
                gameId       = gameId,
                slot         = slot.name,
                placementsData = encodePlacements(placements)
            )
        )
    }

    override suspend fun getBoardState(
        gameId: String,
        slot: PlayerSlot
    ): List<ShipPlacement>? {
        val entity = db.boardStateDao().getByGameIdAndSlot(gameId, slot.name)
            ?: return null
        return decodePlacements(entity.placementsData)
    }

    // ── Shots ─────────────────────────────────────────────────────────────────

    override suspend fun saveShot(gameId: String, shot: Shot) {
        db.shotDao().insert(shot.toEntity())
    }

    override suspend fun getShots(gameId: String): List<Shot> =
        db.shotDao().getByGameId(gameId).map { it.toDomain() }

    override fun observeShots(gameId: String, firedBy: PlayerSlot): Flow<List<Shot>> =
        db.shotDao()
            .observeByGameIdAndFiredBy(gameId, firedBy.name)
            .map { entities -> entities.map { it.toDomain() } }

    // ── Placement serialisation ───────────────────────────────────────────────

    /**
     * Encodes a placement list as a pipe-separated string.
     * Format per token: "<ShipId>:<headRow>:<headCol>:<H|V>"
     * Example: "CARRIER:0:0:H|BATTLESHIP:2:3:V"
     */
    private fun encodePlacements(placements: List<ShipPlacement>): String =
        placements.joinToString("|") { p ->
            val orientChar = if (p.orientation is Orientation.Horizontal) "H" else "V"
            "${p.shipId.name}:${p.headCoord.rowOf()}:${p.headCoord.colOf()}:$orientChar"
        }

    /**
     * Decodes a pipe-separated placement string back to a [ShipPlacement] list.
     * Returns an empty list if any token cannot be parsed (fail-safe).
     */
    private fun decodePlacements(data: String): List<ShipPlacement> {
        if (data.isBlank()) return emptyList()
        return data.split("|").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 4) return@mapNotNull null
            val shipId      = runCatching { ShipId.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val row         = parts[1].toIntOrNull() ?: return@mapNotNull null
            val col         = parts[2].toIntOrNull() ?: return@mapNotNull null
            val orientation = if (parts[3] == "H") Orientation.Horizontal else Orientation.Vertical
            ShipPlacement(
                shipId      = shipId,
                headCoord   = Coord.fromRowCol(row, col),
                orientation = orientation
            )
        }
    }
}