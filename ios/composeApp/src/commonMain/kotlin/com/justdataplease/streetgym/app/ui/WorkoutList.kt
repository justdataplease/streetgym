package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.FocusState
import com.justdataplease.streetgym.ui.WorkoutStep
import androidx.compose.ui.draw.alpha
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.abandon_workout
import com.justdataplease.streetgym.app.resources.active_sets_progress
import com.justdataplease.streetgym.app.resources.complete_day_no_points
import com.justdataplease.streetgym.app.resources.finish_workout
import com.justdataplease.streetgym.app.resources.repeat_note
import com.justdataplease.streetgym.app.resources.rest_countdown
import com.justdataplease.streetgym.app.resources.rest_ready
import com.justdataplease.streetgym.app.resources.section_check_description
import com.justdataplease.streetgym.app.resources.set_complete_description
import com.justdataplease.streetgym.app.resources.set_number
import com.justdataplease.streetgym.app.resources.started_minutes_ago
import com.justdataplease.streetgym.app.resources.workout_easier_off
import com.justdataplease.streetgym.app.resources.workout_easier_off_description
import com.justdataplease.streetgym.app.resources.workout_easier_on
import com.justdataplease.streetgym.app.resources.workout_easier_on_description
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.ui.WorkoutFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The class in progress as a list (android-streetgym, HomeActivity.renderActive): a card with the class, its time, the
 * sets ticked and the rest; the safety line; then every section with a tick for all its sets, and every exercise with
 * its line man, notes, easier option and one ticked row per set; Finish workout and End session at the end. A tick
 * starts the set's rest, which the focus view shows too ([Training.tick]); a "max" set asks what was done.
 */
@Composable
internal fun WorkoutList(model: AppModel, state: FocusState, training: Training, onAsk: (Ask) -> Unit, onFinish: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val program = state.program
    val flow = state.flow
    val active = state.active
    val completed = active.completedSetIds
    val done = flow.completedCount(completed)
    val total = flow.size
    // Every set by its key, and where the sets the athlete trains are in the class (switched-off ones are not).
    val steps = state.all.steps.associateBy { it.key }
    val index = flow.steps.withIndex().associate { (position, step) -> step.key to position }
    Page {
        StreetCard(Modifier.padding(bottom = 6.dp), fill = palette.infoSurface, outlined = false) {
            if (active.repeat) BodyText(stringResource(Res.string.repeat_note), 12f, Modifier.padding(bottom = 4.dp), color = palette.primary)
            TitleText(displayName(program), 22f)
            val minutes = ((state.now - active.startedAtEpochMillis) / 60_000L).coerceAtLeast(0L).toInt()
            BodyText(stringResource(Res.string.started_minutes_ago, minutes), 13f, Modifier.padding(top = 3.dp))
            BodyText(stringResource(Res.string.active_sets_progress, done, total), 14f, Modifier.padding(top = 10.dp),
                color = palette.primary, bold = true)
            ProgressBar(if (total == 0) 0 else done * 100 / total, Modifier.padding(top = 7.dp), height = 9.dp)
            // The rest after a tick counts down here; when it runs out the set after it is ready.
            val rest = state.restEndsAt?.let { end -> ((end - state.now + 999L) / 1_000L).coerceAtLeast(1L).toInt() }
            BasicText(
                if (rest == null) stringResource(Res.string.rest_ready) else stringResource(Res.string.rest_countdown, rest),
                Modifier.padding(top = 12.dp).fillMaxWidth().streetBackground(theme, palette.surface, 16.dp).padding(10.dp),
                style = TextStyle(color = palette.action, fontSize = 14.sp, fontFamily = Street.type.bold, textAlign = TextAlign.Center),
            )
            // A repeated class is already complete; it ends with Finish or End session.
            if (!active.repeat) {
                SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 12.dp)) { onAsk(Ask.CompleteDay) }
                BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
            }
        }
        SafetyLine(program, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp))

        val sections = Training.sections(program).filter { it.second.isNotEmpty() }
        for ((section, exercises) in sections) {
            val keys = flow.keys(section)
            val count = keys.count { it in completed }
            val all = keys.isNotEmpty() && count == keys.size
            val name = sectionTitle(section)
            val progress = stringResource(Res.string.active_sets_progress, count, keys.size)
            val description = stringResource(Res.string.section_check_description, name)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    // Ticks only the sets the athlete trains; switched-off ones wait until done anyway.
                    .toggleable(all, enabled = keys.isNotEmpty(), role = Role.Checkbox) { training.tickSection(section, it) }
                    .semantics {
                        contentDescription = description
                        stateDescription = progress
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(name, Modifier.weight(1f))
                BodyText("$count/${keys.size}", 12f, Modifier.width(46.dp), align = TextAlign.Center)
                CheckMark(all, palette.primary)
            }
            exercises.forEachIndexed { exerciseIndex, exercise ->
                ExerciseCard(model, state, training, section, exercise, exerciseIndex, steps, index, onAsk)
            }
            // Free: a card between sections, waiting for consent rather than asking mid-workout (Android: renderActive).
            if (section != sections.last().first) AdCard(model, bottom = 14.dp, training = true)
        }

        PrimaryButton(stringResource(Res.string.finish_workout), Modifier.padding(top = 8.dp), onClick = onFinish)
        SecondaryButton(stringResource(Res.string.abandon_workout), Modifier.padding(top = 6.dp)) { onAsk(Ask.End) }
        EndAd(model, training = true)
    }
}

