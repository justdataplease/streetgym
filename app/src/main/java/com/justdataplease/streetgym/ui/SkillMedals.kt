package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillStatus
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A skill's difficulty as its outline colour: green (easy), lime, yellow, orange, red (extreme). */
fun difficultyColor(grade: Int): Int = when (grade.coerceIn(1, 5)) {
    1 -> 0xFF2E9E5B.toInt()
    2 -> 0xFF8DBF3A.toInt()
    3 -> 0xFFE5B521.toInt()
    4 -> 0xFFE07B2A.toInt()
    else -> 0xFFD2353F.toInt()
}

/**
 * A skill as a medal: its figure on a rounded square [size] dp wide whose outline shows how hard the skill is
 * ([difficultyColor]), with the athlete's rank shield ([RankBadgeView]) hanging from its foot. The medal is
 * [medalHeight] tall.
 */
fun Context.skillMedal(status: SkillStatus, size: Int): FrameLayout = FrameLayout(this).apply {
    val ring = (size / 16).coerceAtLeast(3)
    addView(FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // The figure tile's corner (a share of its side, as ExerciseAnimationView draws it) plus the ring.
            cornerRadius = dp(ring) + TILE_CORNER * dp(size - 2 * ring)
            setColor(AppColors.BACKGROUND)
            setStroke(dp(ring), difficultyColor(status.skill.difficulty))
        }
        addView(skillFigure(status), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT).apply { setMargins(dp(ring), dp(ring), dp(ring), dp(ring)) })
    }, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    if (status.level > SkillLevel.NONE) {
        addView(RankBadgeView(context, status.level, size / MEDAL_SIZE),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }
}

/** The height a medal of [size] takes: the square and the part of the rank shield below it. */
fun Context.medalHeight(size: Int): Int = dp(size) + dp((OVERHANG * size / MEDAL_SIZE).toInt())

/** The size the rank shields are drawn for; smaller medals scale them down. */
private const val MEDAL_SIZE = 78f
private const val OVERHANG = 28f
private const val TILE_CORNER = 0.22f

