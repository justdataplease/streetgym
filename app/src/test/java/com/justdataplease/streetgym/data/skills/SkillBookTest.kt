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
    fun laddersReorderThemselvesWhenTheScheduleChanges() {
        // The schedule now introduces the pull-up before the assisted version; the ladder follows it.
        val reordered = SkillBook.build(movements, listOf(
            SkillProgram("a", 1, 1, 1, listOf(SkillStep("pullup", 2))),
            SkillProgram("b", 1, 1, 2, listOf(SkillStep("assisted_pullup", 2))),
        ), listOf(spec))
        assertEquals(listOf("pullup", "assisted_pullup"), reordered.families.single().skills.map { it.id }.take(2))
    }

    @Test
    fun targetsRiseAlongTheLadder() {
        val thresholds = book.families.first().skills.map { it.threshold }
        assertEquals(thresholds.sorted(), thresholds)
        assertEquals(thresholds.size, thresholds.toSet().size)
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
        // Nothing scheduled counts toward it, so the target is the minimum rather than the test set itself.
        assertEquals(40, front.threshold)
    }

    @Test
    fun theShippedCurriculumCoversEverySkillMovement() {
        val file = File("src/main/assets/curriculum.json")
        val real = SkillCurriculumReader.read(file.readText())
        val skillIds = real.families.flatMap { family -> family.skills.map { it.id } }
        assertTrue("skills found: ${skillIds.size}", skillIds.size >= 60)
        assertEquals("each skill appears once", skillIds.size, skillIds.toSet().size)
        real.families.forEach { family ->
            val thresholds = family.skills.map { it.threshold }
            assertEquals("${family.spec.id} targets rise", thresholds.sorted(), thresholds)
            assertTrue("${family.spec.id} targets positive", thresholds.all { it > 0 })
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
        // Ladders follow the program on their own: each rung the schedule prescribes is introduced no earlier than
        // the one below it. Regression-only rungs, which the program uses as late fallbacks, are exempt.
        val regressions = (0 until library.length()).map(library::getJSONObject)
            .filter { it.optString("category") == "strength_regression" }.map { it.getString("id") }.toSet()
        // Workout order as the app assigns it: by level, then workout number.
        val levels = org.json.JSONObject(file.readText()).getJSONArray("levels")
        val order = (0 until levels.length()).map(levels::getJSONObject).sortedBy { it.getInt("number") }
            .flatMap { level -> level.getJSONArray("programs").let { p -> (0 until p.length()).map(p::getJSONObject) }.sortedBy { it.getInt("number") } }
            .mapIndexed { index, program -> program.getString("id") to index }.toMap()
        real.families.forEach { family ->
            val introduced = family.skills.filter { !it.beyondProgram && it.expected != null && it.id !in regressions }
                .map { it.id to order.getValue(it.expected!!.id) }
            assertEquals("${family.spec.id} ladder matches the schedule", introduced.sortedBy { it.second }, introduced)
        }
    }

    private fun SkillProgress.status(id: String) = statuses.first { it.skill.id == id }
}
