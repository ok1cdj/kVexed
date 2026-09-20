package com.ok1cdj.kvexed.core

/**
 * Tracks whether the player is still walking the stored solution path.
 *
 * A `pathIndex` of `n` means the first `n` moves played match `solution[0..n)`,
 * so the next hintable move is `solution[n]`. `null` means the player has
 * deviated — no move from the stored solution relates to the current board, so
 * hints must be withheld. Once `null`, it stays `null` until undo or restart.
 *
 * The engine is deterministic, so a matching prefix of moves guarantees a
 * matching board — we compare moves as `(x, y, dir)` triples, never boards.
 */
object PathTracker {
    /**
     * The path index after [played] is applied, given the previous [prevIndex].
     * Returns `prevIndex + 1` if still on-path, else `null` (deviated / already off).
     */
    fun next(prevIndex: Int?, solution: List<Move>, played: Move): Int? =
        if (prevIndex != null && solution.getOrNull(prevIndex) == played) prevIndex + 1 else null
}
