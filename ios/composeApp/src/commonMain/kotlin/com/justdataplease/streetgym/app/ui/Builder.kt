package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.DraftProblem
import com.justdataplease.streetgym.app.figure.BadgeStyle
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.builder_add
import com.justdataplease.streetgym.app.resources.builder_down
import com.justdataplease.streetgym.app.resources.builder_edit
import com.justdataplease.streetgym.app.resources.builder_name_hint
import com.justdataplease.streetgym.app.resources.builder_needs_exercise
import com.justdataplease.streetgym.app.resources.builder_needs_name
import com.justdataplease.streetgym.app.resources.builder_remove
import com.justdataplease.streetgym.app.resources.builder_reps
import com.justdataplease.streetgym.app.resources.builder_save
import com.justdataplease.streetgym.app.resources.builder_seconds
import com.justdataplease.streetgym.app.resources.builder_up
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.equipment_none
import com.justdataplease.streetgym.app.resources.picker_empty
import com.justdataplease.streetgym.app.resources.picker_hint
import com.justdataplease.streetgym.app.resources.picker_title
import com.justdataplease.streetgym.app.resources.repetitions_format
import com.justdataplease.streetgym.app.resources.sets_amount
import com.justdataplease.streetgym.app.resources.sets_apply
import com.justdataplease.streetgym.app.resources.sets_count
import com.justdataplease.streetgym.app.resources.sets_reps
import com.justdataplease.streetgym.app.resources.sets_rest
import com.justdataplease.streetgym.app.resources.sets_seconds
import com.justdataplease.streetgym.app.resources.sets_title
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.data.library.CustomExercise
import com.justdataplease.streetgym.data.library.CustomSet
import com.justdataplease.streetgym.data.library.LibraryMovement
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** An own workout's name is at most this long (Android: HomeActivity.MAX_NAME_LENGTH). */
const val WORKOUT_NAME_LENGTH = 60

/**
 * The builder, "Your workout" (android-streetgym, HomeActivity.renderEditDay): the own workout's name, then a card per
 * exercise ("1. Pull-Up", "3 × 8 reps · rest 60 s") with Up, Down, Sets and Remove, Add exercise, which searches every
 * movement of the program, and Save workout, which needs a name and an exercise ([onNote] says which is missing) and
 * then opens the workout's page ([onSaved]). The draft lives in [AppModel.draft] until it is saved or the builder is
 * left.
 */
@Composable
fun BuilderScreen(model: AppModel, dayId: String, onSaved: (String) -> Unit, onNote: (String) -> Unit) {
    DisposableEffect(dayId) { onDispose { if (model.draft?.id == dayId) model.draft = null } }
    val day = model.draft?.takeIf { it.id == dayId } ?: return
    var name by remember(dayId) { mutableStateOf(TextFieldValue(day.name)) }
    var picking by remember { mutableStateOf(false) }
    var dosing by remember { mutableStateOf<Pair<String, List<CustomSet>>?>(null) }
    var applyDose by remember { mutableStateOf<(List<CustomSet>) -> Unit>({}) }
    val needsName = stringResource(Res.string.builder_needs_name)
    val needsExercise = stringResource(Res.string.builder_needs_exercise)
    fun change(edit: (MutableList<CustomExercise>) -> Unit) {
        val current = model.draft ?: return
        model.draft = current.copy(exercises = current.exercises.toMutableList().also(edit))
    }
    Page {
        EntryField(name, stringResource(Res.string.builder_name_hint), size = 20f, maxLength = WORKOUT_NAME_LENGTH, capitalize = true) {
            name = it
            model.draft = model.draft?.copy(name = it.text)
        }
        day.exercises.forEachIndexed { index, exercise ->
            StreetCard(Modifier.padding(top = 12.dp)) {
                TitleText("${index + 1}. ${exercise.name}", 17f)
                BodyText(prescription(exercise), 13f, Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp).fillMaxWidth()) {
                    BuilderAction(stringResource(Res.string.builder_up), index > 0) { change { it.add(index - 1, it.removeAt(index)) } }
                    BuilderAction(stringResource(Res.string.builder_down), index < day.exercises.lastIndex) { change { it.add(index + 1, it.removeAt(index)) } }
                    BuilderAction(stringResource(Res.string.builder_edit)) {
                        dosing = exercise.name to exercise.sets
                        applyDose = { sets -> change { it[index] = exercise.copy(sets = sets) } }
                    }
                    BuilderAction(stringResource(Res.string.builder_remove)) { change { it.removeAt(index) } }
                }
            }
        }
        SecondaryButton(stringResource(Res.string.builder_add), Modifier.padding(top = 16.dp)) { picking = true }
        PrimaryButton(stringResource(Res.string.builder_save), Modifier.padding(top = 10.dp)) {
            when (model.saveDraft()) {
                DraftProblem.NAME -> onNote(needsName)
                DraftProblem.EXERCISE -> onNote(needsExercise)
                null -> onSaved(dayId)
            }
        }
    }
    if (picking) ExercisePickerDialog(model, onClose = { picking = false }) { movement ->
        picking = false
        dosing = movement.name to List(3) { movement.sample }
        applyDose = { sets ->
            change { it += CustomExercise(movement.key, movement.name, movement.movementId, movement.category, movement.equipment, sets) }
        }
    }
    dosing?.let { (exerciseName, sets) ->
        SetsDialog(exerciseName, sets, onClose = { dosing = null }) { chosen ->
            dosing = null
            applyDose(chosen)
        }
    }
}

