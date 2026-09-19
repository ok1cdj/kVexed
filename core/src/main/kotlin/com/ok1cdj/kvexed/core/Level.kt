package com.ok1cdj.kvexed.core

/**
 * One Vexed puzzle.
 *
 * @param title human name, e.g. "Coffee Truffle"
 * @param board the VXL board string (the starting, already-settled position)
 * @param solution the VXL solution string (letter pairs; see [Solution])
 * @param par the target move count, `solution.length / 2`
 */
data class Level(
    val title: String,
    val board: String,
    val solution: String,
    val par: Int,
) {
    /** Parse [board] into a playable [Board]. */
    fun toBoard(): Board = Board.parse(board)

    /** The list of moves that solves this level. */
    fun solutionMoves(): List<Move> = Solution.parse(solution)
}
