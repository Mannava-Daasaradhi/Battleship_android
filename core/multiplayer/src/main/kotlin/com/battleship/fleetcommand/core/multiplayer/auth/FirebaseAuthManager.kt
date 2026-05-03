// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/auth/FirebaseAuthManager.kt
package com.battleship.fleetcommand.core.multiplayer.auth

import com.battleship.fleetcommand.core.domain.repository.PreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Firebase Anonymous Authentication.
 *
 * Invisible to the player — auth happens automatically on first use.
 * The UID is stored in [PreferencesRepository] so it can be used for reconnect
 * logic in [OnlineGameViewModel] without re-querying Firebase.
 *
 * Section 6.1 spec.
 *
 * Design note: [PreferencesRepository] is used for UID persistence instead of
 * [DataStore<Preferences>] directly to respect the :core:multiplayer module
 * boundary (DataStore lives in :core:data which is forbidden to import).
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val preferencesRepository: PreferencesRepository,
) {

    /**
     * Returns the current anonymous UID, signing in anonymously if needed.
     *
     * The Firebase SDK persists anonymous auth state across app restarts.
     * This call is a no-op if already signed in.
     *
     * @throws IllegalStateException if anonymous sign-in fails.
     */
    suspend fun ensureAnonymousAuth(): String {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // Persist UID in DataStore on every call — ensures it's always up-to-date.
            preferencesRepository.setOnlinePlayerUid(currentUser.uid)
            return currentUser.uid
        }

        Timber.d("FirebaseAuthManager: no current user — signing in anonymously")
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            val uid = result.user?.uid
                ?: throw IllegalStateException("Anonymous sign-in succeeded but returned null UID")
            preferencesRepository.setOnlinePlayerUid(uid)
            Timber.d("FirebaseAuthManager: anonymous auth complete, uid=$uid")
            uid
        } catch (e: Exception) {
            Timber.e(e, "FirebaseAuthManager: anonymous sign-in failed")
            throw e
        }
    }

    /**
     * Returns the current UID synchronously, or null if not yet authenticated.
     * Use [ensureAnonymousAuth] for a guaranteed, suspending version.
     */
    val currentUid: String?
        get() = firebaseAuth.currentUser?.uid
}