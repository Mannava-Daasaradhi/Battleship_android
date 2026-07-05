/**
 * Battleship Fleet Command — database.rules.json test suite
 *
 * These rules run CLIENT-AUTHORITATIVE (the free Spark plan has no Cloud Functions):
 * participants resolve shots and settle the winner themselves. The rules therefore
 * still guarantee the structural + privacy invariants that do NOT depend on a trusted
 * server, and this suite verifies them:
 *   - opponent board positions are NEVER readable (ship secrecy is preserved)
 *   - only a game's two participants can touch it; strangers are locked out
 *   - a SHOOTER cannot self-assign a shot result; only the DEFENDER may stamp one
 *   - shots are rejected out of turn, off the board, and can't be rewritten
 *   - winner / currentTurn / finished must reference a real participant
 *   - room codes cannot be hijacked, chat identity cannot be spoofed
 *
 * What these rules intentionally DO NOT prevent (accepted Spark trade-off): a participant
 * lying about the *outcome* of a shot they resolve, or declaring victory early. That
 * requires a trusted server (Blaze Cloud Functions) to verify against the hidden board.
 *
 * Run (from repo root, Firebase CLI installed, JDK 21+ for the emulator):
 *   firebase emulators:exec --only database --project demo-battleship \
 *     "cd rules-tests && npm test"
 *
 * The emulator port (9000) must match the "emulators" block in firebase.json.
 */

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

const HOST = "HOST_UID_AAAA";
const GUEST = "GUEST_UID_BBBB";
const STRANGER = "STRANGER_UID_CCCC";
const GAME = "game_test_1";

let testEnv;

/** Seeds a game directly (rules bypassed), like the Admin SDK would see it. */
async function seedGame(meta, extra = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref(`games/${GAME}`).set({
      meta: {
        hostUid: HOST,
        guestUid: GUEST,
        status: "battle",
        currentTurn: HOST,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        roomCode: "ABC123",
        ...meta,
      },
      ...extra,
    });
    await ctx.database().ref("roomCodes/ABC123").set({ gameId: GAME });
  });
}

function db(uid) {
  return uid
    ? testEnv.authenticatedContext(uid).database()
    : testEnv.unauthenticatedContext().database();
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-battleship",
    database: {
      host: "127.0.0.1",
      port: 9000,
      rules: fs.readFileSync(
        path.resolve(__dirname, "../database.rules.json"),
        "utf8"
      ),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

after(async () => {
  await testEnv.cleanup();
});

// ── Boards: the read-cascade hole ───────────────────────────────────────────

describe("boards", () => {
  beforeEach(() =>
    seedGame(
      { status: "setup" },
      { boards: { [HOST]: { ships: '[{"shipId":"CARRIER"}]' } } }
    )
  );

  it("owner can read their own board", () =>
    assertSucceeds(db(HOST).ref(`games/${GAME}/boards/${HOST}`).once("value")));

  it("OPPONENT CANNOT read the other player's board (anti-cheat)", () =>
    assertFails(db(GUEST).ref(`games/${GAME}/boards/${HOST}`).once("value")));

  it("stranger cannot read any board", () =>
    assertFails(db(STRANGER).ref(`games/${GAME}/boards/${HOST}`).once("value")));

  it("owner can write their board once during setup", () =>
    assertSucceeds(
      db(GUEST).ref(`games/${GAME}/boards/${GUEST}`).set({ ships: "[]" })
    ));

  it("board cannot be rewritten after first placement", () =>
    assertFails(
      db(HOST).ref(`games/${GAME}/boards/${HOST}`).set({ ships: "[]" })
    ));

  it("board cannot be written during battle phase", async () => {
    await seedGame({ status: "battle" });
    await assertFails(
      db(GUEST).ref(`games/${GAME}/boards/${GUEST}`).set({ ships: "[]" })
    );
  });
});

// ── Winner / status / turn: client-authoritative outcome fields ─────────────

describe("meta — client-authoritative outcome fields", () => {
  beforeEach(() => seedGame({}));

  it("participant CAN claim a winner (client-authoritative)", () =>
    assertSucceeds(db(HOST).ref(`games/${GAME}/meta/winner`).set(HOST)));

  it("winner must be one of the two participants", () =>
    assertFails(db(HOST).ref(`games/${GAME}/meta/winner`).set(STRANGER)));

  it("stranger cannot write winner", () =>
    assertFails(db(STRANGER).ref(`games/${GAME}/meta/winner`).set(STRANGER)));

  it("winner cannot be overwritten once set", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.database().ref(`games/${GAME}/meta/winner`).set(GUEST)
    );
    await assertFails(db(HOST).ref(`games/${GAME}/meta/winner`).set(HOST));
  });

  it("participant CAN end the game (battle → finished)", () =>
    assertSucceeds(db(GUEST).ref(`games/${GAME}/meta/status`).set("finished")));

  it("stranger cannot end the game", () =>
    assertFails(db(STRANGER).ref(`games/${GAME}/meta/status`).set("finished")));

  it("the DEFENDER (not on turn) CAN flip currentTurn to a participant", () =>
    // seeded currentTurn = HOST, so GUEST is the defender flipping the turn
    assertSucceeds(db(GUEST).ref(`games/${GAME}/meta/currentTurn`).set(GUEST)));

  it("the player already on turn cannot re-flip currentTurn", () =>
    assertFails(db(HOST).ref(`games/${GAME}/meta/currentTurn`).set(HOST)));

  it("currentTurn cannot be set to a non-participant", () =>
    assertFails(db(GUEST).ref(`games/${GAME}/meta/currentTurn`).set(STRANGER)));

  it("guest slot cannot be overwritten once taken", () =>
    assertFails(db(STRANGER).ref(`games/${GAME}/meta/guestUid`).set(STRANGER)));
});

