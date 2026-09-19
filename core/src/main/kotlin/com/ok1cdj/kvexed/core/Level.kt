package com.ok1cdj.kvexed.core

/**
 * One Vexed puzzle.
 *
 * @param title human name, e.g. "Coffee Truffle"
 * @param board the VXL board string (the starting, already-settled position)
 * @param solution the VXL solution string (letter pairs; see [Solution])
 * @param par the target move count, `solution.length / 2`
 * @param bestKnown an optional shorter solution than [solution] (the VXL 5th
 *   field), present only where our solver found one that beats par
 */
data class Level(
    val title: String,
    val board: String,
    val solution: String,
    val par: Int,
    val bestKnown: String? = null,
) {
    /** The shorter best-known par, or null if none is known better than [par]. */
    val bestPar: Int? get() = bestKnown?.let { it.length / 2 }

    /** Parse [board] into a playable [Board]. */
    fun toBoard(): Board = Board.parse(board)

    /** The list of moves that solves this level. */
    fun solutionMoves(): List<Move> = Solution.parse(solution)

    /** The best-known move list (shorter than [solutionMoves]), or null. */
    fun bestKnownMoves(): List<Move>? = bestKnown?.let { Solution.parse(it) }
}
