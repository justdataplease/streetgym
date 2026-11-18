package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.complete_day_no_points
import com.justdataplease.streetgym.app.resources.cool_down
import com.justdataplease.streetgym.app.resources.empty_description
import com.justdataplease.streetgym.app.resources.ic_play_arrow_white_24dp
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.resume_workout
import com.justdataplease.streetgym.app.resources.start_workout
import com.justdataplease.streetgym.app.resources.main_training
import com.justdataplease.streetgym.app.resources.no_exercises
import com.justdataplease.streetgym.app.resources.safety_line
import com.justdataplease.streetgym.app.resources.sequence_completed_note
import com.justdataplease.streetgym.app.resources.sequence_locked_note
import com.justdataplease.streetgym.app.resources.sets_count
import com.justdataplease.streetgym.app.resources.skill_practice
import com.justdataplease.streetgym.app.resources.warm_up
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.data.model.TrainingExercise
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One class, read before training it (android-streetgym, HomeActivity.renderOverview): what it is for, then every
 * exercise of the warm-up, skill practice, strength work and cool-down with its sets. Today's class ends with Start
 * workout, or Resume while it is in progress ([onStart]).
 */
@Composable
fun ClassScreen(model: AppModel, programId: String, onStart: (String) -> Unit, onDone: () -> Unit) {
    val program = model.program(programId) ?: return
    val isToday = model.isToday(program.id)
    Page {
        StreetCard(Modifier.padding(bottom = 14.dp)) {
            TitleText(displayName(program), 25f)
            BodyText(
                listOf(stringResource(Res.string.level_number, program.levelNumber), programMeta(program))
                    .filter(String::isNotBlank).joinToString(" • "),
                14f, Modifier.padding(top = 4.dp),
            )
            BodyText(program.description.resolve().ifBlank { stringResource(Res.string.empty_description) },
                modifier = Modifier.padding(top = 12.dp))
            BodyText(stringResource(Res.string.safety_line), 13f, Modifier.padding(top = 12.dp, bottom = 6.dp))
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
                val resume = model.training.active()?.let { it.programId == program.id && !it.repeat } == true
                IntensityRow(model, Modifier.padding(top = 8.dp))
                PrimaryButton(
                    stringResource(if (resume) Res.string.resume_workout else Res.string.start_workout),
                    Modifier.padding(top = 8.dp),
                    icon = painterResource(Res.drawable.ic_play_arrow_white_24dp),
                ) { onStart(program.id) }
                SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 8.dp)) {
                    model.completeDay(program.id)
                    onDone()
                }
                BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
            }
            else -> BodyText(
                stringResource(if (model.isCompleted(program.id)) Res.string.sequence_completed_note else Res.string.sequence_locked_note),
                14f, Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ExerciseSection(title: StringResource, exercises: List<TrainingExercise>) {
    SectionLabel(stringResource(title))
    exercises.forEachIndexed { index, exercise ->
        val number = exercise.number.takeIf { it > 0 } ?: (index + 1)
        StreetCard(Modifier.padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberBox(number.toString(), 40.dp, 20f)
                Column(Modifier.padding(start = 12.dp)) {
                    TitleText(displayName(exercise), 18f)
                    BodyText(pluralStringResource(Res.plurals.sets_count, exercise.sets.size, exercise.sets.size), 13f)
                }
            }
            exerciseNotes(exercise).takeIf(String::isNotBlank)?.let { BodyText(it, 14f, Modifier.padding(top = 10.dp)) }
            exercise.sets.forEachIndexed { setIndex, set ->
                Row(Modifier.padding(top = 8.dp)) {
                    NumberBox((set.number.takeIf { it > 0 } ?: (setIndex + 1)).toString(), 26.dp, 12f, face = Street.type.body)
                    BodyText(setPrescription(set), 13f, Modifier.padding(start = 10.dp, top = 2.dp))
                }
            }
        }
    }
}
