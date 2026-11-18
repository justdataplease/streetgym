package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Finished
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.FocusState
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.abandon
import com.justdataplease.streetgym.app.resources.abandon_message
import com.justdataplease.streetgym.app.resources.abandon_title
import com.justdataplease.streetgym.app.resources.active_sets_progress
import com.justdataplease.streetgym.app.resources.back
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.cool_down
import com.justdataplease.streetgym.app.resources.effort_item
import com.justdataplease.streetgym.app.resources.effort_title
import com.justdataplease.streetgym.app.resources.finish
import com.justdataplease.streetgym.app.resources.finish_early_message
import com.justdataplease.streetgym.app.resources.finish_early_title
import com.justdataplease.streetgym.app.resources.finish_workout
import com.justdataplease.streetgym.app.resources.focus_add_rest
import com.justdataplease.streetgym.app.resources.focus_add_rest_description
import com.justdataplease.streetgym.app.resources.focus_back_description
import com.justdataplease.streetgym.app.resources.focus_close
import com.justdataplease.streetgym.app.resources.focus_details
import com.justdataplease.streetgym.app.resources.focus_details_easier
import com.justdataplease.streetgym.app.resources.focus_details_safety
import com.justdataplease.streetgym.app.resources.focus_details_start
import com.justdataplease.streetgym.app.resources.focus_details_tempo
import com.justdataplease.streetgym.app.resources.focus_do_skipped
import com.justdataplease.streetgym.app.resources.focus_done
import com.justdataplease.streetgym.app.resources.focus_done_description
import com.justdataplease.streetgym.app.resources.focus_easier
import com.justdataplease.streetgym.app.resources.focus_easier_description
import com.justdataplease.streetgym.app.resources.focus_easier_generic
import com.justdataplease.streetgym.app.resources.focus_rest
import com.justdataplease.streetgym.app.resources.focus_rest_next
import com.justdataplease.streetgym.app.resources.focus_seconds
import com.justdataplease.streetgym.app.resources.focus_set_of
import com.justdataplease.streetgym.app.resources.focus_side
import com.justdataplease.streetgym.app.resources.focus_skip
import com.justdataplease.streetgym.app.resources.focus_skip_description
import com.justdataplease.streetgym.app.resources.focus_skip_rest
import com.justdataplease.streetgym.app.resources.focus_start
import com.justdataplease.streetgym.app.resources.focus_start_description
import com.justdataplease.streetgym.app.resources.focus_start_side
import com.justdataplease.streetgym.app.resources.focus_summary_complete
import com.justdataplease.streetgym.app.resources.focus_summary_partial
import com.justdataplease.streetgym.app.resources.focus_summary_skipped
import com.justdataplease.streetgym.app.resources.main_training
import com.justdataplease.streetgym.app.resources.maximum_repetitions
import com.justdataplease.streetgym.app.resources.readiness_note
import com.justdataplease.streetgym.app.resources.repetitions_format
import com.justdataplease.streetgym.app.resources.set_done_ask
import com.justdataplease.streetgym.app.resources.set_done_log_reps
import com.justdataplease.streetgym.app.resources.set_done_log_seconds
import com.justdataplease.streetgym.app.resources.set_done_log_window
import com.justdataplease.streetgym.app.resources.set_done_message_hold
import com.justdataplease.streetgym.app.resources.set_done_message_reps
import com.justdataplease.streetgym.app.resources.set_done_message_window
import com.justdataplease.streetgym.app.resources.set_done_plan
import com.justdataplease.streetgym.app.resources.set_done_reps
import com.justdataplease.streetgym.app.resources.set_done_reps_hint
import com.justdataplease.streetgym.app.resources.set_done_save
import com.justdataplease.streetgym.app.resources.set_done_seconds
import com.justdataplease.streetgym.app.resources.set_done_seconds_hint
import com.justdataplease.streetgym.app.resources.set_done_title
import com.justdataplease.streetgym.app.resources.set_number
import com.justdataplease.streetgym.app.resources.skill_practice
import com.justdataplease.streetgym.app.resources.started_minutes_ago
import com.justdataplease.streetgym.app.resources.warm_up
import com.justdataplease.streetgym.app.resources.workout_easier_off
import com.justdataplease.streetgym.app.resources.workout_easier_off_description
import com.justdataplease.streetgym.app.resources.workout_easier_on
import com.justdataplease.streetgym.app.resources.workout_easier_on_description
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.ui.ExerciseNotes
import com.justdataplease.streetgym.ui.SetMeasure
import com.justdataplease.streetgym.ui.SetNotes
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.ui.WorkoutStep
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/*
 * The focus workout (android-streetgym, ui/FocusWorkoutView.kt): one set per screen, a full-screen rest after it, and
 * a summary that leads into finishing. Every screen is strip, fill, info and controls: the controls are never pushed
 * off, the fill (the exercise's panel, or the rest's countdown) takes the height that is left, and the info is cut
 * from its bottom before anything else gives way.
 */

