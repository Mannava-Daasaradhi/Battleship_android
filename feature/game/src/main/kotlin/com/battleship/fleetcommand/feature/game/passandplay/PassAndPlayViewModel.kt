// ============================================================
// feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/passandplay/PassAndPlayViewModel.kt
// ============================================================
// FILE: feature/game/src/main/kotlin/com/battleship/fleetcommand/feature/game/passandplay/PassAndPlayViewModel.kt
package com.battleship.fleetcommand.feature.game.passandplay

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Pass & Play ViewModel — wired through BattleViewModel (polymorphic). Phase 5.
@HiltViewModel
class PassAndPlayViewModel @Inject constructor() : ViewModel()