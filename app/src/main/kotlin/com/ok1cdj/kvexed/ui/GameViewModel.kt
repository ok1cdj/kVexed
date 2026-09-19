package com.ok1cdj.kvexed.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ok1cdj.kvexed.core.Board
import com.ok1cdj.kvexed.core.Direction
import com.ok1cdj.kvexed.core.Engine
import com.ok1cdj.kvexed.core.GameState
import com.ok1cdj.kvexed.core.Level
import com.ok1cdj.kvexed.core.LevelPack
import com.ok1cdj.kvexed.core.LevelParser
import com.ok1cdj.kvexed.core.Move
import com.ok1cdj.kvexed.core.MoveResult
import com.ok1cdj.kvexed.core.PackInfo
import com.ok1cdj.kvexed.data.LevelStat
import com.ok1cdj.kvexed.data.PackProgress
import com.ok1cdj.kvexed.data.Progress
import com.ok1cdj.kvexed.data.ProgressStore
import com.ok1cdj.kvexed.data.Settings
import kotlinx.coroutines.launch

/** Which screen is showing. */
sealed interface Screen {
    data object PackList : Screen
    data class LevelList(val packId: String) : Screen
    data class Game(val packId: String, val levelIndex: Int) : Screen
}

/**
 * Single source of truth for navigation, the active game, and progress. Progress
 * is held in memory and flushed to [ProgressStore] on pause; the game itself is a
 * thin driver over the pure [Engine].
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProgressStore(app)

    /** Pack metadata for the list screen (loaded once). */
    val packs: List<PackInfo> = LevelParser.loadIndex()

    var progress by mutableStateOf(Progress())
        private set

    var settings by mutableStateOf(Settings())
        private set

    /** Monotonic counter bumped on each completed forward move — drives haptics. */
    var moveTick by mutableStateOf(0)
        private set

    var screen: Screen by mutableStateOf(Screen.PackList)
        private set

    // --- active game state (valid while [screen] is a Game) -------------------
    private var pack: LevelPack? = null
    private var level: Level? = null
    private var solutionMoves: List<Move> = emptyList()
    private val undoStack = ArrayDeque<Board>()

    var board: Board? by mutableStateOf(null)
        private set
    var selected: Pair<Int, Int>? by mutableStateOf(null)
        private set
    var moveCount by mutableStateOf(0)
        private set
    var gameState by mutableStateOf(GameState.PLAYING)
        private set
    var hint: Move? by mutableStateOf(null)
        private set
    private var hintUsedThisLevel = false

    // --- solution playback (step-through viewer) ------------------------------
    var solutionActive by mutableStateOf(false)
        private set
    var solutionStep by mutableStateOf(0)
        private set
    private var solutionMoveList: List<Move> = emptyList()
    private var solutionBoards: List<Board> = emptyList()

    val levelTitle: String get() = level?.title ?: ""
    val par: Int get() = level?.par ?: 0
    /** Best-known par (< par) when a shorter solution exists, else null. */
    val bestPar: Int? get() = level?.bestPar
    val levelIndex: Int get() = (screen as? Screen.Game)?.levelIndex ?: 0
    val packTitle: String get() = pack?.title ?: ""
    val levelCount: Int get() = pack?.levels?.size ?: 0
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val solutionBoard: Board? get() = solutionBoards.getOrNull(solutionStep)
    val solutionLength: Int get() = solutionMoveList.size
    /** The move about to be played at the current step (to highlight), or null at the end. */
    val solutionNextMove: Move? get() = solutionMoveList.getOrNull(solutionStep)
    /** True when the viewer is showing the shorter best-known solution (vs the shipped one). */
    val solutionIsBest: Boolean get() = level?.bestKnown != null

    /** Whether the Solve/spoiler button is offered (off by default). */
    val showSolve: Boolean get() = !settings.hideSolve
    /** Whether haptic feedback fires on completed moves. */
    val haptics: Boolean get() = settings.haptics

    init {
        viewModelScope.launch {
            progress = store.load()
            settings = store.loadSettings()
        }
    }

    fun setHideSolve(hide: Boolean) {
        settings = settings.copy(hideSolve = hide)
        val s = settings
        viewModelScope.launch { store.saveSettings(s) }
    }

    fun setHaptics(on: Boolean) {
        settings = settings.copy(haptics = on)
        val s = settings
        viewModelScope.launch { store.saveSettings(s) }
    }

    // --- navigation -----------------------------------------------------------

    fun openPack(packId: String) {
        loadPack(packId)
        screen = Screen.LevelList(packId)
    }

    fun backToPacks() {
        flushResume()
        screen = Screen.PackList
    }

    fun backToLevels() {
        flushResume()
        (screen as? Screen.Game)?.let { screen = Screen.LevelList(it.packId) }
            ?: pack?.let { screen = Screen.LevelList(it.id) }
    }

    /** Open a specific level, resuming its saved in-progress board if present. */
    fun openLevel(packId: String, index: Int) {
        val p = loadPack(packId)
        val lv = p.levels[index]
        level = lv
        solutionMoves = runCatching { lv.solutionMoves() }.getOrDefault(emptyList())
        undoStack.clear()
        hint = null
        selected = null
        hintUsedThisLevel = progress.packs[packId]?.levels?.get(index)?.hintUsed ?: false

        val saved = progress.packs[packId]
        val savedBoard = saved?.resumeBoard
        if (saved?.resumeLevel == index && savedBoard != null) {
            board = runCatching { Board.parse(savedBoard) }.getOrDefault(lv.toBoard())
            moveCount = saved.resumeMoves
        } else {
            board = lv.toBoard()
            moveCount = 0
        }
        gameState = Engine.state(board!!)
        screen = Screen.Game(packId, index)
        setLast(packId, index)
    }

    /** "Continue": jump to the last-played pack/level. */
    fun continueGame() {
        val packId = progress.lastPackId ?: return
        openLevel(packId, progress.lastLevel)
    }

    fun nextLevel() {
        val p = pack ?: return
        val next = levelIndex + 1
        if (next < p.levels.size) openLevel(p.id, next) else backToLevels()
    }

    // --- gameplay -------------------------------------------------------------

    /** Handle a tap on board cell ([x],[y]). */
    fun onCellTap(x: Int, y: Int) {
        val b = board ?: return
        if (gameState == GameState.WON) return
        hint = null

        val sel = selected
        if (sel == null) {
            if (b.hasBlock(x, y)) selected = x to y
            return
        }
        val (sx, sy) = sel
        // Tapping the selected block, or another block, re-selects / deselects.
        if (x == sx && y == sy) { selected = null; return }
        if (b.hasBlock(x, y)) { selected = x to y; return }

        // Tapping an empty cell adjacent to the selected block performs the move.
        if (y == sy && (x == sx - 1 || x == sx + 1)) {
            val dir = if (x < sx) Direction.LEFT else Direction.RIGHT
            applyMove(sx, sy, dir)
        }
        selected = null
    }

    private fun applyMove(x: Int, y: Int, dir: Direction) {
        val b = board ?: return
        when (val r = Engine.move(b, x, y, dir)) {
            is MoveResult.Illegal -> {}
            is MoveResult.Moved -> {
                undoStack.addLast(b)
                board = r.board
                moveCount++
                moveTick++ // signal the UI to fire haptic feedback for this move
                gameState = Engine.state(r.board)
                if (gameState == GameState.WON) onSolved()
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        board = undoStack.removeLast()
        moveCount--
        selected = null
        hint = null
        gameState = Engine.state(board!!)
    }

    fun restart() {
        val lv = level ?: return
        board = lv.toBoard()
        moveCount = 0
        undoStack.clear()
        selected = null
        hint = null
        gameState = Engine.state(board!!)
    }

    /** Reveal only the next move of the stored solution. Marks the level as hinted. */
    fun showHint() {
        hint = solutionMoves.getOrNull(moveCount)
        selected = null
        if (hint != null) {
            hintUsedThisLevel = true
        }
    }

    // --- solution playback ----------------------------------------------------

    /** Enter the step-through viewer, showing the best-known solution if we have
     *  one (shorter than par), otherwise the shipped solution. Precomputes each
     *  board state so stepping is instant and never mutates the live game. */
    fun startSolution() {
        val lv = level ?: return
        val moves = lv.bestKnownMoves() ?: runCatching { lv.solutionMoves() }.getOrDefault(emptyList())
        val states = ArrayList<Board>(moves.size + 1)
        var b = lv.toBoard()
        states.add(b)
        for (m in moves) {
            val r = Engine.move(b, m.x, m.y, m.dir)
            if (r is MoveResult.Moved) { b = r.board; states.add(b) } else break
        }
        solutionMoveList = moves
        solutionBoards = states
        solutionStep = 0
        solutionActive = true
    }

    fun solutionNext() { if (solutionStep < solutionBoards.size - 1) solutionStep++ }
    fun solutionPrev() { if (solutionStep > 0) solutionStep-- }
    fun exitSolution() { solutionActive = false }

    private fun onSolved() {
        val packId = (screen as? Screen.Game)?.packId ?: pack?.id ?: return
        val idx = levelIndex
        val packProg = progress.packs[packId] ?: PackProgress()
        val prev = packProg.levels[idx]
        val best = if (prev?.solved == true) minOf(prev.bestMoves, moveCount) else moveCount
        val stat = LevelStat(solved = true, bestMoves = best, hintUsed = hintUsedThisLevel || (prev?.hintUsed ?: false))
        val newLevels = packProg.levels + (idx to stat)
        // Clear the resume slot for a solved level.
        val newPack = packProg.copy(levels = newLevels, resumeLevel = null, resumeBoard = null, resumeMoves = 0)
        progress = progress.copy(packs = progress.packs + (packId to newPack))
        persist()
    }

    // --- progress plumbing ----------------------------------------------------

    fun packProgress(packId: String): PackProgress = progress.packs[packId] ?: PackProgress()

    private fun setLast(packId: String, index: Int) {
        progress = progress.copy(lastPackId = packId, lastLevel = index)
    }

    /** Store the current unsolved board so the level resumes after a kill. */
    private fun flushResume() {
        val g = screen as? Screen.Game ?: return
        val b = board ?: return
        if (gameState == GameState.WON) return
        val packProg = progress.packs[g.packId] ?: PackProgress()
        val hintStat = if (hintUsedThisLevel) {
            val prev = packProg.levels[g.levelIndex]
            packProg.levels + (g.levelIndex to (prev ?: LevelStat()).copy(hintUsed = true))
        } else packProg.levels
        val newPack = packProg.copy(
            levels = hintStat,
            resumeLevel = g.levelIndex,
            resumeBoard = b.toBoardString(),
            resumeMoves = moveCount,
        )
        progress = progress.copy(packs = progress.packs + (g.packId to newPack))
    }

    /** Persist everything. Call from onPause. */
    fun persist() {
        flushResume()
        val snapshot = progress
        viewModelScope.launch { store.save(snapshot) }
    }

    private fun loadPack(packId: String): LevelPack {
        pack?.let { if (it.id == packId) return it }
        val info = packs.first { it.id == packId }
        return LevelParser.loadPack(info).also { pack = it }
    }
}
