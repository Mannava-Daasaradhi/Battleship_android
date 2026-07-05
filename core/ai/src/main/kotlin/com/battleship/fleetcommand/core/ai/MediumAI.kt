// FILE: core/ai/src/main/kotlin/com/battleship/fleetcommand/core/ai/MediumAi.kt

package com.battleship.fleetcommand.core.ai

import com.battleship.fleetcommand.core.domain.GameConstants
import com.battleship.fleetcommand.core.domain.board.Board
import com.battleship.fleetcommand.core.domain.board.CellState
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
 *  - **TARGET**: After the first hit, probe the four cardinal neighbours. After a second
 *    collinear hit, **lock the axis** (HORIZONTAL or VERTICAL) with a direction derived from
 *    where the second hit sits relative to the first, then walk the whole contiguous hit-line
 *    outward from the origin to the first open water cell. When that end dead-ends (miss, sunk,
 *    or board edge) the search **reverses** and walks the opposite end of the same line.
 *
 * Transitions:
 *  - HUNT → TARGET: first HIT
 *  - TARGET → HUNT: ship SUNK with no other wounded ship left on the board
 *  - TARGET → TARGET: on further HITs, or on SUNK when an un-sunk hit cell still remains
 *    (a touching/adjacent ship that was already grazed is re-anchored, not forgotten)
 *
 * Correctness notes (regressions guarded by MediumAiTest):
 *  - The locked direction is `sign(secondHit − origin)`, never a hard-coded value, so a second
 *    hit *behind* the origin still extends the correct way instead of stranding the ship.
 *  - Line-walking always starts from the origin and steps *through* known hits to the frontier,
 *    so reversing works even when several cells on the near side are already hit.
 *  - Target state is fully cleared whenever targeting is abandoned, so no stale origin/axis can
 *    bleed into the next ship.
 *
 * Pure Kotlin — zero Android imports.
 */
class MediumAi : AiStrategy {

    // ── Phase state ──────────────────────────────────────────────────────────
    private var phase: HuntPhase = HuntPhase.HUNT

    /** Candidate neighbour cells to try before an axis is locked (first hit only). */
    private val targetStack: ArrayDeque<Coord> = ArrayDeque()

    /** The axis locked after two collinear hits. Null until locked. */
    private var lockedAxis: Axis? = null

    /** The first hit that started the current targeting sequence — the line's anchor. */
    private var hitOrigin: Coord? = null

    /** The most recent hit coord. */
    private var lastHit: Coord? = null

