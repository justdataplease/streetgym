package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.figure.BadgeStyle
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.theme.Palette
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * A skill as a medal and its rank as a shield (android-streetgym, ui/SkillMedals.kt): the same sizes, colours and
 * shapes, drawn on Compose's canvas.
 */

/** A skill's difficulty as its outline colour: green (easy), lime, yellow, orange, red (extreme). */
fun difficultyColor(grade: Int): Color = when (grade.coerceIn(1, 5)) {
    1 -> Color(0xFF2E9E5B)
    2 -> Color(0xFF8DBF3A)
    3 -> Color(0xFFE5B521)
    4 -> Color(0xFFE07B2A)
    else -> Color(0xFFD2353F)
}

/** The size the rank shields are drawn for; smaller medals scale them down. */
private const val MEDAL_SIZE = 78f
private const val OVERHANG = 4f
/** How far a shield's right edge passes the square's, and how far its foot hangs below it, at a full-size medal. */
private const val SHIELD_OUT = 12f
private const val SHIELD_OUT_WINGED = 16f
private const val SHIELD_DROP = 14f
private const val TILE_CORNER = 0.22f

/** The height a medal [size] wide takes in its layout: the square and a little room under it. */
fun medalHeight(size: Dp): Dp = size + (OVERHANG * size.value / MEDAL_SIZE).toInt().dp

/** How far a rank shield of a medal [size] wide reaches past the square's right edge, for its parent's room. */
fun shieldOverhang(size: Dp): Dp = ((SHIELD_OUT_WINGED * size.value / MEDAL_SIZE).toInt() + 1).dp

/**
 * A skill as a medal (Android: skillMedal): its figure holding the end pose on a rounded square [size] wide, outlined in
 * its difficulty's colour, with the athlete's rank shield worn like a tag on its bottom-right corner, about half of it
 * outside the square. It takes [size] by [medalHeight]; the shield draws past both, so the parent leaves it the room.
 */
@Composable
fun SkillMedal(status: SkillStatus, size: Dp, modifier: Modifier = Modifier) {
    val palette = Street.colors
    val ring = (size.value / 16f).toInt().coerceAtLeast(3).dp
    val scale = size.value / MEDAL_SIZE
    Box(modifier.size(size, medalHeight(size))) {
        Box(
            Modifier.size(size).align(Alignment.TopCenter).drawBehind {
                // As Android's GradientDrawable: fill and stroke on the bounds inset by half the stroke.
                val stroke = ring.toPx()
                val radius = stroke + TILE_CORNER * (size - ring * 2).toPx()
                val inset = Offset(stroke / 2f, stroke / 2f)
                val inner = Size(this.size.width - stroke, this.size.height - stroke)
                drawRoundRect(palette.background, inset, inner, CornerRadius(radius))
                drawRoundRect(difficultyColor(status.skill.difficulty), inset, inner, CornerRadius(radius), style = Stroke(stroke))
            },
        ) {
            ExerciseFigure(status.skill.id, status.skill.name, Modifier.fillMaxSize().padding(ring), badge = medalStyle(status, palette))
        }
        if (status.level > SkillLevel.NONE) {
            // Elite's wings spread either side of its shield, so it sits further out to keep the shield on the corner.
            val out = if (status.level == SkillLevel.ELITE) SHIELD_OUT_WINGED else SHIELD_OUT
            RankBadge(status.level, scale, Modifier.align(Alignment.BottomEnd).offset((out * scale).dp, ((SHIELD_DROP - OVERHANG) * scale).dp))
        }
    }
}

/** Elite: the night tile with the light figure. Started: a light tint. Not started: muted (Android: skillFigure). */
private fun medalStyle(status: SkillStatus, palette: Palette): BadgeStyle = when {
    status.level == SkillLevel.ELITE -> BadgeStyle(palette.primaryDark, palette.accent, blendColor(palette.accent, palette.primaryDark, 0.5f),
        blendColor(Color.White, palette.primaryDark, 0.55f))
    status.started -> BadgeStyle(palette.infoSurface, palette.primary, blendColor(palette.primary, palette.infoSurface, 0.5f),
        blendColor(palette.textSecondary, palette.infoSurface, 0.4f))
    else -> BadgeStyle(palette.divider, blendColor(palette.textSecondary, palette.divider, 0.3f),
        blendColor(palette.textSecondary, palette.divider, 0.6f), blendColor(palette.textSecondary, palette.divider, 0.6f))
}

