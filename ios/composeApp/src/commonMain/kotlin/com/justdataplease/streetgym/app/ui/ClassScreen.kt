package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.animation_variation
import com.justdataplease.streetgym.app.resources.assigned_later
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.complete_day_no_points
import com.justdataplease.streetgym.app.resources.cool_down
import com.justdataplease.streetgym.app.resources.empty_description
import com.justdataplease.streetgym.app.resources.ic_play_arrow_white_24dp
import com.justdataplease.streetgym.app.resources.intensity_easier
import com.justdataplease.streetgym.app.resources.intensity_harder
import com.justdataplease.streetgym.app.resources.intensity_normal
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.main_training
import com.justdataplease.streetgym.app.resources.no_exercises
import com.justdataplease.streetgym.app.resources.readiness_note
import com.justdataplease.streetgym.app.resources.repeat_day
import com.justdataplease.streetgym.app.resources.repeat_note
import com.justdataplease.streetgym.app.resources.resume_workout
import com.justdataplease.streetgym.app.resources.safety_line
import com.justdataplease.streetgym.app.resources.safety_notes
import com.justdataplease.streetgym.app.resources.sequence_completed_note
import com.justdataplease.streetgym.app.resources.sequence_locked_note
import com.justdataplease.streetgym.app.resources.sets_count
import com.justdataplease.streetgym.app.resources.skill_practice
import com.justdataplease.streetgym.app.resources.start_workout
import com.justdataplease.streetgym.app.resources.warm_up
import com.justdataplease.streetgym.app.resources.workout_completed
import com.justdataplease.streetgym.app.resources.workout_view
import com.justdataplease.streetgym.app.resources.workout_view_focus
import com.justdataplease.streetgym.app.resources.workout_view_list
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One class, read before training it (android-streetgym, HomeActivity.renderOverview): what it is for, then every
 * exercise of the warm-up, skill practice, strength work and cool-down with its line man and its sets. Today's class
 * ends with the workout view, Easier / Normal / Harder and Start workout, or Resume while it is in progress
 * ([onStart]); a completed one can be trained again ([onRepeat]), at the top and at the end. [onDone] follows Complete
 * day.
 */
