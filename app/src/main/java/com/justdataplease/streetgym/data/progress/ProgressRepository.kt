package com.justdataplease.streetgym.data.progress

/**
 * One finished class. [credits] holds the sets ticked per movement in its practice and main work, with an easier
 * option credited to the easier movement; null for a class saved before ticks were kept. A [repeat] is a day the
 * athlete had already completed and chose to train again: its ticked sets earn skill points like any class, but
 * completed days, Today, level progress and the level replay come from [ProgressRepository.completedProgramIds],
 * which a repeat never changes. [manuallyCompleted]: marked done without training, by "Complete day" or "Complete
 * level" (worth nothing) or by the placement test ([placed], credited as trained). [intensity]: the day's Easier,
 * Normal or Harder.
 */
data class WorkoutSession(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationSeconds: Long,
    val effortRating: Int? = null,
    val manuallyCompleted: Boolean = false,
    val repeat: Boolean = false,
    val credits: Map<String, Int>? = null,
    val placed: Boolean = false,
    val intensity: Intensity = Intensity.NORMAL,
)

/** A class in progress. [easierExercises]: the exercises the athlete does as their easier option (WorkoutFlow.exerciseKey). */
data class ActiveWorkout(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedSetIds: Set<String> = emptySet(),
    val repeat: Boolean = false,
    val easierExercises: Set<String> = emptySet(),
    val intensity: Intensity = Intensity.NORMAL,
)

interface ProgressRepository {
    fun completedProgramIds(): Set<String>
    fun isProgramCompleted(programId: String): Boolean
    fun sessionHistory(): List<WorkoutSession>
    fun activeWorkout(): ActiveWorkout?
    /** A [repeat] trains a completed day again without moving the program on; only completed days can be repeated. */
    fun startWorkout(
        programId: String,
        startedAtEpochMillis: Long = System.currentTimeMillis(),
        repeat: Boolean = false,
        intensity: Intensity = Intensity.NORMAL,
    )
    fun setCompleted(setId: String, completed: Boolean)
    /** The athlete does an exercise of the active workout as its easier option, or goes back to the prescribed one. */
    fun setEasier(exerciseKey: String, easier: Boolean)
    /** Easier, Normal or Harder for the active workout, until it finishes. */
    fun setIntensity(intensity: Intensity) {}
    fun finishWorkout(
        completedAtEpochMillis: Long = System.currentTimeMillis(),
        effortRating: Int? = null,
        credits: Map<String, Int>? = null,
    ): WorkoutSession?
    /** Logged skill tests and readiness checks ([SkillTestLog]). */
    fun skillTests(): SkillTestLog
    fun recordSkillTest(skillId: String, passed: Boolean, at: Long = System.currentTimeMillis())
    /** Exam results ([ExamLog]); a repository without them keeps none. */
    fun examLog(): ExamLog = ExamLog()
    fun recordExam(result: ExamResult) {}
    fun abandonWorkout()
    fun markProgramIncomplete(programId: String)
}
