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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun GameScreen(vm: GameViewModel, onAbout: () -> Unit) {
    val board = vm.board ?: return

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header: pack · level x/n · info
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTextButton(text = "‹", onClick = vm::backToLevels)
                Spacer(Modifier.width(8.dp))
                TextMMD(
                    text = "${vm.packTitle} · ${vm.levelIndex + 1}/${vm.levelCount}",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            IconTextButton(text = "ⓘ", onClick = onAbout)
        }

        // Board — one Canvas, one repaint per move.
        BoardCanvas(
            board = board,
            selected = vm.selected,
            hint = vm.hint,
            onCellTap = vm::onCellTap,
        )

        // Status
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextMMD(text = "Moves ${vm.moveCount} · Par ${vm.par}", fontSize = 15.sp)
        }

        // Win / stuck banner
        when (vm.gameState) {
            GameState.WON -> Banner(
                text = "Solved in ${vm.moveCount} moves (par ${vm.par})",
                actionLabel = "Next",
                onAction = vm::nextLevel,
            )
            GameState.LOST -> Banner(
                text = "Stuck — undo or restart.",
                actionLabel = "Restart",
                onAction = vm::restart,
            )
            GameState.PLAYING -> Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameButton("Undo", enabled = vm.canUndo, modifier = Modifier.weight(1f), onClick = vm::undo)
            GameButton("Restart", modifier = Modifier.weight(1f), onClick = vm::restart)
            GameButton("Hint", modifier = Modifier.weight(1f), onClick = vm::showHint)
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
private fun Banner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextMMD(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        GameButton(actionLabel, onClick = onAction)
    }
}

@Composable
private fun GameButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ButtonMMD(
        onClick = { if (enabled) onClick() },
        modifier = modifier.border(1.dp, if (enabled) Color.Black else Color.Gray, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
    ) {
        TextMMD(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconTextButton(text: String, onClick: () -> Unit) {
    ButtonMMD(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        TextMMD(text = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
