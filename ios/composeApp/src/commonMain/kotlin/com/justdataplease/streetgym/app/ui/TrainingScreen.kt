package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.LevelCard
import com.justdataplease.streetgym.app.data.LevelState
import com.justdataplease.streetgym.app.data.Levels
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.empty_description
import com.justdataplease.streetgym.app.resources.exam_replay_body
import com.justdataplease.streetgym.app.resources.exam_replay_title
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.level_progress_format
import com.justdataplease.streetgym.app.resources.replay_card_body
import com.justdataplease.streetgym.app.resources.replay_card_body_complete
import com.justdataplease.streetgym.app.resources.replay_card_title
import com.justdataplease.streetgym.app.resources.replay_return
import com.justdataplease.streetgym.app.resources.theme_stamp_done
import com.justdataplease.streetgym.app.resources.training_tab_levels
import com.justdataplease.streetgym.app.resources.training_tab_parks
import com.justdataplease.streetgym.app.resources.training_tab_ready
import com.justdataplease.streetgym.app.resources.training_tab_workouts
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.blendColor
import org.jetbrains.compose.resources.stringResource

/** Training's four tabs, in Android's order (HomeActivity.trainingTab): Levels, My workouts, Library, Parks. */
private val TRAINING_TABS = listOf(Res.string.training_tab_levels, Res.string.training_tab_workouts,
    Res.string.training_tab_ready, Res.string.training_tab_parks)

/** Training's tabs by number. */
const val TRAINING_WORKOUTS = 1
const val TRAINING_LIBRARY = 2
const val TRAINING_PARKS = 3

/**
 * Training (android-streetgym, HomeActivity.renderLevels): the switch between its four tabs, kept while the app runs
 * ([tab], [onTab]), then Levels: the intro, the replay in effect, and the ten levels, dressed per theme. A level opens
 * its classes ([onOpenLevel]); returning from a replay goes back to Training ([onReturned]). My workouts lists the saved
 * and own days ([MyWorkouts]), the Library the ready workouts with their filters ([readyFilter], [ReadyWorkouts]) and
 * Parks the spots near the athlete ([ParksScreen]).
 */
@Composable
fun TrainingScreen(
    model: AppModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenLevel: (Int) -> Unit,
    onReturned: () -> Unit,
    workoutList: String? = null,
    onOpenList: (String?) -> Unit = {},
    onOpenDay: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onLimit: (Limit) -> Unit = {},
    onPaywall: (Paywall) -> Unit = {},
    readyFilter: ReadyFilter = ReadyFilter(),
    onReadyFilter: (ReadyFilter) -> Unit = {},
    onNote: (String) -> Unit = {},
) {
    val tabs: @Composable (Modifier) -> Unit = { modifier ->
        SegmentedSwitch(TRAINING_TABS.map { stringResource(it) }, tab, modifier, compact = true, onSelect = onTab)
    }
    // The Parks map fills the screen instead of scrolling with the page (Android: renderLevels' setBody(page)).
    if (tab == TRAINING_PARKS) {
        ParksScreen(model, tabs, onPaywall = onPaywall, onNote = onNote)
        return
    }
    Page {
        tabs(Modifier.padding(bottom = 14.dp))
        when (tab) {
            1 -> {
                MyWorkouts(model, workoutList, onOpenList, onOpenDay, onCreate, onLimit, onPaywall)
                return@Page
            }
            2 -> {
                ReadyWorkouts(model, readyFilter, onReadyFilter, onOpenDay, onPaywall)
                return@Page
            }
        }
        BodyText(stringResource(Res.string.empty_description), modifier = Modifier.padding(bottom = 16.dp))
        val levels = model.levels()
        levels.replay?.let {
            ReplayCard(levels, Modifier.padding(bottom = 12.dp)) {
                model.returnToFurthest()
                onReturned()
            }
        }
        levels.cards.forEachIndexed { index, card ->
            LevelCardView(card, index, first = index == 0, last = index == levels.cards.lastIndex) { onOpenLevel(card.level.number) }
        }
    }
}

/**
 * "Repeating from Level N" or "Repeating a block before its exam", where the athlete stopped, and Return to my
 * furthest day (Android: replayCard).
 */
