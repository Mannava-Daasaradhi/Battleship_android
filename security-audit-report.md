# Battleship Fleet Command - Android Security Audit

**Date:** 2026-05-20
**Auditor:** Claude Code (Opus prompt / Sonnet analysis)
**Repo path audited:** `C:\Users\daasa\OneDrive\Desktop\Projects\Battleship_android`
**Files examined:** 270 tracked files + untracked local files (google-services.json, local.properties)
**Classification:** Confidential

---

## 1. Executive Summary

Battleship Fleet Command is a multi-module Kotlin/Compose Android game using Firebase Realtime Database for online multiplayer, Room for local persistence, and DataStore for preferences. The codebase demonstrates strong engineering practices in module isolation and clean architecture, but has **critical security gaps in its online multiplayer implementation** that would allow a malicious player to cheat, read opponent ship positions, and manipulate game outcomes.

The most dangerous finding is the **client-side shot resolution model**: when an opponent fires a shot, the *defending client* resolves hit/miss/sunk and writes the result back to Firebase (`OnlineGameViewModel.kt:341-398`). A modified client can simply lie about every shot, reporting all hits as misses. Firebase security rules cannot validate game logic, so the server has no ability to detect or prevent this. Combined with the fact that **ship placements are uploaded as plaintext JSON to Firebase** (`FirebaseMatchRepositoryImpl.kt:80-98`) and the board read rule allows the opponent to read boards once the game is `finished` (trivially triggered by the attacker), an attacker can read the opponent's exact ship positions mid-game.

Secondary concerns include: `android:allowBackup="true"` in the manifest exposing all local game data, the `.firebaserc` file committed with the real Firebase project ID, no `network_security_config.xml` (allowing potential cleartext traffic), unencrypted Room database and DataStore holding Firebase UIDs, Timber debug/info logs surviving in release builds (only `v`/`d`/`i` stripped, not `w`/`e`), and zero root/tamper detection.

**Production readiness verdict:** The app is **not safe to ship for competitive online play** in its current state. Offline (AI and Pass & Play) modes are reasonably secure. Online multiplayer requires a server-side validation layer or fundamental architectural changes before launch.

---

## 2. Risk Dashboard

| Domain | Score /10 | Risk | Top Finding (file:line) |
|---|---|---|---|
| Data Storage & Secrets | 6/10 | Medium | `AndroidManifest.xml:17` — `allowBackup="true"` |
| Network & Transport | 5/10 | Medium | No `network_security_config.xml` found |
| Auth & Sessions | 7/10 | Low | `FirebaseAuthManager.kt:51` — UID in plaintext DataStore |
| IAP Integrity | 10/10 | None | No IAP code present |
| Permissions & Privacy | 7/10 | Low | Only 3 permissions, all justified |
| Reverse Engineering | 6/10 | Medium | `proguard-rules.pro:71` — domain models fully kept |
| Game Threats | 2/10 | Critical | `OnlineGameViewModel.kt:352` — client-side shot resolution |
| SDK Risk | 7/10 | Low | Compose BOM 2024.12.01 — review for updates |
| Tamper Detection | 1/10 | Critical | Zero root/tamper/integrity detection |
| Compliance | 5/10 | Medium | No GDPR consent flow before Firebase Analytics |
| **OVERALL** | **56/100** | **High Risk** | |

---

## 3. Findings

---
### F-1 - Client-Side Shot Resolution Enables Cheating in Online Multiplayer

| Field | Detail |
|---|---|
| Severity | Critical |
| CVSS v3.1 | 9.1 - AV:N/AC:L/PR:L/UI:N/S:C/C:L/I:H/A:H |
| File | `feature/game/src/main/kotlin/.../online/OnlineGameViewModel.kt` |
| Line / Method | lines 341-398 / `resolveNewOpponentShots()` |
| Effort to Exploit | Low |
| Fix Effort | Weeks |

**Vulnerable code (copied from file):**
```kotlin
private suspend fun resolveNewOpponentShots(shots: List<ShotData>) {
    shots.forEachIndexed { index, shotData ->
        if (shotData.result != null) return@forEachIndexed

        val key = "$index-${shotData.row}-${shotData.col}"
        if (key in resolvedShotKeys) return@forEachIndexed
        resolvedShotKeys.add(key)

        val coord = Coord.fromRowCol(shotData.row, shotData.col)
        val alreadyShotCoords = shots.take(index).map { it.coord }.toSet()
        val outcome: ShotOutcome = gameEngine.fireShot(coord, myPlacements, alreadyShotCoords)
            .getOrElse { ShotOutcome.Miss }

        // ... writes result back to Firebase ...
        val commitResult = repository.commitShotAndFlipTurn(
            gameId      = gameId,
            shooterUid  = opponentUid,
            shotIndex   = index,
            result      = fireResult,
            shipId      = shipIdString,
            nextTurnUid = myUid,
        )
    }
}
```

