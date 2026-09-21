package com.ok1cdj.kvexed.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ok1cdj.kvexed.R
import com.ok1cdj.kvexed.core.Board
import com.ok1cdj.kvexed.core.Direction
import com.ok1cdj.kvexed.core.GameState
import com.ok1cdj.kvexed.core.Move
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD

// --- tuning knobs ------------------------------------------------------------
// Cell size is derived from the board width so 10 columns always fit the screen
// exactly, at any display density. Stroke widths scale with the cell.
private const val BORDER_FRAC = 0.045f  // tile outline, fraction of cell
private const val GRID_FRAC = 0.02f     // playfield gridline, fraction of cell

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

// Walls are a light grey rather than solid black — large black masses read as
// heavy on the panel. Empty cells stay white, separated by a faint grid.
private const val WALL_COLOR = 0xFFC8C8C8.toInt()
private const val GRID_COLOR = 0xFFB0B0B0.toInt()

@Composable
fun GameScreen(vm: GameViewModel, onAbout: () -> Unit, onSettings: () -> Unit) {
    // Fire a short haptic when a move completes (respects the Haptics setting;
    // performHapticFeedback needs no VIBRATE permission).
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(vm.moveTick) {
        if (vm.moveTick > 0 && vm.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (vm.solutionActive) { SolutionView(vm); return }
    val board = vm.board ?: return
    var confirmSolution by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header: pack · level x/n · info
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = vm::backToLevels)
                Spacer(Modifier.width(4.dp))
                TextMMD(
                    text = "${vm.packTitle} · ${vm.levelIndex + 1}/${vm.levelCount}",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsButton(onClick = onSettings)
                InfoButton(onClick = onAbout)
            }
        }

        // Center the board vertically between the header and the controls.
        Spacer(Modifier.weight(1f))

        // Board — one Canvas, one repaint per move.
        BoardCanvas(
            board = board,
            selected = vm.selected,
            hint = vm.hint,
            onCellTap = vm::onCellTap,
        )

        Spacer(Modifier.weight(1f))

        // Status / end-state message — full width on its own line, so it always
        // fits regardless of move count. Shows the tighter "best-known" target
        // next to par when we have a shorter solution for this level.
        val bestSuffix = vm.bestPar?.let { stringResource(R.string.game_best_suffix, it) } ?: ""
        val message = when (vm.gameState) {
            GameState.WON -> stringResource(R.string.game_solved, vm.moveCount, vm.par) + bestSuffix
            GameState.LOST -> stringResource(R.string.game_stuck)
            GameState.PLAYING -> when {
                vm.hintSolving -> stringResource(R.string.game_solving)
                vm.hintUnsolved -> stringResource(R.string.game_no_hint)
                else -> stringResource(R.string.game_moves, vm.moveCount, vm.par) + bestSuffix
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextMMD(
                text = message,
                fontSize = 15.sp,
                fontWeight = if (vm.gameState == GameState.WON) FontWeight.Bold else FontWeight.Normal,
            )
        }

        // Controls — a single fixed-height row whose contents depend on state.
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Solve is optional (hidden by default; enabled in Settings). Font
            // shrinks a touch when it's present to keep four buttons on one line.
            val solve = vm.showSolve
            val fs = if (solve) 13.sp else 15.sp
            when (vm.gameState) {
                GameState.WON -> {
                    GameButton(stringResource(R.string.next_level), modifier = Modifier.weight(1f), onClick = vm::nextLevel)
                    if (solve) GameButton(stringResource(R.string.solve), fontSize = 14.sp, modifier = Modifier.weight(1f)) { confirmSolution = true }
                }
                GameState.LOST -> {
                    GameButton(stringResource(R.string.undo), enabled = vm.canUndo, fontSize = fs, modifier = Modifier.weight(1f), onClick = vm::undo)
                    GameButton(stringResource(R.string.restart), fontSize = fs, modifier = Modifier.weight(1f), onClick = vm::restart)
                    if (solve) GameButton(stringResource(R.string.solve), fontSize = fs, modifier = Modifier.weight(1f)) { confirmSolution = true }
                }
                GameState.PLAYING -> {
                    GameButton(stringResource(R.string.undo), enabled = vm.canUndo, fontSize = fs, modifier = Modifier.weight(1f), onClick = vm::undo)
                    GameButton(stringResource(R.string.restart), fontSize = fs, modifier = Modifier.weight(1f), onClick = vm::restart)
                    GameButton(stringResource(R.string.hint), enabled = vm.hintAvailable, fontSize = fs, modifier = Modifier.weight(1f), onClick = vm::showHint)
                    if (solve) GameButton(stringResource(R.string.solve), fontSize = fs, modifier = Modifier.weight(1f)) { confirmSolution = true }
                }
            }
        }
    }

    if (confirmSolution) {
        val n = vm.bestPar ?: vm.par
        val kind = stringResource(if (vm.bestPar != null) R.string.solution_kind_best else R.string.solution_kind_full)
        ConfirmDialog(
            title = stringResource(R.string.confirm_show_title),
            body = stringResource(R.string.confirm_show_body, kind, n),
            confirmLabel = stringResource(R.string.show),
            onConfirm = { confirmSolution = false; vm.startSolution() },
            onDismiss = { confirmSolution = false },
        )
    }
}

