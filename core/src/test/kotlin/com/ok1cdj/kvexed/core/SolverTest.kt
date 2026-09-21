package com.ok1cdj.kvexed.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * The runtime [Solver] is the counterpart of the oracle in
 * [SolutionVerificationTest]: rather than replay a stored solution, it must find
 * one from scratch. Mirrors the `--selftest` in tools/solve.py.
 */
class SolverTest {

    private val packs = LevelParser.loadAllPacks()

    /**
     * Port-correctness, mirroring `solve.py --selftest`: the easy early levels of
     * the first pack must be solved, and — being breadth-first — in no more than
     * the shipped par. A generous budget (matching solve.py's selftest) is used
     * here so this proves the *search* is right, independent of the tighter
     * runtime budget the app ships with.
     */
    @TestFactory
    fun solvesEasyLevelsWithinPar(): List<DynamicTest> =
        packs.first().levels.take(SELFTEST_LEVELS).mapIndexed { n, level ->
            DynamicTest.dynamicTest("${packs.first().id} $n/${level.title}") {
                val res = Solver.solve(level.toBoard(), maxDepth = level.par + 6, maxStates = 2_000_000)
                assertTrue(res is Solver.Result.Solved) { "$n/${level.title}: $res" }
                val moves = (res as Solver.Result.Solved).moves.size
                assertTrue(moves <= level.par) { "$n/${level.title}: $moves moves > par ${level.par}" }
            }
        }

    /**
     * Soundness at the shipped runtime budget: across a sample of every pack, the
     * solver must NEVER return a solution longer than par (BFS optimality) and any
     * [Solver.Result.Solved] must actually clear the board. Returning
     * [Solver.Result.NotFound] on a hard level is acceptable — hints are
     * best-effort — so it is not counted as a failure here.
     */
    @TestFactory
    fun neverReturnsAWrongAnswer(): List<DynamicTest> = packs.map { pack ->
        DynamicTest.dynamicTest(pack.title) {
            val failures = pack.levels.take(SAMPLE_PER_PACK).mapIndexedNotNull { n, level ->
                val res = Solver.solve(level.toBoard())
                if (res !is Solver.Result.Solved) return@mapIndexedNotNull null
                when {
                    res.moves.size > level.par -> "$n/${level.title}: ${res.moves.size} moves > par ${level.par}"
                    !replaySolves(level, res.moves) -> "$n/${level.title}: moves do not clear the board"
                    else -> null
                }
            }
            assertTrue(failures.isEmpty()) {
                "${failures.size} wrong answers in ${pack.id}:\n" + failures.joinToString("\n")
            }
        }
    }

    private fun replaySolves(level: Level, moves: List<Move>): Boolean {
        var board = level.toBoard()
        for (m in moves) {
            board = (Engine.move(board, m.x, m.y, m.dir) as? MoveResult.Moved)?.board ?: return false
        }
        return Engine.isWon(board)
    }

    /** A shortest solution actually solves the board when played back through [Engine]. */
    @Test
    fun foundSolutionActuallySolves() {
        val level = packs.first().levels.first()
        val res = Solver.solve(level.toBoard())
        assertTrue(res is Solver.Result.Solved)
        var board = level.toBoard()
        for (m in (res as Solver.Result.Solved).moves) {
            board = (Engine.move(board, m.x, m.y, m.dir) as MoveResult.Moved).board
        }
        assertTrue(Engine.isWon(board), "replayed solver moves must clear the board")
    }

    /** An already-cleared board solves in zero moves, and firstMove is null. */
    @Test
    fun emptyBoardIsSolvedInZeroMoves() {
        val empty = Board.parse((0 until Board.H).joinToString("/") { "~".repeat(Board.W) })
        val res = Solver.solve(empty)
        assertEquals(Solver.Result.Solved(emptyList()), res)
        assertNull(res.firstMove)
    }

    /** A lone block of a type can never be matched away: the frontier exhausts as unsolvable. */
    @Test
    fun loneBlockIsUnsolvable() {
        // One 'a' on the floor, seven empty rows above — nothing to pair it with.
        val blank = "~".repeat(Board.W)
        val floor = "a" + "~".repeat(Board.W - 1)
        val board = Board.parse((List(Board.H - 1) { blank } + floor).joinToString("/"))
        assertEquals(Solver.Result.Unsolvable, Solver.solve(board))
    }

    /** A tight budget on a non-trivial board reports NotFound, never a wrong answer. */
    @Test
    fun tinyBudgetReportsNotFound() {
        val level = packs.first().levels.last()
        val res = Solver.solve(level.toBoard(), maxStates = 1)
        assertTrue(res is Solver.Result.NotFound || res is Solver.Result.Solved)
        // With a 1-state budget a multi-move level cannot be fully solved.
        if (res is Solver.Result.Solved) assertTrue(res.moves.size <= 1)
    }

    private companion object {
        const val SELFTEST_LEVELS = 5
        const val SAMPLE_PER_PACK = 6
    }
}