/**
 * One exercise in the list: its name, line man, notes, what it needs, its easier option and a ticked row per set. One
 * left out by the switches is greyed with "Switched off · Handstand", and any tap on it asks before doing it anyway
 * (Android: renderActive's offFamily and doAnyway).
 */
@Composable
private fun ExerciseCard(
    model: AppModel,
    state: FocusState,
    training: Training,
    section: String,
    exercise: TrainingExercise,
    exerciseIndex: Int,
    steps: Map<String, WorkoutStep>,
    index: Map<String, Int>,
    onAsk: (Ask) -> Unit,
) {
    val palette = Street.colors
    val name = displayName(exercise)
    val active = state.active
    val off = model.leftOutBy(state.program, section, exercise, exerciseIndex)
    val exerciseKey = WorkoutFlow.exerciseKey(section, exercise, exerciseIndex)
    fun anyway(then: () -> Unit) = onAsk(Ask.SwitchedOff(listOfNotNull(off), setOf(exerciseKey), then))
    // Ticks a set; a "max" set, a practice window or an easier option measured the other way has no number to count
    // until the athlete gives it.
    fun tick(key: String, step: WorkoutStep, now: Boolean) {
        training.tick(key, now)
        val position = training.state()?.flow?.keys?.indexOf(key)?.takeIf { it >= 0 } ?: return
        if (now && step.credited && training.measure(step).planned == null && key !in active.done) {
            onAsk(Ask.Log(position, thenDone = false, thenTick = true))
        }
    }
    StreetCard(Modifier.padding(bottom = 10.dp).alpha(if (off != null) 0.45f else 1f), onClick = if (off != null) ({ anyway {} }) else null) {
        TitleText(name, 18f)
        off?.let { SwitchedOffBadge(it, Modifier.padding(top = 6.dp)) }
        ExerciseAnimation(exercise, name, Modifier.padding(top = 8.dp))
        exerciseNotes(exercise).takeIf(String::isNotBlank)?.let { BodyText(it, 13f, Modifier.padding(top = 4.dp)) }
        equipmentNeeds(exercise.equipment)?.let { BodyText(it, 13f, Modifier.padding(top = 4.dp)) }
        val exerciseKey = WorkoutFlow.exerciseKey(section, exercise, exerciseIndex)
        exercise.easier?.takeIf { section in WorkoutFlow.CREDITED_SECTIONS && !exercise.readinessCheck }?.let { option ->
            val on = exerciseKey in active.easierExercises
            val optionName = option.name.resolve()
            Chip(
                stringResource(if (on) Res.string.workout_easier_on else Res.string.workout_easier_off, optionName),
                Modifier.padding(top = 8.dp), on = on, maxLines = 2,
                description = stringResource(if (on) Res.string.workout_easier_on_description else Res.string.workout_easier_off_description, optionName),
            ) { training.setEasier(exerciseKey, !on) }
        }
        exercise.sets.forEachIndexed { setIndex, set ->
            val key = WorkoutFlow.setKey(state.program.id, section, exercise, exerciseIndex, set, setIndex)
            val step = steps[key] ?: return@forEachIndexed
            val position = index[key]
            val checked = key in active.completedSetIds
            val number = set.number.takeIf { it > 0 } ?: (setIndex + 1)
            val description = stringResource(Res.string.set_complete_description, number, name)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .toggleable(checked, role = Role.Checkbox) { now ->
                        if (off != null) anyway { tick(key, step, true) } else tick(key, step, now)
                    }
                    .semantics { contentDescription = description }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckMark(checked, if (checked) palette.success else palette.textSecondary)
                Column(Modifier.weight(1f)) {
                    BasicText(stringResource(Res.string.set_number, number), style = TextStyle(color = palette.text, fontSize = 16.sp,
                        fontFamily = Street.type.bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyText(setPrescription(set), 13f)
                    // What the athlete did, where it differs from the plan: skill ranks count sets at a target.
                    if (step.credited) BasicText(
                        setDoneLabel(training.measure(step), active.done[key]),
                        Modifier.clickable(role = Role.Button) {
                            if (position != null) onAsk(Ask.Log(position, thenDone = false, thenTick = true))
                            else anyway { tick(key, step, true) }
                        }
                            .padding(vertical = 4.dp),
                        style = TextStyle(color = palette.primary, fontSize = 13.sp, fontFamily = Street.type.bold),
                    )
                }
            }
        }
    }
}
