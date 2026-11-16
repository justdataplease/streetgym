package com.justdataplease.streetgym.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import com.justdataplease.streetgym.data.progress.Streak
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The streak's flame, with the number of days in its belly. Lit once today's workout is done; unlit, with yesterday's
 * number kept, while today is still open; a dashed outline with 0 when there is no streak. Drawn here for the app's
 * screens ([StreakFlameView]) and for the home-screen widget ([bitmap]), in each theme's colours.
 */
object StreakFlame {
    enum class State { LIT, UNLIT, NONE }

    /** [outline] and [shadow] are null in a theme that prints neither; [dashed] draws only the outline. */
    class Colors(val body: Int, val core: Int, val outline: Int?, val shadow: Int?, val number: Int, val dashed: Boolean)

    /** The flame is drawn in a box 100 wide and 120 tall. */
    const val HEIGHT_PER_WIDTH = 1.2f

    fun state(streak: Streak): State = when {
        streak.litToday -> State.LIT
        streak.days > 0 -> State.UNLIT
        else -> State.NONE
    }

    fun colors(theme: StreetTheme, state: State): Colors {
        val palette = theme.palette
        // Concrete and Wheatpaste outline everything in ink and print a hard shadow; Night Park does neither.
        val outlined = theme.shape.borderDp > 0
        val faint = blendColor(palette.textSecondary, palette.background, 0.45f)
        return when (state) {
            State.LIT -> Colors(
                body = palette.accent,
                core = blendColor(palette.accent, Color.WHITE, if (theme.dark) 0.35f else 0.63f),
                outline = palette.border.takeIf { outlined },
                shadow = when (theme) {
                    StreetTheme.CONCRETE -> palette.border
                    StreetTheme.WHEATPASTE -> palette.primary
                    StreetTheme.NIGHT_PARK -> null
                },
                number = palette.ink,
                dashed = false,
            )
            State.UNLIT -> Colors(palette.infoSurface, palette.surface, if (outlined) palette.border else palette.textSecondary,
                shadow = null, number = palette.text, dashed = false)
            State.NONE -> Colors(Color.TRANSPARENT, Color.TRANSPARENT, faint, shadow = null, number = faint, dashed = true)
        }
    }

    /** How tall a flame [widthPx] wide is, without its printed shadow. */
    fun heightFor(widthPx: Float): Float = widthPx * HEIGHT_PER_WIDTH

    /**
     * Draws the flame [widthPx] wide with its top left at ([left], [top]). [number] null draws the plain flame, without
     * its pale core. [shadowPx] is used only where [colors] has a shadow.
     */
    fun draw(canvas: Canvas, left: Float, top: Float, widthPx: Float, number: String?, colors: Colors, face: Typeface,
             density: Float, shadowPx: Float) {
        val stroke = if (colors.outline == null) 0f else min(2f * density, widthPx * 0.075f).coerceAtLeast(density)
        val scale = (widthPx - stroke) / BOX_WIDTH
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(left + stroke / 2f, top + stroke / 2f)
        }
        val body = Path().apply { BODY.transform(matrix, this) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (colors.shadow != null && shadowPx > 0f) {
            paint.color = colors.shadow
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
            canvas.save()
            canvas.translate(shadowPx, shadowPx)
            canvas.drawPath(body, paint)
            canvas.restore()
        }
        if (!colors.dashed) {
            paint.style = Paint.Style.FILL
            paint.color = colors.body
            canvas.drawPath(body, paint)
            if (number != null) {
                paint.color = colors.core
                canvas.drawPath(Path().apply { CORE.transform(matrix, this) }, paint)
            }
        }
        if (colors.outline != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = colors.outline
            if (colors.dashed) paint.pathEffect = DashPathEffect(floatArrayOf(widthPx * 0.11f, widthPx * 0.08f), 0f)
            canvas.drawPath(body, paint)
            paint.pathEffect = null
        }
        if (number.isNullOrEmpty()) return
        // The number fills the belly: as tall as it allows, narrower when the digits (or a wide face) need it.
        paint.style = Paint.Style.FILL
        paint.color = colors.number
        paint.typeface = face
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 100f
        val bounds = Rect()
        paint.getTextBounds(number, 0, number.length, bounds)
        if (bounds.isEmpty) return
        val fit = min(NUMBER_HEIGHT * scale / bounds.height(), NUMBER_WIDTH * scale / bounds.width())
        paint.textSize = 100f * fit
        paint.getTextBounds(number, 0, number.length, bounds)
        val x = left + stroke / 2f + NUMBER_CENTRE_X * scale - bounds.exactCenterX()
        val y = top + stroke / 2f + NUMBER_CENTRE_Y * scale - bounds.exactCenterY()
        canvas.drawText(number, x, y, paint)
    }

