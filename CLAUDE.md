# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires env vars: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :core:domain:test
./gradlew :feature:game:test

# Run a single test class
./gradlew :core:domain:test --tests "com.battleship.fleetcommand.core.domain.GameEngineTest"

# Lint check
./gradlew lint

# Check Compose stability
python scripts/check-compose-stability.py
```

## Architecture Overview

This is a multi-module Android app using **Jetpack Compose**, **Hilt DI**, **Room**, and **Firebase**. The module graph flows strictly one way: `feature` and `app` depend on `core`; `core` modules never depend on `feature`.

### Module Structure

```
app/                    — Application entry point, NavHost, DI wiring
feature/
  menu/                 — MainMenuScreen, ModeSelectScreen, DifficultyScreen, PlayerNamesScreen
  setup/                — ShipPlacementScreen (drag/drop placement for all modes)
  game/                 — BattleScreen (AI + P&P), OnlineBattleScreen (Firebase), HandOffScreen, GameOverScreen
  lobby/                — OnlineLobbyScreen, WaitingForOpponentScreen
  stats/                — StatisticsScreen
  settings/             — SettingsScreen
core/
  domain/               — Pure Kotlin: game models, GameEngine, GameStateMachine, repository interfaces
  ai/                   — AiTurnProcessor, BoardKnowledge (probability density heat map)
  data/                 — Room DAOs, DataStore, repository implementations
  multiplayer/          — Firebase Realtime Database matchmaking and game sync
  ui/                   — Shared Compose components, theme, animations, Routes (type-safe nav)
  analytics/            — Firebase Analytics wrapper
  ads/                  — AdMob integration (excluded from :app for now — see settings.gradle.kts)
  testing/              — TestDispatcherProvider and shared test utilities
benchmark/              — Macrobenchmark + baseline profile generator
build-logic/convention/ — Custom Gradle convention plugins
```

### Key Architectural Patterns

**Game logic** lives entirely in `:core:domain` — `GameEngine` is a pure Kotlin class (no Android imports, no coroutines). It handles ship placement, shot resolution, auto-placement, and board snapshot generation.

**Navigation** uses type-safe Compose Navigation routes defined in `core/ui/.../navigation/Routes.kt`. All routes are `@Serializable` data classes/objects. The `BattleshipNavHost` in `:app` wires all screens together. Online battles use `OnlineBattleRoute`/`OnlineGameViewModel`; AI and Pass & Play use `BattleRoute`/`BattleViewModel`.

**Dependency injection** uses Hilt. Module bindings live in `app/di/` (AppModule, RepositoryModule, DatabaseModule). Feature modules use `@HiltViewModel`.

**State management** follows Compose-idiomatic unidirectional data flow: `ViewModel` exposes `StateFlow<UiState>` and a `Channel<UiEffect>` for one-shot events. ViewModels consume `TestDispatcherProvider` from `:core:testing` for testability.

**Pass & Play hand-off**: `HandOffScreen` is a full-screen opaque overlay with a 3-second mandatory countdown (cannot be skipped). The `HandOffRoute` carries `isP1HandOff` and `phase` (SETUP vs BATTLE).

**AI tiers**: Easy = random, Medium = hunt-and-target, Hard = probability density heat map (recalculated each shot). AI logic lives in `:core:ai`.

**Online multiplayer**: Firebase Anonymous Auth (invisible to user), 6-char room code, Firebase Realtime Database for game state sync. `FirebaseMatchRepository` is the interface in `:core:domain`; implementation is in `:core:multiplayer`.

**Room DB schema** exports live in `app/schemas/` (checked into version control).

## Convention Plugins

All modules use custom convention plugins from `build-logic/`:
- `battleship.android.application` — app module
- `battleship.android.library` — core modules without Compose
- `battleship.android.library.compose` — core modules with Compose
- `battleship.android.feature` — feature modules (auto-adds Compose, Hilt, Lifecycle, test deps)
- `battleship.kotlin.library` — pure Kotlin modules (`:core:domain`, `:core:ai`)

**SDK versions**: compileSdk 35, minSdk 26, JVM target 17, Kotlin 2.x.

## Constraints

- **Zero paid dependencies** — free stack only. Check `instruct.md` Section 2 before adding any library.
- **Zero XML layouts** — all UI is Jetpack Compose.
- **Zero Java** — 100% Kotlin.
- **No ads during gameplay** — never show ads on BattleScreen, HandOffScreen, WaitingForOpponentScreen, or LobbyScreen.
- `:core:ads` is implemented but intentionally excluded from `:app` dependencies until the owner integrates AdMob.

## Testing

Tests use **JUnit 5**, **MockK**, **Turbine** (Flow testing), and `kotlinx-coroutines-test`. The `core:testing` module provides `TestDispatcherProvider`. Run unit tests with `./gradlew test`; there are no instrumented tests outside the `benchmark` module.
