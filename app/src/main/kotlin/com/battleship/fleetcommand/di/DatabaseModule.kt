package com.battleship.fleetcommand.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "battleship_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "battleship_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        encryptedPrefs: SharedPreferences
    ): BattleshipDatabase {
        migrateFromUnencrypted(context)
        val passphrase = getOrCreatePassphrase(encryptedPrefs)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context.applicationContext,
            BattleshipDatabase::class.java,
            "battleship_db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun getOrCreatePassphrase(prefs: SharedPreferences): ByteArray {
        val key = "db_passphrase"
        val existing = prefs.getString(key, null)
        if (existing != null) {
            return existing.toByteArray(Charsets.UTF_8)
        }
        val passphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        prefs.edit().putString(key, passphrase).apply()
        return passphrase.toByteArray(Charsets.UTF_8)
    }

    /**
     * Detects and removes a pre-existing unencrypted database so SQLCipher
     * can create a fresh encrypted one with the same name.
     */
    private fun migrateFromUnencrypted(context: Context) {
        val dbFile = context.getDatabasePath("battleship_db")
        if (!dbFile.exists()) return
        try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.close()
            // Opened successfully without encryption → unencrypted DB exists. Delete it.
            context.deleteDatabase("battleship_db")
        } catch (_: Exception) {
            // Already encrypted or corrupted — leave it for SQLCipher to handle
        }
    }

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
