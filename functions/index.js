/**
 * Battleship Fleet Command — Firebase Cloud Functions
 *
 * Server-side game logic that cannot be trusted to clients:
 *   1. resolveShot       — reads defender's board, resolves hit/miss/sunk, writes result + flips turn
 *   2. claimVictory      — verifies all opponent ships are sunk before declaring a winner
 *   3. forfeit           — sets opponent as winner
 *   4. cleanupStaleGames — scheduled hourly: deletes abandoned/finished games and frees room codes
 *
 * Deploy: firebase deploy --only functions
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");

initializeApp();

// ── App Check enforcement ────────────────────────────────────────────────────
//
// Set to true ONLY after:
//   1. The Android app ships with the App Check SDK (Play Integrity provider),
//   2. Firebase Console → App Check metrics show ~100% verified requests.
// Flipping this early will reject every call from app versions without App Check.
const ENFORCE_APP_CHECK = false;

const CALLABLE_OPTS = { enforceAppCheck: ENFORCE_APP_CHECK };

// ── Ship definitions (must match ShipRegistry.ALL in Kotlin) ──────────────
const SHIPS = [
  { id: "CARRIER",     size: 5 },
  { id: "BATTLESHIP",  size: 4 },
  { id: "CRUISER",     size: 3 },
  { id: "SUBMARINE",   size: 3 },
  { id: "DESTROYER",   size: 2 },
];
const BOARD_SIZE = 10;
const TOTAL_SHIP_CELLS = SHIPS.reduce((sum, s) => sum + s.size, 0); // 17

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Parses the JSON-encoded ship placements string stored at /boards/{uid}/ships.
 * Returns array of { shipId, coords: [{row, col}] }.
 */
function parsePlacements(shipsJson) {
  if (!shipsJson) return [];
  const dtos = JSON.parse(shipsJson);
  return dtos.map((dto) => {
    const def = SHIPS.find((s) => s.id === dto.shipId);
    if (!def) return null;
    const coords = [];
    for (let i = 0; i < def.size; i++) {
      const row = dto.orientation === "H" ? dto.row : dto.row + i;
      const col = dto.orientation === "H" ? dto.col + i : dto.col;
      if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return null;
      coords.push({ row, col });
    }
    return { shipId: dto.shipId, coords };
  }).filter(Boolean);
}

/**
 * Checks whether a coordinate hits any ship. Returns { hit, shipId, sunk } or { hit: false }.
 */
function resolveHit(row, col, placements, previousHitCoords) {
  for (const ship of placements) {
    const hitCell = ship.coords.find((c) => c.row === row && c.col === col);
    if (hitCell) {
      // Check if this sinks the ship: all other cells already hit + this one
      const allHit = ship.coords.every((c) =>
        (c.row === row && c.col === col) ||
        previousHitCoords.some((h) => h.row === c.row && h.col === c.col)
      );
      return { hit: true, shipId: ship.shipId, sunk: allHit };
    }
  }
  return { hit: false, shipId: null, sunk: false };
}

/**
 * Validates that the caller is a participant (host or guest) in the game.
 */
async function validateParticipant(db, gameId, uid) {
  const metaSnap = await db.ref(`games/${gameId}/meta`).get();
  if (!metaSnap.exists()) throw new HttpsError("not-found", "Game not found");
  const meta = metaSnap.val();
  if (meta.hostUid !== uid && meta.guestUid !== uid) {
    throw new HttpsError("permission-denied", "Not a participant in this game");
  }
  return meta;
}

// ── resolveShot ──────────────────────────────────────────────────────────────
//
// Called by the DEFENDER when an opponent shot arrives without a result.
// The Cloud Function reads the defender's board (admin SDK bypasses rules),
// resolves the shot, writes result + shipId, and flips the turn.
//
// Request: { gameId, shooterUid, shotIndex, row, col }
// Response: { result: "hit"|"miss"|"sunk", shipId: string|null }

