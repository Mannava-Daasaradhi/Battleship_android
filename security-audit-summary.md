# Battleship Fleet Command - Security Audit Summary

**Date:** 2026-05-20 | **Auditor:** Claude Code | **Classification:** Confidential

---

## Risk Dashboard

| Domain | Score /10 | Risk | Top Finding |
|---|---|---|---|
| Data Storage & Secrets | 6/10 | Medium | `AndroidManifest.xml:17` — `allowBackup="true"` |
| Network & Transport | 5/10 | Medium | No `network_security_config.xml` |
| Auth & Sessions | 7/10 | Low | UID in plaintext DataStore |
| IAP Integrity | 10/10 | None | No IAP code present |
| Permissions & Privacy | 7/10 | Low | Only 3 permissions, all justified |
| Reverse Engineering | 6/10 | Medium | Domain models fully kept in ProGuard |
| Game Threats | 2/10 | Critical | Client-side shot resolution in online multiplayer |
| SDK Risk | 7/10 | Low | Dependencies current; review for updates |
| Tamper Detection | 1/10 | Critical | Zero root/tamper/integrity detection |
| Compliance | 5/10 | Medium | No GDPR consent before Firebase Analytics |
| **OVERALL** | **56/100** | **High Risk** | |

**Bottom line:** Offline modes (AI, Pass & Play) are safe to ship. **Online multiplayer is critically exploitable** — a cheating player can make their fleet invincible, read opponent ship positions, or declare instant victory. These are architectural issues requiring server-side validation.

---

## Action Plan

### Block ship - fix now
| ID | File:Line | Action |
|---|---|---|
| F-1 | `OnlineGameViewModel.kt:341` | Move shot resolution to Firebase Cloud Function |
| F-3 | `database.rules.json:13` | Replace blanket meta write with per-field rules |
| F-4 | `FirebaseMatchRepositoryImpl.kt:196` | Route `claimVictory()` through Cloud Function |

### Fix within 30 days
| ID | File:Line | Action |
|---|---|---|
| F-2 | `FirebaseMatchRepositoryImpl.kt:92` | Encrypt ship placements before Firebase upload |
| F-5 | `AndroidManifest.xml:17` | Set `android:allowBackup="false"` |
| F-6 | Missing file | Create `network_security_config.xml` |
| F-12 | N/A | Integrate Play Integrity API |
| F-13 | `app/build.gradle.kts:79` | Add consent flow before Firebase Analytics |

### Fix within 90 days
| ID | File:Line | Action |
|---|---|---|
| F-7 | `BattleshipDatabase.kt:43` | Evaluate SQLCipher encryption |
| F-8 | `PreferencesRepositoryImpl.kt:83` | Migrate UID to EncryptedSharedPreferences |
| F-9 | `proguard-rules.pro:102` | Strip Timber.w() and Timber.e() in release |
| F-10 | `GameStateMachine.kt:27` | Replace `println()` with no-op |
| F-11 | `.firebaserc:3` | Add to .gitignore |
| F-14 | `proguard-rules.pro:71` | Reduce blanket ProGuard keep rules |

---

## Pre-Launch Checklist

- [x] No secrets committed to git (google-services.json properly gitignored)
- [ ] `network_security_config.xml` blocks all cleartext
- [ ] Certificate pinning on production endpoints
- [ ] Sensitive tokens in EncryptedSharedPreferences/Keystore
- [x] No IAP (N/A)
- [x] `minifyEnabled true` in release
- [ ] All log calls stripped from release (Timber.w/e survive)
- [ ] `allowBackup="false"` in manifest
- [ ] Play Integrity API integrated and enforced server-side
- [x] All permissions justified (INTERNET, VIBRATE, ACCESS_NETWORK_STATE)
- [ ] GDPR consent before data collection
- [ ] Data safety section matches actual behavior
- [ ] SDK versions checked against CVE databases
- [x] No hardcoded `http://` endpoints
