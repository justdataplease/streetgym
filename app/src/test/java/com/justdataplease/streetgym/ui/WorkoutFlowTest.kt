package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.data.model.EasierOption
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.skills.SkillStandard
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
    }

    @Test fun eachTickedSetKeepsItsDoseInTheSkillsMeasure() {
        val hold = exercise("wall_handstand", 2).let { e -> e.copy(sets = e.sets.map { it.copy(repetitions = null, durationSeconds = 30) }) }
        val max = exercise("pullup", 3).let { e -> e.copy(sets = e.sets.map { it.copy(repetitionsMax = true) }) }
        val flow = WorkoutFlow.of("p1", listOf("practice" to listOf(hold), "main" to listOf(max, main[1])))
        val movementOf = { exercise: TrainingExercise -> exercise.id }
        val standards = mapOf("wall_handstand" to standard(seconds = true), "pullup" to standard(), "dip" to standard())
        val ticked = flow.keys.toSet()
        val pullups = flow.steps.filter { it.exercise.id == "pullup" }
        // Holds in seconds and reps as planned; a "max" set counts what was logged, or nothing.
        val done = mapOf(pullups[0].key to 12, pullups[1].key to 9)
        assertEquals(mapOf("wall_handstand" to listOf(30, 30), "pullup" to listOf(12, 9, 0), "dip" to listOf(8, 8)),
            flow.doses(ticked, emptySet(), done, movementOf, standards::get))
        // A logged set beats the plan.
        val dip = flow.steps.first { it.exercise.id == "dip" }.key
        assertEquals(listOf(15, 8), flow.doses(ticked, emptySet(), mapOf(dip to 15), movementOf, standards::get)["dip"])
        // A movement that is no skill is left out; a set measured the other way counts 0 until logged.
        assertNull(flow.doses(ticked, emptySet(), emptyMap(), movementOf, mapOf("dip" to standard())::get)["pullup"])
        assertEquals(listOf(0, 0), flow.doses(ticked, emptySet(), emptyMap(), movementOf, mapOf("wall_handstand" to standard())::get)["wall_handstand"])
        assertEquals(listOf(30, null, 8), listOf(flow[0], flow[2], flow[5]).map { it.set.planned })
    }

    @Test fun aPracticeWindowCountsOnlyTheLongestHoldLogged() {
        val window = exercise("handstand_balance", 2).let { e -> e.copy(sets = e.sets.map { it.copy(repetitions = null, durationSeconds = 30) }) }
        val flow = WorkoutFlow.of("p1", listOf("practice" to listOf(window)))
        val standards = mapOf("handstand_balance" to standard(seconds = true, window = true))
        // The 30 s window is for attempts: it holds nothing until the athlete logs the longest clean balance.
        assertEquals(SetMeasure(timed = true, planned = null, window = true), WorkoutFlow.measure(flow[0], standards["handstand_balance"]))
        assertEquals(listOf(6, 0), flow.doses(flow.keys.toSet(), emptySet(), mapOf(flow[0].key to 6), { it.id }, standards::get)["handstand_balance"])
    }

    @Test fun anEasierOptionMeasuredTheOtherWayAsksForItsOwnNumber() {
        val row = EasierOption("tuck_front_row", LocalizedText("Tuck Front Lever Row"))
        val hold = exercise("tuck_front_hold", 2).let { e ->
            e.copy(movementId = "tuck_front_hold", easier = row, sets = e.sets.map { it.copy(repetitions = null, durationSeconds = 10) })
        }
        val flow = WorkoutFlow.of("p1", listOf("main" to listOf(hold)))
        val standards = mapOf("tuck_front_hold" to standard(seconds = true), "tuck_front_row" to standard())
        val movementOf = { exercise: TrainingExercise -> exercise.movementId }
        val easier = setOf(flow[0].exerciseKey)
        fun measure(easier: Set<String>) = WorkoutFlow.measure(flow[0], WorkoutFlow.movement(flow[0], easier, movementOf)?.let(standards::get))
        // As written, the 10 s hold counts; as rows, reps count, which the plan doesn't give.
        assertEquals(SetMeasure(timed = true, planned = 10), measure(emptySet()))
        assertEquals(SetMeasure(timed = false, planned = null), measure(easier))
        assertEquals(mapOf("tuck_front_row" to listOf(8, 0)),
            flow.doses(flow.keys.toSet(), easier, mapOf(flow[0].key to 8), movementOf, standards::get))
    }

    private fun standard(seconds: Boolean = false, window: Boolean = false) = SkillStandard(seconds, 1, listOf(1), window = window)

    @Test fun anExamTargetStartsReachedOnlyWhenEverySetWasDoneInFullAsWritten() {
        val easierOption = EasierOption("assisted_pullup", LocalizedText("Assisted Pull-Up"))
        val pullup = exercise("pullup", 2).copy(assessment = true, easier = easierOption)
        val dip = exercise("dip", 1).copy(assessment = true)
        val flow = WorkoutFlow.of("exam", listOf("practice" to practice, "main" to listOf(pullup, dip, main[0])))
        val all = flow.keys.toSet()
        fun reached(completed: Set<String> = all, easier: Set<String> = emptySet(), done: Map<String, Int> = emptyMap()) =
            flow.examTests(completed, easier, done).map { (exercise, reached) -> exercise.id to reached }
        // Only the assessment sets of the main work are targets.
        assertEquals(listOf("pullup" to true, "dip" to true), reached())
        val pullups = flow.steps.filter { it.exercise === pullup }
        assertEquals(listOf("pullup" to false, "dip" to true), reached(completed = all - pullups[1].key))
        assertEquals(listOf("pullup" to false, "dip" to true), reached(easier = setOf(pullups[0].exerciseKey)))
        // A logged set short of the 8 planned misses it; meeting or beating it doesn't.
        assertEquals(listOf("pullup" to false, "dip" to true), reached(done = mapOf(pullups[0].key to 7)))
        assertEquals(listOf("pullup" to true, "dip" to true), reached(done = mapOf(pullups[0].key to 8, pullups[1].key to 11)))
    }
}
