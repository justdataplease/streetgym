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
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.resources.custom_delete
import com.justdataplease.streetgym.app.resources.custom_delete_message
import com.justdataplease.streetgym.app.resources.custom_delete_title
import com.justdataplease.streetgym.app.resources.custom_edit
import com.justdataplease.streetgym.app.resources.delete
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.ReadyWorkouts
import com.justdataplease.streetgym.app.data.LeftOut
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.resources.inspect_only
import com.justdataplease.streetgym.app.resources.inspect_only_note
import com.justdataplease.streetgym.app.resources.switch_off_badge
import com.justdataplease.streetgym.app.resources.switch_off_class
import androidx.compose.ui.draw.alpha
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.figure.effortColor
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

/** The words of a class not reached yet, written in Android's screen (renderOverview) rather than its string files. */
internal const val DAILY_PLAN_LOCKED = "This daily plan is locked."

/**
 * One class, read before training it (android-streetgym, HomeActivity.renderOverview): what it is for and how hard it
 * is for the athlete, an exam's card, then every exercise of the warm-up, skill practice, strength work and cool-down
 * with its line man and its sets. Today's class ends with the workout view, Easier / Normal / Harder and Start workout,
 * or Resume while it is in progress ([onStart]); a completed one can be trained again ([onRepeat]), at the top and at
 * the end. [onDone] follows Complete day. A class not reached yet shows only its name; one in a level the tier doesn't
 * open shows Unlock, which opens the offer ([onOffer], with the level).
 */
