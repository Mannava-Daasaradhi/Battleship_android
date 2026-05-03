package com.battleship.fleetcommand.core.data.local.migration

/**
 * Room migration definitions for BattleshipDatabase.
 *
 * No migrations are needed for version 1 (fresh install).
 * Add migrations here as the schema evolves:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE games ADD COLUMN ...")
 *     }
 * }
 *
 * Section 13 — Data Layer.
 */
object DatabaseMigrations