exports.resolveShot = onCall(CALLABLE_OPTS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Must be authenticated");

  const { gameId, shooterUid, shotIndex } = request.data;
  if (!gameId || !shooterUid || shotIndex === undefined) {
    throw new HttpsError("invalid-argument", "Missing required fields");
  }
  if (shooterUid === uid) {
    throw new HttpsError("permission-denied", "Cannot resolve your own shots");
  }

  const db = getDatabase();
  const meta = await validateParticipant(db, gameId, uid);

  // The only shots this caller may resolve are the opponent's — never a third party's.
  const opponentUid = meta.hostUid === uid ? meta.guestUid : meta.hostUid;
  if (shooterUid !== opponentUid) {
    throw new HttpsError("permission-denied", "May only resolve the opponent's shots");
  }

  if (meta.status !== "battle") {
    throw new HttpsError("failed-precondition", "Game is not in battle phase");
  }

  // Read defender's board (admin SDK — bypasses client read rules)
  const boardSnap = await db.ref(`games/${gameId}/boards/${uid}/ships`).get();
  const placements = parsePlacements(boardSnap.val());
  if (placements.length === 0) {
    throw new HttpsError("failed-precondition", "Defender board not found");
  }

  // Read the shooter's shots once; use them both to locate the target shot and to build
  // the prior-hit history. Reading here (not from request.data) is what makes the server
  // authoritative: the client cannot lie about where it fired or force a re-resolve.
  const shotsSnap = await db.ref(`games/${gameId}/shots/${shooterUid}`).get();
  if (!shotsSnap.exists()) {
    throw new HttpsError("not-found", "No shots found for shooter");
  }
  const shotsObj = shotsSnap.val();
  const allKeys = Object.keys(shotsObj);
  const pushKey = allKeys[shotIndex];
  if (!pushKey) {
    throw new HttpsError("not-found", `Shot push-key not found at index ${shotIndex}`);
  }

  const storedShot = shotsObj[pushKey];
  // Refuse to re-resolve: a shot that already has a result must never be recomputed
  // (that would let a defender flip an earlier "hit" into a "miss").
  if (storedShot.result !== undefined && storedShot.result !== null) {
    throw new HttpsError("failed-precondition", "Shot has already been resolved");
  }
  // Authoritative coordinates come from the stored shot, not the request payload.
  const row = storedShot.row;
  const col = storedShot.col;
  if (typeof row !== "number" || typeof col !== "number") {
    throw new HttpsError("failed-precondition", "Stored shot is missing valid coordinates");
  }

  const previousHitCoords = [];
  for (let i = 0; i < allKeys.length; i++) {
    if (allKeys[i] === pushKey) continue; // exclude the shot being resolved
    const s = shotsObj[allKeys[i]];
    if (s.result === "hit" || s.result === "sunk") {
      previousHitCoords.push({ row: s.row, col: s.col });
    }
  }

  // Resolve using the trusted, stored coordinates.
  const outcome = resolveHit(row, col, placements, previousHitCoords);
  const resultStr = outcome.sunk ? "sunk" : outcome.hit ? "hit" : "miss";

  // Build atomic multi-path update
  const updates = {};
  updates[`games/${gameId}/shots/${shooterUid}/${pushKey}/result`] = resultStr;
  if (outcome.hit) {
    updates[`games/${gameId}/shots/${shooterUid}/${pushKey}/shipId`] = outcome.shipId;
  }
  updates[`games/${gameId}/meta/currentTurn`] = uid; // defender's turn next

  await db.ref().update(updates);

  return { result: resultStr, shipId: outcome.hit ? outcome.shipId : null };
});

// ── claimVictory ─────────────────────────────────────────────────────────────
//
// Verifies all opponent ship cells have been hit before declaring a winner.
//
// Request: { gameId }
// Response: { success: true, winner: uid }

exports.claimVictory = onCall(CALLABLE_OPTS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Must be authenticated");

  const { gameId } = request.data;
  if (!gameId) throw new HttpsError("invalid-argument", "Missing gameId");

  const db = getDatabase();
  const meta = await validateParticipant(db, gameId, uid);

  if (meta.status === "finished") {
    return { success: true, winner: meta.winner };
  }
  if (meta.status !== "battle") {
    throw new HttpsError("failed-precondition", "Game is not in battle phase");
  }

  // Determine opponent
  const opponentUid = meta.hostUid === uid ? meta.guestUid : meta.hostUid;
  if (!opponentUid) {
    throw new HttpsError("failed-precondition", "No opponent found");
  }

  // Read opponent's board
  const boardSnap = await db.ref(`games/${gameId}/boards/${opponentUid}/ships`).get();
  const placements = parsePlacements(boardSnap.val());
  if (placements.length === 0) {
    throw new HttpsError("failed-precondition", "Opponent board not found");
  }

  // Read claimer's shots against opponent
  const shotsSnap = await db.ref(`games/${gameId}/shots/${uid}`).get();
  const hitCoords = new Set();
  if (shotsSnap.exists()) {
    const shotsObj = shotsSnap.val();
    for (const key of Object.keys(shotsObj)) {
      const s = shotsObj[key];
      if (s.result === "hit" || s.result === "sunk") {
        hitCoords.add(`${s.row},${s.col}`);
      }
    }
  }

  // Verify all opponent ship cells are hit
  const allSunk = placements.every((ship) =>
    ship.coords.every((c) => hitCoords.has(`${c.row},${c.col}`))
  );

  if (!allSunk) {
    throw new HttpsError("failed-precondition", "Not all opponent ships have been sunk");
  }

  // Write winner + finished status atomically
  const updates = {};
  updates[`games/${gameId}/meta/winner`] = uid;
  updates[`games/${gameId}/meta/status`] = "finished";
  await db.ref().update(updates);

  return { success: true, winner: uid };
});

