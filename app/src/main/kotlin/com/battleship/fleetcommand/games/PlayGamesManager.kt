// FILE: app/src/main/kotlin/com/battleship/fleetcommand/games/PlayGamesManager.kt
// Section 7 — Google Play Games — Full Spec
package com.battleship.fleetcommand.games

import android.app.Activity
import com.battleship.fleetcommand.core.domain.model.GameMode
import com.battleship.fleetcommand.core.domain.model.GameResult
import com.battleship.fleetcommand.core.domain.model.PlayerStats
import com.battleship.fleetcommand.core.domain.player.Difficulty
import com.battleship.fleetcommand.core.domain.ship.ShipId
import com.google.android.gms.games.PlayGames
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ── Leaderboard IDs ────────────────────────────────────────────────────────
// ⚠️ Replace with real IDs from Play Console → Play Games Services → Leaderboards
private const val LEADERBOARD_FASTEST_ADMIRAL = "CgkIxxxxxxxxxxxxEAIQ"
private const val LEADERBOARD_MOST_VICTORIES  = "CgkIxxxxxxxxxxxxEAIR"
private const val LEADERBOARD_SHARPSHOOTER    = "CgkIxxxxxxxxxxxxEAIS"

enum class LeaderboardType { FASTEST_ADMIRAL, MOST_VICTORIES, SHARPSHOOTER }

/**
 * Manages Google Play Games sign-in, leaderboard submission, and all 12 achievements.
 * Section 7 — Google Play Games — Full Spec.
 *
 * All Play Games v2 clients require an Activity context — never Application.
 * Call [initializeSilentSignIn] from MainActivity.onResume.
 * Sign-in is silent; failures degrade invisibly (Section 7.3).
 */
@Singleton
class PlayGamesManager @Inject constructor() {

    // ── Sign-in (Section 7.3) ──────────────────────────────────────────────

    /**
     * Attempt silent sign-in. Pass the current Activity.
     * Never block gameplay; never show UI on failure.
     */
    fun initializeSilentSignIn(activity: Activity) {
        val signInClient = PlayGames.getGamesSignInClient(activity)
        signInClient.isAuthenticated.addOnCompleteListener { task ->
            val isAuthenticated = task.isSuccessful && task.result.isAuthenticated
            if (!isAuthenticated) {
                signInClient.signIn()
                    .addOnSuccessListener {
                        Timber.d("PlayGamesManager: silent sign-in succeeded")
                    }
                    .addOnFailureListener { e ->
                        // Degrade invisibly — no UI error shown (Section 7.3)
                        Timber.d("PlayGamesManager: sign-in failed — features disabled (${e.message})")
                    }
            }
        }
    }

    // ── Leaderboards (Section 7.1) ─────────────────────────────────────────

    fun submitLeaderboardScores(
        activity: Activity,
        result: GameResult,
        difficulty: Difficulty?,
        stats: PlayerStats,
    ) {
        val lb = PlayGames.getLeaderboardsClient(activity)

        // Fastest Admiral — Hard + Win only, lower score is better
        if (result.winner != null && difficulty == Difficulty.HARD) {
            lb.submitScore(LEADERBOARD_FASTEST_ADMIRAL, result.durationSeconds)
        }

        // Most Victories — every win, submit cumulative total
        if (result.winner != null) {
            lb.submitScore(LEADERBOARD_MOST_VICTORIES, stats.wins.toLong())
        }

        // Sharpshooter — every game, single-game accuracy percent
        lb.submitScore(LEADERBOARD_SHARPSHOOTER, result.accuracyPercent.toLong())
    }

    fun showLeaderboard(activity: Activity, type: LeaderboardType) {
        val id = when (type) {
            LeaderboardType.FASTEST_ADMIRAL -> LEADERBOARD_FASTEST_ADMIRAL
            LeaderboardType.MOST_VICTORIES  -> LEADERBOARD_MOST_VICTORIES
            LeaderboardType.SHARPSHOOTER    -> LEADERBOARD_SHARPSHOOTER
        }
        PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(id)
            .addOnSuccessListener { intent -> activity.startActivity(intent) }
            .addOnFailureListener { Timber.w("PlayGamesManager: showLeaderboard failed") }
    }

    // ── Achievements (Section 7.2) ─────────────────────────────────────────

    fun unlock(activity: Activity, achievementId: String) {
        PlayGames.getAchievementsClient(activity).unlock(achievementId)
    }

    fun increment(activity: Activity, achievementId: String, steps: Int = 1) {
        if (steps <= 0) return
        PlayGames.getAchievementsClient(activity).increment(achievementId, steps)
    }

    fun showAchievements(activity: Activity) {
        PlayGames.getAchievementsClient(activity)
            .achievementsIntent
            .addOnSuccessListener { intent -> activity.startActivity(intent) }
            .addOnFailureListener { Timber.w("PlayGamesManager: showAchievements failed") }
    }

    /**
     * Evaluate and submit all 12 achievements post-game.
     * Section 7.2 — full achievement logic.
     */
    fun checkPostGameAchievements(
        activity: Activity,
        result: GameResult,
        difficulty: Difficulty?,
        sunkEnemyShips: Set<ShipId>,
        myRemainingShips: Set<ShipId>,
        stats: PlayerStats,
    ) {
        val isWin = result.winner != null
        val mode  = result.mode

        // ── Standard (one-time) ──────────────────────────────────────────
        if (isWin && stats.wins == 1)
            unlock(activity, AchievementIds.ACH_FIRST_BLOOD)

        if (sunkEnemyShips.contains(ShipId.CARRIER))
            unlock(activity, AchievementIds.ACH_CARRIER_KILL)

        if (isWin && difficulty == Difficulty.HARD && result.missCount == 0)
            unlock(activity, AchievementIds.ACH_PERFECT_GAME)

        if (isWin && difficulty == Difficulty.HARD && result.durationSeconds < 120L)
            unlock(activity, AchievementIds.ACH_SPEED_RUN)

        if (isWin && myRemainingShips == setOf(ShipId.DESTROYER))
            unlock(activity, AchievementIds.ACH_SURVIVOR)

        if (isWin && mode == GameMode.LOCAL && stats.localWins >= 5)
            unlock(activity, AchievementIds.ACH_LOCAL_LEGEND)

        if (isWin && mode == GameMode.ONLINE && stats.onlineWins == 1)
            unlock(activity, AchievementIds.ACH_ONLINE_WIN)

        // ── Incremental ──────────────────────────────────────────────────
        if (isWin) {
            increment(activity, AchievementIds.ACH_WINS_10)
            increment(activity, AchievementIds.ACH_WINS_50)
            increment(activity, AchievementIds.ACH_WINS_100)
        }

        if (isWin && difficulty == Difficulty.HARD)
            increment(activity, AchievementIds.ACH_HARD_WINS_25)

        if (result.totalShots > 0)
            increment(activity, AchievementIds.ACH_SHOTS_1000, result.totalShots)
    }
}

// missCount derived from GameResult fields
private val GameResult.missCount: Int get() = totalShots - totalHits