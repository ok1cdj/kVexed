package com.ok1cdj.kvexed.core

/**
 * An immutable Vexed board: a fixed [W]×[H] grid stored row-major in an
 * 80-cell [CharArray].
 *
 * Cell values match the VXL encoding exactly:
 *  - `'#'` — wall
 *  - `' '` — empty
 *  - `'a'`–`'h'` — a block of that type
 *
 * A snapshot is 80 bytes, so copies (for the undo stack) are cheap. Equality and
 * hashing are by content, so boards work as map keys and in assertions.
 */
class Board internal constructor(internal val cells: CharArray) {

    init {
        require(cells.size == W * H) { "board must be $W×$H = ${W * H} cells, got ${cells.size}" }
    }

    /** The cell at column [x] (0..9), row [y] (0..7). Origin is top-left. */
    operator fun get(x: Int, y: Int): Char = cells[y * W + x]

    /** True if [x],[y] holds a movable block (not a wall, not empty). */
    fun hasBlock(x: Int, y: Int): Boolean = get(x, y).let { it != WALL && it != EMPTY }

    /** Number of movable blocks left on the board. */
    fun blockCount(): Int = cells.count { it != WALL && it != EMPTY }

    /** A mutable working copy for the engine to mutate in place. */
    internal fun toCharArray(): CharArray = cells.copyOf()

    /** The VXL `board` string for this state (used to persist an in-progress game). */
    fun toBoardString(): String = buildString {
        for (y in 0 until H) {
            var x = 0
            while (x < W) {
                val c = cells[y * W + x]
                when {
                    c == WALL -> {
                        var run = 0
                        while (x < W && cells[y * W + x] == WALL) { run++; x++ }
                        append(run)
                    }
                    c == EMPTY -> { append('~'); x++ }
                    else -> { append(c); x++ }
                }
            }
            if (y < H - 1) append('/')
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Board && cells.contentEquals(other.cells))

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String = buildString {
        for (y in 0 until H) {
            append(cells, y * W, W)
            append('\n')
        }
    }

    companion object {
        const val W = 10
        const val H = 8
        const val WALL = '#'
        const val EMPTY = ' '

        /**
         * Parse a VXL `board` string into a [Board].
         *
         * Rows are separated by `/`; within a row, digits are a run of walls
         * (read GREEDILY — `10` is ten walls, not `1` then `0`), `~` is empty,
         * and `a`–`h` are blocks. Every row must be exactly [W] wide and there
         * must be exactly [H] rows.
         *
         * Mirrors `parse_board` in tools/pdb2vxl.py.
         */
        fun parse(boardStr: String): Board {
            val rows = boardStr.split('/')
            require(rows.size == H) { "expected $H rows, got ${rows.size} in $boardStr" }
            val cells = CharArray(W * H)
            for ((y, r) in rows.withIndex()) {
                var i = 0
                var x = 0
                while (i < r.length) {
                    val ch = r[i]
                    when {
                        ch.isDigit() -> {
                            var j = i
                            while (j < r.length && r[j].isDigit()) j++
                            val run = r.substring(i, j).toInt()
                            repeat(run) {
                                require(x < W) { "row '$r' overflows $W columns" }
                                cells[y * W + x] = WALL; x++
                            }
                            i = j
                        }
                        ch == '~' -> {
                            require(x < W) { "row '$r' overflows $W columns" }
                            cells[y * W + x] = EMPTY; x++; i++
                        }
                        ch in 'a'..'h' -> {
                            require(x < W) { "row '$r' overflows $W columns" }
                            cells[y * W + x] = ch; x++; i++
                        }
                        else -> throw IllegalArgumentException("illegal char '$ch' in row '$r'")
                    }
                }
                require(x == W) { "row '$r' is $x wide, expected $W" }
            }
            return Board(cells)
        }
    }
}