/** How often the screen looks at the clock, as Android's view does. */
private const val TICK_MILLIS = 250L

/** A question the workout asks over itself, in either view. */
internal sealed interface Ask {
    /** The movement's notes, tempo, safety, easier option and start criteria. */
    data class Details(val index: Int) : Ask
    /**
     * What the athlete did in the set at [index]; [thenDone] moves on after it (Done · Next on a set without a number),
     * [thenTick] ticks it in the list when it isn't yet (Android: logDone).
     */
    data class Log(val index: Int, val thenDone: Boolean, val thenTick: Boolean = false) : Ask
    data object FinishEarly : Ask
    data object Effort : Ask
    data object End : Ask
    /** "Complete day" from the list: the class marked done without training. */
    data object CompleteDay : Ask
}

/**
 * The class in progress, in the view the athlete picked (Android: HomeActivity's Screen.Active, renderFocus or
 * renderActive): Focus, set by set, or the List of every set with its tick. Both read and change the same training, so
 * switching keeps every tick, the rest and the position. [askEffort] opens it on the effort question (for the
 * screenshots). [onFinished] gets what finishing saved, [onEnded] follows "End session", [onCompleted] follows
 * "Complete day", and [onGone] leaves when no class is in progress.
 */
@Composable
fun WorkoutScreen(
    model: AppModel,
    askEffort: Boolean = false,
    onFinished: (Finished) -> Unit,
    onEnded: () -> Unit,
    onCompleted: () -> Unit,
    onGone: () -> Unit,
) {
    val training = model.training
    DisposableEffect(model) {
        model.device.keepScreenOn(true)
        onDispose { model.device.keepScreenOn(false) }
    }
    LaunchedEffect(training) {
        while (true) {
            training.tick()
            delay(TICK_MILLIS)
        }
    }
    val state = training.state()
    if (state == null) {
        LaunchedEffect(Unit) { onGone() }
        return
    }
    var ask by remember { mutableStateOf<Ask?>(if (askEffort) Ask.Effort else null) }
    fun close() { ask = null }
    // Finishing early asks first; a fully ticked class goes straight to the effort question (Android: requestFinish).
    fun finish() {
        val now = training.state() ?: return
        ask = if (now.flow.completedCount(now.active.completedSetIds) < now.flow.size) Ask.FinishEarly else Ask.Effort
    }

    if (model.workoutView == WorkoutView.LIST) {
        // The list scrolls over the page's grain, as Android's does; the focus view paints its own flat background.
        WorkoutList(state, training, onAsk = { ask = it }, onFinish = ::finish)
    } else Box(Modifier.fillMaxSize().background(Street.colors.background)) {
        when (val page = state.page) {
            is FocusPage.Set -> SetPage(state, page.index, training, onAsk = { ask = it }, onFinish = ::finish)
            is FocusPage.Rest -> RestPage(state, page, training, onFinish = ::finish)
            FocusPage.Summary -> SummaryPage(state, training, onFinish = ::finish)
        }
    }

    when (val open = ask) {
        is Ask.Details -> DetailsSheet(state, open.index, onClose = ::close)
        is Ask.Log -> {
            val step = state.flow[open.index]
            LogDialog(training.measure(step), state.active.done[step.key], onClose = ::close) { amount ->
                training.setDone(step.key, amount)
                if (open.thenDone) training.done(open.index)
                else if (open.thenTick && step.key !in training.active()?.completedSetIds.orEmpty()) training.tick(step.key, true)
            }
        }
        Ask.FinishEarly -> StreetDialog(
            title = stringResource(Res.string.finish_early_title),
            message = stringResource(Res.string.finish_early_message),
            onDismiss = ::close,
            confirm = DialogAction(stringResource(Res.string.finish)) { ask = Ask.Effort },
            dismiss = DialogAction(stringResource(Res.string.cancel), ::close),
        )
        Ask.Effort -> EffortDialog(state.program.targetRpe, onClose = ::close) { effort ->
            close()
            training.finish(effort)?.let(onFinished)
        }
        Ask.End -> StreetDialog(
            title = stringResource(Res.string.abandon_title),
            message = stringResource(Res.string.abandon_message),
            onDismiss = ::close,
            confirm = DialogAction(stringResource(Res.string.abandon)) {
                close()
                training.abandon()
                onEnded()
            },
            dismiss = DialogAction(stringResource(Res.string.cancel), ::close),
        )
        Ask.CompleteDay -> CompleteDayDialog(state.program, onClose = ::close) {
            close()
            model.completeDay(state.program.id)
            onCompleted()
        }
        null -> Unit
    }
}

