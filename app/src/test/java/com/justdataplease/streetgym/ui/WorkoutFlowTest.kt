package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.data.model.EasierOption
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingSet
import org.junit.Assert.*
import org.junit.Test

class WorkoutFlowTest {
    private fun set(id: String, number: Int = 0, breakSeconds: Int? = 60) =
        TrainingSet(id, number, LocalizedText.EMPTY, LocalizedText.EMPTY, 8, false, null, breakSeconds)

    private fun exercise(id: String, sets: Int) =
        TrainingExercise(id, null, 1, LocalizedText(id), LocalizedText.EMPTY, List(sets) { set("$id-s${it + 1}", it + 1) })

    private val warmup = listOf(exercise("march", 1), exercise("swings", 2))
    private val practice = listOf(exercise("wall_handstand", 2))
    private val main = listOf(exercise("pullup", 3), exercise("dip", 2))
    private val cooldown = listOf(exercise("stretch", 1))
    private fun flow(practice: List<TrainingExercise> = this.practice) = WorkoutFlow.of(
        "p1",
        listOf("warmup" to warmup, "practice" to practice, "main" to main, "cooldown" to cooldown),
    )

    @Test fun stepsFollowSectionsExercisesAndSets() {
        val flow = flow()
        assertEquals(11, flow.size)
        assertEquals(
            listOf("warmup", "warmup", "warmup", "practice", "practice", "main", "main", "main", "main", "main", "cooldown"),
            flow.steps.map { it.section },
        )
        assertEquals(listOf("march", "swings", "swings"), flow.steps.take(3).map { it.exercise.id })
        assertEquals(listOf(1, 2, 3), flow.steps.filter { it.exercise.id == "pullup" }.map { it.setNumber })
        assertTrue(flow.steps.all { it.setCount == it.exercise.sets.size })
        assertEquals(flow.size, flow.keys.distinct().size)
    }

    @Test fun keysMatchTheListViewFormat() {
        val step = flow()[6]
        assertEquals("pullup", step.exercise.id)
        assertEquals("p1:main:pullup-0:pullup-s2-1", step.key)
        assertEquals(step.key, WorkoutFlow.setKey("p1", "main", step.exercise, 0, step.set, 1))
        assertEquals(listOf("p1:practice:wall_handstand-0:wall_handstand-s1-0", "p1:practice:wall_handstand-0:wall_handstand-s2-1"), flow().keys("practice"))
    }

    @Test fun setNumberFallsBackToPosition() {
        val unnumbered = TrainingExercise("hang", null, 1, LocalizedText("Hang"), LocalizedText.EMPTY, listOf(set("a"), set("b")))
        val flow = WorkoutFlow.of("p1", listOf("main" to listOf(unnumbered)))
        assertEquals(listOf(1, 2), flow.steps.map { it.setNumber })
    }

    @Test fun nextAndPreviousCrossSectionBoundaries() {
        val flow = flow()
        assertEquals(3, flow.next(2))
        assertEquals(2, flow.previous(3))
        assertEquals("warmup", flow[2].section)
        assertEquals("practice", flow[3].section)
        assertEquals(10, flow.next(9))
        assertEquals("cooldown", flow[10].section)
        assertNull(flow.previous(0))
        assertNull(flow.next(flow.size - 1))
        assertEquals(flow.size - 1, flow.previous(flow.size))
    }

    @Test fun firstIncompleteIgnoresSetsCheckedOutOfOrder() {
        val flow = flow()
        val keys = flow.keys
        assertEquals(0, flow.firstIncomplete(emptySet()))
        val outOfOrder = setOf(keys[0], keys[2], keys[3], keys[7])
        assertEquals(1, flow.firstIncomplete(outOfOrder))
        assertEquals(4, flow.firstIncomplete(outOfOrder + keys[1]))
        assertEquals(5, flow.firstIncomplete(outOfOrder + keys[1], skipped = setOf(keys[4])))
        assertEquals(4, flow.completedCount(outOfOrder))
        assertEquals(10, flow.firstIncomplete(keys.toSet() - keys[10]))
        assertNull(flow.firstIncomplete(keys.toSet()))
        assertNull(flow.firstIncomplete(keys.take(10).toSet(), skipped = setOf(keys[10])))
        assertEquals(0, flow.firstIncomplete(setOf("p2:warmup:march-0:march-s1-0")))
    }