/** Step-through viewer for the best-known (or shipped) solution. */
@Composable
private fun SolutionView(vm: GameViewModel) {
    val board = vm.solutionBoard ?: return
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = vm::exitSolution)
            Spacer(Modifier.width(4.dp))
            val kind = stringResource(if (vm.solutionIsBest) R.string.solution_best else R.string.solution_par)
            TextMMD(
                text = stringResource(R.string.solution_header, kind, vm.solutionLength),
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.weight(1f))
        // Board at the current step; the upcoming move is highlighted like a hint.
        BoardCanvas(board = board, selected = null, hint = vm.solutionNextMove, onCellTap = { _, _ -> })
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextMMD(text = stringResource(R.string.solution_move, vm.solutionStep, vm.solutionLength), fontSize = 15.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameButton(stringResource(R.string.prev), enabled = vm.solutionStep > 0, modifier = Modifier.weight(1f), onClick = vm::solutionPrev)
            GameButton(stringResource(R.string.next_arrow), enabled = vm.solutionStep < vm.solutionLength, modifier = Modifier.weight(1f), onClick = vm::solutionNext)
            GameButton(stringResource(R.string.done), modifier = Modifier.weight(1f), onClick = vm::exitSolution)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            TextMMD(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextMMD(text = body, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(stringResource(R.string.cancel), modifier = Modifier.weight(1f), onClick = onDismiss)
                GameButton(confirmLabel, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun BoardCanvas(
    board: Board,
    selected: Pair<Int, Int>?,
    hint: Move?,
    onCellTap: (Int, Int) -> Unit,
) {
    // Fill the available width and keep the board's 10:8 ratio, so 10 columns
    // always fit exactly regardless of display density. Cell size is derived from
    // the actual laid-out size, not from fixed dp.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Board.W.toFloat() / Board.H.toFloat())
            .pointerInput(board, selected) {
                val cell = size.width / Board.W.toFloat()
                detectTapGestures { off ->
                    val x = (off.x / cell).toInt()
                    val y = (off.y / cell).toInt()
                    if (x in 0 until Board.W && y in 0 until Board.H) onCellTap(x, y)
                }
            },
    ) {
        val cellPx = size.width / Board.W
        val border = cellPx * BORDER_FRAC
        drawContext.canvas.nativeCanvas.let { nc ->
            val paint = Paint().apply { isAntiAlias = false }
            for (y in 0 until Board.H) {
                for (x in 0 until Board.W) {
                    val left = x * cellPx
                    val top = y * cellPx
                    val c = board[x, y]
                    val isSelected = selected?.let { it.first == x && it.second == y } == true
                    when {
                        c == Board.WALL -> {
                            paint.style = Paint.Style.FILL; paint.color = WALL_COLOR
                            nc.drawRect(left, top, left + cellPx, top + cellPx, paint)
                        }
                        c == Board.EMPTY -> {
                            paint.style = Paint.Style.FILL; paint.color = WHITE
                            nc.drawRect(left, top, left + cellPx, top + cellPx, paint)
                            // faint grid so empty playfield cells are legible
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = cellPx * GRID_FRAC
                            paint.color = GRID_COLOR
                            nc.drawRect(left, top, left + cellPx, top + cellPx, paint)
                        }
                        else -> {
                            // Block tile. Selected = inverted (black tile, white glyph).
                            val tile = if (isSelected) BLACK else WHITE
                            val fg = if (isSelected) WHITE else BLACK
                            paint.style = Paint.Style.FILL; paint.color = tile
                            nc.drawRect(left, top, left + cellPx, top + cellPx, paint)
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = border
                            paint.color = BLACK
                            nc.drawRect(
                                left + border, top + border,
                                left + cellPx - border, top + cellPx - border, paint,
                            )
                            BlockGlyphs.draw(nc, paint, c, left, top, cellPx, fg)
                        }
                    }
                }
            }
            // Hint marker: a hollow ring on the source cell plus a direction wedge.
            hint?.let { m -> drawHint(nc, paint, m, cellPx) }
        }
    }
}

private fun drawHint(nc: android.graphics.Canvas, paint: Paint, m: Move, cellPx: Float) {
    val left = m.x * cellPx
    val top = m.y * cellPx
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = cellPx * 0.10f
    paint.color = BLACK
    val inset = cellPx * 0.10f
    nc.drawRect(left + inset, top + inset, left + cellPx - inset, top + cellPx - inset, paint)
    // Direction arrowhead pointing to the target empty cell.
    paint.style = Paint.Style.FILL
    val cy = top + cellPx / 2f
    val tip = if (m.dir == Direction.LEFT) left - cellPx * 0.30f else left + cellPx + cellPx * 0.30f
    val baseX = if (m.dir == Direction.LEFT) left - cellPx * 0.02f else left + cellPx + cellPx * 0.02f
    val path = android.graphics.Path().apply {
        moveTo(tip, cy)
        lineTo(baseX, cy - cellPx * 0.16f)
        lineTo(baseX, cy + cellPx * 0.16f)
        close()
    }
    nc.drawPath(path, paint)
}

@Composable
private fun GameButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    onClick: () -> Unit,
) {
    ButtonMMD(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .height(56.dp)
            .border(1.dp, if (enabled) Color.Black else Color.Gray, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
    ) {
        // Center the label in the button box — MMD's default content alignment
        // leaves it sitting high.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextMMD(text = text, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}
