// FILE: app/src/main/kotlin/com/battleship/fleetcommand/di/RepositoryModule.kt
package com.battleship.fleetcommand.di

import com.battleship.fleetcommand.core.data.datastore.PreferencesRepositoryImpl
import com.battleship.fleetcommand.core.data.repository.GameRepositoryImpl
import com.battleship.fleetcommand.core.data.repository.StatsRepositoryImpl
import com.battleship.fleetcommand.core.domain.repository.GameRepository
import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import com.battleship.fleetcommand.core.domain.repository.StatsRepository
import com.battleship.fleetcommand.core.multiplayer.repository.FirebaseMatchRepositoryImpl
import com.battleship.fleetcommand.core.domain.multiplayer.FirebaseMatchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseMatchRepository(impl: FirebaseMatchRepositoryImpl): FirebaseMatchRepository
}