// ── forfeit ──────────────────────────────────────────────────────────────────
//
// Sets the opponent as winner.
//
// Request: { gameId }
// Response: { success: true }

exports.forfeit = onCall(CALLABLE_OPTS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Must be authenticated");

  const { gameId } = request.data;
  if (!gameId) throw new HttpsError("invalid-argument", "Missing gameId");

  const db = getDatabase();
  const meta = await validateParticipant(db, gameId, uid);

  if (meta.status === "finished") {
    return { success: true };
  }

  const opponentUid = meta.hostUid === uid ? meta.guestUid : meta.hostUid;

  const updates = {};
  updates[`games/${gameId}/meta/winner`] = opponentUid;
  updates[`games/${gameId}/meta/status`] = "finished";
  await db.ref().update(updates);

  return { success: true };
});

// ── cleanupStaleGames ────────────────────────────────────────────────────────
//
// Scheduled hourly. Long-term hygiene: without this, /games and /roomCodes
// grow forever — storage costs rise, room codes can never be reused (the
// !data.exists() write rule makes every used code permanently dead), and
// listener fan-out on large nodes slows down.
//
// Deletion policy:
//   waiting        → older than 1 hour   (opponent never joined)
//   setup / battle → inactive  24 hours  (both players abandoned mid-game)
//   finished       → older than 24 hours (players had time to view results)
//   no status      → malformed, delete
//
// Requires ".indexOn": ["meta/createdAt"] under /games in database.rules.json.
// Processes up to 300 games per run; hourly schedule drains any backlog.

const RETENTION = {
  waitingMs: 60 * 60 * 1000,         // 1 hour
  finishedMs: 24 * 60 * 60 * 1000,   // 24 hours
  abandonedMs: 24 * 60 * 60 * 1000,  // 24 hours of inactivity in setup/battle
};
const CLEANUP_BATCH_LIMIT = 300;

exports.cleanupStaleGames = onSchedule(
  { schedule: "every 60 minutes", timeoutSeconds: 300, memory: "256MiB" },
  async () => {
    const db = getDatabase();
    const now = Date.now();

    // Only games at least 1 hour old are candidates.
    const snap = await db
      .ref("games")
      .orderByChild("meta/createdAt")
      .endAt(now - RETENTION.waitingMs)
      .limitToFirst(CLEANUP_BATCH_LIMIT)
      .get();

    if (!snap.exists()) {
      logger.info("cleanupStaleGames: nothing to clean");
      return;
    }

    const updates = {};
    let deleted = 0;

    snap.forEach((gameSnap) => {
      const meta = gameSnap.child("meta").val() || {};
      const status = meta.status;
      const lastActivity = meta.updatedAt || meta.createdAt || 0;
      const idleMs = now - lastActivity;

      const shouldDelete =
        !status ||
        (status === "waiting" && idleMs >= RETENTION.waitingMs) ||
        (status === "finished" && idleMs >= RETENTION.finishedMs) ||
        ((status === "setup" || status === "battle") && idleMs >= RETENTION.abandonedMs);

      if (shouldDelete) {
        updates[`games/${gameSnap.key}`] = null;
        if (meta.roomCode) {
          updates[`roomCodes/${meta.roomCode}`] = null; // free the code for reuse
        }
        deleted++;
      }
    });

    if (deleted > 0) {
      await db.ref().update(updates);
    }
    logger.info(`cleanupStaleGames: deleted ${deleted} of ${snap.numChildren()} candidates`);
  }
);