@Composable
fun ClassScreen(model: AppModel, programId: String, onStart: (String) -> Unit, onRepeat: (String) -> Unit, onDone: () -> Unit) {
    val program = model.program(programId) ?: return
    val isToday = model.isToday(program.id)
    val active = model.training.active()
    // Today's class starts as usual; any other completed class can be repeated (Android: repeatable).
    val repeatable = !isToday && model.canRepeat(program.id)
    val repeatResume = active?.programId == program.id && active.repeat
    var completing by remember { mutableStateOf(false) }
    Page {
        StreetCard(Modifier.padding(bottom = 14.dp)) {
            TitleText(displayName(program), 25f)
            val intensity = model.intensity.takeIf { it != Intensity.NORMAL && !program.isExam && program.exercises.any(Intensity::scales) }
            BodyText(
                listOfNotNull(stringResource(Res.string.level_number, program.levelNumber), programMeta(program),
                    intensity?.let { intensityLabel(it) })
                    .filter(String::isNotBlank).joinToString(" • "),
                14f, Modifier.padding(top = 4.dp),
            )
            BodyText(program.description.resolve().ifBlank { stringResource(Res.string.empty_description) },
                modifier = Modifier.padding(top = 12.dp))
            SafetyLine(program, Modifier.padding(top = 6.dp))
            // Also at the top, so a completed class can be repeated without scrolling past every exercise.
            if (repeatable) {
                BodyText(stringResource(Res.string.repeat_note), 13f, Modifier.padding(top = 12.dp), color = Street.colors.primary)
                RepeatButton(repeatResume, Modifier.padding(top = 10.dp)) { onRepeat(program.id) }
            }
        }

        val sections = listOf(
            Res.string.warm_up to program.warmup,
            Res.string.skill_practice to program.practice,
            Res.string.main_training to program.exercises,
            Res.string.cool_down to program.cooldown,
        ).filter { it.second.isNotEmpty() }
        if (sections.isEmpty()) {
            StreetCard(Modifier.padding(bottom = 14.dp)) { BodyText(stringResource(Res.string.no_exercises)) }
            return@Page
        }
        sections.forEach { (title, exercises) -> ExerciseSection(title, exercises) }

        when {
            isToday -> {
                val resume = active?.let { it.programId == program.id && !it.repeat } == true
                WorkoutViewRow(model, Modifier.padding(top = 4.dp))
                IntensityRow(model, Modifier.padding(top = 8.dp))
                PrimaryButton(
                    stringResource(if (resume) Res.string.resume_workout else Res.string.start_workout),
                    Modifier.padding(top = 8.dp),
                    icon = painterResource(Res.drawable.ic_play_arrow_white_24dp),
                ) { onStart(program.id) }
                SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 8.dp)) { completing = true }
                BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
            }
            repeatable -> {
                BodyText(stringResource(Res.string.sequence_completed_note), 14f, Modifier.padding(top = 8.dp))
                WorkoutViewRow(model, Modifier.padding(top = 4.dp))
                IntensityRow(model, Modifier.padding(top = 8.dp))
                RepeatButton(repeatResume, Modifier.padding(top = 8.dp)) { onRepeat(program.id) }
                BodyText(stringResource(Res.string.repeat_note), 13f, Modifier.padding(top = 8.dp))
            }
            else -> {
                val completed = model.isCompleted(program.id)
                BodyText(stringResource(if (completed) Res.string.sequence_completed_note else Res.string.sequence_locked_note),
                    14f, Modifier.padding(top = 8.dp))
                PrimaryButton(stringResource(if (completed) Res.string.workout_completed else Res.string.assigned_later),
                    Modifier.padding(top = 8.dp), color = Street.colors.textSecondary, enabled = false) {}
            }
        }
    }
    if (completing) CompleteDayDialog(program, onClose = { completing = false }) {
        completing = false
        model.completeDay(program.id)
        onDone()
    }
}

@Composable
private fun ExerciseSection(title: StringResource, exercises: List<TrainingExercise>) {
    SectionLabel(stringResource(title))
    exercises.forEachIndexed { index, exercise ->
        val number = exercise.number.takeIf { it > 0 } ?: (index + 1)
        val name = displayName(exercise)
        StreetCard(Modifier.padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberBox(number.toString(), 40.dp, 20f)
                Column(Modifier.padding(start = 12.dp)) {
                    TitleText(name, 18f)
                    BodyText(pluralStringResource(Res.plurals.sets_count, exercise.sets.size, exercise.sets.size), 13f)
                }
            }
            ExerciseAnimation(exercise, name, Modifier.padding(top = 10.dp))
            exerciseNotes(exercise).takeIf(String::isNotBlank)?.let { BodyText(it, 14f, Modifier.padding(top = 10.dp)) }
            equipmentNeeds(exercise.equipment)?.let { BodyText(it, 13f, Modifier.padding(top = 4.dp)) }
            exercise.sets.forEachIndexed { setIndex, set ->
                Row(Modifier.padding(top = 8.dp)) {
                    NumberBox((set.number.takeIf { it > 0 } ?: (setIndex + 1)).toString(), 26.dp, 12f, face = Street.type.body)
                    BodyText(setPrescription(set), 13f, Modifier.padding(start = 10.dp, top = 2.dp))
                }
            }
        }
    }
}

/**
 * An exercise's line man on a class page or in the list (Android: HomeActivity.exerciseAnimation): 168 dp tall, the
 * card's width. An exercise known only by its name with several variations in its sets steps through them with a
 * button under it.
 */
