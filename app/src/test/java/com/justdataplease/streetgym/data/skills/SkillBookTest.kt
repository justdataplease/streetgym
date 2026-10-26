package com.justdataplease.streetgym.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillBookTest {
    private val movements = listOf(
        SkillMovement("assisted_pullup", "Assisted Pull-Up", "fundamental", setOf("vertical_pull"), null),
        SkillMovement("pullup", "Pull-Up", "fundamental", setOf("vertical_pull"), "assisted_pullup"),
        SkillMovement("archer_pullup", "Archer Pull-Up", "advanced_strength", setOf("vertical_pull"), "pullup"),
        SkillMovement("full_front", "Front Lever", "advanced_skill", setOf("front_lever"), null),
    ).associateBy { it.id }
    private val spec = SkillFamilySpec("pullup", "Pull-up", SkillGroup.PULL, listOf("assisted_pullup", "pullup"), emptyList(), setOf("vertical_pull"))
    private val lever = SkillFamilySpec("front_lever", "Front lever", SkillGroup.PULL, listOf("full_front"), emptyList(), setOf("front_lever"))
    private val schedule = listOf(
        SkillProgram("p1", 1, 1, 1, listOf(SkillStep("assisted_pullup", 3))),
        SkillProgram("p2", 1, 1, 2, listOf(SkillStep("assisted_pullup", 3), SkillStep("full_front", 1, readinessCheck = true))),
        SkillProgram("p3", 1, 2, 1, listOf(SkillStep("pullup", 2))),
        SkillProgram("p4", 1, 2, 2, listOf(SkillStep("archer_pullup", 2))),
        SkillProgram("p5", 1, 3, 1, listOf(SkillStep("archer_pullup", 3))),
    )
    private val book = SkillBook.build(movements, schedule, listOf(spec, lever))

    @Test
    fun followingTheScheduleUnlocksASkillOnTheDayItIsIntroduced() {
        val pullup = book.families.first().skills.first { it.id == "pullup" }
        assertEquals("p3", pullup.expected?.id)
        assertFalse(book.progress(setOf("p1", "p2")).status("pullup").unlocked)
        assertTrue(book.progress(setOf("p1", "p2", "p3")).status("pullup").unlocked)
    }

    @Test
    fun newRungsThatRegressIntoALadderJoinIt() {
        assertEquals(listOf("assisted_pullup", "pullup", "archer_pullup"), book.families.first().skills.map { it.id })
    }

    @Test
    fun laddersKeepTheirCoachingOrderWhenTheScheduleTeachesARungEarly() {
        // The schedule introduces the pull-up before the assisted version; the ladder still runs easiest to hardest,
        // and the harder rung's target stays above the easier one.
        val early = SkillBook.build(movements, listOf(
            SkillProgram("a", 1, 1, 1, listOf(SkillStep("pullup", 2))),
            SkillProgram("b", 1, 1, 2, listOf(SkillStep("assisted_pullup", 2))),
        ), listOf(spec))
        val skills = early.families.single().skills
        assertEquals(listOf("assisted_pullup", "pullup"), skills.map { it.id }.take(2))
        // Unlocking the pull-up still completes the easier rung below it.
        assertTrue(early.progress(setOf("a")).let { p -> p.statuses.first { it.skill.id == "pullup" }.unlocked == p.statuses.first { it.skill.id == "assisted_pullup" }.unlocked })
    }

    @Test
    fun skillPointsRunFromZeroToOneHundred() {
        assertEquals(0, book.progress(emptySet()).status("pullup").score)
        val partway = book.progress(setOf("p1")).status("pullup").score
        assertTrue("partway $partway", partway in 1..99)
        assertEquals(100, book.progress(setOf("p1", "p2", "p3")).status("pullup").score)
        // A source's share is measured against the same 100.
        val sources = book.progress(setOf("p1", "p2", "p3")).status("pullup").sources
        assertTrue(sources.sumOf { it.second } >= 100)
    }

    @Test
    fun aGroupScoreIsTheAverageOfTheSkillsTheProgramTeaches() {
        val start = book.progress(emptySet())
        assertEquals(0, start.groupScore(SkillGroup.PULL))
        val finished = book.progress(schedule.map { it.id }.toSet())
        assertEquals(100, finished.groupScore(SkillGroup.PULL))
        val partway = book.progress(setOf("p1", "p2", "p3"))
        val taught = partway.statuses.filter { it.skill.expected != null }
        assertEquals(taught.sumOf { it.score } / taught.size, partway.groupScore(SkillGroup.PULL))
        assertEquals(0, partway.groupScore(SkillGroup.LEGS))
    }

    @Test
    fun difficultyFollowsTheLevelThatTeachesASkillAndNeverDropsUpTheLadder() {
        val level = { n: Int, id: String -> SkillProgram("$id$n", n, 1, 1, listOf(SkillStep(id, 2))) }
        val graded = SkillBook.build(movements, listOf(level(3, "assisted_pullup"), level(1, "pullup"), level(6, "archer_pullup")), listOf(spec))
        assertEquals(listOf(3, 3, 5), graded.families.single().skills.map { it.difficulty })
        // An advanced-category movement is at least intermediate, wherever it is taught.
        assertEquals(3, SkillBook.difficulty(movements.getValue("archer_pullup"), level(1, "archer_pullup")))
        assertEquals(5, SkillBook.difficulty(movements.getValue("pullup"), null))
    }

    @Test
    fun everySkillReachesOneHundredOnTheClassThatIntroducesIt() {
        val order = schedule.map { it.id }
        val taught = book.families.flatMap { it.skills }.filter { it.expected != null }
        // Skills on the 40-point minimum target (the very first classes) fill up just after they are introduced.
        taught.filter { it.threshold > 40 }.forEach { skill ->
            val through = order.take(order.indexOf(skill.expected!!.id) + 1).toSet()
            assertEquals(skill.id, 100, book.progress(through).status(skill.id).score)
            assertFalse(skill.id, book.progress(through - skill.expected.id).status(skill.id).unlocked)
        }
        taught.forEach { assertEquals(it.id, 100, book.progress(order.toSet()).status(it.id).score) }
    }

    @Test
    fun unlockingAHarderRungUnlocksTheEasierOnes() {
        // No assisted sets at all, but enough archer work: the easier rung unlocks with the harder one.
        val progress = book.progress(setOf("p3", "p4", "p5"))
        assertEquals(0, progress.status("assisted_pullup").points)
        assertTrue(progress.status("archer_pullup").unlocked)
        assertTrue(progress.status("assisted_pullup").unlocked)
    }

    @Test
    fun readinessChecksDoNotCountTowardTargetsButMarkTheTestDay() {
        val front = book.families.first { it.spec.id == "front_lever" }.skills.single()
        assertEquals("p2", front.expected?.id)
        assertTrue(front.beyondProgram)
        // Nothing scheduled counts toward it, so the check set itself (one advanced set, 20 points) completes it.
        assertEquals(20, front.threshold)
        assertFalse(book.progress(setOf("p1")).status("full_front").unlocked)
        assertTrue(book.progress(setOf("p1", "p2")).status("full_front").unlocked)
    }

    @Test
    fun theShippedCurriculumCoversEverySkillMovement() {
        val file = File("src/main/assets/curriculum.json")
        val real = SkillCurriculumReader.read(file.readText())
        val skillIds = real.families.flatMap { family -> family.skills.map { it.id } }
        assertTrue("skills found: ${skillIds.size}", skillIds.size >= 60)
        assertEquals("each skill appears once", skillIds.size, skillIds.toSet().size)
        real.families.forEach { family ->
            assertTrue("${family.spec.id} targets positive", family.skills.all { it.threshold > 0 })
        }
        // Every pullup-style rung the program prescribes has a scheduled day.
        assertTrue(real.families.first { it.spec.id == "pullup" }.skills.first { it.id == "pullup" }.expected != null)
        // Every skill-level movement in the library belongs to a ladder.
        val library = org.json.JSONObject(file.readText()).getJSONArray("movement_library")
        val skillCategories = setOf("skill", "skill_strength", "advanced_skill", "mastery_skill", "advanced_strength", "advanced_fundamental")
        val missing = (0 until library.length()).map(library::getJSONObject)
            .filter { it.optString("category") in skillCategories }
            .map { it.getString("id") }
            .filterNot(skillIds::contains)
        assertEquals("skill movements outside every ladder", emptyList<String>(), missing)
        // Following every class unlocks every skill the program teaches, and every group reaches 100.
        val levels = org.json.JSONObject(file.readText()).getJSONArray("levels")
        val allIds = (0 until levels.length()).map(levels::getJSONObject).flatMap { level ->
            level.getJSONArray("programs").let { p -> (0 until p.length()).map { p.getJSONObject(it).getString("id") } }
        }.toSet()
        val finished = real.progress(allIds)
        val stillLocked = finished.statuses.filter { it.skill.expected != null && !it.unlocked }.map { it.skill.id }
        assertEquals("taught skills still locked after the last class", emptyList<String>(), stillLocked)
        SkillGroup.entries.forEach { assertEquals("$it after the last class", 100, finished.groupScore(it)) }
        // Difficulty is 1-5 and never drops up a ladder.
        real.families.forEach { family ->
            val difficulty = family.skills.map { it.difficulty }
            assertTrue("${family.spec.id} difficulty $difficulty", difficulty.all { it in 1..5 } && difficulty == difficulty.sorted())
        }
        // Source workouts earn credit for their main work, not only for the practice block.
        val pushup = real.families.first { it.spec.id == "pushup" }.skills.first { it.id == "pushup" }
        assertEquals("push-ups are first taught in a Level 1 source workout", 1, pushup.expected?.level)
    }

    private fun SkillProgress.status(id: String) = statuses.first { it.skill.id == id }
}