/** One of an exercise card's four small buttons, a quarter of the row, greyed when it can't act. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.BuilderAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    SecondaryButton(label, Modifier.weight(1f).padding(horizontal = 2.dp).alpha(if (enabled) 1f else 0.4f),
        enabled = enabled, compact = true, onClick = onClick)
}

/** "3 × 8 reps · rest 60 s" or "3 × 30 s · rest 45 s": every set shares the first one's dose (Android: prescription). */
@Composable
fun prescription(exercise: CustomExercise): String {
    val set = exercise.sets.firstOrNull() ?: return ""
    val seconds = set.durationSeconds
    return if (seconds != null) stringResource(Res.string.builder_seconds, exercise.sets.size, seconds, set.breakSeconds)
    else (set.repetitions ?: 0).let { reps ->
        stringResource(Res.string.builder_reps, exercise.sets.size, pluralStringResource(Res.plurals.repetitions_format, reps, reps), set.breakSeconds)
    }
}

/**
 * "Add an exercise" (Android: HomeActivity.pickExercise and PickerRow): a search over every movement of the program
 * (every word must match its name, category or equipment), its rows each with the movement playing on a small tile,
 * then "Category · equipment" or "No equipment"; "No exercise matches." when none does. A tap picks one.
 */
@Composable
fun ExercisePickerDialog(model: AppModel, onClose: () -> Unit, onPick: (LibraryMovement) -> Unit) {
    val palette = Street.colors
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val shown = remember(query.text) { model.exercises.search(query.text) }
    val badge = remember(palette) {
        BadgeStyle(palette.infoSurface, palette.primary, blendColor(palette.primary, palette.infoSurface, 0.5f),
            blendColor(palette.textSecondary, palette.infoSurface, 0.4f))
    }
    val none = stringResource(Res.string.equipment_none)
    StreetDialog(
        title = stringResource(Res.string.picker_title),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp)) {
            EntryField(query, stringResource(Res.string.picker_hint), size = 16f) { query = it }
            if (shown.isEmpty()) BodyText(stringResource(Res.string.picker_empty), 14f, Modifier.padding(top = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                itemsIndexed(shown, key = { _, movement -> movement.key }) { index, movement ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                    val details = listOfNotNull(movement.category?.let(::humanize), equipmentText(movement.equipment) ?: none).joinToString(" · ")
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(movement) }
                            .semantics(mergeDescendants = true) { contentDescription = "${movement.name}, $details" }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseFigure(movement.movementId, movement.name, Modifier.padding(end = 12.dp).size(52.dp),
                            category = movement.category, equipment = movement.equipment, badge = badge, moving = true)
                        Column(Modifier.weight(1f)) {
                            BasicText(movement.name, style = TextStyle(color = palette.text, fontSize = 16.sp, fontFamily = Street.type.bold),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (details.isNotBlank()) BodyText(details, 13f, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Sets for Pull-Up" (Android: HomeActivity.editSets): the same dose for every set: how many (1 to 20), reps or seconds
 * (1 to 600) and the rest after each (0 to 600 s); Apply gives the sets.
 */
@Composable
fun SetsDialog(name: String, sets: List<CustomSet>, onClose: () -> Unit, onApply: (List<CustomSet>) -> Unit) {
    val first = sets.firstOrNull() ?: CustomSet(repetitions = 8)
    var timed by remember { mutableStateOf(first.durationSeconds != null) }
    var count by remember { mutableStateOf(TextFieldValue(sets.size.coerceAtLeast(1).toString())) }
    var amount by remember { mutableStateOf(TextFieldValue((first.durationSeconds ?: first.repetitions ?: 8).toString())) }
    var rest by remember { mutableStateOf(TextFieldValue(first.breakSeconds.toString())) }
    StreetDialog(
        title = stringResource(Res.string.sets_title, name),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.sets_apply)) {
            val n = count.text.toIntOrNull()?.coerceIn(1, 20) ?: 1
            val value = amount.text.toIntOrNull()?.coerceIn(1, 600) ?: 1
            val pause = rest.text.toIntOrNull()?.coerceIn(0, 600) ?: 60
            onApply(List(n) { if (timed) CustomSet(durationSeconds = value, breakSeconds = pause) else CustomSet(repetitions = value, breakSeconds = pause) })
        },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp)) {
            val countLabel = stringResource(Res.string.sets_count)
            BodyText(countLabel, 13f)
            EntryField(count, countLabel, digits = true, maxLength = 3) { count = it }
            SegmentedSwitch(listOf(stringResource(Res.string.sets_reps), stringResource(Res.string.sets_seconds)), if (timed) 1 else 0,
                Modifier.padding(top = 8.dp, bottom = 4.dp)) { timed = it == 1 }
            val amountLabel = stringResource(Res.string.sets_amount)
            BodyText(amountLabel, 13f)
            EntryField(amount, amountLabel, digits = true, maxLength = 3) { amount = it }
            val restLabel = stringResource(Res.string.sets_rest)
            BodyText(restLabel, 13f)
            EntryField(rest, restLabel, digits = true, maxLength = 3) { rest = it }
        }
    }
}
