package com.justdataplease.streetgym.data.model

import java.util.Locale

data class CurriculumCatalog(
    val schemaVersion: String,
    val levels: List<TrainingLevel>,
    /** Pro's ready workouts (assets/workouts.json): days outside the ten levels, run any time. */
    val workouts: List<TrainingProgram> = emptyList(),
    /** Each movement's grade, 1 easy to 5 extreme (the movement library): what WorkoutEffort weighs a set by. */
    val movementGrades: Map<String, Int> = emptyMap(),
)

data class TrainingLevel(
    val id: String,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val programs: List<TrainingProgram>,
)

data class TrainingProgram(
    val id: String,
    val number: Int,
    val levelNumber: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val hasDetails: Boolean,
    val proPlaceholder: Boolean,
    val week: Int?,
    val day: Int?,
    val estimatedMinutes: Int?,
    val exercises: List<TrainingExercise>,
    val readiness: LocalizedText = LocalizedText.EMPTY,
    val safety: LocalizedText = LocalizedText.EMPTY,
    val targetRpe: Int? = null,
    val dayType: String? = null,
    val focusTags: List<String> = emptyList(),
    val practice: List<TrainingExercise> = emptyList(),
    val warmup: List<TrainingExercise> = emptyList(),
    val cooldown: List<TrainingExercise> = emptyList(),
    /** An exam's headline skill (a movement id): missing it offers to repeat the block ([ExamResult.offersRepeat]). */
    val headline: String? = null,
    /** A ready workout's group in the Library (pull, push, legs…) and its grade: 1 beginner, 2 intermediate, 3 advanced. */
    val group: String? = null,
    val grade: Int? = null,
) {
    /** An exam day (about every 20 classes): its [TrainingExercise.assessment] sets are the targets to reach. */
    val isExam: Boolean get() = dayType?.startsWith("exam") == true
}

/**
 * One exercise of a class. [easier]: the step's easier rung, which the athlete may do instead and is credited for.
 * [readinessCheck]: a single test set that tells the athlete whether to move on; it isn't credited as training.
 * [assessment]: one recorded set at the number shown; on an exam day it is one of the exam's targets.
 */
data class TrainingExercise(
    val id: String,
    val movementId: String? = null,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val sets: List<TrainingSet>,
    val category: String? = null,
    val equipment: List<String> = emptyList(),
    val easier: EasierOption? = null,
    val readinessCheck: Boolean = false,
    val assessment: Boolean = false,
)

data class EasierOption(val movementId: String, val name: LocalizedText)

data class TrainingSet(
    val id: String,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val repetitions: Int?,
    val repetitionsMax: Boolean,
    val durationSeconds: Int?,
    val breakSeconds: Int?,
)

data class LocalizedText(
    val en: String? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    fun resolve(locale: Locale = Locale.ENGLISH): String =
        en?.takeIf(String::isNotBlank).orEmpty()

    companion object {
        val EMPTY = LocalizedText()
    }
}
