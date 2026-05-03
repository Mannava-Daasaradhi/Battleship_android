// core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/di/MultiplayerModule.kt

package com.battleship.fleetcommand.core.multiplayer.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MultiplayerModule {

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // NOTE: FirebaseMatchRepositoryImpl is bound in :app's RepositoryModule via @Binds.
    // Do NOT rebind it here — that would create a duplicate binding and fail the Hilt graph.
}