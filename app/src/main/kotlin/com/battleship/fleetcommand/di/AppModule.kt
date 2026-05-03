// FILE: app/src/main/kotlin/com/battleship/fleetcommand/di/AppModule.kt
package com.battleship.fleetcommand.di

import com.battleship.fleetcommand.core.domain.engine.GameEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGameEngine(): GameEngine = GameEngine()
}