// ── Shots ────────────────────────────────────────────────────────────────────

describe("shots", () => {
  beforeEach(() => seedGame({ status: "battle", currentTurn: HOST }));

  const validShot = { row: 4, col: 7, timestamp: Date.now() };

  it("player on turn can fire a valid shot", () =>
    assertSucceeds(
      db(HOST).ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    ));

  it("PLAYER OFF TURN CANNOT fire (anti-cheat)", () =>
    assertFails(
      db(GUEST).ref(`games/${GAME}/shots/${GUEST}/shot1`).set(validShot)
    ));

  it("cannot fire as someone else", () =>
    assertFails(
      db(GUEST).ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    ));

  it("out-of-bounds coordinates rejected", () =>
    assertFails(
      db(HOST)
        .ref(`games/${GAME}/shots/${HOST}/shot1`)
        .set({ row: 99, col: 0, timestamp: Date.now() })
    ));

  it("SHOOTER CANNOT forge the result field (anti-cheat)", () =>
    assertFails(
      db(HOST)
        .ref(`games/${GAME}/shots/${HOST}/shot1`)
        .set({ ...validShot, result: "sunk" })
    ));

  it("an existing shot cannot be overwritten", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.database().ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    );
    await assertFails(
      db(HOST)
        .ref(`games/${GAME}/shots/${HOST}/shot1`)
        .set({ ...validShot, row: 5 })
    );
  });

  it("cannot fire outside battle phase", async () => {
    await seedGame({ status: "setup", currentTurn: HOST });
    await assertFails(
      db(HOST).ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    );
  });

  // Client-authoritative resolution: the DEFENDER stamps the result of the opponent's shot.
  it("DEFENDER can stamp a result onto the opponent's shot", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.database().ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    );
    await assertSucceeds(
      db(GUEST).ref(`games/${GAME}/shots/${HOST}/shot1/result`).set("hit")
    );
  });

  it("DEFENDER cannot tamper with the shot's coordinates", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.database().ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    );
    await assertFails(
      db(GUEST).ref(`games/${GAME}/shots/${HOST}/shot1/row`).set(9)
    );
  });

  it("a stranger cannot resolve a shot", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.database().ref(`games/${GAME}/shots/${HOST}/shot1`).set(validShot)
    );
    await assertFails(
      db(STRANGER).ref(`games/${GAME}/shots/${HOST}/shot1/result`).set("miss")
    );
  });
});