@Composable
fun ExerciseAnimation(exercise: TrainingExercise, name: String, modifier: Modifier = Modifier) {
    val variations = exercise.sets.map { it.name.resolve() }.filter(String::isNotBlank).distinct()
    var variation by remember(exercise) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        ExerciseFigure(
            exercise.movementId, name, Modifier.fillMaxWidth().height(168.dp), category = exercise.category,
            equipment = exercise.equipment, descriptor = variations.getOrNull(variation) ?: exercise.description.resolve(),
        )
        if (exercise.movementId == null && variations.size > 1) {
            SecondaryButton(stringResource(Res.string.animation_variation, variation + 1, variations.size, variations[variation])) {
                variation = (variation + 1) % variations.size
            }
        }
    }
}

/** "Workout view: Focus · List" above Start on a class page (Android: workoutViewSetting), at least 56 dp tall. */
@Composable
fun WorkoutViewRow(model: AppModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        BodyText(stringResource(Res.string.workout_view), 15f, Modifier.weight(1f), color = Street.colors.text)
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.workout_view_focus), stringResource(Res.string.workout_view_list)),
            selected = if (model.workoutView == WorkoutView.FOCUS) 0 else 1,
        ) { model.workoutView = if (it == 0) WorkoutView.FOCUS else WorkoutView.LIST }
    }
}

/** "Repeat this day", or Resume while the repeat is in progress, with the play arrow (Android: repeatButton). */
@Composable
private fun RepeatButton(resume: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PrimaryButton(
        stringResource(if (resume) Res.string.resume_workout else Res.string.repeat_day), modifier,
        icon = painterResource(Res.drawable.ic_play_arrow_white_24dp), onClick = onClick,
    )
}

@Composable
private fun intensityLabel(intensity: Intensity): String = stringResource(when (intensity) {
    Intensity.EASIER -> Res.string.intensity_easier
    Intensity.NORMAL -> Res.string.intensity_normal
    Intensity.HARDER -> Res.string.intensity_harder
})

/** Android's dialog buttons and titles written in its screens rather than its string files. */
internal const val COMPLETE_DAY_TITLE = "Complete this day?"
internal const val COMPLETE = "Complete"
internal const val OK = "OK"

/**
 * One quiet safety line on a class page and during a workout, ending in "Safety notes ›" in the theme's colour; a tap
 * shows the readiness note and the class's own readiness and safety notes (Android: HomeActivity.safetyLine).
 */
@Composable
fun SafetyLine(program: TrainingProgram, modifier: Modifier = Modifier) {
    val palette = Street.colors
    var open by remember { mutableStateOf(false) }
    val link = stringResource(Res.string.safety_notes) + " ›"
    val text = buildAnnotatedString {
        append(stringResource(Res.string.safety_line))
        append(' ')
        withStyle(SpanStyle(color = palette.primary, fontFamily = Street.type.bold)) { append(link) }
    }
    BasicText(
        text,
        modifier.fillMaxWidth().clickable(role = Role.Button) { open = true }.padding(vertical = 6.dp),
        style = TextStyle(color = palette.textSecondary, fontSize = 13.sp, lineHeight = 1.344.em, fontFamily = Street.type.body),
    )
    if (open) {
        val notes = listOf(stringResource(Res.string.readiness_note), program.readiness.resolve(), program.safety.resolve())
            .filter(String::isNotBlank).joinToString("\n\n")
        StreetDialog(
            title = stringResource(Res.string.safety_notes),
            message = notes,
            onDismiss = { open = false },
            confirm = DialogAction(OK) { open = false },
        )
    }
}

/**
 * "Complete this day?": the class's name and that marking it earns no points, then Cancel or Complete (Android:
 * requestCompleteDay). [onComplete] marks it done.
 */
@Composable
fun CompleteDayDialog(program: TrainingProgram, onClose: () -> Unit, onComplete: () -> Unit) {
    StreetDialog(
        title = COMPLETE_DAY_TITLE,
        message = displayName(program) + "\n\n" + stringResource(Res.string.complete_day_no_points),
        onDismiss = onClose,
        confirm = DialogAction(COMPLETE, onComplete),
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    )
}
