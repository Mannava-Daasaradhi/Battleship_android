// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/MediumAi.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.Coord
import com.battleship.fleetcommand.core.domain.engine.FireResult
import com.battleship.fleetcommand.core.domain.ship.ShipId

/**
 * Medium AI — Hunt/Target mode.
 *
 * Phases:
 *  - **HUNT**: Random probing using a checkerboard parity filter to maximize coverage.
 *    Fires only at cells where (row + col) % 2 == 0, halving wasted shots on large ships.
 *    Falls back to any unshot cell if no parity-filtered candidate remains.
 *  - **TARGET**: After a hit, fire adjacent to the last hit in all 4 cardinal directions.
 *    After a second collinear hit, **locks the axis** (HORIZONTAL or VERTICAL) and
 *    continues along that axis from the hit origin in the current direction.
 *    On a miss while axis-locked, **reverses direction** from the hit origin.
 *
 * Transitions:
 *  - HUNT → TARGET: first HIT
 *  - TARGET → HUNT: ship SUNK (clears all state)
 *  - TARGET stays TARGET: on HIT (keeps targeting)
 *  - TARGET direction reversal: on MISS while axis-locked
 *
 * Pure Kotlin — zero Android imports.
 */
class MediumAi : AiStrategy {

    // ── Phase state ──────────────────────────────────────────────────────────
    private var phase: HuntPhase = HuntPhase.HUNT

    /** Stack of candidate cells to try in TARGET mode. */
    private val targetStack: ArrayDeque<Coord> = ArrayDeque()

    /** The axis locked after two collinear hits. Null until locked. */
    private var lockedAxis: Axis? = null

    /** The first hit that started the current targeting sequence. */
    private var hitOrigin: Coord? = null

    /** The most recent hit coord. */
    private var lastHit: Coord? = null

    /** Current direction along the locked axis: +1 forward, -1 reversed. */
    private var axisDirection: Int = 1

    private val random = kotlin.random.Random.Default

    // ── Phase enum ───────────────────────────────────────────────────────────
    internal enum class HuntPhase { HUNT, TARGET }
    internal enum class Axis { HORIZONTAL, VERTICAL }

    // ── AiStrategy ───────────────────────────────────────────────────────────

    override fun selectShot(opponentBoard: Board): Coord {
        return when (phase) {
            HuntPhase.HUNT   -> selectHunt(opponentBoard)
            HuntPhase.TARGET -> selectTarget(opponentBoard)
        }
    }

    override fun onShotResult(
        coord: Coord,
        result: FireResult,
        sunkShipId: ShipId?,
        opponentBoard: Board
    ) {
        when (result) {
            FireResult.HIT -> onHit(coord, opponentBoard)
            FireResult.MISS -> onMiss(opponentBoard)
            FireResult.SUNK -> onSunk()
        }
    }

    override fun reset() {
        phase = HuntPhase.HUNT
        targetStack.clear()
        lockedAxis = null
        hitOrigin = null
        lastHit = null
        axisDirection = 1
    }

    // ── Internal AI logic ─────────────────────────────────────────────────────

    internal fun currentPhase(): HuntPhase = phase
    internal fun currentAxis(): Axis? = lockedAxis

    private fun selectHunt(board: Board): Coord {
        // Checkerboard parity: only cells where (row + col) % 2 == 0
        val candidates = board.unshotCoords().filter { coord ->
            (coord.rowOf() + coord.colOf()) % 2 == 0
        }
        val pool = candidates.ifEmpty { board.unshotCoords() }
        require(pool.isNotEmpty()) { "MediumAi.selectHunt: no unshot cells remaining." }
        return pool[random.nextInt(pool.size)]
    }

    private fun selectTarget(board: Board): Coord {
        // Drain the stack, skipping any cells that were already fired (shouldn't happen, but guard).
        while (targetStack.isNotEmpty()) {
            val candidate = targetStack.removeFirst()
            if (board.isUnshot(candidate)) return candidate
        }
        // Stack exhausted — fall back to hunt mode.
        phase = HuntPhase.HUNT
        lockedAxis = null
        return selectHunt(board)
    }

    private fun onHit(coord: Coord, board: Board) {
        phase = HuntPhase.TARGET

        val origin = hitOrigin
        if (origin != null && lockedAxis == null) {
            // Two consecutive hits — lock the axis based on relative position.
            lockedAxis = if (coord.rowOf() == origin.rowOf()) Axis.HORIZONTAL else Axis.VERTICAL
            axisDirection = 1
        }

        if (hitOrigin == null) hitOrigin = coord
        lastHit = coord

        // Push new candidates based on current lock state.
        pushTargetCandidates(coord, board)
    }

    private fun onMiss(board: Board) {
        if (lockedAxis != null) {
            // Axis is locked but we missed — reverse and try from the origin in opposite direction.
            axisDirection = -axisDirection
            hitOrigin?.let { pushAxisCandidates(it, board) }
        }
        // If no axis locked, the stack will exhaust naturally; no special action needed.
    }

    private fun onSunk() {
        // Ship fully destroyed — reset everything and return to random hunting.
        phase = HuntPhase.HUNT
        lockedAxis = null
        hitOrigin = null
        lastHit = null
        axisDirection = 1
        targetStack.clear()
    }

    /**
     * Push adjacent candidates onto the stack.
     * If axis is locked, only push one cell in the locked direction.
     * If axis is not yet locked, push all 4 cardinal neighbours.
     */
    private fun pushTargetCandidates(coord: Coord, board: Board) {
        when (val axis = lockedAxis) {
            null -> {
                // Not axis-locked yet: push all 4 adjacent unshot cells.
                coord.adjacentCoords()
                    .filter { board.isUnshot(it) }
                    .forEach { targetStack.addLast(it) }
            }
            Axis.HORIZONTAL -> {
                val next = Coord.fromRowCol(coord.rowOf(), coord.colOf() + axisDirection)
                if (next.isValid() && board.isUnshot(next)) targetStack.addFirst(next)
            }
            Axis.VERTICAL -> {
                val next = Coord.fromRowCol(coord.rowOf() + axisDirection, coord.colOf())
                if (next.isValid() && board.isUnshot(next)) targetStack.addFirst(next)
            }
        }
    }

    /**
     * After direction reversal: push the next cell from [origin] along the locked axis
     * in the (now-reversed) [axisDirection].
     */
    private fun pushAxisCandidates(origin: Coord, board: Board) {
        val axis = lockedAxis ?: return
        val next = when (axis) {
            Axis.HORIZONTAL -> Coord.fromRowCol(origin.rowOf(), origin.colOf() + axisDirection)
            Axis.VERTICAL   -> Coord.fromRowCol(origin.rowOf() + axisDirection, origin.colOf())
        }
        if (next.isValid() && board.isUnshot(next)) targetStack.addFirst(next)
    }
}