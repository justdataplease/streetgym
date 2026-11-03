package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff

/**
 * The class the sequence offers next, for Today and the home-screen widget alike: the catalog in training order and
 * [DayAssignment.next] over it. There is no calendar; a class is simply the next one until it is done or skipped.
 */
object NextClass {
    /** Every class, level by level, in the order they are trained. */
    fun ordered(catalog: CurriculumCatalog): List<TrainingProgram> =
        catalog.levels.sortedBy { it.number }.flatMap { level -> level.programs.sortedBy { it.number } }

    /**
     * The classes that count as passed: those completed, and those the athlete's skill switches leave with nothing to
     * train ([switchedOff]), which the sequence steps over.
     */
    fun passed(ordered: List<TrainingProgram>, completed: Set<String>, switches: SkillSwitches): Set<String> =
        if (!switches.any) completed else completed + ordered.filter { it.switchedOff(switches) }.map(TrainingProgram::id)

    /** The next class in [ordered], or null once every class is complete. */
    fun of(ordered: List<TrainingProgram>, completed: Set<String>, replay: LevelReplay?): TrainingProgram? =
        DayAssignment.next(ordered, completed, replay, TrainingProgram::id, TrainingProgram::levelNumber)
}
