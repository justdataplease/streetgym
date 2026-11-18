package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.StreakSnapshot
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.skills_close
import com.justdataplease.streetgym.app.resources.streak_best
import com.justdataplease.streetgym.app.resources.streak_best_only
import com.justdataplease.streetgym.app.resources.streak_best_short
import com.justdataplease.streetgym.app.resources.streak_rules
import com.justdataplease.streetgym.app.resources.streak_see_history
import com.justdataplease.streetgym.app.resources.streak_days
import com.justdataplease.streetgym.app.resources.streak_ended
import com.justdataplease.streetgym.app.resources.streak_ended_title
import com.justdataplease.streetgym.app.resources.streak_lit
import com.justdataplease.streetgym.app.resources.streak_none
import com.justdataplease.streetgym.app.resources.streak_none_title
import com.justdataplease.streetgym.app.resources.streak_rest
import com.justdataplease.streetgym.app.resources.streak_unlit
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.data.progress.Streak
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/*
 * The streak as Android draws it (android-streetgym, ui/StreakFlame.kt and ui/StreakUi.kt): the flame with the number
 * of days in its belly, and this week's seven days.
 */

/** Lit once today's workout is done; unlit, with the number kept, while the streak is alive; a dashed outline with none. */
enum class FlameState { LIT, UNLIT, NONE }

/** [outline] and [shadow] are null in a theme that prints neither; [dashed] draws only the outline. */
class FlameColors(val body: Color, val core: Color, val outline: Color?, val shadow: Color?, val number: Color, val dashed: Boolean)

fun flameState(streak: Streak): FlameState = when {
    streak.litToday -> FlameState.LIT
    streak.days > 0 -> FlameState.UNLIT
    else -> FlameState.NONE
}

fun flameColors(theme: StreetTheme, state: FlameState): FlameColors {
    val palette = theme.palette
    // Concrete and Wheatpaste outline everything in ink and print a hard shadow; Night Park does neither.
    val outlined = theme.shape.borderDp > 0
    val faint = blendColor(palette.textSecondary, palette.background, 0.45f)
    return when (state) {
        FlameState.LIT -> FlameColors(
            body = palette.accent,
            core = blendColor(palette.accent, Color.White, if (theme.dark) 0.35f else 0.63f),
            outline = palette.border.takeIf { outlined },
            shadow = when (theme) {
                StreetTheme.CONCRETE -> palette.border
                StreetTheme.WHEATPASTE -> palette.primary
                StreetTheme.NIGHT_PARK -> null
            },
            number = palette.ink,
            dashed = false,
        )
        FlameState.UNLIT -> FlameColors(palette.infoSurface, palette.surface, if (outlined) palette.border else palette.textSecondary,
            shadow = null, number = palette.text, dashed = false)
        FlameState.NONE -> FlameColors(Color.Transparent, Color.Transparent, faint, shadow = null, number = faint, dashed = true)
    }
}

/** The flame is drawn in a box 100 wide and 120 tall. */
private const val FLAME_BOX = 100f
private const val FLAME_HEIGHT_PER_WIDTH = 1.2f
/** The digits' box inside the belly, in the flame's own units. */
private const val NUMBER_CENTRE_X = 50.5f
private const val NUMBER_CENTRE_Y = 84f
private const val NUMBER_HEIGHT = 34f
private const val NUMBER_WIDTH = 48f

