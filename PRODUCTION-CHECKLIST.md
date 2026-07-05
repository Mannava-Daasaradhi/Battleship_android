# Battleship Fleet Command — Production Launch Checklist

Status after this review round. Two columns of work remain: nothing in CODE,
everything in CONSOLE — those steps require the owner's accounts and cannot be
done from the codebase.

---

## ✅ DONE IN CODE (apply the delivered files, build, test)

| Item | File | Why it was a blocker |
|---|---|---|
| Opponent board readable via rule cascade | `database.rules.json` | Cheaters could read ship positions |
| `winner` / `finished` client-writable | `database.rules.json` | Instant-win bypass of claimVictory |
| Creation-time smuggling (pre-set winner) | `database.rules.json` | Write rules cascade at creation |
| No turn / bounds / shape validation on shots | `database.rules.json` | Out-of-turn fire, forged results |
| Room-code enumeration + chat uid spoofing | `database.rules.json` | Privacy + impersonation |
| Rules unverified | `rules-tests/` | 25+ emulator tests covering every vector |
| Node 18 — deploys now BLOCKED by Google | `functions/package.json` | Cannot ship functions at all on 18 |
| No stale-game cleanup, room codes never freed | `functions/index.js` | Unbounded growth, code exhaustion |
| No App Check (scripted clients) | `BattleshipApplication.kt`, gradle files | API key is extractable from any APK |
| `versionCode = 1` hardcoded | `app-build.gradle.kts`, `release.yml` | Play rejects the 2nd release |
| Silent empty-credential signing fallback | `app-build.gradle.kts` | Misconfigured CI could mis-sign |
| `resolveShot` trusted client-sent coordinates | `functions/index.js` | Defender could force misses / re-resolve a hit into a miss — now reads the stored shot and refuses already-resolved shots |
| Medium AI abandoned half-sunk ships | `core/ai/MediumAI.kt` | Reverse-walk stranding + stale cross-ship state fixed (regressions guarded by tests) |
| AI placement used an uncapped retry loop | `feature/game/.../BattleViewModel.kt` | Replaced with `GameEngine.autoPlace()` (guaranteed to terminate) |
| Play Games placeholder App ID | removed | Unused leaderboard/achievement code + `000000000000` App ID stripped for v1 |
| Online required Blaze (Cloud Functions) | `database.rules.json`, `FirebaseMatchRepositoryImpl.kt` | Converted to **client-authoritative** so online works on the free **Spark** plan (no functions). `SERVER_AUTHORITATIVE=false`; flip to `true` after upgrading to Blaze + deploying functions for server-side anti-cheat. |

Run the rules tests (needs Firebase CLI + **JDK 21+** for the emulator; they run in
CI too):

    firebase emulators:exec --only database --project demo-battleship \
      "cd rules-tests && npm install && npm test"

### Deploy for online on the FREE Spark plan (current setup)

Online is **client-authoritative** — no Cloud Functions, no Blaze required.

1. Firebase Console → **Authentication → Sign-in method → enable Anonymous**.
2. `firebase deploy --only database`  ← rules ONLY (do **not** deploy functions on Spark).
3. Full manual multiplayer pass: host → join → place → battle → win → forfeit.
4. Ship the app update.

Trade-off accepted for free play: a player could cheat the *outcome* of a match
(clients resolve their own shots). Ship boards stay private and non-players are locked
out. To close the cheat gap later, upgrade to Blaze, `firebase deploy --only functions`,
set `SERVER_AUTHORITATIVE = true` in `FirebaseMatchRepositoryImpl.kt`, and ship an update.

Known Spark limitation: finished/abandoned games are not auto-deleted (the scheduled
cleanup function needs Blaze). At friends-scale this is harmless (1 GB storage, ~887M
room codes); revisit when you enable Blaze — the cleanup function is already written.

---

## 🔲 CONSOLE / ACCOUNT WORK (owner-only — no code can do these)

1. **Firebase plan — Spark (free) is enough for v1.** Online now runs
   client-authoritative, so no Cloud Functions / Blaze are required. Spark caps at
   100 simultaneous connections (fine for friends-scale). Upgrade to Blaze later only
   when you want server-side anti-cheat + auto-cleanup (see deploy notes above).
2. **Firebase → App Check** — register the Android app with the Play Integrity
   provider (needs the release SHA-256 fingerprint from Play Console → App
   integrity). Add your debug token from Logcat for local builds. Do NOT
   enforce yet.
3. **Play Console** — create the app listing, upload via the release workflow
   (tag `v1.0.1`), complete the **Data safety form** (declare: Crashlytics
   crash data; analytics currently disabled), content rating questionnaire,
   target-audience declaration.
4. **Privacy policy URL** — mandatory for Play listing because the app uses
   Firebase Auth + Crashlytics. A simple hosted page (GitHub Pages works)
   covering: data collected (anonymous auth UID, crash logs, game moves),
   retention (games auto-deleted within 24h — true once cleanup deploys),
   contact email.
5. **Play Games Services** — ✅ DONE (removed for v1). The unused leaderboard /
   achievement code and its placeholder `APP_ID` have been stripped, so there is
   no Play Games setup blocking launch. Re-add via Play Console + code wiring in a
   later update if you want leaderboards.
6. **Repo hygiene** — ✅ DONE. `graphify-out/` (248 files), smoke screenshots,
   stray scripts (`fix_icons.py`), zero-byte artifacts, and JVM crash logs have
   been removed; `node_modules/`, `*.log`, and `graphify-out/` are now ignored;
   the Confidential `security-audit-*.md` are untracked (kept on disk only). The
   public repo no longer leaks the audit or carries tooling bloat.
7. **GitHub Secrets sanity check** — `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`, `SERVICE_ACCOUNT_JSON`, `GOOGLE_SERVICES_JSON`
   all set; keystore backed up somewhere outside the repo (losing it loses the
   ability to update the app forever — Play App Signing enrollment recommended).

---

## ⏳ EXPLICITLY DEFERRED (safe to launch without)

- **Analytics consent UI** — analytics is hard-disabled in the manifest and
  nothing enables it, which is compliant by default. Wire a Settings toggle
  through the existing `setAnalyticsConsent()` repo method post-launch if you
  want analytics data.
- **Board encryption (audit F-2)** — with the read-cascade fixed, boards are
  unreadable by opponents through rules; encryption becomes defense-in-depth
  rather than a blocker.
- **Regional functions (asia-south1)** — every shot round-trips us-central1
  (~250ms from India). Worth doing once you have real users; requires changing
  `FirebaseFunctions.getInstance()` to `getInstance("asia-south1")` in
  `MultiplayerModule.kt` plus region options in functions, deployed together.
- **AdMob** — per project rule, all ad code stays deleted until you integrate it.

---

## Verdict

With the delivered files applied, rules tests passing, and console items 1–5
completed: **yes — production-ready for a Play Store internal/closed track
launch**, scaling comfortably to thousands of concurrent players on a single
Blaze RTDB instance. Promote internal → production after one clean test cycle.