**What is wrong:**
The defending client receives the opponent's shot coordinates, resolves the hit/miss/sunk outcome locally using `gameEngine.fireShot()` against `myPlacements`, then writes the result back to Firebase via `commitShotAndFlipTurn()`. A modified client can intercept this flow and always report `MISS` regardless of the actual outcome. Firebase security rules cannot validate game logic — the `result` field's write rule (`database.rules.json:32`) only checks that the writer is a game participant, not that the result is correct.

**Attack scenario:**
1. Attacker decompiles the APK with `apktool d battleship.apk`
2. Modifies `resolveNewOpponentShots` to always set `fireResult = FireResult.MISS`
3. Rebuilds and signs with a debug key
4. Every opponent shot registers as a miss; attacker's fleet is invincible

```bash
# Frida script to intercept shot resolution
Java.perform(function() {
    var ShotOutcome = Java.use("com.battleship.fleetcommand.core.domain.engine.ShotOutcome$Miss");
    var OnlineVM = Java.use("com.battleship.fleetcommand.feature.game.online.OnlineGameViewModel");
    // Hook resolveNewOpponentShots to always return Miss
});
```

**Business impact:**
Complete destruction of online multiplayer integrity. Leaderboards become meaningless. Player trust collapses. Potential Play Store policy violation for unfair gameplay.

**Fix:**
This requires an architectural change. The correct approach is to have *both* players submit their placements encrypted/hashed before the game starts, and use a trusted intermediary (Cloud Function) to resolve shots server-side. At minimum:

```kotlin
// In a Firebase Cloud Function (server-side):
// 1. Both players submit ship placements hashed during setup
// 2. When a shot is fired, Cloud Function resolves it against the stored placements
// 3. Neither client can lie about outcomes
```

Short-term mitigation: have the attacker (shooter) also submit a claimed result, and if defender and attacker disagree, flag the game for review. This doesn't prevent cheating but enables detection.

**How to verify the fix in Claude Code:**
```bash
grep -rn "commitShotAndFlipTurn\|writeShotResult" --include="*.kt" . | grep -v test
# After fix: these calls should originate from a server-side Cloud Function, not client code
```

---
### F-2 - Ship Placements Uploaded as Plaintext JSON to Firebase

| Field | Detail |
|---|---|
| Severity | Critical |
| CVSS v3.1 | 8.6 - AV:N/AC:L/PR:L/UI:N/S:C/C:H/I:N/A:N |
| File | `core/multiplayer/src/main/kotlin/.../repository/FirebaseMatchRepositoryImpl.kt` |
| Line / Method | lines 80-98 / `submitShipPlacement()` |
| Effort to Exploit | Low |
| Fix Effort | Days |

**Vulnerable code (copied from file):**
```kotlin
override suspend fun submitShipPlacement(gameId: String, ships: List<ShipPlacement>): Result<Unit> {
    val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
    return try {
        val dtos: List<ShipPlacementDto> = ships.map { placement ->
            ShipPlacementDto(
                shipId      = placement.shipId.name,
                row         = placement.headCoord.rowOf(),
                col         = placement.headCoord.colOf(),
                orientation = if (placement.orientation is Orientation.Horizontal) "H" else "V"
            )
        }
        val shipsJson = Json.encodeToString(dtos)
        val gameRef = database.getReference("${FirebaseSchema.GAMES}/$gameId")
        gameRef.child("${FirebaseSchema.BOARDS}/$myUid/${FirebaseSchema.BOARD_SHIPS}")
            .setValue(shipsJson).await()
```

**What is wrong:**
Ship positions are stored as plaintext JSON at `/games/{gameId}/boards/{uid}/ships`. While the Firebase security rule (`database.rules.json:23`) restricts reads to the owner (`auth.uid === $uid`) during active play, it also allows reads when `meta/status === 'finished'`. A cheating client can:
1. Write `finished` to the meta status (the meta `.write` rule at line 13 allows participants to write to meta)
2. Read the opponent's board data
3. Write status back to `battle`
4. Now knows exact ship positions

Even without the status manipulation, the plaintext format means anyone with Firebase database access (e.g., Firebase console, stolen API key + anonymous auth) can read all boards.

