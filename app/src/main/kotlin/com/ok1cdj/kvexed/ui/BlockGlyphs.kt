package com.ok1cdj.kvexed.ui

import android.graphics.Paint
import android.graphics.Path

/**
 * The eight Vexed block glyphs, drawn as vector [Path]s — no bitmaps, so they
 * scale to any cell size, carry no third-party artwork licence, and stay crisp
 * 1-bit on e-ink (anti-aliasing is left off by the caller's [Paint]).
 *
 * The shapes are re-drawn from the original Vexed 16×16 monochrome block set
 * (block1–block8.bmp): a=ring, b=right arrow, c=four corner pips, d=plus,
 * e=saltire (×), f=hollow square, g=goblet, h=diamond. A shape is an idea; the
 * original bitmaps are not reused.
 *
 * All stroke widths are fractions of the cell size, collected here as the glyph
 * tuning knobs.
 */
object BlockGlyphs {

    // --- tuning knobs: stroke weights as a fraction of the cell size ----------
    private const val RING_STROKE = 0.14f
    private const val HOLLOW_STROKE = 0.14f
    private const val PLUS_STROKE = 0.20f
    private const val SALTIRE_STROKE = 0.18f
    private const val GOBLET_STROKE = 0.13f
    private const val PIP_SIZE = 0.22f // side of each corner square, fraction of cell

    /** Inset of the glyph box inside the cell, as a fraction of the cell size. */
    const val GLYPH_INSET = 0.22f

    /**
     * Draw the glyph for block [type] ('a'..'h') centered in the cell whose
     * top-left is ([left],[top]) with side [cell], in colour [color] (ARGB).
     * [paint] should already have anti-aliasing disabled.
     */
    fun draw(canvas: android.graphics.Canvas, paint: Paint, type: Char, left: Float, top: Float, cell: Float, color: Int) {
        val inset = cell * GLYPH_INSET
        val gx = left + inset
        val gy = top + inset
        val gs = cell - 2 * inset // glyph box side
        val cx = left + cell / 2f
        val cy = top + cell / 2f
        paint.color = color

        when (type) {
            'a' -> ring(canvas, paint, cx, cy, gs, cell)
            'b' -> arrow(canvas, paint, gx, gy, gs)
            'c' -> pips(canvas, paint, gx, gy, gs, cell)
            'd' -> plus(canvas, paint, cx, cy, gs, cell)
            'e' -> saltire(canvas, paint, gx, gy, gs, cell)
            'f' -> hollowSquare(canvas, paint, gx, gy, gs, cell)
            'g' -> goblet(canvas, paint, gx, gy, gs, cell)
            'h' -> diamond(canvas, paint, cx, cy, gs)
            else -> {}
        }
    }

    private fun ring(c: android.graphics.Canvas, p: Paint, cx: Float, cy: Float, gs: Float, cell: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * RING_STROKE
        c.drawCircle(cx, cy, gs / 2f - p.strokeWidth / 2f, p)
        p.style = Paint.Style.FILL
    }

    private fun arrow(c: android.graphics.Canvas, p: Paint, gx: Float, gy: Float, gs: Float) {
        p.style = Paint.Style.FILL
        val path = Path().apply {
            moveTo(gx, gy)
            lineTo(gx + gs, gy + gs / 2f)
            lineTo(gx, gy + gs)
            close()
        }
        c.drawPath(path, p)
    }

    private fun pips(c: android.graphics.Canvas, p: Paint, gx: Float, gy: Float, gs: Float, cell: Float) {
        p.style = Paint.Style.FILL
        val s = cell * PIP_SIZE
        // four corners of the glyph box
        c.drawRect(gx, gy, gx + s, gy + s, p)
        c.drawRect(gx + gs - s, gy, gx + gs, gy + s, p)
        c.drawRect(gx, gy + gs - s, gx + s, gy + gs, p)
        c.drawRect(gx + gs - s, gy + gs - s, gx + gs, gy + gs, p)
    }

    private fun plus(c: android.graphics.Canvas, p: Paint, cx: Float, cy: Float, gs: Float, cell: Float) {
        p.style = Paint.Style.FILL
        val h = gs / 2f
        val t = cell * PLUS_STROKE / 2f
        c.drawRect(cx - t, cy - h, cx + t, cy + h, p) // vertical bar
        c.drawRect(cx - h, cy - t, cx + h, cy + t, p) // horizontal bar
    }

    private fun saltire(c: android.graphics.Canvas, p: Paint, gx: Float, gy: Float, gs: Float, cell: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * SALTIRE_STROKE
        c.drawLine(gx, gy, gx + gs, gy + gs, p)
        c.drawLine(gx + gs, gy, gx, gy + gs, p)
        p.style = Paint.Style.FILL
    }

    private fun hollowSquare(c: android.graphics.Canvas, p: Paint, gx: Float, gy: Float, gs: Float, cell: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * HOLLOW_STROKE
        val h = p.strokeWidth / 2f
        c.drawRect(gx + h, gy + h, gx + gs - h, gy + gs - h, p)
        p.style = Paint.Style.FILL
    }

    private fun goblet(c: android.graphics.Canvas, p: Paint, gx: Float, gy: Float, gs: Float, cell: Float) {
        // A funnel/goblet: a wide top edge whose sides converge to the centre,
        // then a vertical stem down to the base.
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * GOBLET_STROKE
        p.strokeCap = Paint.Cap.SQUARE
        val midX = gx + gs / 2f
        val midY = gy + gs * 0.45f
        val path = Path().apply {
            moveTo(gx, gy)              // top-left
            lineTo(midX, midY)          // converge to centre
            lineTo(gx + gs, gy)         // top-right
            moveTo(midX, midY)          // stem
            lineTo(midX, gy + gs)
        }
        c.drawPath(path, p)
        // base foot
        c.drawLine(midX - gs * 0.25f, gy + gs, midX + gs * 0.25f, gy + gs, p)
        p.style = Paint.Style.FILL
    }

    private fun diamond(c: android.graphics.Canvas, p: Paint, cx: Float, cy: Float, gs: Float) {
        p.style = Paint.Style.FILL
        val h = gs / 2f
        val path = Path().apply {
            moveTo(cx, cy - h)
            lineTo(cx + h, cy)
            lineTo(cx, cy + h)
            lineTo(cx - h, cy)
            close()
        }
        c.drawPath(path, p)
    }
}
