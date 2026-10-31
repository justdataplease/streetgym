package com.justdataplease.streetgym.data.points

import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.data.skills.SkillCredit
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillMovement
import com.justdataplease.streetgym.data.skills.SkillStandards
import org.junit.Assert.assertEquals
import org.junit.Test

class PointsTest {
    @Test fun aSetIsRepsTimesDifficultyBothSidesCounted() {
        assertEquals(24.0, Points.set(8, timed = false, sides = 1, difficulty = 3), 1e-9)
        assertEquals(20.0, Points.set(30, timed = true, sides = 1, difficulty = 2), 1e-9)
        assertEquals(20.0, Points.set(5, timed = false, sides = 2, difficulty = 2), 1e-9)
        // Difficulty stays 1 to 5.
        assertEquals(5.0, Points.set(1, timed = false, sides = 1, difficulty = 9), 1e-9)
    }

    @Test fun everyRankIsWorthOneHundredTimesTheSkillsDifficulty() {
        val book = SkillBook.build(SkillBook.DEFAULT_FAMILIES.flatMap { it.ladder }.associateWith { SkillMovement(it, it, "skill", emptySet(), null) },
            emptyList())
        fun ranks(id: String, level: SkillLevel) = book.progress(listOf(SkillCredit("custom:$id", mapOf(id to level.sets),
            doses = mapOf(id to List(level.sets) { SkillStandards.of(id).target(level)!! })))).statuses.filter { it.skill.id == id }
        // A push-up (difficulty 1) at rank 2: 200; the full planche (5) at rank 1: 500.
        assertEquals(1, SkillStandards.of("pushup").grade)
        assertEquals(200, Points.chevrons(ranks("pushup", SkillLevel.INTERMEDIATE)))
        assertEquals(500, Points.chevrons(ranks("full_planche", SkillLevel.BEGINNER)))
        assertEquals(0, Points.chevrons(book.progress(emptyList()).statuses))
    }

    @Test fun classesEarnTheirPointsAndCompleteDayNothing() {
        val sessions = listOf(
            WorkoutSession("a", 1, 2, 1, points = 120),
            WorkoutSession("b", 1, 2, 1, points = 80, repeat = true),
            WorkoutSession("c", 1, 2, 0, manuallyCompleted = true, points = 999),
            WorkoutSession("d", 1, 2, 1),
        )
        assertEquals(200, Points.classes(sessions))
    }

    @Test fun theMonthIsTheUtcMonth() {
        // 2026-10-31 23:30 UTC is still October; an hour later it is November.
        val lateOctober = 1_793_489_400_000L
        assertEquals("2026-10", Points.month(lateOctober))
        assertEquals("2026-11", Points.month(lateOctober + 3_600_000L))
        assertEquals(1_790_812_800_000L, Points.monthStart(lateOctober))
        assertEquals("1970-01", Points.month(0))
    }
}
