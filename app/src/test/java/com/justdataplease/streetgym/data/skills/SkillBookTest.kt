package com.justdataplease.streetgym.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillBookTest {
    private val movements = listOf(
        SkillMovement("assisted_pullup", "Assisted Pull-Up", "fundamental", setOf("vertical_pull"), null),
        SkillMovement("pullup", "Pull-Up", "fundamental", setOf("vertical_pull"), "assisted_pullup"),
        SkillMovement("archer_pullup", "Archer Pull-Up", "advanced_strength", setOf("vertical_pull"), "pullup"),
        SkillMovement("scapular_pull", "Scapular Pull", "activation", setOf("vertical_pull"), "assisted_pullup"),
        SkillMovement("full_front", "Front Lever", "advanced_skill", setOf("front_lever"), null),
    ).associateBy { it.id }
    private val spec = SkillFamilySpec("pullup", "Pull-up", SkillGroup.PULL, listOf("assisted_pullup", "pullup"),
        listOf("scapular_pull"), setOf("vertical_pull"))
    private val lever = SkillFamilySpec("front_lever", "Front lever", SkillGroup.PULL, listOf("full_front"), emptyList(), setOf("front_lever"))
    private val schedule = listOf(
        SkillProgram("p1", 1, 1, 1, listOf(SkillStep("assisted_pullup", 3))),
        SkillProgram("p2", 1, 1, 2, listOf(SkillStep("assisted_pullup", 3), SkillStep("full_front", 1, readinessCheck = true))),
        SkillProgram("p3", 1, 2, 1, listOf(SkillStep("pullup", 2))),
        SkillProgram("p4", 1, 2, 2, listOf(SkillStep("archer_pullup", 2))),
        SkillProgram("p5", 1, 3, 1, listOf(SkillStep("archer_pullup", 3))),
        SkillProgram("p6", 1, 3, 2, listOf(SkillStep("pullup", 2)), minutes = 50),
    )
    private val book = SkillBook.build(movements, schedule, listOf(spec, lever))

    private fun classes(vararg ids: String) = ids.map { SkillCredit(it) }

    @Test
    fun prepFillsOnTheIntroducingClassAndASecondOwnClassCompletesTheRung() {
        val pullup = book.families.first().skills.first { it.id == "pullup" }
        assertEquals("p3", pullup.expected?.id)
        val before = book.progress(classes("p1", "p2")).status("pullup")
        assertTrue("prep ${before.prep}", before.prep in 1..49)
        assertEquals(0, before.practice)
        // The introducing class fills prep and gives the first practice points; a second own class completes it.
        val introduced = book.progress(classes("p1", "p2", "p3")).status("pullup")
        assertEquals(50, introduced.prep)
        assertEquals(75, introduced.score)
        assertFalse(introduced.achieved)
        val twice = book.progress(classes("p1", "p2", "p3", "p6")).status("pullup")
        assertEquals(100, twice.score)
        // A pull-up is worth less than the test gate: training alone achieves it.
        assertTrue(twice.achieved)
    }

    @Test
    fun newRungsThatRegressIntoALadderJoinItButFoundationsNeverDo() {
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
        assertEquals(listOf("assisted_pullup", "pullup"), early.families.single().skills.map { it.id }.take(2))
    }

    @Test
    fun skillPointsRunFromZeroToOneHundred() {
        assertEquals(0, book.progress(emptyList()).status("pullup").score)
        val partway = book.progress(classes("p1")).status("pullup").score
        assertTrue("partway $partway", partway in 1..99)
        assertEquals(100, book.progress(classes("p1", "p2", "p3", "p6")).status("pullup").score)
        // Sources share the 50 prep points.
        val sources = book.progress(classes("p1", "p2", "p3")).status("pullup").sources
        assertTrue(sources.sumOf { it.second } >= 50)
    }

    @Test
    fun onlyTickedSetsCountUpToThePrescription() {
        val full = book.progress(classes("p1", "p2", "p3")).status("pullup")
        val one = book.progress(listOf(SkillCredit("p1"), SkillCredit("p2"), SkillCredit("p3", mapOf("pullup" to 1)))).status("pullup")
        assertEquals(1, one.sessions)
        assertTrue("${one.prep} < ${full.prep}", one.prep < full.prep)
        // Finishing with nothing ticked earns nothing; more ticks than the class prescribes count as the prescription.
        assertEquals(0, book.progress(listOf(SkillCredit("p3", emptyMap()))).status("pullup").sessions)
        assertEquals(book.progress(classes("p3")), book.progress(listOf(SkillCredit("p3", mapOf("pullup" to 99)))))
    }

    @Test
    fun anEasierOptionCreditsTheEasierMovement() {
        // Pull-ups done on the archer day: the pull-up gets the class, the archer doesn't.
        val progress = book.progress(listOf(SkillCredit("p4", mapOf("pullup" to 2))))
        assertEquals(1, progress.status("pullup").sessions)
        assertEquals(0, progress.status("archer_pullup").sessions)
    }

    @Test
    fun aClassCountsOnlyWhenItRanLongEnough() {
        // p6 plans 50 minutes: 40% is 20.
        assertEquals(0, book.progress(listOf(SkillCredit("p6", seconds = 19 * 60L))).status("pullup").sessions)
        assertEquals(1, book.progress(listOf(SkillCredit("p6", seconds = 20 * 60L))).status("pullup").sessions)
        // Classes without a planned length always count.
        assertEquals(1, book.progress(listOf(SkillCredit("p3", seconds = 0))).status("pullup").sessions)
    }

    @Test
    fun difficultyIsTheCoachGradeWhereverTheProgramTeachesTheRung() {
        val level = { n: Int, id: String -> SkillProgram("$id$n", n, 1, 1, listOf(SkillStep(id, 2))) }
        val graded = SkillBook.build(movements, listOf(level(3, "assisted_pullup"), level(1, "pullup"), level(6, "archer_pullup")), listOf(spec))
        assertEquals(listOf(1, 2, 3), graded.families.single().skills.map { it.difficulty })
    }

    @Test
    fun aRungWithoutAGradeIsEstimatedBetweenItsNeighboursAndNeverDropsUpTheLadder() {
        val level = { n: Int, id: String -> SkillProgram("$id$n", n, 1, 1, listOf(SkillStep(id, 2))) }
        val ungraded = SkillBook.build(movements, listOf(level(3, "assisted_pullup"), level(1, "pullup"), level(6, "archer_pullup")),
            listOf(spec), grades = emptyMap())
        assertEquals(listOf(3, 3, 5), ungraded.families.single().skills.map { it.difficulty })
        // A rung the program never teaches is one step past the rung below, not Elite, and stays under the graded rung above.
        val skipped = SkillBook.build(movements, listOf(level(1, "assisted_pullup"), level(4, "archer_pullup")), listOf(spec),
            grades = mapOf("assisted_pullup" to 1, "archer_pullup" to 3))
        assertEquals(listOf(1, 2, 3), skipped.families.single().skills.map { it.difficulty })
        assertEquals(3, SkillBook.estimate(movements.getValue("pullup"), null, below = 2, above = 3))
        // An advanced-category movement is at least intermediate, wherever it is taught.
        assertEquals(3, SkillBook.estimate(movements.getValue("archer_pullup"), level(1, "archer_pullup"), below = 1, above = 5))
    }

    @Test
    fun everySkillFillsItsPrepOnTheClassThatIntroducesIt() {
        val order = schedule.map { it.id }
        val taught = book.families.flatMap { it.skills }.filter { it.expected != null && !it.beyondProgram }
        // Skills on the 40-point minimum target (the very first classes) fill up just after they are introduced.
        taught.filter { it.threshold > 40 }.forEach { skill ->
            val through = order.take(order.indexOf(skill.expected!!.id) + 1)
            assertEquals(skill.id, 50, book.progress(through.map(::SkillCredit)).status(skill.id).prep)
            assertTrue(skill.id, book.progress((through - skill.expected.id).map(::SkillCredit)).status(skill.id).prep < 50)
        }
    }

    @Test
    fun aTrainedRungAtTheTestGateWaitsForItsTest() {
        // Archer pull-ups are worth 22: two classes of them train the rung fully, but only a passed test achieves it.
        val archer = book.families.first().skills.first { it.id == "archer_pullup" }
        assertEquals(SkillTest(SkillTest.Kind.REPS, 3, perSide = true), archer.test)
        val trained = book.progress(classes("p3", "p4", "p5"))
        assertTrue(trained.status("archer_pullup").trained)
        assertFalse(trained.status("archer_pullup").achieved)
        // A trained rung achieves nothing below it.
        assertFalse(trained.status("assisted_pullup").achieved)
        val passed = book.progress(classes("p3", "p4", "p5"), verified = setOf("archer_pullup"))
        assertTrue(passed.status("archer_pullup").achieved)
    }

    @Test
    fun achievingAHarderRungAchievesTheEasierOnesByValue() {
        // No assisted or plain pull-ups at all, but a passed archer test: both easier rungs are achieved with it.
        val progress = book.progress(emptyList(), verified = setOf("archer_pullup"))
        assertEquals(0, progress.status("assisted_pullup").own)
        assertTrue(progress.status("pullup").achieved)
        assertTrue(progress.status("assisted_pullup").achieved)
        assertFalse(progress.status("full_front").achieved)
    }

    @Test
    fun readinessChecksAreAnsweredNotTrained() {
        val front = book.families.first { it.spec.id == "front_lever" }.skills.single()
        assertEquals("p2", front.expected?.id)
        assertTrue(front.beyondProgram)
        // The check set earns neither prep nor practice: finishing the class achieves nothing.
        val attended = book.progress(classes("p1", "p2")).status("full_front")
        assertEquals(0, attended.own)
        assertFalse(attended.achieved)
        // "Yes" on the check is a passed test.
        assertTrue(book.progress(classes("p1", "p2"), verified = setOf("full_front")).status("full_front").achieved)
    }

    @Test
    fun closestListsOnlyRungsTheAthleteTrainedThemselves() {
        // An assisted pull-up class feeds the pull-up's prep, but the pull-up isn't listed until it is trained.
        val progress = book.progress(classes("p1"))
        assertEquals(listOf("assisted_pullup"), progress.closest(3).map { it.skill.id })
        assertNull(progress.closest(3).firstOrNull { it.skill.id == "pullup" })
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
        // Every skill-level movement in the library is a rung or a family's foundation.
        val library = org.json.JSONObject(file.readText()).getJSONArray("movement_library")
        val skillCategories = setOf("skill", "skill_strength", "advanced_skill", "mastery_skill", "advanced_strength", "advanced_fundamental")
        val foundations = SkillBook.DEFAULT_FAMILIES.flatMap { it.foundations }.toSet()
        val missing = (0 until library.length()).map(library::getJSONObject)
            .filter { it.optString("category") in skillCategories }
            .map { it.getString("id") }
            .filterNot { it in skillIds || it in foundations }
        assertEquals("skill movements outside every ladder", emptyList<String>(), missing)
        // Ladders as a coach teaches them: the pseudo-planche push-up is a planche rung, the pull-over and the
        // Bulgarian split squat are foundations, and the handstand runs wall work, exit, toe pull, balance.
        fun ladder(id: String) = real.families.first { it.spec.id == id }.skills.map { it.id }
        assertEquals(listOf("elevated_plank", "planche_lean", "frog_stand", "pseudo_planche_pushup", "tuck_planche",
            "advanced_tuck_planche", "straddle_planche", "full_planche"), ladder("planche"))
        assertFalse("pullover_lowbar" in skillIds || "bulgarian_split_squat" in skillIds)
        assertEquals(listOf("overhead_line", "pike_handstand_hold", "wall_handstand", "wall_kickup", "handstand_exit",
            "handstand_toe_pull", "handstand_balance", "freestanding_handstand"), ladder("handstand"))
        listOf("one_arm_pullup", "one_arm_pushup", "freestanding_handstand", "v_sit").forEach { top ->
            val skill = real.families.flatMap { it.skills }.first { it.id == top }
            assertTrue("$top tops its ladder", real.families.any { it.skills.last() == skill })
            assertNull("$top is beyond the program", skill.expected)
        }
        // Following every class fills the prep of every rung the program teaches.
        val levels = org.json.JSONObject(file.readText()).getJSONArray("levels")
        val allIds = (0 until levels.length()).map(levels::getJSONObject).flatMap { level ->
            level.getJSONArray("programs").let { p -> (0 until p.length()).map { p.getJSONObject(it).getString("id") } }
        }
        val finished = real.progress(allIds.map(::SkillCredit))
        val short = finished.statuses.filter { it.skill.expected != null && !it.skill.beyondProgram && it.prep < 50 }.map { it.skill.id }
        assertEquals("taught skills without full prep after the last class", emptyList<String>(), short)
        // Every rung has a coach's grade and a value; both never drop up a ladder, and gated rungs have a test.
        assertEquals("rungs without a grade", emptyList<String>(), skillIds.filterNot(SkillBook.DEFAULT_GRADES::containsKey))
        assertEquals("rungs without a value", emptyList<String>(), skillIds.filterNot(OverallScore.SKILL_VALUES::containsKey))
        real.families.forEach { family ->
            val difficulty = family.skills.map { it.difficulty }
            assertTrue("${family.spec.id} difficulty $difficulty", difficulty.all { it in 1..5 } && difficulty == difficulty.sorted())
            assertEquals(family.spec.id, family.skills.map { SkillBook.DEFAULT_GRADES[it.id] }, difficulty)
            val values = family.skills.map { it.value }
            assertEquals("${family.spec.id} values $values", values.sorted(), values)
            family.skills.filter { it.value >= OverallScore.TEST_GATE }.forEach { assertTrue("${it.id} test", it.test != null) }
        }
        // The program never schedules the negative dip and first schedules wall push-ups in Level 3; neither moves a grade.
        fun grade(id: String) = real.families.flatMap { it.skills }.first { it.id == id }.difficulty
        assertEquals(listOf(1, 2, 1, 1), listOf("eccentric_dip", "parallel_dip", "wall_pushup", "pushup").map(::grade))
        // Source workouts earn credit for their main work, not only for the practice block.
        val pushup = real.families.first { it.spec.id == "pushup" }.skills.first { it.id == "pushup" }
        assertEquals("push-ups are first taught in a Level 1 source workout", 1, pushup.expected?.level)
        // The leaderboard function checks scores against the same family ceilings.
        val boards = File("../firebase/functions/boards.py").readText()
        val ceilings = Regex("\"(\\w+)\": (\\d+)").findAll(boards.substringAfter("FAMILY_CEILINGS = {").substringBefore("}"))
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(real.families.associate { it.spec.id to it.ceiling }, ceilings)
    }

    private fun SkillProgress.status(id: String) = statuses.first { it.skill.id == id }
}
