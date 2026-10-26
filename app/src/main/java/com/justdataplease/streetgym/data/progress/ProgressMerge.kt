package com.justdataplease.streetgym.data.progress

/**
 * Pro's cloud copy meets this device's progress: completed days are united and history keeps every session either
 * side has, once. Nothing is ever removed by a merge.
 */
object ProgressMerge {
    fun completed(local: Set<String>, cloud: Collection<String>): Set<String> = local + cloud

    fun sessions(local: List<WorkoutSession>, cloud: List<WorkoutSession>): List<WorkoutSession> =
        (local + cloud).distinctBy { Triple(it.programId, it.startedAtEpochMillis, it.completedAtEpochMillis) }
            .sortedBy { it.completedAtEpochMillis }
}
