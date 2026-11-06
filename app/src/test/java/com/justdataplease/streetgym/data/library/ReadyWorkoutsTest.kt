package com.justdataplease.streetgym.data.library

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.effort.WorkoutEffort
import com.justdataplease.streetgym.json.JSONObject
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
        assertEquals(217, catalog.workouts.size)
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
    fun aBeginnerFindsMoreSteadyDaysThanEasyOnes() {
        // 2.10.2, the owner: "a lot are easy; remove some easy ones and have some medium". For a new athlete at Level 1
        // or 3 the Library has more Moderate days than Easy ones, and still some of each in its own grades.
        listOf(1, 3).forEach { level ->
            val effort = WorkoutEffort(catalog.movementGrades, emptyMap(), level, catalog.levels.first { it.number == level }.programs)
            val rated = catalog.workouts.groupingBy { effort.rate(it, onProgram = false) }.eachCount()
            assertTrue("Level $level: $rated", rated.getValue(Effort.MODERATE) > rated.getValue(Effort.EASY))
            val own = catalog.workouts.filter { it.grade == (level + 1) / 2 }.groupingBy { effort.rate(it, onProgram = false) }.eachCount()
            assertTrue("Level $level, grade ${(level + 1) / 2}: $own", (own[Effort.MODERATE] ?: 0) >= 15 && (own[Effort.EASY] ?: 0) >= 15)
        }
    }

    @Test
    fun fourAchesAndPostureWorkoutsAreFree() {
        // The owner (2026-11-05): three free on every tier, for the neck and the back from desk work; the rest stay Pro.
        // Chest Opener joined them on 2026-11-06.
        val free = catalog.workouts.filter { it.free }
        assertEquals(setOf("ready:neck_at_the_desk", "ready:upper_back_reset", "ready:chest_opener", "ready:low_back_basics"), free.map { it.id }.toSet())
        assertTrue(free.all { it.group == "problems" && it.grade == 1 })
        // The category opens the Library, its free workouts first; each says when to stop and see someone.
        assertEquals(free.map { it.id }, catalog.workouts.take(4).map { it.id })
        catalog.workouts.filter { it.group == "problems" }.forEach { it.safety.resolve().let { s -> assertTrue(it.id, "professional" in s || "urgent help" in s) } }
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
    fun theProOfferNamesThemWithoutACount() {
        // The owner (2.10.1): "ready workouts", no number, in the app as on Play.
        val billing = File("src/main/res/values/strings_billing.xml").readText()
        assertTrue(billing.contains("<item>Ready workouts</item>"))
        assertFalse(Regex("\\d+ ready workouts", RegexOption.IGNORE_CASE).containsMatchIn(billing))
    }

    private companion object {
        /** The groups HomeActivity.readyGroupName names. */
        val GROUPS = setOf("problems", "full", "pull", "push", "legs", "core", "skills", "short", "mobility")
        /** HomeActivity.READY_FOCUS: the groups, then the skill families. */
        val FOCUS = GROUPS + setOf("handstand", "front_lever", "back_lever", "planche", "flag", "muscleup", "lsit", "one_arm")
    }
}
