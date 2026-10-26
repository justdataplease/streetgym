package com.justdataplease.streetgym.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.min

/** Night Park's court plate: a rectangle with the top-left and bottom-right corners cut by [cutPx]. */
class ChamferDrawable(
    private val color: Int,
    private val cutPx: Float,
    private val strokeColor: Int? = null,
    private val strokeWidthPx: Int = 0,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@ChamferDrawable.color }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx.toFloat()
        this.color = strokeColor ?: Color.TRANSPARENT
    }
    private val path = Path()
    private val inner = Path()

    override fun onBoundsChange(bounds: Rect) {
        shape(path, RectF(bounds))
        val half = strokeWidthPx / 2f
        shape(inner, RectF(bounds).apply { inset(half, half) })
    }

    private fun shape(target: Path, box: RectF) {
        val cut = min(cutPx, min(box.width(), box.height()) / 2f)
        target.reset()
        target.moveTo(box.left + cut, box.top)
        target.lineTo(box.right, box.top)
        target.lineTo(box.right, box.bottom - cut)
        target.lineTo(box.right - cut, box.bottom)
        target.lineTo(box.left, box.bottom)
        target.lineTo(box.left, box.top + cut)
        target.close()
    }

    override fun draw(canvas: Canvas) {
        if (Color.alpha(color) > 0) canvas.drawPath(path, fill)
        if (strokeColor != null && strokeWidthPx > 0) canvas.drawPath(inner, stroke)
    }

    override fun getOutline(outline: Outline) {
        if (bounds.isEmpty) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(path)
        } else {
            @Suppress("DEPRECATION")
            outline.setConvexPath(path)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; stroke.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; stroke.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Concrete or paper grain: [base] with two offset dot grids, one darker and one lighter, tiled from a small bitmap so
 * a whole page costs one shader draw.
 */
class GrainDrawable(private val base: Int, density: Float, texture: Texture) : Drawable() {
    private val paint = Paint()

    init {
        val concrete = texture == Texture.CONCRETE
        val cell = ((if (concrete) 9f else 6f) * density).toInt().coerceAtLeast(4)
        val size = cell * 2
        val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        canvas.drawColor(base)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = 0.7f * density
        dot.color = Color.argb(if (concrete) 20 else 13, 18, 18, 18)
        for (x in 0 until 2) for (y in 0 until 2) canvas.drawCircle(x * cell + cell * 0.3f, y * cell + cell * 0.4f, radius, dot)
        if (concrete) {
            dot.color = Color.argb(90, 255, 255, 255)
            canvas.drawCircle(cell * 0.85f, cell * 1.2f, radius, dot)
            canvas.drawCircle(cell * 1.7f, cell * 0.15f, radius, dot)
            dot.color = Color.argb(14, 18, 18, 18)
            canvas.drawCircle(cell * 1.35f, cell * 1.75f, radius * 1.3f, dot)
        }
        paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** [fill] (may be transparent) with a [rule] line [thicknessPx] tall along the top edge. */
class TopRuleDrawable(private val fill: Int, private val rule: Int, private val thicknessPx: Float) : Drawable() {
    private val paint = Paint()

    override fun draw(canvas: Canvas) {
        if (Color.alpha(fill) > 0) { paint.color = fill; canvas.drawRect(bounds, paint) }
        paint.color = rule
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.top + thicknessPx, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Wheatpaste's halftone: a disc of [color] dots on a [pitchPx] grid, filling the bounds' inscribed circle. */
class HalftoneDrawable(private val color: Int, private val pitchPx: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@HalftoneDrawable.color }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = min(bounds.width(), bounds.height()) / 2f
        val dot = pitchPx * 0.32f
        var y = bounds.top + pitchPx / 2f
        while (y < bounds.bottom) {
            var x = bounds.left + pitchPx / 2f
            while (x < bounds.right) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r * r) canvas.drawCircle(x, y, dot, paint)
                x += pitchPx
            }
            y += pitchPx
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A rubber stamp ("DONE") drawn tilted in the top-right corner, over the content. */
class StampDrawable(
    private val text: String,
    private val color: Int,
    private val density: Float,
    typeface: android.graphics.Typeface,
) : Drawable() {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@StampDrawable.color
        textSize = 14f * density
        this.typeface = typeface
        letterSpacing = 0.08f
    }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@StampDrawable.color
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    override fun draw(canvas: Canvas) {
        val width = textPaint.measureText(text)
        val padX = 8f * density
        val padY = 4f * density
        val metrics = textPaint.fontMetrics
        val height = metrics.descent - metrics.ascent
        val right = bounds.right - 14f * density
        val top = bounds.top + 14f * density
        canvas.save()
        canvas.rotate(-12f, right - width / 2f, top + height / 2f)
        canvas.drawRect(right - width - padX * 2, top, right, top + height + padY * 2, frame)
        canvas.drawText(text, right - width - padX, top + padY - metrics.ascent, textPaint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { textPaint.alpha = alpha; frame.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { textPaint.colorFilter = colorFilter; frame.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
