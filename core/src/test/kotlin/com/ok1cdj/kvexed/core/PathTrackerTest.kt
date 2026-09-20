package com.ok1cdj.kvexed.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The hint-availability fix lives in [PathTracker]; the ViewModel is thin wiring
 * over it. These tests mirror that wiring (an undo stack of path indices, restart
 * resetting to 0) so the behaviour is verified without Android test infra.
 *
 * Invariant we protect: a hint is offered only while on the stored path, and the
 * offered move (`solution[pathIndex]`) is always legal on the current board — the
 * original bug returned `solution[moveCount]`, a move unrelated to the board once
 * the player deviated.
 */
class PathTrackerTest {

    private val levels = LevelParser.loadAllPacks().flatMap { it.levels }
    // A level with enough moves and at least one alternate first move to deviate with.
    private val level = levels.first { it.solutionMoves().size >= 3 && alternateFirstMove(it) != null }
    private val solution = level.solutionMoves()

    /** Whether a hint would be offered at [index], matching GameViewModel.hintAvailable. */
    private fun available(index: Int?) = index?.let { it < solution.size } ?: false

    /** The move the hint would reveal, matching GameViewModel.showHint(). */
    private fun hintMove(index: Int?) = index?.let { solution.getOrNull(it) }

    @Test
    fun playingThePrefixGrowsTheIndexAndKeepsHintsAvailable() {
        var board = level.toBoard()
        var index: Int? = 0
        for ((k, m) in solution.withIndex()) {
            assertTrue(available(index)) { "hint should be available at step $k" }
            // The offered hint must be a legal move on the actual current board.
            val hint = hintMove(index)!!
            assertTrue(Engine.move(board, hint.x, hint.y, hint.dir) is MoveResult.Moved) {
                "hint at step $k points to an illegal move"
            }
            board = (Engine.move(board, m.x, m.y, m.dir) as MoveResult.Moved).board
            index = PathTracker.next(index, solution, m)
            assertEquals(k + 1, index) { "index should track the prefix" }
        }
    }

    @Test
    fun deviatingClearsTheIndexAndWithholdsHints() {
        val alt = alternateFirstMove(level)!!
        val index = PathTracker.next(0, solution, alt)
        assertNull(index) { "an off-path move must drop the index to null" }
        assertFalse(available(index))
        assertNull(hintMove(index)) { "off-path must yield no hint move (the original bug)" }
    }

    @Test
    fun onceNullTheIndexStaysNull() {
        // Even if a later move happens to equal a solution move, a deviated player
        // stays off-path until undo/restart.
        var index: Int? = null
        for (m in solution) index = PathTracker.next(index, solution, m)
        assertNull(index)
    }

    @Test
    fun undoRestoresThePreviousIndex() {
        // Mirror the VM: push the pre-move index, then pop on undo.
        val stack = ArrayDeque<Int?>()
        var index: Int? = 0
        for (m in solution.take(2)) {
            stack.addLast(index)
            index = PathTracker.next(index, solution, m)
        }
        assertEquals(2, index)
        index = stack.removeLast(); assertEquals(1, index)
        index = stack.removeLast(); assertEquals(0, index)
    }

    @Test
    fun undoAfterDeviationCanReturnToAHintablePosition() {
        val stack = ArrayDeque<Int?>()
        var index: Int? = 0
        // Deviate off the path with a legal-but-wrong move.
        stack.addLast(index); index = PathTracker.next(index, solution, alternateFirstMove(level)!!)
        assertNull(index) { "deviated" }
        assertFalse(available(index))
        // Undo the deviation → back on the path, hint available again.
        index = stack.removeLast()
        assertEquals(0, index)
        assertTrue(available(index))
    }

    @Test
    fun restartResetsToZero() {
        var index: Int? = PathTracker.next(0, solution, solution[0])
        assertEquals(1, index)
        index = 0 // restart()
        assertTrue(available(index))
        assertNotNull(hintMove(index))
    }

    @Test
    fun completingTheSolutionLeavesNoHint() {
        var index: Int? = 0
        for (m in solution) index = PathTracker.next(index, solution, m)
        assertEquals(solution.size, index)
        assertFalse(available(index)) { "nothing left to hint once solved" }
        assertNull(hintMove(index))
    }

    private companion object {
        /** A legal first move that is NOT the solution's first move, or null if none. */
        fun alternateFirstMove(level: Level): Move? {
            val board = level.toBoard()
            val first = level.solutionMoves().firstOrNull() ?: return null
            for (y in 0 until Board.H) for (x in 0 until Board.W) {
                if (!board.hasBlock(x, y)) continue
                for (dir in Direction.values()) {
                    val m = Move(x, y, dir)
                    if (m == first) continue
                    if (Engine.move(board, x, y, dir) is MoveResult.Moved) return m
                }
            }
            return null
        }
    }
}