/** A main tongue and a small one on the left, over a wide belly for the number. */
private fun flameBody() = Path().apply {
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
private fun flameCore() = Path().apply {
    moveTo(50f, 110f)
    cubicTo(33f, 110f, 22f, 97f, 26f, 80f)
    cubicTo(28f, 71f, 34f, 66f, 38f, 60f)
    cubicTo(41f, 66f, 46f, 68f, 51f, 64f)
    cubicTo(55f, 71f, 75f, 76f, 75f, 91f)
    cubicTo(75f, 102f, 64f, 110f, 50f, 110f)
    close()
}

/**
 * Draws the flame [widthPx] wide from the top left. [number] null draws the plain flame, without its pale core.
 * [shadowPx] is used only where [colors] has a shadow. [digits] is how tall the face's digits are, in em.
 */
private fun DrawScope.drawFlame(widthPx: Float, number: String?, colors: FlameColors, shadowPx: Float,
                                measurer: TextMeasurer, face: FontFamily, digits: Float) {
    val stroke = if (colors.outline == null) 0f else min(2f * density, widthPx * 0.075f).coerceAtLeast(density)
    val scale = (widthPx - stroke) / FLAME_BOX
    val body = flameBody()
    // The paths are in the flame's own units; lines keep their width on screen.
    fun inFlame(dx: Float, dy: Float, draw: DrawScope.() -> Unit) = withTransform({
        translate(stroke / 2f + dx, stroke / 2f + dy)
        scale(scale, scale, pivot = Offset.Zero)
    }, draw)
    val line = stroke / scale
    if (colors.shadow != null && shadowPx > 0f) inFlame(shadowPx, shadowPx) {
        drawPath(body, colors.shadow, style = Fill)
        drawPath(body, colors.shadow, style = Stroke(line, join = StrokeJoin.Round))
    }
    inFlame(0f, 0f) {
        if (!colors.dashed) {
            drawPath(body, colors.body)
            if (number != null) drawPath(flameCore(), colors.core)
        }
        if (colors.outline != null) {
            val dash = if (colors.dashed) PathEffect.dashPathEffect(floatArrayOf(widthPx * 0.11f / scale, widthPx * 0.08f / scale)) else null
            drawPath(body, colors.outline, style = Stroke(line, join = StrokeJoin.Round, pathEffect = dash))
        }
    }
    if (number.isNullOrEmpty()) return
    // The number fills the belly: as tall as it allows, narrower when the digits (or a wide face) need it.
    val probe = measurer.measure(number, TextStyle(fontFamily = face, fontSize = 100f.toSp()), maxLines = 1)
    if (probe.size.width == 0) return
    val fit = min(NUMBER_HEIGHT * scale / (100f * digits), NUMBER_WIDTH * scale / probe.size.width)
    val text = measurer.measure(number, TextStyle(fontFamily = face, fontSize = (100f * fit).toSp(), color = colors.number), maxLines = 1)
    // Centred on the digits themselves: from the baseline up by their height.
    val centreY = text.firstBaseline - 100f * fit * digits / 2f
    drawText(text, topLeft = Offset(
        stroke / 2f + NUMBER_CENTRE_X * scale - text.size.width / 2f,
        stroke / 2f + NUMBER_CENTRE_Y * scale - centreY,
    ))
}

/** The flame, [width] wide in the active theme, with the streak's days in it; [numbered] false draws it plain. */
@Composable
fun StreakFlame(streak: Streak, width: Dp, modifier: Modifier = Modifier, shadow: Dp = 0.dp, numbered: Boolean = true) {
    val theme = Street.theme
    val colors = flameColors(theme, flameState(streak))
    val printed = if (colors.shadow != null) shadow else 0.dp
    val measurer = rememberTextMeasurer()
    val face = Street.type.display
    Canvas(modifier.size(width + printed, width * FLAME_HEIGHT_PER_WIDTH + printed)) {
        drawFlame(width.toPx(), streak.days.toString().takeIf { numbered }, colors, printed.toPx(), measurer, face, theme.displayDigits)
    }
}

/** How tall each theme's display face draws its digits, in em (measured from the font files). */
private val StreetTheme.displayDigits: Float get() = when (this) {
    StreetTheme.CONCRETE -> 0.884f
    StreetTheme.NIGHT_PARK -> 0.82f
    StreetTheme.WHEATPASTE -> 0.712f
}

/** Monday to Sunday, as [Streak.weekday] numbers them from 1. */
private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * The week's seven days as boxes with the weekday's letter: filled once trained, pale for a rest day the streak carried
 * on over, outlined for today, and faint for the rest. Today is underlined.
 */
@Composable
fun StreakWeek(days: List<Streak.Day>, box: Dp, gap: Dp, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    val measurer = rememberTextMeasurer()
    val face = Street.type.bold
    val outlined = theme.shape.borderDp > 0
    val faint = blendColor(palette.textSecondary, palette.background, 0.45f)
    val pale = blendColor(palette.accent, palette.background, 0.62f)
    val underlineGap = 4.dp
    val underline = 3.dp
    Canvas(modifier.size(box * days.size + gap * (days.size - 1), box + underlineGap + underline)) {
        val boxPx = box.toPx()
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        val inner = Size(boxPx - stroke, boxPx - stroke)
        days.forEachIndexed { index, day ->
            translate(left = index * (boxPx + gap.toPx())) {
                val letter: Color
                when {
                    day.trained -> {
                        drawRect(palette.accent, size = Size(boxPx, boxPx))
                        if (outlined) drawRect(palette.border, Offset(inset, inset), inner, style = Stroke(stroke))
                        letter = palette.contentOn(palette.accent)
                    }
                    day.rest -> {
                        drawRect(pale, size = Size(boxPx, boxPx))
                        drawRect(faint, Offset(inset, inset), inner, style = Stroke(stroke))
                        letter = palette.textSecondary
                    }
                    day.today -> {
                        drawRect(palette.surface, size = Size(boxPx, boxPx))
                        drawRect(palette.text, Offset(inset, inset), inner, style = Stroke(stroke))
                        letter = palette.text
                    }
                    else -> {
                        drawRect(faint, Offset(inset, inset), inner, style = Stroke(stroke))
                        letter = faint
                    }
                }
                val text = measurer.measure(
                    WEEKDAY_LETTERS[Streak.weekday(day.epochDay) - 1],
                    TextStyle(fontFamily = face, fontSize = (box * 0.3f + 4.dp).toSp(), color = letter), maxLines = 1,
                )
                drawText(text, topLeft = Offset((boxPx - text.size.width) / 2f, (boxPx - text.size.height) / 2f))
                if (day.today) drawRect(palette.text, Offset(0f, boxPx + underlineGap.toPx()), Size(boxPx, underline.toPx()))
            }
        }
    }
}

/** "12 day streak"; with none, "Streak ended" after two missed days or "No streak yet" before the first workout. */
@Composable
fun streakTitle(streak: Streak): String = when {
    streak.days > 0 -> stringResource(Res.string.streak_days, streak.days)
    streak.best > 0 -> stringResource(Res.string.streak_ended_title)
    else -> stringResource(Res.string.streak_none_title)
}

/** What today still asks: done, a rest day if wanted, train to keep it, or finish a workout to start one. */
@Composable
fun streakStatus(streak: Streak): String = stringResource(when {
    streak.litToday -> Res.string.streak_lit
    streak.restToday -> Res.string.streak_rest
    streak.days > 0 -> Res.string.streak_unlit
    streak.best > 0 -> Res.string.streak_ended
    else -> Res.string.streak_none
})

/**
 * The streak on Today's line: the flame with its number, then this week's seven days. [roomy] false (a narrow phone)
 * draws smaller boxes, so the line still fits.
 */
@Composable
fun StreakStrip(snapshot: StreakSnapshot, roomy: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val spoken = "${streakTitle(snapshot.streak)}. ${streakStatus(snapshot.streak)}"
    Row(
        modifier.heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakFlame(snapshot.streak, width = if (roomy) 33.dp else 29.dp, shadow = 2.dp)
        Spacer(Modifier.width(if (roomy) 9.dp else 7.dp))
        StreakWeek(snapshot.week, box = if (roomy) 24.dp else 20.dp, gap = if (roomy) 4.dp else 3.dp)
    }
}

/**
 * What a tap on the flame opens (Android: StreakUi.showStreakSheet): the big flame, "12 day streak", the best one, what
 * today asks, this week's days and the rule. [onHistory] adds See history, which opens History's calendar; History's own
 * line leaves it out.
 */
@Composable
fun StreakSheet(snapshot: StreakSnapshot, onHistory: (() -> Unit)?, onClose: () -> Unit) {
    val streak = snapshot.streak
    val close = DialogAction(stringResource(Res.string.skills_close), onClose)
    StreetDialog(
        title = null,
        onDismiss = onClose,
        confirm = onHistory?.let { DialogAction(stringResource(Res.string.streak_see_history), it) } ?: close,
        dismiss = close.takeIf { onHistory != null },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFlame(streak, width = 76.dp, shadow = 4.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    TitleText(streakTitle(streak), 24f)
                    if (streak.best > 0) BodyText(pluralStringResource(Res.plurals.streak_best, streak.best, streak.best), 14f,
                        Modifier.padding(top = 4.dp))
                    BodyText(streakStatus(streak), 14f, Modifier.padding(top = 4.dp), color = Street.colors.text)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.Center) {
                StreakWeek(snapshot.week, box = 30.dp, gap = 6.dp)
            }
            BodyText(stringResource(Res.string.streak_rules), 14f, Modifier.padding(top = 18.dp))
        }
    }
}

