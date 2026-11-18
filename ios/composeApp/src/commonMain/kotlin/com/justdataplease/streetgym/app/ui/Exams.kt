package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.ExamQuestion
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.exam_badge
import com.justdataplease.streetgym.app.resources.exam_card_missed
import com.justdataplease.streetgym.app.resources.exam_card_passed
import com.justdataplease.streetgym.app.resources.exam_card_result
import com.justdataplease.streetgym.app.resources.exam_card_untaken
import com.justdataplease.streetgym.app.resources.exam_class_label
import com.justdataplease.streetgym.app.resources.exam_continue
import com.justdataplease.streetgym.app.resources.exam_missed_message
import com.justdataplease.streetgym.app.resources.exam_missed_title
import com.justdataplease.streetgym.app.resources.exam_passed_message
import com.justdataplease.streetgym.app.resources.exam_passed_title
import com.justdataplease.streetgym.app.resources.exam_repeat_block
import com.justdataplease.streetgym.app.resources.exam_results_title
import com.justdataplease.streetgym.app.resources.exam_save
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import org.jetbrains.compose.resources.stringResource

/*
 * Exams (android-streetgym, HomeActivity.examCard, askExam and askRepeatBlock): an exam class's card on its page, the
 * targets ticked after it, and what a result leads to.
 */

/** An exam class's card: what the test asks, or how its last attempt went (Android: examCard). */
@Composable
fun ExamCard(model: AppModel, exam: TrainingProgram, modifier: Modifier = Modifier) {
    val latest = model.examResult(exam.id)
    val text = if (latest == null) stringResource(Res.string.exam_card_untaken) else {
        val date = mediumDate(latest.at, model.zone)
        val total = latest.reached.size + latest.missed.size
        val result = stringResource(Res.string.exam_card_result, date, latest.reached.size, total)
        if (latest.passed) stringResource(Res.string.exam_card_passed, date) + " " + result
        else {
            val names = (exam.practice + exam.exercises).filter { (it.movementId ?: it.id) in latest.missed }.map { displayName(it) }
            result + " " + stringResource(Res.string.exam_card_missed, names.joinToString(", "))
        }
    }
    StreetCard(modifier) {
        TitleText(stringResource(Res.string.exam_badge), 18f)
        BodyText(text, 14f, Modifier.padding(top = 6.dp))
    }
}

/**
 * "Exam: tick the targets you reached": every target with its dose, those that look reached already ticked, and Save
 * results; it stays up until saved (Android: askExam, not cancelable). [onSave] gets one flag per target.
 */
@Composable
fun ExamDialog(question: ExamQuestion, onSave: (List<Boolean>) -> Unit) {
    val reached = remember(question) { mutableStateListOf(*question.tests.map { it.second }.toTypedArray()) }
    StreetDialog(
        title = stringResource(Res.string.exam_results_title),
        onDismiss = {},
        cancelable = false,
        confirm = DialogAction(stringResource(Res.string.exam_save)) { onSave(reached.toList()) },
    ) {
        question.tests.forEachIndexed { index, (exercise, _) ->
            CheckRow(examTarget(exercise), reached[index]) { reached[index] = it }
        }
    }
}

/** Every target reached: "Exam passed" (Android: askExam). */
@Composable
fun ExamPassedDialog(onClose: () -> Unit) {
    StreetDialog(
        title = stringResource(Res.string.exam_passed_title),
        message = stringResource(Res.string.exam_passed_message),
        onDismiss = onClose,
        confirm = DialogAction(OK, onClose),
    )
}

/**
 * The headline target, or three or more, missed: "Not every target yet", with the [missed] targets and the block since
 * the previous exam ([first], [count] classes); Repeat N classes ([onRepeat]) or Keep going (Android: askRepeatBlock).
 */
@Composable
fun RepeatBlockDialog(missed: List<TrainingExercise>, first: TrainingProgram, count: Int, onRepeat: () -> Unit, onClose: () -> Unit) {
    StreetDialog(
        title = stringResource(Res.string.exam_missed_title),
        message = stringResource(Res.string.exam_missed_message, missed.map { displayName(it) }.joinToString(", "), count,
            stringResource(Res.string.exam_class_label, first.levelNumber, first.number)),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.exam_repeat_block, count), onRepeat),
        dismiss = DialogAction(stringResource(Res.string.exam_continue), onClose),
    )
}
