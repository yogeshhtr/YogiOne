package com.yogi.one

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** Round timer ring with big text in the middle. */
class RingView(ctx: Context) : View(ctx) {
    var progress = 0f
    var big = ""
    var small = ""
    var ringColor = C.ACCENT
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.CARD2 }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = C.TEXT; typeface = Typeface.DEFAULT_BOLD
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = C.SUB; typeface = Typeface.DEFAULT_BOLD
    }
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val sw = s * 0.06f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        arc.color = ringColor
        val cx = width / 2f
        val cy = height / 2f
        val r = s / 2f - sw
        box.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(box, 0f, 360f, false, track)
        c.drawArc(box, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)
        bigP.textSize = s * 0.19f
        c.drawText(big, cx, cy + bigP.textSize * 0.3f, bigP)
        smallP.textSize = s * 0.055f
        smallP.letterSpacing = 0.2f
        c.drawText(small, cx, cy + s * 0.2f, smallP)
    }
}

/** Simple 7-day bar chart. */
class BarChart(ctx: Context) : View(ctx) {
    var values: List<Float> = emptyList()
    var labels: List<String> = emptyList()
    var barColor = C.ACCENT
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = C.SUB }
    private val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = C.TEXT; typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val n = values.size
        if (n == 0) return
        val d = resources.displayMetrics.density
        tp.textSize = 11 * d
        vp.textSize = 11 * d
        val yBottom = height - 18 * d
        val yTop = 18 * d
        val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val slot = width / n.toFloat()
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val bw = slot * 0.5f
            val h = ((yBottom - yTop) * (values[i] / maxV)).coerceAtLeast(4 * d)
            bp.color = if (values[i] > 0) barColor else C.CARD2
            rect.set(cx - bw / 2, yBottom - h, cx + bw / 2, yBottom)
            c.drawRoundRect(rect, 6 * d, 6 * d, bp)
            if (values[i] > 0) c.drawText(values[i].toInt().toString(), cx, rect.top - 4 * d, vp)
            labels.getOrNull(i)?.let { c.drawText(it, cx, height - 4 * d, tp) }
        }
    }
}

/** Bubble level: the bubble goes to the high side. */
class LevelView(ctx: Context) : View(ctx) {
    private var gx = 0f
    private var gy = 0f
    private var gz = 9.81f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.LINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = C.TEXT; typeface = Typeface.DEFAULT_BOLD
    }

    fun set(x: Float, y: Float, z: Float) {
        gx = gx + 0.2f * (x - gx)
        gy = gy + 0.2f * (y - gy)
        gz = gz + 0.2f * (z - gz)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 8 * d
        ring.strokeWidth = 3 * d
        c.drawCircle(cx, cy, r, ring)
        c.drawCircle(cx, cy, r * 0.16f, ring)
        c.drawLine(cx - r, cy, cx + r, cy, ring)
        c.drawLine(cx, cy - r, cx, cy + r, ring)
        var bx = gx / 9.81f * r
        var by = -gy / 9.81f * r
        val len = sqrt(bx * bx + by * by)
        val maxL = r * 0.86f
        if (len > maxL) {
            bx = bx / len * maxL
            by = by / len * maxL
        }
        val tilt = Math.toDegrees(atan2(sqrt(gx * gx + gy * gy).toDouble(), gz.toDouble())).toFloat()
        fill.color = if (tilt < 1f) C.GREEN else C.GOLD
        c.drawCircle(cx + bx, cy + by, r * 0.13f, fill)
        tp.textSize = 26 * d
        tp.color = if (tilt < 1f) C.GREEN else C.TEXT
        c.drawText(String.format(Locale.US, "%.1f°", tilt), cx, cy - r * 0.45f, tp)
    }
}

/** Compass dial that turns so N points north. */
class CompassView(ctx: Context) : View(ctx) {
    var heading = 0f
        set(v) { field = v; invalidate() }
    private val dial = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.LINE }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.SUB }
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.RED }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = C.TEXT; typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 14 * d
        dial.strokeWidth = 3 * d
        tick.strokeWidth = 2 * d
        letter.textSize = 20 * d
        c.save()
        c.rotate(-heading, cx, cy)
        c.drawCircle(cx, cy, r, dial)
        for (deg in 0 until 360 step 10) {
            c.save()
            c.rotate(deg.toFloat(), cx, cy)
            val len = if (deg % 30 == 0) 14 * d else 7 * d
            c.drawLine(cx, cy - r, cx, cy - r + len, tick)
            if (deg % 90 == 0) {
                letter.color = if (deg == 0) C.RED else C.TEXT
                val name = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; else -> "W" }
                c.drawText(name, cx, cy - r + 40 * d, letter)
            }
            c.restore()
        }
        c.restore()
        // fixed pointer at the top = where the phone points
        path.reset()
        path.moveTo(cx, cy - r + 2 * d)
        path.lineTo(cx - 10 * d, cy - r - 12 * d)
        path.lineTo(cx + 10 * d, cy - r - 12 * d)
        path.close()
        c.drawPath(path, needle)
        center.textSize = 34 * d
        c.drawText("${heading.toInt()}°", cx, cy + 6 * d, center)
        center.textSize = 18 * d
        c.drawText(dirName(heading), cx, cy + 32 * d, center)
    }

    companion object {
        fun dirName(h: Float): String =
            arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
