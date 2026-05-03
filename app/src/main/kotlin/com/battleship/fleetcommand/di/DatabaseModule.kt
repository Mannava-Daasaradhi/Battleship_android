package com.battleship.fleetcommand.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.battleship.fleetcommand.core.data.local.BattleshipDatabase
import com.battleship.fleetcommand.core.data.local.dao.BoardStateDao
import com.battleship.fleetcommand.core.data.local.dao.GameDao
import com.battleship.fleetcommand.core.data.local.dao.ShotDao
import com.battleship.fleetcommand.core.data.local.dao.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "battleship_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BattleshipDatabase =
        BattleshipDatabase.create(context)

    @Provides
    @Singleton
    fun provideGameDao(db: BattleshipDatabase): GameDao = db.gameDao()

    @Provides
    @Singleton
    fun provideShotDao(db: BattleshipDatabase): ShotDao = db.shotDao()

    @Provides
    @Singleton
    fun provideStatsDao(db: BattleshipDatabase): StatsDao = db.statsDao()

    @Provides
    @Singleton
    fun provideBoardStateDao(db: BattleshipDatabase): BoardStateDao = db.boardStateDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}