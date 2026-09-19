package com.ok1cdj.kvexed.core

import com.ok1cdj.kvexed.core.Board.Companion.EMPTY
import com.ok1cdj.kvexed.core.Board.Companion.H
import com.ok1cdj.kvexed.core.Board.Companion.W
import com.ok1cdj.kvexed.core.Board.Companion.WALL

/** Direction of a move: a block slides one cell left or right into empty space. */
enum class Direction(val dx: Int) { LEFT(-1), RIGHT(1) }

/** Outcome of [Engine.move]. */
sealed interface MoveResult {
    /** The move was legal; [board] is the fully resolved (settled + cleared) result. */
    data class Moved(val board: Board) : MoveResult

    /** The move was not legal (no block at source, or target off-board / occupied). */
    data object Illegal : MoveResult
}

/** High-level state of a board. */
enum class GameState { PLAYING, WON, LOST }

/**
 * The Vexed rules engine.
 *
 * This is a deliberate, line-for-line mirror of the reference engine in
 * tools/pdb2vxl.py (`settle` / `clear` / `resolve` / `verify`). That Python
 * engine is the oracle: it verified all shipped solutions. When any rules
 * detail is ambiguous, the Python is authoritative and this file must match it —
 * NOT the prose in the spec.
 */
object Engine {

    /**
     * The one rules decision worth stating out loud: **matches are evaluated
     * only after the board is FULLY settled, never mid-fall.** After a move we
     * settle everything, then remove all matching groups simultaneously, then
     * settle again, and repeat until the board stops changing. This is not a
     * guess — it is verified against every shipped solution by the oracle. If
     * this ever needs to change, this is the single switch.
     */
    const val MATCH_AFTER_FULL_SETTLE = true

    /** The minimum group size that clears. */
    private const val MATCH_GROUP_MIN = 2

    /**
     * Attempt to slide the block at ([x],[y]) one cell in [dir], then resolve
     * gravity and matches. Returns [MoveResult.Illegal] (never throws) when the
     * move is not allowed.
     *
     * Mirrors the per-move logic in `verify`.
     */
    fun move(board: Board, x: Int, y: Int, dir: Direction): MoveResult {
        // 1. Validation: a block must sit at the source; the target cell must be
        //    on-board and empty.
        if (!board.hasBlock(x, y)) return MoveResult.Illegal
        val nx = x + dir.dx
        if (nx !in 0 until W) return MoveResult.Illegal
        if (board[nx, y] != EMPTY) return MoveResult.Illegal

        // 2. Shift one cell.
        val cells = board.toCharArray()
        cells[y * W + nx] = cells[y * W + x]
        cells[y * W + x] = EMPTY

        // 3-5. Settle, clear, repeat until stable.
        resolve(cells)
        return MoveResult.Moved(Board(cells))
    }

    /** Classify a board. See [GameState]. */
    fun state(board: Board): GameState {
        val counts = IntArray(8) // 'a'..'h'
        var total = 0
        for (c in board.cells) {
            if (c != WALL && c != EMPTY) {
                counts[c - 'a']++
                total++
            }
        }
        if (total == 0) return GameState.WON
        // Weak dead-state detector: a block type with exactly one instance can
        // never be matched away. (The stronger reachability detector is
        // deliberately not implemented — it produces false positives.)
        for (n in counts) if (n == 1) return GameState.LOST
        return GameState.PLAYING
    }

    /** True if the board has no movable blocks left. */
    fun isWon(board: Board): Boolean = board.blockCount() == 0

    // --- internal mechanics, operating on a mutable row-major CharArray -------

    /**
     * Settle, then clear, repeating until the board stops changing. Matches are
     * evaluated only after a full settle. Mirrors `resolve`.
     */
    internal fun resolve(g: CharArray) {
        while (true) {
            settle(g)
            if (!clear(g)) return
        }
    }

    /** Gravity: every block falls while the cell below it is empty. Mirrors `settle`. */
    internal fun settle(g: CharArray) {
        var moved = true
        while (moved) {
            moved = false
            for (y in H - 2 downTo 0) {
                for (x in 0 until W) {
                    val here = g[y * W + x]
                    if (here != WALL && here != EMPTY && g[(y + 1) * W + x] == EMPTY) {
                        g[(y + 1) * W + x] = here
                        g[y * W + x] = EMPTY
                        moved = true
                    }
                }
            }
        }
    }

    /**
     * Remove every group of >= [MATCH_GROUP_MIN] orthogonally connected
     * same-type blocks, all groups simultaneously. Returns true if anything was
     * removed. Diagonals do not connect. Mirrors `clear`.
     */
    internal fun clear(g: CharArray): Boolean {
        val seen = BooleanArray(W * H)
        val doomed = BooleanArray(W * H)
        val stackX = IntArray(W * H)
        val stackY = IntArray(W * H)
        val groupIdx = IntArray(W * H) // reused across groups — no per-group allocation
        var removedAny = false

        for (y0 in 0 until H) {
            for (x0 in 0 until W) {
                val idx0 = y0 * W + x0
                val t = g[idx0]
                if (t == WALL || t == EMPTY || seen[idx0]) continue

                var sp = 0
                stackX[sp] = x0; stackY[sp] = y0; sp++
                seen[idx0] = true
                var groupSize = 0

                while (sp > 0) {
                    sp--
                    val cx = stackX[sp]; val cy = stackY[sp]
                    groupIdx[groupSize++] = cy * W + cx
                    // orthogonal neighbours
                    sp = neighbour(cx + 1, cy, t, g, seen, stackX, stackY, sp)
                    sp = neighbour(cx - 1, cy, t, g, seen, stackX, stackY, sp)
                    sp = neighbour(cx, cy + 1, t, g, seen, stackX, stackY, sp)
                    sp = neighbour(cx, cy - 1, t, g, seen, stackX, stackY, sp)
                }

                if (groupSize >= MATCH_GROUP_MIN) {
                    for (k in 0 until groupSize) doomed[groupIdx[k]] = true
                    removedAny = true
                }
            }
        }

        if (removedAny) {
            for (i in doomed.indices) if (doomed[i]) g[i] = EMPTY
        }
        return removedAny
    }

    /** Push a matching, unseen orthogonal neighbour onto the flood-fill stack. Returns the new stack pointer. */
    private fun neighbour(
        nx: Int, ny: Int, t: Char, g: CharArray, seen: BooleanArray,
        stackX: IntArray, stackY: IntArray, sp: Int,
    ): Int {
        if (nx !in 0 until W || ny !in 0 until H) return sp
        val nIdx = ny * W + nx
        if (seen[nIdx] || g[nIdx] != t) return sp
        seen[nIdx] = true
        stackX[sp] = nx; stackY[sp] = ny
        return sp + 1
    }

    // --- helpers used by tests and the settled-board asserts ------------------

    /** A copy of [board] after gravity only (no matching). */
    fun settled(board: Board): Board {
        val g = board.toCharArray()
        settle(g)
        return Board(g)
    }

    /** True if [board] contains at least one matchable group (>= 2 connected same type). */
    fun hasMatch(board: Board): Boolean = clear(board.toCharArray())
}