**Attack scenario:**
```bash
# Using Firebase REST API with anonymous auth token:
curl "https://battleship-f5406-default-rtdb.asia-southeast1.firebasedatabase.app/games/GAME_ID/meta/status.json?auth=TOKEN" \
  -X PUT -d '"finished"'
# Now read opponent's board:
curl "https://battleship-f5406-default-rtdb.asia-southeast1.firebasedatabase.app/games/GAME_ID/boards/OPPONENT_UID/ships.json?auth=TOKEN"
# Reset status:
curl "https://battleship-f5406-default-rtdb.asia-southeast1.firebasedatabase.app/games/GAME_ID/meta/status.json?auth=TOKEN" \
  -X PUT -d '"battle"'
```

**Business impact:**
Opponent can see all ship positions in real-time, rendering the game trivially winnable. This is the equivalent of seeing an opponent's cards in poker.

**Fix:**
Encrypt ship placements client-side before uploading. Each player encrypts with a key derived from their UID + a game-specific salt. Server (Cloud Function) holds the decryption logic. At minimum, hash the placements and only reveal them post-game.

```kotlin
// Before uploading, encrypt the JSON:
val encryptedShips = AesGcm.encrypt(shipsJson, deriveKey(myUid, gameId))
gameRef.child("${FirebaseSchema.BOARDS}/$myUid/${FirebaseSchema.BOARD_SHIPS}")
    .setValue(Base64.encode(encryptedShips)).await()
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "setValue(shipsJson)" --include="*.kt" .
# Should return zero results after fix — only encrypted data should be written
```

---
### F-3 - Meta Write Rule Allows Participants to Manipulate Game Status

| Field | Detail |
|---|---|
| Severity | High |
| CVSS v3.1 | 7.5 - AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N |
| File | `database.rules.json` |
| Line / Method | line 13 / meta `.write` rule |
| Effort to Exploit | Low |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```json
"meta": {
  ".write": "auth != null && (
    !data.exists() ||
    data.child('hostUid').val() === auth.uid ||
    data.child('guestUid').val() === auth.uid ||
    (
      data.child('guestUid').val() === null &&
      newData.child('guestUid').val() === auth.uid
    )
  )"
}
```

**What is wrong:**
Both host and guest can write *any* field under `/meta`, including `status`, `winner`, and `currentTurn`. A cheating player can:
- Set `winner` to their own UID at any time to claim victory
- Set `currentTurn` to always be their turn
- Set `status` to `finished` to end the game prematurely
- Set `status` to `finished` then back to `battle` to exploit the board read rule (see F-2)

**Attack scenario:**
```bash
# Claim victory instantly after game starts:
curl "https://battleship-f5406-default-rtdb.asia-southeast1.firebasedatabase.app/games/GAME_ID/meta.json?auth=TOKEN" \
  -X PATCH -d '{"winner":"MY_UID","status":"finished"}'
```

**Business impact:**
Any player can win any game instantly. Leaderboards are completely compromisable. Stats become meaningless.

**Fix — replace the meta write rule with granular per-field rules:**
```json
"meta": {
  "hostUid":     { ".write": "auth != null && !data.exists()" },
  "guestUid":    { ".write": "auth != null && data.val() === null && newData.val() === auth.uid" },
  "status":      { ".write": "auth != null && (
    (data.val() === 'waiting' && newData.val() === 'setup' && data.parent().child('guestUid').val() === auth.uid) ||
    (data.val() === 'setup' && newData.val() === 'battle')
  )" },
  "winner":      { ".write": false },
  "currentTurn": { ".write": false },
  "createdAt":   { ".write": "auth != null && !data.exists()" },
  "updatedAt":   { ".write": "auth != null" },
  "roomCode":    { ".write": "auth != null && !data.exists()" }
}
```
The `winner` and `currentTurn` fields should only be writable by a Cloud Function with admin privileges.

**How to verify the fix in Claude Code:**
```bash
cat database.rules.json | grep -A2 '"winner"'
# Should show ".write": false or a Cloud Function-only rule
```

---
### F-4 - `claimVictory()` Allows Unilateral Win Declaration

| Field | Detail |
|---|---|
| Severity | High |
| CVSS v3.1 | 7.5 - AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N |
| File | `core/multiplayer/src/main/kotlin/.../repository/FirebaseMatchRepositoryImpl.kt` |
| Line / Method | lines 196-206 / `claimVictory()` |
| Effort to Exploit | Trivial |
| Fix Effort | Days |