/** Elite: the night tile with the mint figure. Started: light tint. Not started: muted. */
private fun Context.skillFigure(status: SkillStatus): ExerciseAnimationView {
    val style = when {
        status.level == SkillLevel.ELITE -> ExerciseAnimationView.BadgeStyle(
            AppColors.PRIMARY_DARK, AppColors.ACCENT, blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, 0.5f),
            blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.55f))
        status.started -> ExerciseAnimationView.BadgeStyle(
            AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f))
        else -> ExerciseAnimationView.BadgeStyle(
            AppColors.DIVIDER, blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.3f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.6f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.6f))
    }
    return ExerciseAnimationView(this).bind(status.skill.id, status.skill.name).apply {
        setBadgeStyle(style)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

/**
 * A rank as a shield, like a soldier's insignia in a game: a peaked gold rim, a glossy graphite face and gold marks.
 * One chevron band for Beginner, two for Intermediate, three stars for Advanced, a big star for Master, and for Elite
 * the big star with wings. The big-star ranks wear a stacked crest. [scale] sizes it to its medal.
 */
class RankBadgeView(context: Context, private val level: SkillLevel, private val scale: Float = 1f) : View(context) {
    /** How far the crest rises above the shield; the shield itself is 32 by 38 units. */
    private val crest = when (level) {
        SkillLevel.MASTER -> 3f
        SkillLevel.ELITE -> 6f
        else -> 0f
    }
    /** The drawing's own box: the shield and its crest, and the wings either side of it for Elite. */
    private val box = (if (level == SkillLevel.ELITE) 56f else 32f) to 38f + crest
    private val shieldLeft = (box.first - 32f) / 2
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -6f, 0f, 38f, GOLD_LIGHT, GOLD_DARK, Shader.TileMode.CLAMP)
    }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 5f, 0f, 33f, FACE_TOP, FACE_BOTTOM, Shader.TileMode.CLAMP)
    }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 8f, 0f, 30f, GOLD_LIGHT, GOLD, Shader.TileMode.CLAMP)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GOLD_EDGE
        strokeWidth = 0.7f
        strokeJoin = Paint.Join.ROUND
    }
    /** The bevel where the rim meets the face. */
    private val bevel = Paint(line).apply {
        color = GOLD_BEVEL
        strokeWidth = 1f
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unit = context.dp(1) * SHIELD_WIDTH_DP / 32f * scale
        setMeasuredDimension((box.first * unit).toInt(), (box.second * unit).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val unit = min(width / box.first, height / box.second)
        canvas.save()
        canvas.scale(unit, unit)
        canvas.translate(0f, crest)
        if (level == SkillLevel.ELITE) drawGold(canvas, wings())
        canvas.translate(shieldLeft, 0f)
        if (crest > 0f) drawGold(canvas, crest())
        val inner = polygon(INNER)
        drawGold(canvas, polygon(OUTER))
        canvas.drawPath(inner, face)
        canvas.save()
        canvas.clipPath(inner)
        canvas.drawRect(0f, 0f, 32f, 16f, gloss)
        when (level) {
            SkillLevel.BEGINNER -> drawMarks(canvas, bands(19f))
            SkillLevel.INTERMEDIATE -> drawMarks(canvas, bands(13.5f, 20.5f))
            SkillLevel.ADVANCED -> drawMarks(canvas, star(11.2f, 14.5f, 4.6f).apply {
                addPath(star(20.8f, 14.5f, 4.6f))
                addPath(star(16f, 23f, 4.6f))
            })
            SkillLevel.MASTER -> drawMarks(canvas, star(16f, 18.5f, 9f))
            SkillLevel.ELITE -> drawMarks(canvas, star(16f, 18.5f, 9.5f))
            SkillLevel.NONE -> Unit
        }
        canvas.restore()
        canvas.drawPath(inner, bevel)
        canvas.restore()
    }

    private fun drawGold(canvas: Canvas, path: Path) {
        canvas.drawPath(path, rim)
        canvas.drawPath(path, line)
    }

    private fun drawMarks(canvas: Canvas, path: Path) {
        canvas.drawPath(path, mark)
        canvas.drawPath(path, line)
    }

    private fun polygon(points: FloatArray): Path = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }

    /** Chevron bands pointing down, each starting at one of [tops]. */
    private fun bands(vararg tops: Float): Path = Path().apply {
        for (top in tops) addPath(polygon(floatArrayOf(4f, top, 16f, top + 5.5f, 28f, top, 28f, top + 4f, 16f, top + 9.5f, 4f, top + 4f)))
    }

    /** Plates stacked on the shield's top, narrowing as they rise: two for Master, three for Elite. */
    private fun crest(): Path = Path().apply {
        CREST.take(if (level == SkillLevel.ELITE) 3 else 2).forEach { addPath(polygon(it)) }
    }

    private fun star(cx: Float, cy: Float, outer: Float): Path = Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else outer * 0.42f
            val angle = Math.toRadians(-90.0 + 36.0 * i)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Three feathers a side, sweeping up and out from behind the shield. */
    private fun wings(): Path = Path().apply {
        for (feather in FEATHERS) for (mirror in listOf(false, true)) {
            fun x(value: Float) = if (mirror) box.first - value else value
            moveTo(x(feather[0]), feather[1])
            cubicTo(x(feather[2]), feather[3], x(feather[4]), feather[5], x(feather[6]), feather[7])
            cubicTo(x(feather[8]), feather[9], x(feather[10]), feather[11], x(feather[12]), feather[13])
            close()
        }
    }

    private companion object {
        /** The shield's width at a full-size medal. */
        const val SHIELD_WIDTH_DP = 28f
        const val GOLD = 0xFFF2C14E.toInt()
        const val GOLD_LIGHT = 0xFFFFE597.toInt()
        const val GOLD_DARK = 0xFFC2851A.toInt()
        const val GOLD_EDGE = 0xFF8A5A0C.toInt()
        const val GOLD_BEVEL = 0xFF9A650E.toInt()
        const val FACE_TOP = 0xFF3A3A35.toInt()
        const val FACE_BOTTOM = 0xFF141413.toInt()
        /** The shield: a low peak on top, near-straight sides and a pointed foot. */
        val OUTER = floatArrayOf(1.5f, 6f, 16f, 2.5f, 30.5f, 6f, 29.5f, 26.5f, 16f, 36.5f, 2.5f, 26.5f)
        /** The face, the shield's outline moved 3 units in from every edge. */
        val INNER = floatArrayOf(4.6f, 8.3f, 16f, 5.6f, 27.4f, 8.3f, 26.6f, 24.9f, 16f, 32.8f, 5.4f, 24.9f)
        val CREST = listOf(
            floatArrayOf(6.5f, 5f, 8f, 0.5f, 24f, 0.5f, 25.5f, 5f),
            floatArrayOf(9f, 0.5f, 10f, -2.5f, 22f, -2.5f, 23f, 0.5f),
            floatArrayOf(11.5f, -2.5f, 12.5f, -5.5f, 19.5f, -5.5f, 20.5f, -2.5f),
        )
        /** Each left feather in the 56-wide box: its root at the shield, a curve out to its tip and a curve back. */
        val FEATHERS = listOf(
            floatArrayOf(14f, 9f, 9f, 6f, 4f, 3.5f, 0.5f, 1f, 3.5f, 7f, 8.5f, 11.5f, 14f, 14f),
            floatArrayOf(14f, 14.5f, 9f, 13f, 4.5f, 11.5f, 1.5f, 9.5f, 4f, 14.5f, 9f, 18f, 14f, 19.5f),
            floatArrayOf(14f, 20f, 10f, 19.5f, 6.5f, 19f, 3.5f, 18f, 6f, 22f, 10f, 24f, 14f, 24.5f),
        )
    }
}
