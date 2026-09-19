package com.ok1cdj.kvexed.core

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The oracle. Every bundled solution is replayed move-by-move against [Engine];
 * ~2799 human solutions that must each solve their board exactly.
 *
 * This is the strongest test in the project. Mirrors `verify` in
 * tools/pdb2vxl.py, including the negative checks: after every non-final move
 * the board must not be [GameState.LOST] and must not already be solved — that's
 * tens of thousands of free assertions for the win/lose detector, which has no
 * other oracle.
 *
 * One dynamic test per pack; failures collected so you see "K/N failed" with the
 * list, not just the first failure.
 */
class SolutionVerificationTest {

    private val packs = LevelParser.loadAllPacks()

    @TestFactory
    fun allPacksSolvable(): List<DynamicTest> = packs.map { pack ->
        DynamicTest.dynamicTest(pack.title) {
            val failures = pack.levels.mapIndexedNotNull { n, level ->
                verify(level)?.let { "$n/${level.title}: $it" }
            }
            assertTrue(failures.isEmpty()) {
                "${failures.size}/${pack.levels.size} failed:\n" + failures.joinToString("\n")
            }
        }
    }

    /** Returns null on success, or a human-readable reason on failure. */
    private fun verify(level: Level): String? {
        var board = level.toBoard()
        val moves = try {
            level.solutionMoves()
        } catch (e: Exception) {
            return "unparseable solution: ${e.message}"
        }

        for ((k, m) in moves.withIndex()) {
            when (val r = Engine.move(board, m.x, m.y, m.dir)) {
                is MoveResult.Illegal -> return "move $k (${m.x},${m.y},${m.dir}) is illegal"
                is MoveResult.Moved -> board = r.board
            }
            if (k < moves.size - 1) {
                if (Engine.isWon(board)) return "solved early at move $k — solution longer than needed"
                if (Engine.state(board) == GameState.LOST) return "dead state at move $k"
            }
        }

        if (!Engine.isWon(board)) return "${board.blockCount()} blocks left after the last move"
        return null
    }
}