**Vulnerable code (copied from file):**
```kotlin
override suspend fun claimVictory(gameId: String): Result<Unit> {
    val myUid = authManager.currentUid ?: return Result.failure(Exception("Not authenticated"))
    return try {
        val metaRef = database.getReference("${FirebaseSchema.GAMES}/$gameId/${FirebaseSchema.META}")
        metaRef.child(FirebaseSchema.WINNER).setValue(myUid).await()
        metaRef.child(FirebaseSchema.STATUS).setValue(FirebaseSchema.STATUS_FINISHED).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**What is wrong:**
Any authenticated game participant can call `claimVictory()` at any time. The function writes their UID as the winner with no validation that they actually sunk all opponent ships. Combined with F-3 (permissive meta write rules), this is trivially exploitable.

**Attack scenario:**
Call `claimVictory()` immediately after the game starts — before any shots are fired. The opponent's device sees `status=finished, winner=attackerUid` and navigates to the game-over screen showing a loss.

**Business impact:**
Leaderboard farming. A bot could create/join hundreds of games and instantly claim victory in each, dominating leaderboards.

**Fix:**
Victory declaration must be server-side only. Use a Firebase Cloud Function that:
1. Reads both players' placements
2. Reads the shot history
3. Verifies all opponent ship cells have been hit
4. Only then writes the winner

```kotlin
// Client should call a Cloud Function instead:
override suspend fun claimVictory(gameId: String): Result<Unit> {
    return try {
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("claimVictory")
            .call(mapOf("gameId" to gameId))
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "setValue.*WINNER\|setValue.*STATUS_FINISHED" --include="*.kt" . | grep -v test
# Should return zero results — only Cloud Functions should write winner/status
```

---
### F-5 - `android:allowBackup="true"` Exposes All App Data

| Field | Detail |
|---|---|
| Severity | Medium |
| CVSS v3.1 | 5.5 - AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N |
| File | `app/src/main/AndroidManifest.xml` |
| Line / Method | line 17 |
| Effort to Exploit | Low |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```xml
<application
    android:name=".BattleshipApplication"
    android:allowBackup="true"
```

**What is wrong:**
With `allowBackup="true"`, the entire app data directory (Room database, DataStore preferences, cached files) can be extracted via `adb backup` on devices with USB debugging enabled, or restored from a backup onto another device. This exposes:
- Firebase anonymous UID (stored in DataStore via `PreferencesRepositoryImpl.kt:83`)
- All game history, ship placements, and statistics
- Player preferences

On rooted devices, the data directory is directly accessible regardless of this flag, but `allowBackup="true"` extends the risk to non-rooted devices with USB debugging.

**Attack scenario:**
```bash
adb backup -f battleship_backup.ab com.battleship.fleetcommand
java -jar abe.jar unpack battleship_backup.ab backup.tar
tar xf backup.tar
# Now read databases/battleship_db and datastore/battleship_preferences.preferences_pb
```

**Business impact:**
Player identity theft (Firebase UID), game data exposure, potential account cloning.

**Fix:**
```xml
<application
    android:name=".BattleshipApplication"
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
```

**How to verify the fix in Claude Code:**
```bash
grep -n "allowBackup" app/src/main/AndroidManifest.xml
# Should show allowBackup="false"
```

---
### F-6 - No `network_security_config.xml` Present

| Field | Detail |
|---|---|
| Severity | Medium |
| CVSS v3.1 | 5.3 - AV:N/AC:H/PR:N/UI:R/S:U/C:H/I:N/A:N |
| File | `app/src/main/AndroidManifest.xml` |
| Line / Method | Missing `android:networkSecurityConfig` attribute |
| Effort to Exploit | Medium |
| Fix Effort | Hours |

**Vulnerable code:**
No `network_security_config.xml` exists anywhere in the project. The manifest has no `android:networkSecurityConfig` attribute.

**What is wrong:**
Without an explicit network security configuration, the app relies on platform defaults. While Android 9+ blocks cleartext by default, an explicit config is a defense-in-depth best practice. More critically, there is no certificate pinning for Firebase endpoints, making the app vulnerable to MITM attacks on networks with compromised CAs (corporate proxies, state-level adversaries).

**Attack scenario:**
On a compromised network with a rogue CA installed on the device, an attacker can intercept Firebase RTDB traffic, read game state in transit, and inject modified responses.

**Business impact:**
Game data interception, potential manipulation of online multiplayer state in transit.

**Fix — create `app/src/main/res/xml/network_security_config.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```
Then add to manifest: `android:networkSecurityConfig="@xml/network_security_config"`

**How to verify the fix in Claude Code:**
```bash
grep -rn "networkSecurityConfig" app/src/main/AndroidManifest.xml
# Should return a match
```

---
### F-7 - Room Database Not Encrypted

| Field | Detail |
|---|---|
| Severity | Medium |
| CVSS v3.1 | 4.0 - AV:L/AC:L/PR:H/UI:N/S:U/C:H/I:N/A:N |
| File | `core/data/src/main/kotlin/.../local/BattleshipDatabase.kt` |
| Line / Method | lines 43-48 / `create()` |
| Effort to Exploit | Medium |
| Fix Effort | Days |

**Vulnerable code (copied from file):**
```kotlin
companion object {
    fun create(context: Context): BattleshipDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            BattleshipDatabase::class.java,
            "battleship_db"
        ).build()
}
```

**What is wrong:**
The Room database is created without encryption. On a rooted device or via `adb backup` (see F-5), the `battleship_db` file can be opened with any SQLite browser. It contains complete game history, ship placements for all games, shot histories, and player statistics. For a game app without PII this is lower severity, but the database also stores online game IDs that link to Firebase records.

**Business impact:**
Game data exposure on rooted devices. Low direct impact for this app type, but relevant for data safety section claims.

**Fix:**
Consider using SQLCipher for Android if data protection is required:
```kotlin
// Using net.zetetic:android-database-sqlcipher
val passphrase = getOrCreatePassphrase(context)
Room.databaseBuilder(context, BattleshipDatabase::class.java, "battleship_db")
    .openHelperFactory(SupportFactory(passphrase))
    .build()
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "SupportFactory\|sqlcipher\|openHelperFactory" --include="*.kt" .
# Should return matches in BattleshipDatabase.kt
```

---
### F-8 - Firebase Anonymous UID Stored in Plaintext DataStore

| Field | Detail |
|---|---|
| Severity | Low |
| CVSS v3.1 | 3.3 - AV:L/AC:L/PR:H/UI:N/S:U/C:L/I:N/A:N |
| File | `core/data/src/main/kotlin/.../datastore/PreferencesRepositoryImpl.kt` |
| Line / Method | lines 80-84 / `getOnlinePlayerUid()` / `setOnlinePlayerUid()` |
| Effort to Exploit | Low |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```kotlin
override suspend fun getOnlinePlayerUid(): String? =
    dataStore.data.first()[DataStoreKeys.ONLINE_PLAYER_UID]

override suspend fun setOnlinePlayerUid(uid: String) {
    dataStore.edit { it[DataStoreKeys.ONLINE_PLAYER_UID] = uid }
}
```

**What is wrong:**
The Firebase anonymous UID is stored in plaintext Preferences DataStore (`battleship_preferences`). This UID is the sole identity credential for online play — if stolen, an attacker can impersonate the user in any active game. While Firebase also persists auth state internally, the DataStore copy is additionally exposed.

**Business impact:**
Limited — Firebase anonymous UIDs are ephemeral and don't map to real user accounts. However, combined with `allowBackup="true"`, a cloned backup could impersonate a player.

**Fix:**
Use `EncryptedSharedPreferences` or Android Keystore for sensitive values.

**How to verify the fix in Claude Code:**
```bash
grep -rn "ONLINE_PLAYER_UID" --include="*.kt" .
# Verify it's stored via EncryptedSharedPreferences, not plaintext DataStore
```

---
### F-9 - Timber Warning/Error Logs Not Stripped in Release Builds

| Field | Detail |
|---|---|
| Severity | Low |
| CVSS v3.1 | 3.1 - AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N |
| File | `app/proguard-rules.pro` |
| Line / Method | lines 102-107 |
| Effort to Exploit | Trivial |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```proguard
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
```

**What is wrong:**
Only `Timber.v()`, `Timber.d()`, and `Timber.i()` are stripped. `Timber.w()` and `Timber.e()` survive in release builds. The codebase contains 40+ `Timber.w` and `Timber.e` calls that log:
- Firebase game IDs (`MatchmakingRepository.kt:74`: `"createGame success gameId=$gameId roomCode=$roomCode"`)
- Player UIDs (`FirebaseAuthManager.kt:52`: `"anonymous auth complete, uid=$uid"`)
- Room codes (`MatchmakingRepository.kt:132`: `"joinGame roomCode=$normalised"`)

An attacker running `adb logcat` on a connected device or emulator can see these values in real-time.

**Business impact:**
Information disclosure of game IDs, room codes, and UIDs to anyone with USB access to the device.

**Fix — also strip `w()` and `e()` in release:**
```proguard
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
```

**How to verify the fix in Claude Code:**
```bash
grep -A5 "assumenosideeffects.*Timber" app/proguard-rules.pro
# Should include w(...) and e(...)
```

---
### F-10 - `println()` Used in Production Code

| Field | Detail |
|---|---|
| Severity | Low |
| CVSS v3.1 | 2.0 - AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N |
| File | `core/domain/src/main/kotlin/.../engine/GameStateMachine.kt` |
| Line / Method | line 27 |
| Effort to Exploit | Trivial |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```kotlin
private object DefaultLogger : Logger {
    override fun warn(message: String) {
        println("GameStateMachine WARNING: $message")
    }
}
```

**What is wrong:**
`println()` bypasses all ProGuard stripping rules and log level filtering. It writes directly to `System.out`, which is captured by `adb logcat`. This leaks game state machine transitions (e.g., "Illegal transition: state=PlayerTurn event=CellFired") in release builds.

**Business impact:**
Minor information disclosure about game state internals.

**Fix:**
Replace with a no-op logger for production, or inject Timber:
```kotlin
private object DefaultLogger : Logger {
    override fun warn(message: String) { /* no-op in production */ }
}
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "println" --include="*.kt" . | grep -v test | grep -v build
# Should return zero results in production source
```

---
### F-11 - `.firebaserc` Committed with Real Project ID

| Field | Detail |
|---|---|
| Severity | Low |
| CVSS v3.1 | 3.7 - AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:N/A:N |
| File | `.firebaserc` |
| Line / Method | line 3 |
| Effort to Exploit | Trivial |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```json
{
  "projects": {
    "default": "battleship-f5406"
  }
}
```

**What is wrong:**
The `.firebaserc` file exposes the real Firebase project ID `battleship-f5406`. Combined with the database URL visible in the (untracked) `google-services.json`, this gives attackers the exact endpoints to target. While the API key in `google-services.json` is correctly gitignored, the project ID in `.firebaserc` and the database URL pattern (`battleship-f5406-default-rtdb.asia-southeast1.firebasedatabase.app`) are discoverable.

**Business impact:**
Reduces attacker reconnaissance effort. Not a vulnerability by itself, but lowers the bar for exploiting F-1 through F-4.

**Fix:**
Add `.firebaserc` to `.gitignore` and create a `.firebaserc.template` with a placeholder.

**How to verify the fix in Claude Code:**
```bash
grep "firebaserc" .gitignore
# Should return a match
```

---
### F-12 - Zero Root/Tamper/Integrity Detection

| Field | Detail |
|---|---|
| Severity | Medium |
| CVSS v3.1 | 6.5 - AV:L/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N |
| File | N/A — no detection code found |
| Line / Method | N/A |
| Effort to Exploit | Trivial |
| Fix Effort | Days |

**What is wrong:**
The codebase contains zero root detection, zero tamper detection, zero Play Integrity API calls, and zero Frida/instrumentation detection. Confirmed by grep:
```
grep -rn "RootBeer|SafetyNet|PlayIntegrity|isRooted|detectEmulator|Frida|tamper" → No matches found
```

This means a rooted device running a modified APK faces zero resistance. Combined with the client-side shot resolution (F-1) and permissive Firebase rules (F-3), this is a critical enabler for cheating.

**Business impact:**
All client-side security measures can be trivially bypassed on rooted devices or emulators.

**Fix:**
Integrate Play Integrity API and enforce server-side:
```kotlin
// In Firebase Cloud Function:
// 1. Client sends Play Integrity token with each game action
// 2. Cloud Function verifies token before processing the action
// 3. Modified/rooted devices are blocked from competitive play
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "PlayIntegrity\|IntegrityManager" --include="*.kt" .
# Should return matches after integration
```

---
### F-13 - No GDPR/Consent Flow Before Firebase Analytics

| Field | Detail |
|---|---|
| Severity | Medium |
| CVSS v3.1 | N/A (compliance, not technical) |
| File | `app/build.gradle.kts` |
| Line / Method | line 79 / `implementation(libs.firebase.analytics)` |
| Effort to Exploit | N/A |
| Fix Effort | Days |

**What is wrong:**
Firebase Analytics is included as a dependency and initialized automatically (Firebase auto-init). There is no consent flow before data collection begins. The `core:ads` module has a `ConsentManager.kt`, but it is excluded from the app dependencies. Firebase Analytics collects device identifiers, app usage events, and crash data without user consent.

The app targets ages 8+ (per `instruct.md` Section 1). If any user could be under 13, COPPA compliance requires verifiable parental consent before data collection.

**Business impact:**
Potential GDPR/COPPA violation. Risk of Play Store policy enforcement action. Fines up to 4% of annual revenue (GDPR) or $50,120 per violation (COPPA).

**Fix:**
1. Disable Firebase Analytics auto-init in the manifest
2. Implement a consent dialog on first launch
3. Only enable Analytics after consent is obtained

```xml
<!-- In AndroidManifest.xml: -->
<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />
```
```kotlin
// After consent obtained:
FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
```

**How to verify the fix in Claude Code:**
```bash
grep -rn "firebase_analytics_collection_deactivated\|setAnalyticsCollectionEnabled" --include="*.kt" --include="*.xml" .
# Should return matches
```

---
### F-14 - ProGuard Over-Keeps Domain Models, Reducing Obfuscation

| Field | Detail |
|---|---|
| Severity | Info |
| CVSS v3.1 | N/A |
| File | `app/proguard-rules.pro` |
| Line / Method | lines 71-72 |
| Effort to Exploit | N/A |
| Fix Effort | Hours |

**Vulnerable code (copied from file):**
```proguard
-keep class com.battleship.fleetcommand.core.domain.** { *; }
-keepclassmembers class com.battleship.fleetcommand.core.domain.** { *; }
```

**What is wrong:**
The entire `core.domain` package is kept unobfuscated, including `GameEngine`, `GameStateMachine`, `PlacementValidator`, AI difficulty enums, and all game logic classes. This makes reverse engineering the game logic trivial — an attacker can decompile the APK and read class/method/field names in plain text.

**Business impact:**
Lowers the effort to understand and exploit game logic (especially the AI probability density algorithm).

**Fix:**
Only keep the classes that actually need reflection (serialization, Room entities):
```proguard
-keep @kotlinx.serialization.Serializable class com.battleship.fleetcommand.core.domain.** { *; }
# Remove the blanket -keep for all domain classes
```

**How to verify the fix in Claude Code:**
```bash
grep "core.domain.\*\*" app/proguard-rules.pro
# Should only show targeted keeps, not blanket keeps
```

---

## 4. Attack Narratives

### Narrative 1: "The Invincible Admiral" — Online Multiplayer Cheat Combo

**Attacker profile:** Intermediate Android developer with Frida installed.

1. Attacker installs the app normally, creates a Firebase anonymous account.
2. Attacker decompiles the APK with `jadx` and reads `OnlineGameViewModel.kt` — immediately spots that `resolveNewOpponentShots()` at line 352 resolves shots client-side.
3. Attacker writes a Frida script that hooks `gameEngine.fireShot()` to always return `ShotOutcome.Miss`, regardless of actual placement.
4. Attacker joins an online game via room code. The opponent places ships normally.
5. During battle, every shot the opponent fires registers as a miss. The attacker's fleet is untouchable.
6. The attacker fires normally, eventually sinks all opponent ships, and calls `claimVictory()` (`FirebaseMatchRepositoryImpl.kt:196`).
7. The opponent's device receives the `status=finished, winner=attackerUid` update and shows a loss screen.
8. The attacker repeats this across hundreds of games, farming the "Most Victories" leaderboard (`PlayGamesManager.kt:75`).
9. Firebase security rules (`database.rules.json:32`) never validate that shot results are correct — they only check participant identity.

**Files involved:** `OnlineGameViewModel.kt:352`, `FirebaseMatchRepositoryImpl.kt:196`, `database.rules.json:32`, `PlayGamesManager.kt:75`

### Narrative 2: "The Oracle" — Reading Opponent Ships Mid-Game

1. Attacker joins an online game and opens a second terminal with `adb logcat`.
2. From Timber.d logs (surviving in debug builds, or via Frida in release), the attacker reads: `"MatchmakingRepository: joinGame success gameId=-NxAbCdEf"` — now has the gameId.
3. Attacker authenticates to Firebase REST API using their anonymous token.
4. Attacker writes `"finished"` to `/games/-NxAbCdEf/meta/status` — the permissive meta write rule (`database.rules.json:13`) allows this since they're a participant.
5. The board read rule (`database.rules.json:23`) now allows reading the opponent's board: `auth.uid === $uid || meta/status === 'finished'`.
6. Attacker reads `/games/-NxAbCdEf/boards/OPPONENT_UID/ships` — gets the full plaintext JSON of opponent ship positions.
7. Attacker writes `"battle"` back to status before the opponent's client processes the state change.
8. Attacker now knows the exact position of every enemy ship and wins with perfect accuracy.

**Files involved:** `database.rules.json:11,13,23`, `FirebaseMatchRepositoryImpl.kt:92`, `MatchmakingRepository.kt:74`

### Narrative 3: "The Data Thief" — Backup Extraction on Shared Devices

1. Victim plays Battleship Fleet Command on a shared tablet (family device, school tablet).
2. Another user with USB debugging access runs: `adb backup -f bs.ab com.battleship.fleetcommand`
3. Because `allowBackup="true"` (`AndroidManifest.xml:17`), the entire data directory is exported.
4. Attacker extracts the backup and opens `battleship_db` with DB Browser for SQLite.
5. All game history, ship placements, shot patterns, and statistics are exposed.
6. Attacker extracts the DataStore file and reads the Firebase UID (`PreferencesRepositoryImpl.kt:81`).
7. Attacker can now sign in as the victim's anonymous Firebase identity on another device and join/disrupt their active online games.

**Files involved:** `AndroidManifest.xml:17`, `BattleshipDatabase.kt:43-48`, `PreferencesRepositoryImpl.kt:80-84`

---

## 5. Action Plan

### Block ship - fix now
- **F-1**: `OnlineGameViewModel.kt:341` — Move shot resolution to a Firebase Cloud Function; clients must not resolve opponent shots
- **F-3**: `database.rules.json:13` — Replace blanket meta write with per-field rules; `winner` and `currentTurn` must be server-write-only
- **F-4**: `FirebaseMatchRepositoryImpl.kt:196` — `claimVictory()` must call a Cloud Function, not write directly to Firebase

### Fix within 30 days
- **F-2**: `FirebaseMatchRepositoryImpl.kt:92` — Encrypt ship placements before uploading to Firebase
- **F-5**: `AndroidManifest.xml:17` — Set `android:allowBackup="false"`
- **F-6**: Missing `network_security_config.xml` — Create and reference in manifest
- **F-12**: No files — Integrate Play Integrity API for online multiplayer
- **F-13**: `app/build.gradle.kts:79` — Disable Firebase Analytics auto-init; add consent flow

### Fix within 90 days
- **F-7**: `BattleshipDatabase.kt:43` — Evaluate SQLCipher for database encryption
- **F-8**: `PreferencesRepositoryImpl.kt:83` — Migrate UID storage to EncryptedSharedPreferences
- **F-9**: `proguard-rules.pro:102` — Strip Timber.w() and Timber.e() in release
- **F-10**: `GameStateMachine.kt:27` — Replace `println()` with no-op logger
- **F-11**: `.firebaserc:3` — Add to .gitignore, rotate Firebase project if public repo
- **F-14**: `proguard-rules.pro:71` — Reduce blanket keep rules to targeted keeps

---

## 6. Pre-Launch Checklist

- [ ] No secrets in source, .properties, or BuildConfig
- [x] `google-services.json` correctly gitignored (template committed instead)
- [ ] `network_security_config.xml` blocks all cleartext — **MISSING**
- [ ] Certificate pinning on all production endpoints — **NOT IMPLEMENTED**
- [ ] Tokens stored in EncryptedSharedPreferences or Keystore only — **PLAINTEXT DATASTORE**
- [x] IAP receipt validation is server-side only — **N/A (no IAP)**
- [ ] `android:debuggable` absent or false in release manifest — **OK (not set, defaults to false)**
- [x] `minifyEnabled true` in release buildType — **CONFIRMED** (`app/build.gradle.kts:39`)
- [ ] All `Log.d` / `Log.v` calls stripped from release — **PARTIAL (Timber w/e survive)**
- [ ] `allowBackup` false or explicit backup rules defined — **FAILING (`true`)**
- [ ] Play Integrity API integrated and enforced server-side — **NOT IMPLEMENTED**
- [x] Every permission in manifest is actively used and justified — **3 permissions, all justified**
- [ ] GDPR consent fires before any data collection — **NOT IMPLEMENTED**
- [ ] Data safety section on Play Store matches actual code behaviour — **CANNOT VERIFY (no listing yet)**
- [ ] All SDK versions checked against current CVE databases — **MANUAL REVIEW REQUIRED**
- [x] No hardcoded `http://` endpoints anywhere in codebase — **CONFIRMED (zero matches)**

---

## 7. Verification Commands

```bash
# F-1: Verify client no longer resolves shots
grep -rn "gameEngine.fireShot.*myPlacements" --include="*.kt" feature/game/ | grep -v test
# Expected: zero results (shot resolution moved to Cloud Function)

# F-3: Verify meta write rules are granular
cat database.rules.json | python -c "import sys,json; rules=json.load(sys.stdin); print('winner' in rules['rules']['games']['\$gameId']['meta'])"
# Expected: True (per-field rules exist)

# F-4: Verify claimVictory uses Cloud Function
grep -rn "claimVictory" --include="*.kt" . | grep -v test | grep -v interface
# Expected: should show FirebaseFunctions.getHttpsCallable, not direct setValue

# F-5: Verify allowBackup is false
grep "allowBackup" app/src/main/AndroidManifest.xml
# Expected: android:allowBackup="false"

# F-6: Verify network security config exists
ls app/src/main/res/xml/network_security_config.xml
# Expected: file exists

# F-9: Verify Timber fully stripped
grep -c "Timber" app/proguard-rules.pro
# Expected: rule includes w(...) and e(...)

# F-10: Verify no println in production
grep -rn "println" --include="*.kt" core/ feature/ app/src/main/ | grep -v test | grep -v build
# Expected: zero results

# F-12: Verify Play Integrity integrated
grep -rn "PlayIntegrity\|IntegrityManager\|integrity" --include="*.kt" . | grep -v test
# Expected: matches in multiplayer module

# F-13: Verify Analytics consent gate
grep -rn "firebase_analytics_collection_deactivated" app/src/main/AndroidManifest.xml
# Expected: one match
```
