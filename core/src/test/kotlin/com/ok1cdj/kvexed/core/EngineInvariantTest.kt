package com.ok1cdj.kvexed.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Properties that must hold for the engine across the whole dataset, regardless
 * of the specific puzzle: undo round-trips, determinism, gravity idempotence,
 * and block-count monotonicity.
 */
class EngineInvariantTest {

    private val levels = LevelParser.loadAllPacks().flatMap { it.levels }

    @Test
    fun undoRestoresTheStartingBoard() {
        val failures = mutableListOf<String>()
        for (level in levels) {
            val start = level.toBoard()
            val stack = ArrayDeque<Board>()
            var board = start
            for (m in level.solutionMoves()) {
                val r = Engine.move(board, m.x, m.y, m.dir)
                if (r is MoveResult.Moved) { stack.addLast(board); board = r.board } else break
            }
            // Undo everything.
            while (stack.isNotEmpty()) board = stack.removeLast()
            if (board != start) failures += level.title
        }
        assertTrue(failures.isEmpty()) { "undo failed to restore start for: ${failures.take(20)}" }
    }

    @Test
    fun replayIsDeterministic() {
        val failures = mutableListOf<String>()
        for (level in levels) {
            val a = playAll(level)
            val b = playAll(level)
            if (a != b) failures += level.title
        }
        assertTrue(failures.isEmpty()) { "non-deterministic replay for: ${failures.take(20)}" }
    }

    @Test
    fun gravityIsIdempotent() {
        val failures = mutableListOf<String>()
        for (level in levels) {
            val once = Engine.settled(level.toBoard())
            val twice = Engine.settled(once)
            if (once != twice) failures += level.title
        }
        assertTrue(failures.isEmpty()) { "settle not idempotent for: ${failures.take(20)}" }
    }

    @Test
    fun blockCountNeverIncreases() {
        val failures = mutableListOf<String>()
        for (level in levels) {
            var board = level.toBoard()
            var prev = board.blockCount()
            for ((k, m) in level.solutionMoves().withIndex()) {
                val r = Engine.move(board, m.x, m.y, m.dir)
                if (r !is MoveResult.Moved) break
                board = r.board
                val now = board.blockCount()
                if (now > prev) { failures += "${level.title}@$k"; break }
                prev = now
            }
        }
        assertTrue(failures.isEmpty()) { "block count increased for: ${failures.take(20)}" }
    }

    private fun playAll(level: Level): Board {
        var board = level.toBoard()
        for (m in level.solutionMoves()) {
            val r = Engine.move(board, m.x, m.y, m.dir)
            if (r is MoveResult.Moved) board = r.board else break
        }
        return board
    }
}