// ── Room codes ───────────────────────────────────────────────────────────────

describe("roomCodes", () => {
  it("authenticated user can claim a fresh code", () =>
    assertSucceeds(db(HOST).ref("roomCodes/FRESH1").set({ gameId: GAME })));

  it("a claimed code cannot be hijacked", async () => {
    await seedGame({});
    await assertFails(
      db(STRANGER).ref("roomCodes/ABC123").set({ gameId: "evil_game" })
    );
  });

  it("unauthenticated user cannot read codes", async () => {
    await seedGame({});
    await assertFails(db(null).ref("roomCodes/ABC123").once("value"));
  });
});

// ── Chat ─────────────────────────────────────────────────────────────────────

describe("chat", () => {
  beforeEach(() => seedGame({}));

  it("participant can send a valid message", () =>
    assertSucceeds(
      db(GUEST)
        .ref(`games/${GAME}/chat/msg1`)
        .set({ uid: GUEST, text: "gg", timestamp: Date.now() })
    ));

  it("SENDER CANNOT spoof another player's uid (anti-cheat)", () =>
    assertFails(
      db(GUEST)
        .ref(`games/${GAME}/chat/msg1`)
        .set({ uid: HOST, text: "I forfeit", timestamp: Date.now() })
    ));

  it("messages over 120 chars rejected", () =>
    assertFails(
      db(HOST)
        .ref(`games/${GAME}/chat/msg1`)
        .set({ uid: HOST, text: "x".repeat(121), timestamp: Date.now() })
    ));

  it("stranger cannot write into a game's chat", () =>
    assertFails(
      db(STRANGER)
        .ref(`games/${GAME}/chat/msg1`)
        .set({ uid: STRANGER, text: "hi", timestamp: Date.now() })
    ));
});

// ── Game creation & meta visibility ─────────────────────────────────────────

describe("game lifecycle", () => {
  it("host can create a game claiming themselves as hostUid", () =>
    assertSucceeds(
      db(HOST).ref(`games/new_game`).set({
        meta: {
          hostUid: HOST,
          status: "waiting",
          createdAt: Date.now(),
          roomCode: "NEW001",
        },
      })
    ));

  it("cannot create a game with a pre-set winner (creation smuggling)", () =>
    assertFails(
      db(HOST).ref(`games/smuggled`).set({
        meta: {
          hostUid: HOST,
          status: "waiting",
          winner: HOST,
          createdAt: Date.now(),
        },
      })
    ));

  it("cannot create a game already in finished state", () =>
    assertFails(
      db(HOST).ref(`games/smuggled2`).set({
        meta: { hostUid: HOST, status: "finished", createdAt: Date.now() },
      })
    ));

  it("cannot create a game claiming someone else as host", () =>
    assertFails(
      db(STRANGER).ref(`games/forged`).set({
        meta: { hostUid: HOST, status: "waiting", createdAt: Date.now() },
      })
    ));

  it("joining guest can read waiting-game meta", async () => {
    await seedGame({ status: "waiting", guestUid: null, currentTurn: null });
    await assertSucceeds(db(GUEST).ref(`games/${GAME}/meta`).once("value"));
  });

  it("stranger cannot read meta of an in-progress game", async () => {
    await seedGame({ status: "battle" });
    await assertFails(db(STRANGER).ref(`games/${GAME}/meta`).once("value"));
  });

  it("joining guest can claim the empty guest slot", async () => {
    await seedGame({ status: "waiting", guestUid: null, currentTurn: null });
    await assertSucceeds(db(GUEST).ref(`games/${GAME}/meta/guestUid`).set(GUEST));
  });
});

assert.ok(true);