@Composable
private fun ReplayCard(levels: Levels, modifier: Modifier = Modifier, onReturn: () -> Unit) {
    val replay = levels.replay ?: return
    val block = replay.fromProgramId != null
    val stopped = levels.furthest?.let { "${stringResource(Res.string.level_number, it.levelNumber)} · ${displayName(it)}" }
    StreetCard(modifier) {
        TitleText(if (block) stringResource(Res.string.exam_replay_title) else stringResource(Res.string.replay_card_title, replay.levelNumber), 18f)
        BodyText(
            when {
                stopped == null -> stringResource(Res.string.replay_card_body_complete)
                block -> stringResource(Res.string.exam_replay_body, stopped)
                else -> stringResource(Res.string.replay_card_body, stopped)
            },
            14f, Modifier.padding(top = 6.dp),
        )
        SecondaryButton(stringResource(Res.string.replay_return), Modifier.padding(top = 12.dp), onClick = onReturn)
    }
}

/**
 * A level's card (Android: levelCard), dressed for its state as ThemeDecor.decorateLevel dresses it: Concrete tapes the
 * current level in yellow with a hard shadow and dashes the levels ahead; Night Park strings the levels on a route;
 * Wheatpaste pastes them up as tilted posters, the current one blue, finished ones stamped DONE.
 */
@Composable
private fun LevelCardView(card: LevelCard, index: Int, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val border = theme.shape.borderDp.dp
    val name = displayName(card.level)
    val tier = levelTierName(card.level.number)
    val progress = stringResource(Res.string.level_progress_format, card.complete, card.level.programs.size)
    val spoken = "$name, $progress" + if (card.locked) ", $tier" else ""
    // The card's own surface, outline and padding; a tap anywhere opens the level.
    fun Modifier.frame(background: Modifier, padding: PaddingValues = PaddingValues(16.dp)): Modifier = this
        .fillMaxWidth()
        .clip(streetOutline(theme, 24.dp))
        .then(background)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = spoken }
        .padding(padding)
    val plain = Modifier.streetBackground(theme, palette.surface, 24.dp, palette.border, border)
    val dashed = Modifier.streetBackground(theme, Color.Transparent, 24.dp, palette.border, 2.dp, dash = 7.dp)
    when (theme) {
        StreetTheme.CONCRETE -> {
            val shadow = theme.shape.shadowDp.dp
            val (background, padding) = when (card.state) {
                // Tape yellow, outlined in ink, over a hard shadow down and right of it.
                LevelState.CURRENT -> Modifier.drawBehind {
                    val o = shadow.toPx()
                    val face = Size(size.width - o, size.height - o)
                    streetShape(theme, palette.border, 0f, topLeft = Offset(o, o), size = face)
                    streetShape(theme, palette.accent, 0f, palette.border, 2.dp.toPx(), size = face)
                } to PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp + shadow, bottom = 16.dp + shadow)
                LevelState.UPCOMING -> dashed to PaddingValues(16.dp)
                LevelState.DONE -> plain to PaddingValues(16.dp)
            }
            Column(Modifier.padding(bottom = 12.dp).frame(background, padding)) { LevelCardContent(card, name, tier, progress, poster = false) }
        }
        StreetTheme.NIGHT_PARK -> {
            val background = when (card.state) {
                LevelState.CURRENT -> Modifier.streetBackground(theme, palette.surface, 24.dp, palette.primary, 2.dp)
                LevelState.UPCOMING -> Modifier.streetBackground(theme, palette.background, 24.dp, palette.divider, 1.dp)
                LevelState.DONE -> plain
            }
            // The route runs in a 30 dp rail beside the card, as tall as the card; the 12 dp under it is a break.
            Column(
                Modifier.padding(bottom = 12.dp).fillMaxWidth()
                    .drawBehind { routeRail(card.state, first, last, palette.text, blendColor(palette.textSecondary, palette.background, 0.45f), palette.primary, palette.background) }
                    .padding(start = 40.dp),
            ) {
                Column(Modifier.frame(background)) { LevelCardContent(card, name, tier, progress, poster = false) }
            }
        }
        StreetTheme.WHEATPASTE -> {
            val poster = card.state == LevelState.CURRENT
            val background = when (card.state) {
                LevelState.CURRENT -> Modifier.streetBackground(theme, palette.primaryDark, 0.dp)
                LevelState.UPCOMING -> dashed
                LevelState.DONE -> plain
            }
            val stamp = stringResource(Res.string.theme_stamp_done)
            val measurer = rememberTextMeasurer()
            val face = Street.type.label
            Column(
                Modifier.padding(bottom = 12.dp)
                    .graphicsLayer { rotationZ = if (index % 2 == 0) -1f else 1f }
                    .drawWithContent {
                        drawContent()
                        if (card.state == LevelState.DONE) {
                            val text = measurer.measure(stamp, TextStyle(color = palette.primary, fontSize = 14.sp, fontFamily = face, letterSpacing = 0.08.em))
                            doneStamp(text, palette.primary)
                        }
                    }
                    .frame(background),
            ) { LevelCardContent(card, name, tier, progress, poster) }
        }
    }
}