    @Test fun sectionIsCompleteOnlyWhenEverySetIsChecked() {
        val flow = flow()
        val warmupKeys = flow.keys("warmup")
        assertEquals(3, warmupKeys.size)
        assertFalse(flow.isSectionComplete("warmup", warmupKeys.take(2).toSet()))
        assertTrue(flow.isSectionComplete("warmup", warmupKeys.toSet()))
        assertFalse(flow.isSectionComplete("missing", warmupKeys.toSet()))
    }

    @Test fun classWithoutPracticeGoesStraightFromWarmUpToMain() {
        val flow = flow(practice = emptyList())
        assertEquals(9, flow.size)
        assertTrue(flow.keys("practice").isEmpty())
        assertFalse(flow.isSectionComplete("practice", flow.keys.toSet()))
        assertEquals(3, flow.next(2))
        assertEquals("main", flow[3].section)
        assertEquals("warmup", flow[2].section)
        assertEquals(3, flow.firstIncomplete(flow.keys("warmup").toSet()))
    }

    @Test fun emptyClassHasNoPosition() {
        val flow = WorkoutFlow.of("p1", listOf("warmup" to emptyList<TrainingExercise>(), "main" to emptyList()))
        assertEquals(0, flow.size)
        assertNull(flow.firstIncomplete(emptySet()))
        assertNull(flow.previous(0))
    }

    @Test fun exerciseNotesSplitSafetyAndRegression() {
        val notes = ExerciseNotes.parse(
            "Pull until the chin clears the bar; lower with control.; Safety: Stop for sharp elbow pain.; Regression: Assisted Pull-up",
        )
        assertEquals("Pull until the chin clears the bar; lower with control.", notes.description)
        assertEquals("Stop for sharp elbow pain.", notes.safety)
        assertEquals("Assisted Pull-up", notes.easier)
        assertEquals(ExerciseNotes("Hanging Shoulder Shrugs", "", ""), ExerciseNotes.parse("Hanging Shoulder Shrugs"))
        assertEquals(ExerciseNotes("", "", ""), ExerciseNotes.parse(""))
    }

    @Test fun setNotesSeparateTheSideFromTheTempo() {
        val notes = SetNotes.parse("per side; 2s lower, 1s pause, 1s lift, 1s reset")
        assertEquals(listOf("per side"), notes.sides)
        assertEquals(listOf("2s lower, 1s pause, 1s lift, 1s reset"), notes.cues)
        assertEquals(SetNotes(emptyList(), listOf("2 times on each side")), SetNotes.parse("2 times on each side"))
        assertEquals(SetNotes(emptyList(), emptyList()), SetNotes.parse(""))
    }

    @Test fun aFinishedClassCreditsTickedPracticeAndMainSetsPerMovement() {
        val check = exercise("tuck_planche", 1).copy(movementId = "tuck_planche", readinessCheck = true)
        val pullup = main[0].copy(movementId = "pullup", easier = EasierOption("eccentric_pullup", LocalizedText("Negative Pull-Up")))
        val flow = WorkoutFlow.of("p1", listOf("warmup" to warmup, "practice" to practice + check,
            "main" to listOf(pullup, main[1]), "cooldown" to cooldown))
        val movementOf = { exercise: TrainingExercise -> exercise.movementId ?: exercise.id }
        // Warm-up and cooldown ticks prepare and recover; unticked sets and the readiness check earn nothing.
        val ticked = flow.keys.toSet() - flow.steps.first { it.exercise.id == "dip" }.key
        assertEquals(mapOf("wall_handstand" to 2, "pullup" to 3, "dip" to 1), flow.credits(ticked, emptySet(), movementOf))
        // Pull-ups done as their easier option count for the negative instead.
        val easier = setOf(flow.steps.first { it.exercise.id == "pullup" }.exerciseKey)
        assertEquals(mapOf("wall_handstand" to 2, "eccentric_pullup" to 3, "dip" to 1), flow.credits(ticked, easier, movementOf))
        assertEquals(emptyMap<String, Int>(), flow.credits(emptySet(), easier, movementOf))
        // The ticked readiness check is asked about instead.
        assertEquals(listOf("tuck_planche"), flow.readinessChecks(ticked).map { it.id })
        assertEquals(emptyList<TrainingExercise>(), flow.readinessChecks(emptySet()))
    }
}
