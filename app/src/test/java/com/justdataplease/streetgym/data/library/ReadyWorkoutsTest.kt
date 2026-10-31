package com.justdataplease.streetgym.data.library

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.effort.WorkoutEffort
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pro's ready workouts (assets/workouts.json, written by tools/build_ready_workouts.py), read as the app reads them. */
class ReadyWorkoutsTest {
    private val root = JSONObject(File("src/main/assets/curriculum.json").readText())
    private val workouts = JSONObject(File("src/main/assets/workouts.json").readText()).getJSONArray("workouts")
    private val catalog = CurriculumJsonParser.parse(root, workouts)
    private val movements = root.getJSONArray("movement_library").let { all -> (0 until all.length()).map { all.getJSONObject(it).getString("id") }.toSet() }

    @Test
    fun everyReadyWorkoutIsAWholeClassOfTheProgramsMovements() {
        assertEquals(200, catalog.workouts.size)
        assertEquals(catalog.workouts.size, catalog.workouts.map { it.id }.toSet().size)
        assertEquals(catalog.workouts.size, catalog.workouts.map { it.name.resolve() }.toSet().size)
        catalog.workouts.forEach { workout ->
            assertTrue(workout.id, ReadyWorkouts.isReady(workout.id) && CustomDays.offProgram(workout.id))
            assertFalse(workout.id, CustomDays.isCustom(workout.id))
            assertEquals(workout.id, 0, workout.levelNumber)
            assertTrue(workout.id, workout.group in GROUPS && workout.grade in 1..5)
            assertTrue(workout.id, (workout.estimatedMinutes ?: 0) in 10..60 && workout.description.resolve().isNotBlank())
            assertTrue(workout.id, workout.warmup.isNotEmpty() && workout.exercises.isNotEmpty() && workout.cooldown.isNotEmpty())
            (workout.warmup + workout.exercises + workout.cooldown).forEach { exercise ->
                assertTrue("${workout.id}: ${exercise.movementId}", exercise.movementId in movements)
                assertTrue(exercise.id, exercise.name.resolve().isNotBlank())
                assertTrue(exercise.id, exercise.sets.all { it.repetitions != null || it.repetitionsMax || it.durationSeconds != null })
            }
        }
    }

    @Test
    fun everyFocusChipHasWorkoutsAndABeginnerFindsEasyOnes() {
        // The Library's focus filter (2.9.1): each workout is tagged with its group and the skill families it trains.
        assertEquals(FOCUS, catalog.workouts.flatMap { it.focusTags }.toSet())
        catalog.workouts.forEach { assertTrue(it.id, it.group in it.focusTags && FOCUS.containsAll(it.focusTags)) }
        fun easy(level: Int, focus: String): Int {
            val effort = WorkoutEffort(catalog.movementGrades, emptyMap(), level, catalog.levels.first { it.number == level }.programs)
            return catalog.workouts.count { focus in it.focusTags && effort.rate(it, onProgram = false) == Effort.EASY }
        }
        // The owner: "the library has no easy programs". A new athlete finds easy ones in every focus but one-arm work.
        (FOCUS - "one_arm").forEach { assertTrue("Level 1, $it", easy(1, it) > 0) }
        FOCUS.forEach { assertTrue("Level 5, $it", easy(5, it) > 0) }
        assertTrue(catalog.workouts.count { it.grade == 1 } >= 50)
    }

    @Test
    fun theyStayOutOfTheTenLevels() {
        val classes = CurriculumJsonParser.parse(root)
        assertTrue(classes.workouts.isEmpty())
        assertEquals(classes.levels, catalog.levels)
    }

    @Test
    fun theirIdsPassTheAnalyticsRules() {
        val rule = Regex("programId\\.matches\\('([^']+)'\\)").find(File("../firebase/firestore.rules").readText())!!.groupValues[1]
        catalog.workouts.forEach { assertTrue(it.id, Regex(rule).matches(it.id)) }
    }

    @Test
    fun theProOfferCountsThem() {
        assertTrue(File("src/main/res/values/strings_billing.xml").readText().contains("<item>${catalog.workouts.size} ready workouts</item>"))
    }

    private companion object {
        /** The groups HomeActivity.readyGroupName names. */
        val GROUPS = setOf("full", "pull", "push", "legs", "core", "skills", "short", "mobility")
        /** HomeActivity.READY_FOCUS: the groups, then the skill families. */
        val FOCUS = GROUPS + setOf("handstand", "front_lever", "back_lever", "planche", "flag", "muscleup", "lsit", "one_arm")
    }
}
