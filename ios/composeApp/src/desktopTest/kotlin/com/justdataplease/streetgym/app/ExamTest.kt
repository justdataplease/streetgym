package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Start
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.TodayLabel
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.exam_card_missed
import com.justdataplease.streetgym.app.resources.exam_card_result
import com.justdataplease.streetgym.app.resources.exam_card_untaken
import com.justdataplease.streetgym.app.resources.exam_missed_message
import com.justdataplease.streetgym.app.resources.exam_class_label
import com.justdataplease.streetgym.app.resources.exam_replay_today
import com.justdataplease.streetgym.app.resources.exam_saved_not_yet
import com.justdataplease.streetgym.app.resources.focus_seconds
import com.justdataplease.streetgym.app.resources.repetitions_format
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.mediumDate
import com.justdataplease.streetgym.data.equipment.EquipmentNeeds
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.ui.SetNotes
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.ui.WorkoutStep
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exams over the real program (Android: HomeActivity.askExam, askRepeatBlock, examCard): the targets asked after an exam
 * class, the result saved as Android saves it, the block repeat as a level replay under Android's names, a repeated
 * exam, the targets switched off, and the whole flow by taps.
 */
@OptIn(ExperimentalTestApi::class)
class ExamTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore()) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    /** Every class before [target] marked done the day before, so [target] is today's. */
    private fun reach(model: AppModel, target: TrainingProgram) {
        model.progress.completePrograms(ordered.takeWhile { it.id != target.id }.map { it.id }, at = time - DAY)
    }

    /** Trains the class in progress to its end, each set after its planned time; [done] logs a number for a set. */
    private fun train(model: AppModel, done: (WorkoutStep) -> Int? = { null }) {
        val training = model.training
        while (true) {
            val state = training.state()!!
            val index = (state.page as? FocusPage.Set)?.index ?: break
            val step = state.flow[index]
            done(step)?.let { training.setDone(step.key, it) }
            time += (WorkoutFlow.plannedSeconds(step) * 1_000).toLong() + 1_000L
            training.done(index)
            training.skipRest()
        }
    }

    /** The id an exam result keeps for a target, as Android keeps it. */
    private fun id(exercise: TrainingExercise) = exercise.movementId ?: exercise.id

    @Test
    fun anExamAsksForItsTargetsAndSavesTheResultAsAndroidDoes() {
        val store = MemoryStore()
        val model = model(store)
        val exam = ordered.first { candidate -> candidate.isExam && candidate.tests.any { (it.sets.firstOrNull()?.repetitions ?: 0) > 1 } }
        reach(model, exam)
        assertEquals(exam.id, model.today().next!!.id)
        assertEquals(Start.STARTED, model.start(exam.id))
        val flow = model.training.state()!!.flow
        // One target logged short of its plan; the others as planned.
        val short = flow.steps.first { it.exercise.assessment && (it.set.repetitions ?: 0) > 1 }
        train(model) { step -> if (step.key == short.key) step.set.repetitions!! - 1 else null }

        val finished = assertNotNull(model.training.finish(effort = 8))
        val question = assertNotNull(finished.exam)
        assertEquals(exam.id, question.exam.id)
        assertTrue(question.offerRepeat)
        assertEquals(exam.tests.distinct(), question.tests.map { it.first })
        assertFalse(question.tests.single { it.first == short.exercise }.second)
        assertTrue(question.tests.filter { it.first != short.exercise }.all { it.second })

        // Every target ticked in the end: passed, kept with Android's names and in its order.
        val result = model.saveExam(question, question.tests.map { true })
        assertTrue(result.passed)
        val reached = question.tests.joinToString(",") { "\"${id(it.first)}\"" }
        assertEquals("""[{"program_id":"${exam.id}","at":$time,"reached":[$reached],"missed":[]}]""", store.get("streetgym_progress.exam_results"))
        assertEquals(result, StoredProgress(store).examLog().latest(exam.id))
        // An ordinary class asks nothing.
        val next = model.today().next!!
        assertFalse(next.isExam)
        model.start(next.id)
        train(model)
        assertNull(model.training.finish(effort = 6)!!.exam)
    }

    @Test
    fun missingTheHeadlineRepeatsTheBlockUntilTheExamIsTakenAgain() {
        val store = MemoryStore()
        val model = model(store)
        val exam = ordered.first { it.isExam && it.headline != null && ordered.indexOf(it) > 0 }
        reach(model, exam)
        model.start(exam.id)
        train(model)
        val question = model.training.finish(effort = 8)!!.exam!!
        val result = model.saveExam(question, question.tests.map { (exercise, _) -> id(exercise) != exam.headline })
        assertFalse(result.passed)
        assertTrue(result.offersRepeat(exam.headline))

        // The block: from the class after the previous exam (or the first class) to this exam.
        val (first, count) = model.examBlock(exam)!!
        val start = ordered.indexOf(first)
        assertEquals(ordered.indexOf(exam) - start + 1, count)
        assertTrue(ordered.subList(start, ordered.indexOf(exam)).none { it.isExam })
        assertTrue(start == 0 || ordered[start - 1].isExam)

        // Repeat N classes: a level replay from the block's first class, under Android's names.
        model.repeatBlock(exam)
        assertEquals(first.levelNumber.toString(), store.get("streetgym_progress.replay_level"))
        assertEquals(first.id, store.get("streetgym_progress.replay_from_program_id"))
        assertNull(store.get("streetgym_progress.replay_repeated_ids"))
        var today = model.today()
        assertEquals(first.id, today.next!!.id)
        assertEquals(TodayLabel.Exam, today.label)
        assertTrue(today.canComplete)

        // Complete day on a class done before moves Today on; it was done already, so the history stays.
        val history = model.progress.sessionHistory().size
        model.completeDay(first.id)
        assertEquals(history, model.progress.sessionHistory().size)
        assertEquals("[\"${first.id}\"]", store.get("streetgym_progress.replay_repeated_ids"))
        assertEquals(ordered[start + 1].id, model.today().next!!.id)

        // Through the block to the exam, which is taken again; then the replay ends where the athlete had stopped.
        ordered.subList(start + 1, ordered.indexOf(exam)).forEach { model.completeDay(it.id) }
        today = model.today()
        assertEquals(exam.id, today.next!!.id)
        assertEquals(Start.STARTED, model.start(exam.id))
        train(model)
        val again = model.training.finish(effort = 7)!!.exam!!
        assertTrue(again.offerRepeat)
        today = model.today()
        assertEquals(ordered[ordered.indexOf(exam) + 1].id, today.next!!.id)
        assertNull(today.label)
        assertNull(store.get("streetgym_progress.replay_level"))
    }

    @Test
    fun aRepeatedExamSavesItsResultsButOffersNoBlock() {
        val model = model()
        val exam = ordered.first { it.isExam }
        reach(model, exam)
        model.progress.completePrograms(listOf(exam.id), at = time - DAY)
        assertEquals(Start.STARTED, model.start(exam.id, repeat = true))
        train(model)
        val question = model.training.finish(effort = 7)!!.exam!!
        assertFalse(question.offerRepeat)
        val result = model.saveExam(question, question.tests.map { false })
        assertEquals(question.tests.size, result.missed.size)
        assertEquals(result, model.examResult(exam.id))
    }

    @Test
    fun targetsOfSwitchedOffSkillsOrEquipmentAreLeftOut() {
        val exam = ordered.first { candidate ->
            candidate.isExam && candidate.tests.any { EquipmentNeeds.of(it.equipment).isNotEmpty() } &&
                candidate.tests.any { test -> test.movementId?.let(SkillSwitches.HOMES::get).orEmpty().isNotEmpty() }
        }
        // The equipment one target needs (every option of it) and the skill of another, switched off as Settings keeps them.
        val needy = exam.tests.first { EquipmentNeeds.of(it.equipment).isNotEmpty() }
        val skilled = exam.tests.first { test -> test.movementId?.let(SkillSwitches.HOMES::get).orEmpty().isNotEmpty() }
        val store = MemoryStore()
        val settings = AppSettings(store)
        settings.equipment = EquipmentSwitches(EquipmentNeeds.of(needy.equipment).first())
        settings.skillSwitches = SkillSwitches(SkillSwitches.HOMES.getValue(skilled.movementId!!).toMap())
        assertNotNull(store.get("streetgym_settings.equipment_off"))
        assertNotNull(store.get("streetgym_settings.skill_switches"))
        val model = model(store)
        reach(model, exam)
        model.start(exam.id)
        train(model)

        val question = model.training.finish(effort = 7)?.exam
        val asked = question?.tests.orEmpty().map { it.first }
        assertFalse(needy in asked)
        assertFalse(skilled in asked)
        assertTrue(asked.all { exercise ->
            exercise.movementId?.let(settings.skillSwitches::isOff) != true && !settings.equipment.isOff(exercise)
        })
    }

    @Test
    fun anExamTakenByTapsOffersTheBlockAndTodaySaysSo() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        val exam = ordered.first { it.isExam && it.headline != null }
        reach(model, exam)
        val first = model.examBlock(exam)!!.first
        // The exam's page says what it asks.
        val app = Opener(Screen.Class(exam.id))
        setContent { key(app.screen) { StreetGymApp(model, PaddingValues(), startAt = app.screen.first) } }
        onNodeWithText(text(Res.string.exam_card_untaken)).performScrollTo().assertExists()

        model.start(exam.id)
        train(model)
        onNodeWithText("RESUME").performScrollTo().performClick()
        onNodeWithText("FINISH WORKOUT").performClick()
        onNodeWithText("FINISH").performClick()

        // The targets, all reached as ticked; the headline unticked, then saved.
        onNodeWithText("Exam: tick the targets you reached").assertExists()
        val headline = exam.tests.first { id(it) == exam.headline }
        onNodeWithText(target(headline)).assertIsOn().performClick()
        onNodeWithText(target(headline)).assertIsOff()
        onNodeWithText("SAVE RESULTS").performClick()

        // Not every target yet: the missed one named, and the block offered.
        val (_, count) = model.examBlock(exam)!!
        onNodeWithText("Not every target yet").assertExists()
        onNodeWithText(text(Res.string.exam_missed_message, headline.name.resolve(), count,
            text(Res.string.exam_class_label, first.levelNumber, first.number))).assertExists()
        onNodeWithText("REPEAT $count CLASSES").performClick()

        // Today goes back to the block's first class and says why.
        onNodeWithText(text(Res.string.exam_replay_today)).assertExists()
        assertEquals(first.id, model.today().next!!.id)
        assertEquals(first.id, store.get("streetgym_progress.replay_from_program_id"))

        // The exam's page now tells how it went.
        val latest = model.examResult(exam.id)!!
        val date = mediumDate(latest.at, TimeZone.UTC)
        assertEquals("Nov 18, 2026", date)
        val card = text(Res.string.exam_card_result, date, latest.reached.size, latest.reached.size + latest.missed.size) + " " +
            text(Res.string.exam_card_missed, headline.name.resolve())
        app.open(Screen.Class(exam.id))
        onNodeWithText(card).performScrollTo().assertExists()
    }

    @Test
    fun anExamPassedSaysSoAndAnOrdinaryMissIsTestedAgainLater() = runComposeUiTest {
        val model = model()
        val exam = ordered.first { candidate -> candidate.isExam && candidate.tests.count { id(it) != candidate.headline } >= 2 }
        reach(model, exam)
        model.start(exam.id)
        train(model)
        val app = Opener(Screen.Workout(exam.id))
        setContent { key(app.screen) { StreetGymApp(model, PaddingValues(), startAt = app.screen.first) } }
        onNodeWithText("FINISH WORKOUT").performClick()
        onNodeWithText("FINISH").performClick()
        // One target that isn't the headline missed: saved, to be tested again at the next exam.
        val missed = exam.tests.first { id(it) != exam.headline }
        onNodeWithText(target(missed)).performClick()
        onNodeWithText("SAVE RESULTS").performClick()
        onNodeWithText(text(Res.string.exam_saved_not_yet)).assertExists()
        onNodeWithText("Not every target yet").assertDoesNotExist()
        assertEquals(listOf(id(missed)), model.examResult(exam.id)!!.missed)
        assertNull(model.levelReplay())

        // Taken again later as a repeat, every target reached: passed.
        time += DAY
        assertEquals(Start.STARTED, model.start(exam.id, repeat = true))
        train(model)
        app.open(Screen.Workout(exam.id))
        onNodeWithText("FINISH WORKOUT").performClick()
        onNodeWithText("FINISH").performClick()
        onNodeWithText("SAVE RESULTS").performClick()
        onNodeWithText("Exam passed").assertExists()
        onNodeWithText("Every target reached. The next block starts with your next class.").assertExists()
        onNodeWithText("OK").performClick()
        assertTrue(model.examResult(exam.id)!!.passed)
    }

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /** Opens the app afresh on a screen, as a new start of the app would: [screen] keys the app. */
    private class Opener(start: Screen) {
        var screen by mutableStateOf(start to 0)
            private set

        fun open(next: Screen) { screen = next to screen.second + 1 }
    }

    /** A target as the exam's question lists it: "Name · 5 reps · per side" (Android: examTarget). */
    private fun target(exercise: TrainingExercise): String = runBlocking {
        val set = exercise.sets.first()
        val dose = set.repetitions?.let { getPluralString(Res.plurals.repetitions_format, it, it) }
            ?: set.durationSeconds?.let { getString(Res.string.focus_seconds, it) }
        (listOfNotNull(exercise.name.resolve(), dose) + SetNotes.parse(set.description.resolve()).sides).joinToString(" · ")
    }

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
