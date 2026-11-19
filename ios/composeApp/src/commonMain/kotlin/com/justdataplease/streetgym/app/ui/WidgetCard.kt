package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.StreetGymTheme
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.NextClassCard
import com.justdataplease.streetgym.app.data.WidgetSnapshot
import com.justdataplease.streetgym.app.data.WidgetWords
import com.justdataplease.streetgym.app.figure.AnimationLibrary
import com.justdataplease.streetgym.app.figure.BadgeStyle
import com.justdataplease.streetgym.app.figure.FigureRenderer
import com.justdataplease.streetgym.app.figure.FigureText
import com.justdataplease.streetgym.app.figure.LocalMotionLibrary
import com.justdataplease.streetgym.app.figure.LocalMotionWords
import com.justdataplease.streetgym.app.figure.MotionText
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.ic_lock
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.blendColor
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/*
 * The "Next class" widget drawn in Compose from the snapshot Swift draws it from, so its looks are seen on Windows
 * (`:composeApp:screenshots`, widget-*.png), and the tile's drawing the snapshot carries. On iPhone the widget itself is
 * SwiftUI (iosApp/StreetGymWidget/StreetGymWidget.swift), measure for measure this card.
 */

/** A medium widget on an iPhone 17 Pro Max's home screen, in points, and its corner. */
val WIDGET_WIDTH = 364.dp
val WIDGET_HEIGHT = 170.dp
private val WIDGET_CORNER = 22.dp

/**
 * The lead movement's still pose on its tile, at 3x (Android: WidgetRenderer.frames with animations off, the one
 * drawing a still widget holds): the exercise picker's tile, the figure in the theme's main colour on the info surface.
 * [words] name the guide's phases; [text] measures them (a tile draws none).
 */
fun widgetFigure(card: NextClassCard, theme: StreetTheme, library: AnimationLibrary, words: (MotionText) -> String, text: FigureText): ImageBitmap {
    val side = (WidgetSnapshot.FIGURE_PT * WidgetSnapshot.FIGURE_SCALE).toInt()
    val image = ImageBitmap(side, side)
    val palette = theme.palette
    val surface = palette.infoSurface
    val renderer = FigureRenderer(library, palette, WidgetSnapshot.FIGURE_SCALE, words).apply {
        bind(card.movementId, card.movementName.ifBlank { card.role }, card.category, card.equipment)
        setBadgeStyle(BadgeStyle(surface, palette.primary, blendColor(palette.primary, surface, 0.5f),
            blendColor(palette.textSecondary, surface, 0.4f)), moving = true)
    }
    CanvasDrawScope().draw(Density(WidgetSnapshot.FIGURE_SCALE), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
        drawRect(surface)
        renderer.draw(this, text, renderer.loopStartPhase, 0, playing = false)
    }
    return image
}

/** The tile's drawing for [card] in [theme], measured with [measurer] in the [family] face. */
fun widgetFigure(card: NextClassCard, theme: StreetTheme, library: AnimationLibrary, words: Map<MotionText, String>,
                 measurer: TextMeasurer, family: FontFamily): ImageBitmap =
    widgetFigure(card, theme, library, { words[it].orEmpty() }, FigureText(measurer, family, Density(WidgetSnapshot.FIGURE_SCALE)))

/** How long the widget waits for a burst of changes to settle before it is written (Android: WidgetUpdates.SETTLE_MS). */
private const val SETTLE_MS = 600L

/**
 * Keeps the widget in step with the app while it runs (Android: NextClassWidget.watch): the snapshot is read again on
 * every change of what it shows, and written once the changes settle. Put inside the app's theme.
 */
@Composable
internal fun WidgetUpdates(model: AppModel) {
    val words by produceState<WidgetWords?>(null) { value = runCatching { WidgetWords.load() }.getOrNull() }
    val measurer = rememberTextMeasurer()
    // The line man's own face (Roboto Bold), as on every other figure; the widget's still tile draws no words.
    val family = Street.type.systemBold
    val library = LocalMotionLibrary.current
    val guideWords = LocalMotionWords.current
    LaunchedEffect(model, words, family) {
        val loaded = words ?: return@LaunchedEffect
        snapshotFlow { model.widgetSnapshot(loaded) }.collect { snapshot ->
            delay(SETTLE_MS)
            model.widgetFeed.offer(
                snapshot,
                figure = {
                    runCatching {
                        widgetFigure(snapshot.card, snapshot.theme, library ?: AnimationLibrary.fallbackLibrary(), guideWords, measurer, family)
                    }.getOrNull()
                },
                font = { file -> com.justdataplease.streetgym.app.resources.Res.readBytes("font/$file") },
            )
        }
    }
}

/**
 * The widget as the home screen shows it: a medium widget [WIDGET_WIDTH] by [WIDGET_HEIGHT] in the page's colour with
 * Android's card on it ([NextClassWidgetCard]), showing the flame [flame] of the snapshot's days.
 */
@Composable
fun WidgetPreview(snapshot: WidgetSnapshot, figure: ImageBitmap?, flame: Int = 0, modifier: Modifier = Modifier) {
    StreetGymTheme(snapshot.theme) {
        val palette = snapshot.theme.palette
        Box(
            modifier.size(WIDGET_WIDTH, WIDGET_HEIGHT).clip(RoundedCornerShape(WIDGET_CORNER)).background(palette.background)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            NextClassWidgetCard(snapshot, figure, flame)
        }
    }
}