@Composable
fun ClassScreen(
    model: AppModel,
    programId: String,
    onStart: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onDone: () -> Unit,
    onOffer: (Int) -> Unit = {},
    onPaywall: (Paywall) -> Unit = {},
    onLimit: (Limit) -> Unit = {},
    onNote: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val program = model.program(programId) ?: return
    val completed = model.isCompleted(program.id)
    val custom = CustomDays.isCustom(program.id)
    val ready = ReadyWorkouts.isReady(program.id)
    // Developer inspection opens a class not reached yet, or behind a tier, read-only: nothing to start or complete.
    val inspecting = model.inspects(program.id)
    if (!inspecting && model.levelLocked(program.levelNumber) && !completed) {
        // A class of a level the tier doesn't open: its name, meta and Unlock, none of its exercises.
        Page {
            StreetCard(Modifier.padding(bottom = 14.dp)) {
                TitleText(displayName(program), 25f)
                BodyText(listOf(stringResource(Res.string.level_number, program.levelNumber), programMeta(program))
                    .filter(String::isNotBlank).joinToString(" • "), 14f, Modifier.padding(top = 4.dp))
                UnlockButton(levelTierName(program.levelNumber), Modifier.padding(top = 16.dp)) { onOffer(program.levelNumber) }
            }
        }
        return
    }
    // Below Pro a ready workout stays closed: its name, its line and Unlock, none of its exercises.
    if (!inspecting && model.readyLocked(program.id)) {
        Page {
            StreetCard(Modifier.padding(bottom = 14.dp)) {
                TitleText(displayName(program), 25f)
                BodyText(readyMeta(program), 14f, Modifier.padding(top = 4.dp))
                UnlockButton(stringResource(Res.string.tier_pro), Modifier.padding(top = 16.dp)) { onPaywall(Paywall.Ready) }
            }
        }
        return
    }
    if (!inspecting && !ready && !model.canStart(program.id) && !completed) {
        // Not reached yet: only its name, and that it comes later.
        Page {
            StreetCard {
                TitleText(displayName(program), 25f)
                BodyText(DAILY_PLAN_LOCKED, modifier = Modifier.padding(top = 12.dp))
                PrimaryButton(stringResource(Res.string.assigned_later), Modifier.padding(top = 16.dp),
                    color = Street.colors.textSecondary, enabled = false) {}
            }
        }
        return
    }
    val isToday = model.isToday(program.id)
    val active = model.training.active()
    // Today's class starts as usual; any other completed class can be repeated (Android: repeatable). An own or ready
    // workout starts any time.
    val repeatable = !custom && !ready && !isToday && model.canRepeat(program.id)
    val repeatResume = active?.programId == program.id && active.repeat
    var completing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Page {
        StreetCard(Modifier.padding(bottom = 14.dp)) {
            if (inspecting) Badge(stringResource(Res.string.inspect_only), Modifier.padding(bottom = 8.dp))
            // The heart, but not to inspect, and not on Free's own workouts, which are always saved and have no lists.
            if (inspecting || custom && !model.tier.lists) TitleText(displayName(program), 25f)
            else Row {
                TitleText(displayName(program), 25f, Modifier.weight(1f))
                HeartButton(model, program.id, onNote = onNote, onLimit = onLimit)
            }
            val intensity = model.intensity.takeIf { it != Intensity.NORMAL && !program.isExam && program.exercises.any(Intensity::scales) }
            BodyText(
                when {
                    custom -> customMeta(program.exercises.size, program.estimatedMinutes ?: 1)
                    ready -> readyMeta(program)
                    else -> listOfNotNull(stringResource(Res.string.level_number, program.levelNumber), programMeta(program),
                        intensity?.let { intensityLabel(it) }).filter(String::isNotBlank).joinToString(" • ")
                },
                14f, Modifier.padding(top = 4.dp),
            )
            // How hard the class is for this athlete, in its colour (Android: effortText and effortColor).
            model.effortOf(program.id)?.let { effort ->
                Badge(effortText(effort), Modifier.padding(top = 8.dp), color = effortColor(effort))
            }
            if (!custom) BodyText(program.description.resolve().ifBlank { stringResource(Res.string.empty_description) },
                modifier = Modifier.padding(top = 12.dp))
            SafetyLine(program, Modifier.padding(top = 6.dp))
            if (custom) {
                SecondaryButton(stringResource(Res.string.custom_edit), Modifier.padding(top = 12.dp)) { onEdit(program.id) }
                SecondaryButton(stringResource(Res.string.custom_delete), Modifier.padding(top = 8.dp)) { deleting = true }
            }
            // Also at the top, so a completed class can be repeated without scrolling past every exercise.
            if (repeatable) {
                BodyText(stringResource(Res.string.repeat_note), 13f, Modifier.padding(top = 12.dp), color = Street.colors.primary)
                RepeatButton(repeatResume, Modifier.padding(top = 10.dp)) { onRepeat(program.id) }
            }
        }
        if (program.isExam) ExamCard(model, program, Modifier.padding(bottom = 14.dp))
        // Everything left out by the switches: the class counts as passed, and can still be done.
        if (model.switchedOff(program)) StreetCard(Modifier.padding(bottom = 14.dp), fill = Street.colors.infoSurface, outlined = false) {
            BodyText(stringResource(Res.string.switch_off_class), 14f)
        }

        val sections = Training.sections(program).filter { it.second.isNotEmpty() }
        if (sections.isEmpty()) {
            StreetCard(Modifier.padding(bottom = 14.dp)) { BodyText(stringResource(Res.string.no_exercises)) }
            return@Page
        }
        sections.forEach { (section, exercises) -> ExerciseSection(model, program, section, exercises) }

        when {
            inspecting -> {
                BodyText(stringResource(Res.string.inspect_only_note), 14f, Modifier.padding(top = 8.dp))
                PrimaryButton(stringResource(Res.string.inspect_only), Modifier.padding(top = 8.dp), color = Street.colors.textSecondary,
                    enabled = false) {}
            }
            custom || ready || isToday -> {
                val resume = active?.let { it.programId == program.id && !it.repeat } == true
                WorkoutViewRow(model, Modifier.padding(top = 4.dp))
                // An own or ready workout runs as written, any time: no intensity, and nothing to complete.
                if (!custom && !ready) IntensityRow(model, Modifier.padding(top = 8.dp))
                PrimaryButton(
                    stringResource(if (resume) Res.string.resume_workout else Res.string.start_workout),
                    Modifier.padding(top = 8.dp),
                    icon = painterResource(Res.drawable.ic_play_arrow_white_24dp),
                ) { onStart(program.id) }
                if (!custom && !ready) {
                    SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 8.dp)) { completing = true }
                    BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
                }
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
    // Android: renderOverview's Delete workout. Its history stays; it is ended if in progress.
    if (deleting) StreetDialog(
        title = stringResource(Res.string.custom_delete_title),
        message = stringResource(Res.string.custom_delete_message, displayName(program)),
        onDismiss = { deleting = false },
        confirm = DialogAction(stringResource(Res.string.delete)) {
            deleting = false
            model.deleteCustom(program.id)
            onDeleted()
        },
        dismiss = DialogAction(stringResource(Res.string.cancel)) { deleting = false },
    )
}

/**
 * A section of the class page: each exercise with its number, sets, line man, notes and needs. One left out by the
 * switches is greyed with "Switched off · Handstand" (Android: addExerciseSection).
 */
@Composable
private fun ExerciseSection(model: AppModel, program: TrainingProgram, section: String, exercises: List<TrainingExercise>) {
    SectionLabel(sectionTitle(section))
    exercises.forEachIndexed { index, exercise ->
        val number = exercise.number.takeIf { it > 0 } ?: (index + 1)
        val name = displayName(exercise)
        val off = model.leftOutBy(program, section, exercise, index)
        StreetCard(Modifier.padding(bottom = 10.dp).alpha(if (off != null) 0.45f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberBox(number.toString(), 40.dp, 20f)
                Column(Modifier.padding(start = 12.dp)) {
                    TitleText(name, 18f)
                    BodyText(pluralStringResource(Res.plurals.sets_count, exercise.sets.size, exercise.sets.size), 13f)
                }
            }
            off?.let { SwitchedOffBadge(it, Modifier.padding(top = 8.dp)) }
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

/** "Switched off · Handstand" or "Switched off · Pull-up bar" on a left-out exercise (Android: switch_off_badge). */
@Composable
fun SwitchedOffBadge(reason: LeftOut, modifier: Modifier = Modifier) {
    Badge(stringResource(Res.string.switch_off_badge, leftOutName(reason)), modifier, color = Street.colors.textSecondary)
}

/** The skill's family or the equipment that leaves an exercise out, by name. */
@Composable
fun leftOutName(reason: LeftOut): String = when (reason) {
    is LeftOut.Skill -> reason.name
    is LeftOut.Gear -> stringResource(equipmentName(reason.equipment))
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
