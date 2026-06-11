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

Run the rules tests locally (needs Firebase CLI + Java for the emulator):

    firebase emulators:exec --only database --project demo-battleship \
      "cd rules-tests && npm install && npm test"

Then full deploy in this order:
1. `firebase deploy --only database,functions`
2. Full manual multiplayer pass: host → join → place → battle → win → forfeit.
   Watch for one specific thing: the host's client must write `currentTurn`
   exactly ONCE at battle start — arbitrary turn flips are now blocked.
3. Ship the app update (App Check included, enforcement still off).
4. After App Check metrics look clean: enable enforcement in console AND flip
   `ENFORCE_APP_CHECK = true` in functions, redeploy functions.

---

## 🔲 CONSOLE / ACCOUNT WORK (owner-only — no code can do these)

1. **Firebase: Blaze plan** — required for Cloud Functions; also lifts the
   100-connection Spark cap to 200k. Set a budget alert (e.g. $10/mo) the same day.
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
5. **Play Games Services** — the manifest `APP_ID` is still the `000000000000`
   placeholder. Either configure Play Games in Play Console and set the real
   ID, or remove the integration before launch — a placeholder ID crashes the
   Play Games SDK on init for some flows.
6. **Repo hygiene** (one-time):

        git rm "CUsersdaasaAppDataLocalTemplogcat.txt" fix_icons.py
        git rm -r graphify-out
        git rm security-audit-report.md security-audit-summary.md  # marked Confidential — keep locally, not in a public repo
        echo -e "graphify-out/\n*.keystore\n*logcat*.txt" >> .gitignore

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
