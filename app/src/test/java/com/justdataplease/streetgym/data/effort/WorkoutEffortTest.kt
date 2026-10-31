package com.justdataplease.streetgym.data.effort

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.skills.SkillStandards
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Easy, Moderate or Hard for one athlete (the owner, 2.9.0): their level's typical class and their best sets. */
class WorkoutEffortTest {
    private val grades = mapOf("pushup" to 1, "pullup" to 2, "tuck_planche" to 3, "full_front" to 5, "march" to 1)

    private fun step(movement: String, sets: Int, reps: Int? = null, seconds: Int? = null, rest: Int = 90) = TrainingExercise(
        id = movement, movementId = movement, number = 1, name = LocalizedText(movement), description = LocalizedText.EMPTY,
        sets = List(sets) { TrainingSet("$movement:$it", it + 1, LocalizedText.EMPTY, LocalizedText.EMPTY, reps, false, seconds, rest) },
    )

    private fun day(vararg steps: TrainingExercise, level: Int = 1, type: String = "build_a", rpe: Int? = 6) = TrainingProgram(
        id = "d", number = 1, levelNumber = level, name = LocalizedText("d"), description = LocalizedText.EMPTY, hasDetails = true,
        proPlaceholder = false, week = 1, day = 1, estimatedMinutes = 30, exercises = steps.toList(), targetRpe = rpe, dayType = type,
    )

    /** A level whose typical class is ten push-up sets and two pull-up sets: a load of 13. */
    private val level = listOf(day(step("pushup", 10, reps = 10), step("pullup", 2, reps = 3)))

    @Test
    fun aWorkoutIsRatedAgainstTheTypicalClassOfTheAthletesLevel() {
        val effort = WorkoutEffort(grades, emptyMap(), 1, level)
        assertEquals(Effort.EASY, effort.rate(day(step("pushup", 6, reps = 8)), onProgram = false))
        assertEquals(Effort.MODERATE, effort.rate(day(step("pushup", 8, reps = 8), step("pullup", 2, reps = 3)), onProgram = false))
        // Two full front lever sets weigh 8: with nine push-up sets the day is far over the typical 13.
        assertEquals(Effort.HARD, effort.rate(day(step("pushup", 9, reps = 8), step("full_front", 2, seconds = 3)), onProgram = false))
    }

    @Test
    fun theAthletesBestsMakeTheSameDayEasierOrHarder() {
        val workout = day(step("pullup", 7, reps = 5))
        assertEquals(Effort.MODERATE, WorkoutEffort(grades, emptyMap(), 1, level).rate(workout, onProgram = false))
        // Far under a best of 20 pull-ups the sets weigh 0.7: an easy day for a strong puller.
        assertEquals(Effort.EASY, WorkoutEffort(grades, mapOf("pullup" to 20), 1, level).rate(workout, onProgram = false))
        // Past a best of 4 the day is new ground: hard.
        assertEquals(Effort.HARD, WorkoutEffort(grades, mapOf("pullup" to 4), 1, level).rate(workout, onProgram = false))
    }

    @Test
    fun shortRestsWeighMore() {
        val effort = WorkoutEffort(grades, emptyMap(), 1, level)
        assertEquals(Effort.MODERATE, effort.rate(day(step("pushup", 14, reps = 8)), onProgram = false))
        assertEquals(Effort.HARD, effort.rate(day(step("pushup", 14, reps = 8, rest = 30)), onProgram = false))
    }

    @Test
    fun theProgramsOwnLabelsCountAtTheAthletesLevel() {
        val effort = WorkoutEffort(grades, emptyMap(), 1, level)
        val light = step("pushup", 6, reps = 8)
        assertEquals(Effort.HARD, effort.rate(day(light, rpe = 8)))
        assertEquals(Effort.MODERATE, effort.rate(day(light, type = "exam_block", rpe = 7)))
        // A class of another level is rated by its load alone.
        assertEquals(Effort.EASY, effort.rate(day(light, level = 2, rpe = 8)))
    }

    @Test
    fun theHardestExerciseIsTheIcon() {
        val effort = WorkoutEffort(grades, emptyMap(), 1, level)
        assertEquals("full_front", effort.hardest(day(step("pushup", 10, reps = 20), step("full_front", 1, seconds = 3)))?.movementId)
        assertEquals("pullup", effort.hardest(day(step("pushup", 2, reps = 5), step("pullup", 3, reps = 5)))?.movementId)
    }

    @Test
    fun everyMovementHasAGradeAndTheSkillsMatchTheirStandards() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val ids = JSONObject(File("src/main/assets/curriculum.json").readText()).getJSONArray("movement_library")
            .let { all -> (0 until all.length()).map { all.getJSONObject(it).getString("id") } }
        assertEquals(ids.toSet(), catalog.movementGrades.keys)
        assertTrue(catalog.movementGrades.values.all { it in 1..5 })
        SkillStandards.ALL.forEach { (id, standard) -> assertEquals(id, standard.grade, catalog.movementGrades[id]) }
    }

    @Test
    fun theProgramSpreadsItsDaysOverTheThreeMarks() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        catalog.levels.forEach { level ->
            val effort = WorkoutEffort(catalog.movementGrades, emptyMap(), level.number, level.programs)
            val marks = level.programs.groupingBy { effort.rate(it) }.eachCount()
            // Most days Moderate, a fifth to a third Easy; from Level 2 the heaviest days Hard, even with no best set yet.
            assertTrue("Level ${level.number}: $marks", (marks[Effort.MODERATE] ?: 0) >= 50 && (marks[Effort.EASY] ?: 0) >= 15)
            assertTrue("Level ${level.number}: $marks", level.number == 1 || (marks[Effort.HARD] ?: 0) >= 5)
            level.programs.filter { it.isExam }.forEach { assertTrue(it.id, effort.rate(it) != Effort.EASY) }
            level.programs.filter { "recovery" in it.dayType.orEmpty() }.forEach { assertTrue(it.id, effort.rate(it) != Effort.HARD) }
        }
    }
}
