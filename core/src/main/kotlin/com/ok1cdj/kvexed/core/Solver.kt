package com.ok1cdj.kvexed.core

/**
 * A breadth-first Vexed solver that finds the shortest sequence of moves from an
 * arbitrary board to a win.
 *
 * This is the runtime counterpart of the reference solver in tools/solve.py, and
 * a deliberate line-for-line mirror of its search: same wave-by-wave BFS (so the
 * first solution found is the shortest), same dead-state pruning via
 * [Engine.state], same "not found within budget" vs "genuinely unsolvable"
 * distinction. Neighbours are generated through [Engine.move], so the search
 * obeys exactly the same rules the levels are verified against — there is no
 * second copy of the game semantics here.
 *
 * Its purpose is hints that keep working after the player leaves the stored
 * solution path: solve from wherever the board is now and reveal the first move.
 * The search is bounded ([maxStates], [maxDepth]) so it always terminates; on a
 * phone it must be run off the main thread by the caller.
 */
object Solver {

    /**
     * Budget for a single [solve] call. Off-path hints are best-effort: if the
     * search would exceed these bounds it returns [Result.NotFound] rather than
     * hang. 150k states keeps the two backtracking maps within a phone's heap
     * while still covering every board reachable partway through a real level.
     */
    const val DEFAULT_MAX_STATES = 150_000
    const val DEFAULT_MAX_DEPTH = 60

    /** Outcome of a [solve]. Mirrors the three states of `solve.py`'s Result. */
    sealed interface Result {
        /** A solution was found; [moves] is a shortest sequence (empty if already won). */
        data class Solved(val moves: List<Move>) : Result

        /** A budget limit was hit — NOT a proof of unsolvability. */
        data object NotFound : Result

        /** The frontier was fully exhausted within budget: genuinely unsolvable. */
        data object Unsolvable : Result

        /** The first move of a found solution, or null unless [Solved] with at least one move. */
        val firstMove: Move? get() = (this as? Solved)?.moves?.firstOrNull()
    }

    /**
     * Breadth-first search from [board] to a fully cleared board.
     *
     * Explores in waves so [maxDepth] bounds solution length and the first win
     * found is shortest. States are pruned when [Engine.state] reports the board
     * lost. Returns [Result.Solved] on success, [Result.Unsolvable] if the
     * frontier empties within budget, or [Result.NotFound] if [maxStates] or
     * [maxDepth] is hit first.
     */
    fun solve(
        board: Board,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxStates: Int = DEFAULT_MAX_STATES,
    ): Result {
        if (board.blockCount() == 0) return Result.Solved(emptyList())

        val start = String(board.cells)
        // Backtracking chain, recorded as each state is first discovered.
        val parent = HashMap<String, String>()
        val moveTo = HashMap<String, Move>()
        val visited = HashSet<String>().apply { add(start) }

        var frontier = ArrayList<String>().apply { add(start) }
        var depth = 0
        var states = 0

        while (frontier.isNotEmpty() && depth < maxDepth) {
            val next = ArrayList<String>()
            for (s in frontier) {
                val g = Board(s.toCharArray())
                for ((mv, nb) in neighbours(g)) {
                    val s2 = String(nb.cells)
                    if (!visited.add(s2)) continue
                    parent[s2] = s
                    moveTo[s2] = mv
                    states++

                    if (nb.blockCount() == 0) {
                        return Result.Solved(reconstruct(parent, moveTo, start, s2))
                    }
                    if (Engine.state(nb) == GameState.LOST) continue
                    next.add(s2)

                    if (states >= maxStates) return Result.NotFound
                }
            }
            frontier = next
            depth++
        }

        // Frontier empty within budget → unsolvable; otherwise we ran out of depth.
        return if (frontier.isEmpty()) Result.Unsolvable else Result.NotFound
    }

    /**
     * Every legal move from [board], paired with its fully resolved result, in a
     * deterministic order (row-major source cell, then left before right). Mirrors
     * `_neighbours` in solve.py; delegates the move itself to [Engine.move].
     */
    private fun neighbours(board: Board): List<Pair<Move, Board>> {
        val out = ArrayList<Pair<Move, Board>>()
        for (y in 0 until Board.H) {
            for (x in 0 until Board.W) {
                if (!board.hasBlock(x, y)) continue
                for (dir in Direction.entries) {
                    val r = Engine.move(board, x, y, dir)
                    if (r is MoveResult.Moved) out.add(Move(x, y, dir) to r.board)
                }
            }
        }
        return out
    }

    /** Walk [parent]/[moveTo] back from [goal] to [start], returning moves in play order. */
    private fun reconstruct(
        parent: Map<String, String>,
        moveTo: Map<String, Move>,
        start: String,
        goal: String,
    ): List<Move> {
        val path = ArrayList<Move>()
        var cur = goal
        while (cur != start) {
            path.add(moveTo.getValue(cur))
            cur = parent.getValue(cur)
        }
        path.reverse()
        return path
    }
}