/** The set at [index]: the panel, the exercise and its dose, then Back, the main action and Skip. */
@Composable
private fun SetPage(state: FocusState, index: Int, training: Training, onAsk: (Ask) -> Unit, onFinish: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val step = state.flow[index]
    val name = displayName(step.exercise)
    val notes = ExerciseNotes.parse(step.exercise.description.resolve())
    val (dose, cues) = prescription(step)
    val seconds = holdSeconds(step)
    val rounds = step.sides
    val current = state.hold?.takeIf { it.key == step.key }
    val running = current != null && current.endsAt > state.now
    // The countdown round the main button starts; null means it offers Done.
    val startRound = when {
        seconds == null -> null
        current == null -> 1
        !running && current.round < rounds -> current.round + 1
        else -> null
    }
    val side = if (seconds != null && rounds > 1) startRound ?: current?.round ?: 1 else null
    val variation = step.set.name.resolve().ifBlank { if (step.exercise.movementId == null) notes.description else "" }
    val cue = (listOf(variation) + cues).filter(String::isNotBlank).joinToString(" · ")
    val setLine = listOfNotNull(
        stringResource(Res.string.focus_set_of, step.setNumber, step.setCount),
        side?.let { stringResource(Res.string.focus_side, it) },
    ).joinToString(" · ")
    val big = current?.let { clockText(it.endsAt - state.now, roundUp = true) } ?: dose

    FocusFrame(
        strip = { Strip(sectionTitle(step.section), stringResource(Res.string.focus_set_of, index + 1, state.flow.size), state, index, onFinish) },
        // The exercise's line man, bound as Android's focus view binds it: the set's own name, else the movement's notes.
        fill = { modifier ->
            ExerciseFigure(
                step.exercise.movementId, name, modifier, category = step.exercise.category, equipment = step.exercise.equipment,
                descriptor = step.set.name.resolve().ifBlank { step.exercise.description.resolve() },
            )
        },
        info = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleText(name, 20f, Modifier.weight(1f), maxLines = 2)
                    TextAction(stringResource(Res.string.focus_details)) { onAsk(Ask.Details(index)) }
                }
                BodyText(setLine, 14f, maxLines = 1)
                // Night Park stencils the dose in its light colour; a running countdown is in the timer face.
                val nightPark = theme == StreetTheme.NIGHT_PARK
                TitleText(
                    big, largeSize(big), Modifier.padding(top = 4.dp),
                    color = if (nightPark) palette.primary else palette.text,
                    maxLines = 2,
                    face = when {
                        current != null -> TitleFace.TIMER
                        nightPark -> TitleFace.NUMERAL
                        else -> TitleFace.DISPLAY
                    },
                )
                if (cue.isNotBlank()) BodyText(cue, 14f, Modifier.padding(top = 2.dp), maxLines = 1)
                if (step.credited) TextAction(setDoneLabel(training.measure(step), state.active.done[step.key]),
                    Modifier.padding(top = 2.dp)) { onAsk(Ask.Log(index, thenDone = false)) }
                val option = step.exercise.easier?.takeIf { step.section in WorkoutFlow.CREDITED_SECTIONS && !step.exercise.readinessCheck }
                if (option == null) {
                    val easier = notes.easier.ifBlank { stringResource(Res.string.focus_easier_generic) }
                    Chip(stringResource(Res.string.focus_easier, easier), Modifier.padding(top = 8.dp),
                        description = stringResource(Res.string.focus_easier_description, easier)) { onAsk(Ask.Details(index)) }
                } else {
                    val on = step.exerciseKey in state.active.easierExercises
                    val optionName = option.name.resolve()
                    Chip(
                        stringResource(if (on) Res.string.workout_easier_on else Res.string.workout_easier_off, optionName),
                        Modifier.padding(top = 8.dp), on = on, maxLines = 2,
                        description = stringResource(if (on) Res.string.workout_easier_on_description
                            else Res.string.workout_easier_off_description, optionName),
                    ) { training.setEasier(step.exerciseKey, !on) }
                }
            }
        },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val canGoBack = state.flow.previous(index) != null
                SecondaryButton(stringResource(Res.string.back), Modifier.alpha(if (canGoBack) 1f else 0.4f), enabled = canGoBack,
                    side = true, description = stringResource(Res.string.focus_back_description)) { training.back(index) }
                if (startRound != null && seconds != null) {
                    PrimaryButton(
                        if (startRound == 1) stringResource(Res.string.focus_start, seconds) else stringResource(Res.string.focus_start_side, startRound),
                        Modifier.weight(1f).padding(horizontal = 8.dp), thumb = true,
                        description = stringResource(Res.string.focus_start_description, seconds, step.setNumber, step.setCount, name),
                    ) { training.startHold(index, startRound, seconds) }
                } else {
                    val measure = training.measure(step)
                    PrimaryButton(
                        stringResource(Res.string.focus_done), Modifier.weight(1f).padding(horizontal = 8.dp), thumb = true,
                        description = stringResource(Res.string.focus_done_description, step.setNumber, step.setCount, name),
                    ) {
                        // A "max" set, a practice window or an easier option measured the other way has no number to
                        // count until the athlete gives it.
                        if (step.credited && measure.planned == null && step.key !in state.active.done) onAsk(Ask.Log(index, thenDone = true))
                        else training.done(index)
                    }
                }
                SecondaryButton(stringResource(Res.string.focus_skip), side = true,
                    description = stringResource(Res.string.focus_skip_description, step.setNumber, step.setCount, name)) { training.skip(index) }
            }
        },
    )
}

