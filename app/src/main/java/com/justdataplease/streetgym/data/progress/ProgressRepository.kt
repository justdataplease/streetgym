package com.justdataplease.streetgym.data.progress

/**
 * One finished class. A [repeat] is a day the athlete had already completed and chose to train again: it stays in
 * history with its effort rating, for their own record, but counts toward nothing. Completed days, Today, level
 * progress, the level replay, skill points and every total come from [ProgressRepository.completedProgramIds],
 * which a repeat never changes; nothing reads a repeat's effort rating.
 */
data class WorkoutSession(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationSeconds: Long,
    val effortRating: Int? = null,
    val manuallyCompleted: Boolean = false,
    val repeat: Boolean = false,
)

data class ActiveWorkout(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedSetIds: Set<String> = emptySet(),
    val repeat: Boolean = false,
)

interface ProgressRepository {
    fun completedProgramIds(): Set<String>
    fun isProgramCompleted(programId: String): Boolean
    fun sessionHistory(): List<WorkoutSession>
    fun activeWorkout(): ActiveWorkout?
    /** A [repeat] trains a completed day again without counting toward anything; only completed days can be repeated. */
    fun startWorkout(
        programId: String,
        startedAtEpochMillis: Long = System.currentTimeMillis(),
        repeat: Boolean = false,
    )
    fun setCompleted(setId: String, completed: Boolean)
    fun finishWorkout(
        completedAtEpochMillis: Long = System.currentTimeMillis(),
        effortRating: Int? = null,
    ): WorkoutSession?
    fun abandonWorkout()
    fun markProgramIncomplete(programId: String)
}
