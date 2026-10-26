package com.justdataplease.streetgym.data.library

import com.justdataplease.streetgym.data.model.CatalogSource
import com.justdataplease.streetgym.data.model.ContentOrigin
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import org.junit.Assert.*
import org.junit.Test

class WorkoutLibraryTest {
    private fun set(reps: Int? = null, seconds: Int? = null, rest: Int? = 60) =
        TrainingSet("s", 1, LocalizedText.EMPTY, LocalizedText.EMPTY, reps, false, seconds, rest)
    private fun exercise(name: String, movementId: String? = null, category: String? = null, vararg sets: TrainingSet) =
        TrainingExercise(name, movementId, 1, LocalizedText(name), LocalizedText.EMPTY, sets.toList(), category, listOf("bar"))
    private fun program(id: String, vararg exercises: TrainingExercise) = TrainingProgram(id, 1, 1, LocalizedText(id),
        LocalizedText.EMPTY, ContentOrigin.UNKNOWN, true, false, null, null, null, exercises.toList())
    private val catalog = CurriculumCatalog("1", CatalogSource(null, null, null), listOf(TrainingLevel("l1", 1,
        LocalizedText("Foundations"), LocalizedText.EMPTY, listOf(
            program("a", exercise("Pull-up", "pull_up", "pull", set(reps = 5)), exercise("Hollow hold", null, "core", set(seconds = 20))),
            program("b", exercise("Pull-up", "pull_up", "pull", set(reps = 8)), exercise("Dip", "dip", "push", set(reps = 6))),
        ))))

    private val day = CustomDay("custom:1", "Bar day", listOf(
        CustomExercise("pull_up", "Pull-up", "pull_up", "pull", listOf("bar"), List(3) { CustomSet(repetitions = 5, breakSeconds = 90) }),
        CustomExercise("hollow hold", "Hollow hold", null, "core", emptyList(), List(2) { CustomSet(durationSeconds = 30, breakSeconds = 30) }),
    ), updatedAt = 10)

    @Test fun theLibraryHasEachExerciseOnceWithItsFirstDoseAndTheMostUsedFirst() {
        val library = ExerciseLibrary.from(catalog)
        assertEquals(listOf("Pull-up", "Hollow hold", "Dip"), library.movements.map { it.name })
        val pullUp = library.movements.first()
        assertEquals(2, pullUp.uses)
        assertEquals(CustomSet(repetitions = 5, breakSeconds = 60), pullUp.sample)
        assertEquals(CustomSet(durationSeconds = 20, breakSeconds = 60), library.movements[1].sample)
    }

    @Test fun searchMatchesEveryWordInNameCategoryOrEquipment() {
        val library = ExerciseLibrary.from(catalog)
        assertEquals(listOf("Pull-up"), library.search("pull").map { it.name })
        assertEquals(listOf("Hollow hold"), library.search("core hold").map { it.name })
        assertEquals(3, library.search("bar").size)
        assertTrue(library.search("rings").isEmpty())
        assertEquals(3, library.search("  ").size)
    }

    @Test fun aCustomDayRunsAsAProgramWithItsSetsAndRest() {
        val program = CustomDays.toProgram(day)
        assertEquals("custom:1", program.id)
        assertEquals(0, program.levelNumber)
        assertEquals(listOf("Pull-up", "Hollow hold"), program.exercises.map { it.name.resolve() })
        assertEquals(listOf(5, 5, 5), program.exercises[0].sets.map { it.repetitions })
        assertEquals(listOf(30, 30), program.exercises[1].sets.map { it.durationSeconds })
        assertEquals(90, program.exercises[0].sets[0].breakSeconds)
        assertEquals(program.exercises.flatMap { it.sets }.map { it.id }.distinct().size, 5)
        // 3 x (15 + 90) + 2 x (30 + 30) = 435 s
        assertEquals(8, program.estimatedMinutes)
        assertTrue(CustomDays.isCustom(program.id) && !CustomDays.isCustom("sg:l01:p0001"))
    }

    @Test fun savedAndCustomDaysShareTheCountAndTombstonesDoNotCount() {
        val state = LibraryState(
            saved = mapOf("a" to SavedMark(true, 5), "b" to SavedMark(false, 6), "c" to SavedMark(true, 7)),
            days = mapOf(day.id to day, "custom:2" to day.copy(id = "custom:2", deleted = true)),
        )
        assertEquals(listOf("c", "a"), state.savedIds)
        assertEquals(listOf(day), state.customDays)
        assertEquals(3, state.used)
    }

    @Test fun mergeKeepsTheNewerChangeOfEachItem() {
        val phone = LibraryState(saved = mapOf("a" to SavedMark(true, 10)), days = mapOf(day.id to day))
        val cloud = LibraryState(
            saved = mapOf("a" to SavedMark(false, 20), "b" to SavedMark(true, 1)),
            days = mapOf(day.id to day.copy(name = "Old", updatedAt = 5), "custom:9" to day.copy(id = "custom:9")),
        )
        val merged = phone.merge(cloud)
        assertEquals(listOf("b"), merged.savedIds)
        assertEquals("Bar day", merged.days.getValue(day.id).name)
        assertEquals(setOf(day.id, "custom:9"), merged.days.keys)
        assertEquals(merged, cloud.merge(phone))
    }

    @Test fun aDeletionWinsOverAnOlderCopy() {
        val deleted = day.copy(exercises = emptyList(), updatedAt = 50, deleted = true)
        val merged = LibraryState(days = mapOf(day.id to deleted)).merge(LibraryState(days = mapOf(day.id to day)))
        assertTrue(merged.customDays.isEmpty())
    }

    @Test fun theCodecRoundTrips() {
        val state = LibraryState(saved = mapOf("a" to SavedMark(true, 3)), days = mapOf(day.id to day))
        assertEquals(state, LibraryCodec.decode(LibraryCodec.encode(state)))
        assertEquals(LibraryState(), LibraryCodec.decode(null))
    }
}