/**
 * History's line over the month (Android: StreakUi.streakLine): the plain flame, "12 DAY STREAK" on tape and "Best 20";
 * with no streak now, only "Best streak: N days". Nothing before the first workout. A tap opens the sheet.
 */
@Composable
fun StreakLine(streak: com.justdataplease.streetgym.data.progress.Streak, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (streak.best <= 0) return
    val theme = Street.theme
    val palette = theme.palette
    val best = pluralStringResource(Res.plurals.streak_best, streak.best, streak.best)
    val title = streakTitle(streak)
    val spoken = if (streak.days > 0) "$title. $best" else bestOnly(streak.best)
    Row(
        modifier.heightIn(min = 40.dp).clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (streak.days > 0) {
            StreakFlame(streak, width = 18.dp, shadow = 2.dp, numbered = false)
            BasicText(
                title.uppercase(),
                Modifier.padding(start = 8.dp).background(palette.accent).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(color = palette.contentOn(palette.accent), fontSize = 13.sp, fontFamily = Street.type.display,
                    letterSpacing = theme.shape.labelSpacing.em),
                maxLines = 1,
            )
            BodyText(stringResource(Res.string.streak_best_short, streak.best), 13f, Modifier.padding(start = 8.dp), bold = true)
        } else {
            BodyText(bestOnly(streak.best), 13f)
        }
    }
}

@Composable
private fun bestOnly(best: Int): String = pluralStringResource(Res.plurals.streak_best_only, best, best)