/** The shield's width at a full-size medal. */
private const val SHIELD_WIDTH_DP = 28f
private val GOLD = Color(0xFFF2C14E)
private val GOLD_LIGHT = Color(0xFFFFE597)
private val GOLD_DARK = Color(0xFFC2851A)
private val GOLD_EDGE = Color(0xFF8A5A0C)
private val GOLD_BEVEL = Color(0xFF9A650E)
private val FACE_TOP = Color(0xFF3A3A35)
private val FACE_BOTTOM = Color(0xFF141413)
private val GLOSS = Color(0x1FFFFFFF)
/** The shield: a low peak on top, near-straight sides and a pointed foot. */
private val OUTER = floatArrayOf(1.5f, 6f, 16f, 2.5f, 30.5f, 6f, 29.5f, 26.5f, 16f, 36.5f, 2.5f, 26.5f)
/** The face, the shield's outline moved 3 units in from every edge. */
private val INNER = floatArrayOf(4.6f, 8.3f, 16f, 5.6f, 27.4f, 8.3f, 26.6f, 24.9f, 16f, 32.8f, 5.4f, 24.9f)
private val CREST = listOf(
    floatArrayOf(6.5f, 5f, 8f, 0.5f, 24f, 0.5f, 25.5f, 5f),
    floatArrayOf(9f, 0.5f, 10f, -2.5f, 22f, -2.5f, 23f, 0.5f),
    floatArrayOf(11.5f, -2.5f, 12.5f, -5.5f, 19.5f, -5.5f, 20.5f, -2.5f),
)
/** Each left feather in the 56-wide box: its root at the shield, a curve out to its tip and a curve back. */
private val FEATHERS = listOf(
    floatArrayOf(14f, 9f, 9f, 6f, 4f, 3.5f, 0.5f, 1f, 3.5f, 7f, 8.5f, 11.5f, 14f, 14f),
    floatArrayOf(14f, 14.5f, 9f, 13f, 4.5f, 11.5f, 1.5f, 9.5f, 4f, 14.5f, 9f, 18f, 14f, 19.5f),
    floatArrayOf(14f, 20f, 10f, 19.5f, 6.5f, 19f, 3.5f, 18f, 6f, 22f, 10f, 24f, 14f, 24.5f),
)

/** How far the crest rises above the shield; the shield itself is 32 by 38 units. */
private fun crestOf(level: SkillLevel): Float = when (level) {
    SkillLevel.MASTER -> 3f
    SkillLevel.ELITE -> 6f
    else -> 0f
}

/** The drawing's own box: the shield and its crest, and the wings either side of it for Elite. */
private fun boxOf(level: SkillLevel): Pair<Float, Float> = (if (level == SkillLevel.ELITE) 56f else 32f) to 38f + crestOf(level)

/**
 * A rank as a shield (Android: RankBadgeView), like a soldier's insignia in a game: a peaked gold rim, a glossy graphite
 * face and gold marks. One chevron band for Rank 1, two for Rank 2, three stars for Rank 3, a big star for Rank 4, and
 * for Rank 5 the big star with wings. The big-star ranks wear a stacked crest. [scale] sizes it to its medal.
 */
@Composable
fun RankBadge(level: SkillLevel, scale: Float = 1f, modifier: Modifier = Modifier) {
    val (width, height) = boxOf(level)
    val unit = SHIELD_WIDTH_DP / 32f * scale
    Canvas(modifier.size((width * unit).toInt().dp, (height * unit).toInt().dp)) { drawRank(level) }
}

private fun DrawScope.drawRank(level: SkillLevel) {
    val (width, height) = boxOf(level)
    val unit = minOf(size.width / width, size.height / height)
    val crest = crestOf(level)
    withTransform({
        scale(unit, unit, pivot = Offset.Zero)
        translate(0f, crest)
    }) {
        val rim = Brush.verticalGradient(listOf(GOLD_LIGHT, GOLD_DARK), startY = -6f, endY = 38f)
        val face = Brush.verticalGradient(listOf(FACE_TOP, FACE_BOTTOM), startY = 5f, endY = 33f)
        val mark = Brush.verticalGradient(listOf(GOLD_LIGHT, GOLD), startY = 8f, endY = 30f)
        val line = Stroke(0.7f, join = StrokeJoin.Round)
        fun gold(path: Path) {
            drawPath(path, rim)
            drawPath(path, GOLD_EDGE, style = line)
        }
        if (level == SkillLevel.ELITE) gold(wings(width))
        translate((width - 32f) / 2f, 0f) {
            if (crest > 0f) gold(Path().apply { CREST.take(if (level == SkillLevel.ELITE) 3 else 2).forEach { addPath(polygon(it)) } })
            val inner = polygon(INNER)
            gold(polygon(OUTER))
            drawPath(inner, face)
            clipPath(inner) {
                drawRect(GLOSS, Offset.Zero, Size(32f, 16f))
                val marks = when (level) {
                    SkillLevel.BEGINNER -> bands(19f)
                    SkillLevel.INTERMEDIATE -> bands(13.5f, 20.5f)
                    SkillLevel.ADVANCED -> star(11.2f, 14.5f, 4.6f).apply {
                        addPath(star(20.8f, 14.5f, 4.6f))
                        addPath(star(16f, 23f, 4.6f))
                    }
                    SkillLevel.MASTER -> star(16f, 18.5f, 9f)
                    SkillLevel.ELITE -> star(16f, 18.5f, 9.5f)
                    SkillLevel.NONE -> null
                }
                if (marks != null) {
                    drawPath(marks, mark)
                    drawPath(marks, GOLD_EDGE, style = line)
                }
            }
            drawPath(inner, GOLD_BEVEL, style = Stroke(1f, join = StrokeJoin.Round))
        }
    }
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

private fun star(cx: Float, cy: Float, outer: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else outer * 0.42f
        val angle = (-90.0 + 36.0 * i) * PI / 180.0
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Three feathers a side, sweeping up and out from behind the shield. */
private fun wings(width: Float): Path = Path().apply {
    for (feather in FEATHERS) for (mirror in listOf(false, true)) {
        fun x(value: Float) = if (mirror) width - value else value
        moveTo(x(feather[0]), feather[1])
        cubicTo(x(feather[2]), feather[3], x(feather[4]), feather[5], x(feather[6]), feather[7])
        cubicTo(x(feather[8]), feather[9], x(feather[10]), feather[11], x(feather[12]), feather[13])
        close()
    }
}