/** The rest before the set at [page]'s index: its countdown, what comes next, +15 s and Skip rest. */
@Composable
private fun RestPage(state: FocusState, page: FocusPage.Rest, training: Training, onFinish: () -> Unit) {
    val step = state.flow[page.index]
    FocusFrame(
        strip = { Strip(sectionTitle(step.section), stringResource(Res.string.focus_set_of, page.index + 1, state.flow.size), state, page.index, onFinish) },
        fill = { modifier ->
            Column(modifier) {
                SectionLabel(stringResource(Res.string.focus_rest), Modifier.fillMaxWidth(), align = TextAlign.Center)
                Countdown(clockText(page.endsAt - state.now, roundUp = true), Modifier.weight(1f).fillMaxWidth())
            }
        },
        info = {
            BodyText(stringResource(Res.string.focus_rest_next, displayName(step.exercise), step.setNumber), 16f, Modifier.fillMaxWidth(),
                color = Street.colors.text, maxLines = 2, align = TextAlign.Center)
        },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(stringResource(Res.string.focus_add_rest), side = true,
                    description = stringResource(Res.string.focus_add_rest_description)) { training.addRest(ADD_REST_SECONDS) }
                PrimaryButton(stringResource(Res.string.focus_skip_rest), Modifier.weight(1f).padding(start = 8.dp), thumb = true) {
                    training.skipRest()
                }
            }
        },
    )
}

/** "+15 s" on the rest screen. */
private const val ADD_REST_SECONDS = 15

/**
 * The end of the class: how it went, Finish workout, Back to the last set and the skipped sets. As on Android a session
 * is ended from the list view (the title bar's list icon), not from here.
 */
