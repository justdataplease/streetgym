package com.justdataplease.streetgym.data.progress

/**
 * Going back to an earlier level after a break, without losing anything. Completed days and history stay as they
 * are; Today restarts at the first day of [levelNumber] and moves forward through the days repeated since
 * ([repeatedIds]). The replay ends by itself when it reaches the athlete's furthest day.
 */
data class LevelReplay(val levelNumber: Int, val repeatedIds: Set<String> = emptySet())

object DayAssignment {
    /**
     * The day to train next in [ordered] (the whole sequence, in order). Without a replay it is the first day never
     * completed. With one it is the first day at or after the replayed level not yet repeated, until that would pass
     * the first day never completed: the athlete is then back where they stopped.
     */
    fun <T> next(
        ordered: List<T>,
        completed: Set<String>,
        replay: LevelReplay?,
        id: (T) -> String,
        level: (T) -> Int,
    ): T? {
        val furthest = ordered.indexOfFirst { id(it) !in completed }
        if (replay == null) return ordered.getOrNull(furthest)
        val repeat = ordered.indexOfFirst { level(it) >= replay.levelNumber && id(it) !in replay.repeatedIds }
        return when {
            repeat < 0 -> ordered.getOrNull(furthest)
            furthest < 0 || repeat < furthest -> ordered[repeat]
            else -> ordered[furthest]
        }
    }

    /** True while [replay] still assigns a day before the furthest one; false once it has caught up. */
    fun <T> replaying(
        ordered: List<T>,
        completed: Set<String>,
        replay: LevelReplay?,
        id: (T) -> String,
        level: (T) -> Int,
    ): Boolean = replay != null &&
        next(ordered, completed, replay, id, level) != next(ordered, completed, null, id, level)
}