    /** The flame as a picture [widthDp] wide (its shadow adds [shadowDp] right and below), for RemoteViews. */
    fun bitmap(theme: StreetTheme, streak: Streak, face: Typeface, density: Float, widthDp: Float, shadowDp: Float): Bitmap {
        val colors = colors(theme, state(streak))
        val shadow = if (colors.shadow != null) shadowDp * density else 0f
        val width = widthDp * density
        return Bitmap.createBitmap(ceil(width + shadow).toInt().coerceAtLeast(1), ceil(heightFor(width) + shadow).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888).also { bitmap ->
            draw(Canvas(bitmap), 0f, 0f, width, streak.days.toString(), colors, face, density, shadow)
        }
    }

    private const val BOX_WIDTH = 100f
    /** The digits' box inside the belly, in the flame's own units. */
    private const val NUMBER_CENTRE_X = 50.5f
    private const val NUMBER_CENTRE_Y = 84f
    private const val NUMBER_HEIGHT = 34f
    private const val NUMBER_WIDTH = 48f

    /** A main tongue and a small one on the left, over a wide belly for the number. */
    private val BODY = Path().apply {
        moveTo(52f, 3f)
        cubicTo(56f, 24f, 80f, 36f, 88f, 62f)
        cubicTo(96f, 90f, 78f, 117f, 50f, 117f)
        cubicTo(22f, 117f, 4f, 92f, 12f, 64f)
        cubicTo(15f, 53f, 22f, 45f, 27f, 36f)
        cubicTo(29f, 46f, 33f, 53f, 40f, 55f)
        cubicTo(37f, 34f, 41f, 16f, 52f, 3f)
        close()
    }

    /** The pale heart of the flame, behind the number. */
    private val CORE = Path().apply {
        moveTo(50f, 110f)
        cubicTo(33f, 110f, 22f, 97f, 26f, 80f)
        cubicTo(28f, 71f, 34f, 66f, 38f, 60f)
        cubicTo(41f, 66f, 46f, 68f, 51f, 64f)
        cubicTo(55f, 71f, 75f, 76f, 75f, 91f)
        cubicTo(75f, 102f, 64f, 110f, 50f, 110f)
        close()
    }
}

/**
 * The flame on a screen, [widthDp] wide in the active theme. [numbered] false draws the plain flame (History's line).
 * It only decorates: whatever holds it says the streak for screen readers.
 */
@SuppressLint("ViewConstructor")
class StreakFlameView(
    context: Context,
    private val streak: Streak,
    private val widthDp: Int,
    private val shadowDp: Int = 0,
    private val numbered: Boolean = true,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val colors = StreakFlame.colors(AppColors.theme, StreakFlame.state(streak))
    private val shadow = if (colors.shadow != null) shadowDp * density else 0f
    // The headline face, as the widget draws it (RemoteViews gets the same picture).
    private val face = AppType.display

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = widthDp * density
        setMeasuredDimension((width + shadow).roundToInt(), (StreakFlame.heightFor(width) + shadow).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        StreakFlame.draw(canvas, 0f, 0f, widthDp * density, streak.days.toString().takeIf { numbered }, colors, face, density, shadow)
    }
}
