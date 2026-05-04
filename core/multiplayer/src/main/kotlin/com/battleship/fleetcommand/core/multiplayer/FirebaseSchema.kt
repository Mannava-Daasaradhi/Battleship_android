// FILE: core/multiplayer/src/main/kotlin/com/battleship/fleetcommand/core/multiplayer/FirebaseSchema.kt
package com.battleship.fleetcommand.core.multiplayer

/**
 * All Firebase Realtime Database node names and JSON key constants.
 *
 * Node names and JSON keys: camelCase per Section 24 naming conventions.
 * Section 6.2 schema — single source of truth for all path strings.
 *
 * Usage:
 *   database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}")
 */
object FirebaseSchema {

    // ── Top-level collections ─────────────────────────────────────────────────
    /** Root collection node: /games */
    const val GAMES = "games"

    /**
     * Room-code → gameId lookup index: /roomCodes/{roomCode}
     *
     * Each node contains a single "gameId" string value.
     * This node has .read: auth != null in security rules so guests can look up
     * a game by room code without needing collection-level read on /games
     * (which would expose opponent board data).
     */
    const val ROOM_CODES = "roomCodes"

    /** Key inside /roomCodes/{roomCode} that holds the gameId. */
    const val GAME_ID = "gameId"

    // ── Game sub-nodes ────────────────────────────────────────────────────────
    /** /games/{gameId}/meta */
    const val META = "meta"
    /** /games/{gameId}/players */
    const val PLAYERS = "players"
    /** /games/{gameId}/boards */
    const val BOARDS = "boards"
    /** /games/{gameId}/shots */
    const val SHOTS = "shots"
    /** /games/{gameId}/chat */
    const val CHAT = "chat"

    // ── Meta keys (/games/{gameId}/meta) ──────────────────────────────────────
    const val HOST_UID = "hostUid"
    const val GUEST_UID = "guestUid"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val WINNER = "winner"
    const val CURRENT_TURN = "currentTurn"
    const val ROOM_CODE = "roomCode"

    // ── Status string values ──────────────────────────────────────────────────
    const val STATUS_WAITING = "waiting"
    const val STATUS_SETUP = "setup"
    const val STATUS_BATTLE = "battle"
    const val STATUS_FINISHED = "finished"

    // ── Player node keys (/games/{gameId}/players/{uid}) ──────────────────────
    const val PLAYER_NAME = "name"
    const val PLAYER_READY = "ready"
    const val PLAYER_CONNECTED = "connected"
    const val PLAYER_LAST_SEEN = "lastSeen"

    // ── Board node keys (/games/{gameId}/boards/{uid}) ────────────────────────
    const val BOARD_SHIPS = "ships"

    // ── Shot node keys (/games/{gameId}/shots/{shooterUid}/{shotPushKey}) ─────
    const val SHOT_ROW = "row"
    const val SHOT_COL = "col"
    const val SHOT_RESULT = "result"
    const val SHOT_SHIP_ID = "shipId"
    const val SHOT_TIMESTAMP = "timestamp"

    // ── Shot result string values ─────────────────────────────────────────────
    const val RESULT_HIT = "hit"
    const val RESULT_MISS = "miss"
    const val RESULT_SUNK = "sunk"

    // ── Chat node keys (/games/{gameId}/chat/{messageId}) ─────────────────────
    const val CHAT_UID = "uid"
    const val CHAT_TEXT = "text"
    const val CHAT_TIMESTAMP = "timestamp"

    // ── Helpers: full path builders ───────────────────────────────────────────

    fun roomCodePath(roomCode: String) = "$ROOM_CODES/$roomCode"

    fun gamePath(gameId: String) = "$GAMES/$gameId"

    fun metaPath(gameId: String) = "$GAMES/$gameId/$META"

    fun playerPath(gameId: String, uid: String) = "$GAMES/$gameId/$PLAYERS/$uid"

    fun presencePath(gameId: String, uid: String) = "$GAMES/$gameId/$PLAYERS/$uid/$PLAYER_CONNECTED"

    fun lastSeenPath(gameId: String, uid: String) = "$GAMES/$gameId/$PLAYERS/$uid/$PLAYER_LAST_SEEN"

    fun boardPath(gameId: String, uid: String) = "$GAMES/$gameId/$BOARDS/$uid"

    fun shipsPath(gameId: String, uid: String) = "$GAMES/$gameId/$BOARDS/$uid/$BOARD_SHIPS"

    fun shotsPath(gameId: String) = "$GAMES/$gameId/$SHOTS"

    fun shooterShotsPath(gameId: String, shooterUid: String) =
        "$GAMES/$gameId/$SHOTS/$shooterUid"

    fun shotResultPath(gameId: String, shooterUid: String, shotPushKey: String) =
        "$GAMES/$gameId/$SHOTS/$shooterUid/$shotPushKey/$SHOT_RESULT"
}