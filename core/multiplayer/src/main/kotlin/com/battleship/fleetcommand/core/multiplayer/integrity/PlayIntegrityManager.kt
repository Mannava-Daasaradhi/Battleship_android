package com.battleship.fleetcommand.core.multiplayer.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side wrapper for the Play Integrity API.
 *
 * Requests an integrity token that should be sent to Firebase Cloud Functions
 * for server-side verification before allowing game creation or joining.
 *
 * Server-side verification (in Cloud Functions) should:
 * 1. Decode the token via Google Play Integrity API
 * 2. Check deviceRecognitionVerdict for MEETS_DEVICE_INTEGRITY
 * 3. Reject requests from tampered/rooted devices
 */
@Singleton
class PlayIntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val integrityManager = IntegrityManagerFactory.create(context)

    /**
     * Requests a Play Integrity token for the given nonce.
     * The nonce should be a unique, server-generated value tied to the game action.
     *
     * @return the integrity token string, or null if the request failed.
     */
    suspend fun requestIntegrityToken(nonce: String): String? {
        return try {
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()
            val response = integrityManager.requestIntegrityToken(request).await()
            response.token()
        } catch (e: Exception) {
            Timber.w(e, "Play Integrity token request failed")
            null
        }
    }
}