@Composable
private fun SummaryPage(state: FocusState, training: Training, onFinish: () -> Unit) {
    val completed = state.active.completedSetIds
    val done = state.flow.completedCount(completed)
    val missed = state.flow.keys.count { it in state.skipped && it !in completed }
    val minutes = ((state.now - state.active.startedAtEpochMillis) / 60_000L).coerceAtLeast(0L).toInt()
    FocusFrame(
        strip = { Strip(displayName(state.program), stringResource(Res.string.active_sets_progress, done, state.flow.size), state, null, null) },
        fill = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.Center) {
                TitleText(stringResource(if (done == state.flow.size) Res.string.focus_summary_complete else Res.string.focus_summary_partial),
                    28f, Modifier.fillMaxWidth(), align = TextAlign.Center)
                BodyText(stringResource(Res.string.started_minutes_ago, minutes), 15f, Modifier.fillMaxWidth().padding(top = 6.dp),
                    align = TextAlign.Center)
                if (missed > 0) BodyText(pluralStringResource(Res.plurals.focus_summary_skipped, missed, missed), 15f,
                    Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        },
        info = null,
        controls = {
            Column {
                PrimaryButton(stringResource(Res.string.finish_workout), thumb = true, onClick = onFinish)
                Row(Modifier.padding(top = 8.dp)) {
                    SecondaryButton(stringResource(Res.string.back), Modifier.weight(1f), enabled = state.flow.size > 0,
                        description = stringResource(Res.string.focus_back_description)) { training.back(null) }
                    if (missed > 0) SecondaryButton(stringResource(Res.string.focus_do_skipped), Modifier.weight(1f).padding(start = 8.dp)) {
                        training.doSkipped()
                    }
                }
            }
        },
    )
}

/** Strip, fill, info and controls (Android: FocusWorkoutView.screen, portrait). */
@Composable
private fun FocusFrame(
    strip: @Composable () -> Unit,
    fill: @Composable ColumnScope.(Modifier) -> Unit,
    info: (@Composable () -> Unit)?,
    controls: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)) {
        strip()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            fill(Modifier.padding(top = 8.dp).weight(1f).fillMaxWidth())
            if (info != null) Box(Modifier.padding(top = 10.dp).fillMaxWidth().clipToBounds()) { info() }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth()) { controls() }
    }
}

/** The section's name and Finish, then the class's sets as a strip and "Set 7 of 27". */
@Composable
private fun Strip(title: String, position: String, state: FocusState, current: Int?, onFinish: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title, Modifier.weight(1f), padding = 4.dp)
            if (onFinish != null) TextAction(stringResource(Res.string.finish), onClick = onFinish)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetProgress(state.flow.keys.map { it in state.active.completedSetIds }, current, Modifier.weight(1f))
            BodyText(position, 13f, Modifier.padding(start = 10.dp), bold = true, maxLines = 1)
        }
    }
}

/** How tall the class's strip is in each theme, in dp (Android: SetProgressView.heightDp). */
private fun progressHeight(theme: StreetTheme): Dp = when (theme) {
    StreetTheme.CONCRETE -> 10.dp
    StreetTheme.NIGHT_PARK -> 12.dp
    StreetTheme.WHEATPASTE -> 22.dp
}

/**
 * The class's progress (Android: SetProgressView): Concrete's row of set boxes, Night Park's ticked track and
 * Wheatpaste's tally of finished sets. [completed] holds one flag per set in class order; [current] is the set shown.
 */