/**
 * Android's card (res/layout/widget_next_class.xml): a 96-point card with a 2-point outline and a hard 4-point shadow
 * down and right (Wheatpaste's in its pink), the tile with the figure, the tape and "LEVEL 4 · CLASS 20", the headline
 * in the theme's display face, the third line, and the streak's flame, or the lock square on a class beyond the tier.
 */
@Composable
fun NextClassWidgetCard(snapshot: WidgetSnapshot, figure: ImageBitmap?, flame: Int = 0, modifier: Modifier = Modifier) {
    val theme = snapshot.theme
    val palette = theme.palette
    val shadow = if (theme == StreetTheme.WHEATPASTE) palette.spot else palette.border
    val day = snapshot.flames.getOrNull(flame) ?: snapshot.flames.firstOrNull()
    val scale = LocalDensity.current.fontScale
    Box(modifier.fillMaxWidth().height(100.dp).clearAndSetSemantics { day?.let { contentDescription = it.spoken } }) {
        Box(Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp).background(shadow))
        Row(
            Modifier.matchParentSize().padding(end = 4.dp, bottom = 4.dp).background(palette.border).padding(2.dp)
                .background(palette.surface).padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(76.dp).background(palette.border).padding(2.dp).background(palette.infoSurface)) {
                if (figure != null) Image(figure, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 12.dp)) {
              val room = maxWidth
              Column(Modifier.fillMaxWidth()) {
                val tapeStyle = TextStyle(color = palette.contentOn(palette.accent), fontSize = (11f / scale).sp,
                    fontFamily = Street.type.display, letterSpacing = theme.shape.labelSpacing.em)
                // The meta and detail lines are sans-serif in Android's widget layout: Roboto Bold and Regular.
                val metaStyle = TextStyle(color = palette.textSecondary, fontSize = 12.sp, fontFamily = Street.type.systemBold, letterSpacing = 0.06.em)
                // "LEVEL 10 · CLASS 100" keeps its numbers: the tape steps aside first, then the line gets smaller
                // (Android: WidgetLayout.fitMeta); SwiftUI's ViewThatFits does the same on the phone.
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val column = with(density) { room.toPx() }
                val needed = measurer.measure(snapshot.text.meta, metaStyle, maxLines = 1).size.width.toFloat()
                val tapeWidth = measurer.measure(snapshot.text.tape.uppercase(), tapeStyle, maxLines = 1).size.width +
                    with(density) { (12.dp + 6.dp).toPx() }
                val showTape = snapshot.text.meta.isEmpty() || needed <= column - tapeWidth
                val metaScale = if (needed <= column || needed == 0f) 1f else (column / needed).coerceIn(0.75f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The tape: the display face in capitals, letter-spaced, on the accent, 17 points tall.
                    if (showTape) Box(Modifier.background(palette.accent).height(17.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center) {
                        BasicText(snapshot.text.tape.uppercase(), style = tapeStyle, maxLines = 1)
                    }
                    if (snapshot.text.meta.isNotEmpty()) BasicText(
                        snapshot.text.meta,
                        Modifier.padding(start = if (showTape) 6.dp else 0.dp),
                        style = metaStyle.copy(fontSize = (12f * metaScale).sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val title = snapshot.text.headline.let { if (theme.shape.upperTitles) it.uppercase() else it }
                BasicText(
                    title,
                    Modifier.padding(top = 3.dp),
                    style = TextStyle(color = palette.text, fontSize = (WidgetSnapshot.TITLE_PT * theme.shape.titleScale /
                        WidgetSnapshot.TITLE_SCALE_BASE / scale).sp, fontFamily = Street.type.display),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snapshot.text.detail.isNotEmpty()) BasicText(
                    snapshot.text.detail,
                    Modifier.padding(top = 3.dp),
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.5.sp, fontFamily = Street.type.system),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
              }
            }
            if (snapshot.card.locked) {
                // The lock square: the action colour in a 2-point outline, the lock in the colour that reads on it.
                Box(Modifier.size(44.dp).background(palette.border).padding(2.dp).background(palette.action), contentAlignment = Alignment.Center) {
                    Image(painterResource(Res.drawable.ic_lock), contentDescription = null,
                        colorFilter = ColorFilter.tint(palette.contentOn(palette.action)), modifier = Modifier.size(18.dp))
                }
            } else if (day != null) {
                // The flame, 46 points wide with the theme's 3-point printed shadow, in a 58-point slot (Android: fitCenter).
                Box(Modifier.height(58.dp), contentAlignment = Alignment.Center) {
                    StreakFlame(day.streak, 46.dp * (58f / 58.2f), shadow = 3.dp)
                }
            }
        }
    }
}

/** A row of the widget's looks for the review pictures: each of [snapshots] with its figure, one under another. */
@Composable
fun WidgetSheet(rows: List<Pair<WidgetSnapshot, ImageBitmap?>>, flames: List<Int> = List(rows.size) { 0 }) {
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF8E8E93)).padding(20.dp)) {
        rows.forEachIndexed { index, (snapshot, figure) ->
            WidgetPreview(snapshot, figure, flames.getOrElse(index) { 0 }, Modifier.padding(bottom = 18.dp))
        }
    }
}
