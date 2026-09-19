package com.ok1cdj.kvexed.core

/** A single move: slide the block at ([x],[y]) in [dir]. */
data class Move(val x: Int, val y: Int, val dir: Direction)

/**
 * The VXL solution encoding: a string of letter pairs, one pair per move.
 *
 * In each pair exactly one letter is uppercase:
 *  - first letter is the column X (`a`/`A` = 0 … `j`/`J` = 9)
 *  - second letter is the row Y (`a`/`A` = 0 … `h`/`H` = 7)
 *  - uppercase X (`Xy`) means move LEFT; uppercase Y (`xY`) means move RIGHT
 *
 * Coordinates count from the top-left, from 0. Mirrors `parse_solution` in
 * tools/pdb2vxl.py.
 */
object Solution {
    fun parse(sol: String): List<Move> {
        require(sol.length % 2 == 0) { "odd-length solution '$sol'" }
        val moves = ArrayList<Move>(sol.length / 2)
        var i = 0
        while (i < sol.length) {
            val a = sol[i]
            val b = sol[i + 1]
            require(a.isUpperCase() != b.isUpperCase()) {
                "pair '$a$b' must have exactly one uppercase letter"
            }
            val x = a.lowercaseChar() - 'a'
            val y = b.lowercaseChar() - 'a'
            require(x in 0 until Board.W && y in 0 until Board.H) { "pair '$a$b' out of range" }
            moves.add(Move(x, y, if (a.isUpperCase()) Direction.LEFT else Direction.RIGHT))
            i += 2
        }
        return moves
    }
}