    /** Current direction along the locked axis: +1 forward (down/right), -1 reverse (up/left). */
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
            FireResult.HIT  -> onHit(coord, opponentBoard)
            FireResult.MISS -> Unit // A locked-axis dead end is handled lazily in selectTarget().
            FireResult.SUNK -> onSunk(opponentBoard)
        }
    }

    override fun reset() {
        phase = HuntPhase.HUNT
        clearTargetingState()
    }

    // ── Test-visible introspection ─────────────────────────────────────────────

    internal fun currentPhase(): HuntPhase = phase
    internal fun currentAxis(): Axis? = lockedAxis

    // ── HUNT ───────────────────────────────────────────────────────────────────

    private fun selectHunt(board: Board): Coord {
        // Checkerboard parity: only cells where (row + col) % 2 == 0.
        val candidates = board.unshotCoords().filter { coord ->
            (coord.rowOf() + coord.colOf()) % 2 == 0
        }
        val pool = candidates.ifEmpty { board.unshotCoords() }
        require(pool.isNotEmpty()) { "MediumAi.selectHunt: no unshot cells remaining." }
        return pool[random.nextInt(pool.size)]
    }

    // ── TARGET ───────────────────────────────────────────────────────────────

    private fun selectTarget(board: Board): Coord {
        val axis = lockedAxis
        val origin = hitOrigin

        if (axis != null && origin != null) {
            // Walk the line from the origin in the current direction; then reverse the same line.
            frontierCell(origin, axis, axisDirection, board)?.let { return it }
            axisDirection = -axisDirection
            frontierCell(origin, axis, axisDirection, board)?.let { return it }
            // Both ends of the hit-line are bounded but the ship was never reported sunk
            // (e.g. two ships touching end-to-end). Abandon cleanly and hunt.
            return exhaustToHunt(board)
        }

        // Pre-lock: try the queued cardinal neighbours of the first hit.
        while (targetStack.isNotEmpty()) {
            val candidate = targetStack.removeFirst()
            if (board.isUnshot(candidate)) return candidate
        }
        return exhaustToHunt(board)
    }

    /**
     * Starting at [from], step along [axis] in [dir] through any contiguous [CellState.Hit] cells
     * and return the first open water cell reached. Returns null if the line runs into a Miss, a
     * Sunk cell, or the board edge before finding open water.
     */
    private fun frontierCell(from: Coord, axis: Axis, dir: Int, board: Board): Coord? {
        var step = 1
        while (true) {
            val next = when (axis) {
                Axis.HORIZONTAL -> Coord.fromRowCol(from.rowOf(), from.colOf() + dir * step)
                Axis.VERTICAL   -> Coord.fromRowCol(from.rowOf() + dir * step, from.colOf())
            }
            if (!next.isValid()) return null
            when {
                board.cellAt(next) == CellState.Hit -> step++      // walk through the known hit line
                board.isUnshot(next)                -> return next  // frontier — open water
                else                                -> return null  // Miss / Sunk — dead end
            }
        }
    }

    private fun exhaustToHunt(board: Board): Coord {
        clearTargetingState()
        phase = HuntPhase.HUNT
        return selectHunt(board)
    }

    // ── Result handling ────────────────────────────────────────────────────────

    private fun onHit(coord: Coord, board: Board) {
        phase = HuntPhase.TARGET

        val origin = hitOrigin
        if (origin == null) {
            // First hit of a new sequence — anchor here and seed neighbour probes.
            hitOrigin = coord
            lastHit = coord
            seedNeighbourProbes(coord, board)
            return
        }

        lastHit = coord
        if (lockedAxis == null) {
            val axis = axisBetween(origin, coord)
            if (axis != null) {
                lockedAxis = axis
                axisDirection = directionBetween(origin, coord, axis)
                targetStack.clear() // off-axis neighbour probes are now irrelevant
            }
            // If a hit is somehow not collinear-adjacent to the origin, keep draining the stack.
        }
        // Once locked, selectTarget() computes the next cell directly from the board — no push here.
    }

    private fun onSunk(board: Board) {
        clearTargetingState()

        // A sunk ship's cells become Sunk; any cell still marked Hit belongs to a *different*
        // ship that was already grazed (possible when ships touch). Re-anchor onto it rather
        // than dropping back to blind hunting.
        val leftover = firstRemainingHit(board)
        if (leftover != null) {
            phase = HuntPhase.TARGET
            hitOrigin = leftover
            lastHit = leftover
            seedNeighbourProbes(leftover, board)
        } else {
            phase = HuntPhase.HUNT
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun seedNeighbourProbes(coord: Coord, board: Board) {
        coord.adjacentCoords()
            .filter { board.isUnshot(it) }
            .forEach { targetStack.addLast(it) }
    }

    private fun firstRemainingHit(board: Board): Coord? {
        val total = GameConstants.BOARD_SIZE * GameConstants.BOARD_SIZE
        var i = 0
        while (i < total) {
            val coord = Coord(i)
            if (board.cellAt(coord) == CellState.Hit) return coord
            i++
        }
        return null
    }

    /** The shared axis of two cells, or null if they are not on a common row/column. */
    private fun axisBetween(a: Coord, b: Coord): Axis? = when {
        a.rowOf() == b.rowOf() && a.colOf() != b.colOf() -> Axis.HORIZONTAL
        a.colOf() == b.colOf() && a.rowOf() != b.rowOf() -> Axis.VERTICAL
        else -> null
    }

    /** +1 if [second] is below/right of [origin], -1 if above/left. */
    private fun directionBetween(origin: Coord, second: Coord, axis: Axis): Int = when (axis) {
        Axis.HORIZONTAL -> if (second.colOf() > origin.colOf()) 1 else -1
        Axis.VERTICAL   -> if (second.rowOf() > origin.rowOf()) 1 else -1
    }

    private fun clearTargetingState() {
        targetStack.clear()
        lockedAxis = null
        hitOrigin = null
        lastHit = null
        axisDirection = 1
    }
}
