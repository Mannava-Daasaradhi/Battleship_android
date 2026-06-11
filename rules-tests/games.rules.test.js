/**
 * Battleship Fleet Command — database.rules.json test suite
 *
 * Verifies every cheat vector the hardened rules are supposed to close:
 *   - opponent board positions are NOT readable (the read-cascade hole)
 *   - winner / finished status are NOT client-writable (claimVictory bypass)
 *   - shots are rejected out of turn, off the board, or with forged results
 *   - room codes cannot be hijacked, chat identity cannot be spoofed
 *
 * Run (from repo root, Firebase CLI installed):
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

// ── Winner / status: the claimVictory bypass ────────────────────────────────

describe("meta — server-only fields", () => {
  beforeEach(() => seedGame({}));

  it("PARTICIPANT CANNOT write winner directly (anti-cheat)", () =>
    assertFails(db(HOST).ref(`games/${GAME}/meta/winner`).set(HOST)));

  it("participant cannot set status to finished directly", () =>
    assertFails(db(GUEST).ref(`games/${GAME}/meta/status`).set("finished")));

  it("participant cannot flip currentTurn mid-battle", () =>
    assertFails(db(GUEST).ref(`games/${GAME}/meta/currentTurn`).set(GUEST)));

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