/**
 * Inside a level's card: the number, the name with the tier's tag when locked, the description's first sentence, then
 * "N of 100 complete" and a bar. [poster]: on Wheatpaste's blue poster, in white with white plates.
 */
@Composable
private fun ColumnScope.LevelCardContent(card: LevelCard, name: String, tier: String, progress: String, poster: Boolean) {
    val palette = Street.colors
    val text = if (poster) palette.heroText else palette.text
    val quiet = if (poster) palette.heroText else palette.textSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        NumberBox(card.level.number.toString(), 44.dp, 20f, radius = 22.dp,
            fill = if (poster) palette.heroText else palette.primary, color = if (poster) palette.primaryDark else palette.onAccent)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            // The tag rides beside the title, so the description keeps the card's width.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleText(name, 19f, Modifier.weight(1f), color = text)
                if (card.locked) LockBadge(tier, Modifier.padding(start = 8.dp),
                    fill = if (poster) palette.heroText else palette.textSecondary, color = if (poster) palette.primaryDark else palette.onAccent)
            }
            // The list shows the first sentence; the level's own page all of it.
            val description = card.level.description.resolve().let { full -> if (". " in full) full.substringBefore(". ") + "." else full }
            if (description.isNotBlank()) BodyText(description, 13f, color = quiet)
        }
    }
    BodyText(progress, 13f, Modifier.padding(top = 12.dp), color = quiet)
    // On the poster Android tints the track 0x55FFFFFF, under the drawable's own disabled alpha.
    if (poster) ProgressBar(percentage(card.complete, card.level.programs.size), Modifier.padding(top = 7.dp),
        track = Color.White.copy(alpha = 0x55 / 255f * 0.26f), bar = palette.heroText)
    else ProgressBar(percentage(card.complete, card.level.programs.size), Modifier.padding(top = 7.dp))
}

/**
 * Night Park's route (Android: RouteRail) in a 30 dp rail at the left of [size]: a line through every level, solid as far
 * as the athlete has come, and a station 38 dp down: a filled disc with a tick when done, a ringed dot for the current
 * level, an open ring ahead.
 */
private fun DrawScope.routeRail(state: LevelState, first: Boolean, last: Boolean, reached: Color, ahead: Color, primary: Color, background: Color) {
    val unit = density
    val x = 15f * unit
    val y = 38f * unit
    val line = 2f * unit
    val dashes = PathEffect.dashPathEffect(floatArrayOf(6f * unit, 6f * unit))
    fun segment(from: Float, to: Float, solid: Boolean) =
        drawLine(if (solid) reached else ahead, Offset(x, from), Offset(x, to), line, pathEffect = if (solid) null else dashes)
    if (!first) segment(0f, y, solid = state != LevelState.UPCOMING)
    if (!last) segment(y, size.height, solid = state == LevelState.DONE)
    when (state) {
        LevelState.DONE -> {
            drawCircle(reached, 11f * unit, Offset(x, y))
            val tick = Path().apply {
                moveTo(x - 5f * unit, y)
                lineTo(x - 1.5f * unit, y + 3.5f * unit)
                lineTo(x + 5f * unit, y - 3.5f * unit)
            }
            drawPath(tick, background, style = Stroke(2.6f * unit))
        }
        LevelState.CURRENT -> {
            drawCircle(background, 12f * unit, Offset(x, y))
            drawCircle(primary, 10f * unit, Offset(x, y), style = Stroke(4f * unit))
            drawCircle(primary, 4f * unit, Offset(x, y))
        }
        LevelState.UPCOMING -> {
            drawCircle(background, 8f * unit, Offset(x, y))
            drawCircle(ahead, 8f * unit, Offset(x, y), style = Stroke(line))
        }
    }
}

/**
 * Wheatpaste's rubber stamp on a finished level (Android: StampDrawable): the word in a frame, tilted 12° up, its right
 * edge 14 dp in from the card's and its top 14 dp down.
 */
private fun DrawScope.doneStamp(text: androidx.compose.ui.text.TextLayoutResult, color: Color) {
    val unit = density
    val width = text.size.width.toFloat()
    val height = text.size.height.toFloat()
    val padX = 8f * unit
    val padY = 4f * unit
    val right = size.width - 14f * unit
    val top = 14f * unit
    rotate(-12f, pivot = Offset(right - width / 2f, top + height / 2f)) {
        drawRect(color, Offset(right - width - padX * 2f, top), Size(width + padX * 2f, height + padY * 2f), style = Stroke(2.5f * unit))
        drawText(text, topLeft = Offset(right - width - padX, top + padY))
    }
}