@Composable
fun SetProgress(completed: List<Boolean>, current: Int?, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    Canvas(modifier.fillMaxWidth().height(progressHeight(theme))) {
        val count = completed.size
        if (count == 0) return@Canvas
        when (theme) {
            StreetTheme.CONCRETE -> {
                val gap = if (count > 40) density else 2f * density
                val cell = (size.width - gap * (count - 1)) / count
                val stroke = 1.5f * density
                for (index in 0 until count) {
                    val topLeft = Offset(index * (cell + gap) + stroke / 2f, stroke / 2f)
                    val box = Size(cell - stroke, size.height - stroke)
                    when {
                        completed[index] -> drawRect(palette.border, topLeft, box)
                        index == current -> drawRect(palette.accent, topLeft, box)
                    }
                    drawRect(palette.border, topLeft, box, style = Stroke(stroke))
                }
            }
            StreetTheme.NIGHT_PARK -> {
                val mid = size.height / 2f
                val thick = 4f * density
                drawRect(palette.divider, Offset(0f, mid - thick / 2f), Size(size.width, thick))
                val gap = if (count <= 60) 2f * density else 0f
                val cell = (size.width - gap * (count - 1)) / count
                for (index in 0 until count) if (completed[index]) {
                    drawRect(palette.primary, Offset(index * (cell + gap), mid - thick / 2f), Size(cell, thick))
                }
                current?.takeIf { it in 0 until count }?.let { index ->
                    val centre = index * (cell + gap) + cell / 2f
                    drawRect(palette.text, Offset(centre - 1.5f * density, 0f), Size(3f * density, size.height))
                }
            }
            StreetTheme.WHEATPASTE -> {
                val done = completed.count { it }
                val marks = done + if (current != null) 1 else 0
                if (marks == 0) return@Canvas
                val groups = (marks + 4) / 5
                // Strokes 6 dp apart and 10 dp between groups, squeezed when a long class would run off the strip.
                val natural = groups * (4 * 6f + 10f) * density
                val scale = min(1f, size.width / natural)
                val pitch = 6f * density * scale
                val groupGap = 10f * density * scale
                val top = 1.5f * density
                val bottom = size.height - 1.5f * density
                val width = 2.4f * density
                var x = 2f * density
                for (mark in 0 until marks) {
                    val color = if (mark < done) palette.text else palette.spot
                    if (mark % 5 == 4) {
                        drawLine(color, Offset(x - 4 * pitch - 2f * density, bottom - (bottom - top) * 0.25f),
                            Offset(x + 2f * density, top + (bottom - top) * 0.3f), width, StrokeCap.Round)
                        x += groupGap
                    } else {
                        drawLine(color, Offset(x, top), Offset(x, bottom), width, StrokeCap.Round)
                        x += pitch
                    }
                }
            }
        }
    }
}

/**
 * The rest's countdown in the timer face, as large as fits: Android sizes it from 32 to 120 sp in steps of 2, on one
 * line, centred in the room left.
 */
@Composable
private fun Countdown(text: String, modifier: Modifier) {
    val fonts = Street.theme.fonts
    val family = Street.type.timer
    val color = Street.colors.text
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val size = remember(text, width, height, family, density) {
            (120 downTo 32 step 2).firstOrNull { points ->
                val layout = measurer.measure(text, TextStyle(fontFamily = family, fontSize = points.sp), softWrap = false, maxLines = 1)
                val padding = with(density) { (points.sp * (fonts.timerTop + fonts.timerBottom)).toPx() }
                layout.size.width <= width && layout.size.height + padding <= height
            } ?: 32
        }
        BasicText(
            text,
            Modifier.fillMaxWidth().fontPadding(size.sp, fonts.timerTop, fonts.timerBottom),
            style = TextStyle(color = color, fontSize = size.sp, fontFamily = family, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The movement's notes in a sheet: the set and its dose, the description, tempo, safety, easier option and start criteria. */
@Composable
private fun DetailsSheet(state: FocusState, index: Int, onClose: () -> Unit) {
    val step = state.flow[index]
    val notes = ExerciseNotes.parse(step.exercise.description.resolve())
    val (dose, cues) = prescription(step)
    val readiness = stringResource(Res.string.readiness_note)
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TitleText(displayName(step.exercise), 20f, Modifier.weight(1f))
            TextAction(stringResource(Res.string.focus_close), onClick = onClose)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            BodyText("${stringResource(Res.string.focus_set_of, step.setNumber, step.setCount)} · $dose", 14f)
            if (notes.description.isNotBlank()) BodyText(notes.description, 15f, Modifier.padding(top = 10.dp), color = Street.colors.text)
            DetailsPart(stringResource(Res.string.focus_details_tempo), cues.joinToString(" · "))
            DetailsPart(stringResource(Res.string.focus_details_safety), notes.safety)
            DetailsPart(stringResource(Res.string.focus_details_easier), listOf(notes.easier, readiness).filter(String::isNotBlank).joinToString("\n\n"))
            DetailsPart(stringResource(Res.string.focus_details_start), state.program.readiness.resolve())
        }
    }
}

@Composable
private fun DetailsPart(label: String, text: String) {
    if (text.isBlank()) return
    SectionLabel(label, Modifier.padding(top = 8.dp))
    BodyText(text, 14f)
}

/**
 * What the athlete did in a set, in its [measure], starting from [done] or the plan (Android: askSetDone). [onDone]
 * gets the number, or null for "As planned", which is offered only when the plan has a number.
 */
@Composable
private fun LogDialog(measure: SetMeasure, done: Int?, onClose: () -> Unit, onDone: (Int?) -> Unit) {
    var field by remember {
        val start = (done ?: measure.planned)?.toString().orEmpty()
        mutableStateOf(TextFieldValue(start, TextRange(0, start.length)))
    }
    StreetDialog(
        title = stringResource(Res.string.set_done_title),
        message = stringResource(when {
            measure.window -> Res.string.set_done_message_window
            measure.timed -> Res.string.set_done_message_hold
            else -> Res.string.set_done_message_reps
        }),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.set_done_save)) {
            onClose()
            field.text.toIntOrNull()?.let(onDone)
        },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
        neutral = if (measure.planned == null) null else DialogAction(stringResource(Res.string.set_done_plan)) {
            onClose()
            onDone(null)
        },
    ) {
        NumberField(field, stringResource(if (measure.timed) Res.string.set_done_seconds_hint else Res.string.set_done_reps_hint),
            Modifier.padding(start = 24.dp, top = 4.dp, end = 24.dp)) { field = it }
    }
}

/** "How hard did that feel?": RPE 1 to 10, the class's target picked first (Android: askEffortAndFinish). */
@Composable
private fun EffortDialog(target: Int?, onClose: () -> Unit, onFinish: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf((target ?: 7).coerceIn(1, 10)) }
    StreetDialog(
        title = stringResource(Res.string.effort_title),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.finish)) { onFinish(selected) },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        for (value in 1..10) ChoiceRow(stringResource(Res.string.effort_item, value), value == selected) { selected = value }
    }
}

/** A section's name over its sets. */
@Composable
internal fun sectionTitle(section: String): String = stringResource(when (section) {
    Training.WARM_UP -> Res.string.warm_up
    Training.PRACTICE -> Res.string.skill_practice
    Training.COOL_DOWN -> Res.string.cool_down
    else -> Res.string.main_training
})

/** "8 reps" or "30 s · per side"; the set's other notes (the tempo) come back as cues. */
@Composable
private fun prescription(step: WorkoutStep): Pair<String, List<String>> {
    val notes = SetNotes.parse(step.set.description.resolve())
    val cues = notes.cues.toMutableList()
    val set = step.set
    val repetitions = set.repetitions
    val seconds = set.durationSeconds
    val dose = when {
        set.repetitionsMax -> stringResource(Res.string.maximum_repetitions)
        repetitions != null -> pluralStringResource(Res.plurals.repetitions_format, repetitions, repetitions)
        seconds != null -> stringResource(Res.string.focus_seconds, seconds)
        cues.isNotEmpty() -> cues.removeAt(0)
        else -> stringResource(Res.string.set_number, step.setNumber)
    }
    return (listOf(dose) + notes.sides).joinToString(" · ") to cues
}

/** A set held for its seconds, which the main button counts down; null for reps. */
private fun holdSeconds(step: WorkoutStep): Int? =
    step.set.durationSeconds?.takeIf { it > 0 && step.set.repetitions == null && !step.set.repetitionsMax }

/** The dose's size: smaller as the text gets longer. */
private fun largeSize(text: String): Float = when {
    text.length <= 10 -> 36f
    text.length <= 16 -> 27f
    else -> 21.6f
}

/**
 * "Did 12 reps" or "Held 45 s" for a set logged by the athlete; "Did more or fewer?" while it follows the plan, or what
 * to log when there is no plan's number to follow (Android: SetDone.kt).
 */
@Composable
fun setDoneLabel(measure: SetMeasure, done: Int?): String = when {
    done == null -> stringResource(when {
        measure.window -> Res.string.set_done_log_window
        measure.planned != null -> Res.string.set_done_ask
        measure.timed -> Res.string.set_done_log_seconds
        else -> Res.string.set_done_log_reps
    })
    measure.timed -> stringResource(Res.string.set_done_seconds, done)
    else -> pluralStringResource(Res.plurals.set_done_reps, done, done)
}
