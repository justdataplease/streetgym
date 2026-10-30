package com.justdataplease.streetgym.data.skills

import com.justdataplease.streetgym.leaderboard.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

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
        SkillProgram("p1", 1, 1, 1, listOf(SkillStep("assisted_pullup", 3, reps = 8))),
        SkillProgram("p2", 1, 1, 2, listOf(SkillStep("assisted_pullup", 3, reps = 8), SkillStep("full_front", 1, readinessCheck = true))),
        SkillProgram("p3", 1, 2, 1, listOf(SkillStep("pullup", 2, reps = 6))),
        SkillProgram("p4", 1, 2, 2, listOf(SkillStep("archer_pullup", 2, reps = 3))),
        SkillProgram("p5", 1, 3, 1, listOf(SkillStep("archer_pullup", 3, reps = 3))),
        SkillProgram("p6", 1, 3, 2, listOf(SkillStep("pullup", 2, reps = 6)), minutes = 50),
    )
    private val book = SkillBook.build(movements, schedule, listOf(spec, lever))

    /** An own day of [count] sets of [id], each of [dose] reps or seconds. */
    private fun sets(id: String, count: Int, dose: Int) = SkillCredit("custom:$id", mapOf(id to count), doses = mapOf(id to List(count) { dose }))

    @Test
    fun eachLevelTakesItsSetsAtItsTarget() {
        // The pull-up's working-set targets: 3, 6, 10, 15 and 20 reps for Beginner to Elite; 10, 15, 20, 25 and 30 sets
        // (review 11, the owner).
        fun level(vararg days: SkillCredit) = book.progress(days.toList()).status("pullup").level
        assertEquals(listOf(3, 6, 10, 15, 20), SkillStandards.of("pullup").targets)
        assertEquals(listOf(10, 15, 20, 25, 30), SkillLevel.entries.drop(1).map { it.sets })
        assertEquals(SkillLevel.NONE, level(sets("pullup", 9, 3)))
        assertEquals(SkillLevel.BEGINNER, level(sets("pullup", 10, 3)))
        // Sets under the target count toward nothing; a bigger set counts for the lower targets too.
        assertEquals(SkillLevel.NONE, level(sets("pullup", 300, 2)))
        assertEquals(SkillLevel.BEGINNER, level(sets("pullup", 14, 6)))
        assertEquals(SkillLevel.INTERMEDIATE, level(sets("pullup", 14, 6), sets("pullup", 1, 8)))
        assertEquals(SkillLevel.INTERMEDIATE, level(sets("pullup", 19, 10)))
        assertEquals(SkillLevel.ADVANCED, level(sets("pullup", 20, 10)))
        assertEquals(SkillLevel.ADVANCED, level(sets("pullup", 24, 15)))
        assertEquals(SkillLevel.MASTER, level(sets("pullup", 25, 15)))
        // Thirty sets at the Elite target earn all five at once, on an own day too: Master needs no sets of its own.
        assertEquals(SkillLevel.ELITE, level(sets("pullup", 30, 25)))
    }

    @Test
    fun whatTheNextLevelTakes() {
        val status = book.progress(listOf(sets("pullup", 10, 5), sets("pullup", 12, 12))).status("pullup")
        assertEquals(SkillLevel.BEGINNER, status.level)
        assertEquals(SkillLevel.INTERMEDIATE, status.next)
        assertEquals(22, status.sets)
        assertEquals(12, status.best)
        assertEquals(3, status.setsToGo)
        assertEquals(12 * 100 / 15, status.progress)
        val elite = book.progress(listOf(sets("pullup", 30, 40))).status("pullup")
        assertNull(elite.next)
        assertEquals(100, elite.progress)
    }

    @Test
    fun newRungsThatRegressIntoALadderJoinItButFoundationsNeverDo() {
        assertEquals(listOf("assisted_pullup", "pullup", "archer_pullup"), book.families.first().skills.map { it.id })
    }

    @Test
    fun laddersKeepTheirCoachingOrderWhenTheScheduleTeachesARungEarly() {
        val early = SkillBook.build(movements, listOf(
            SkillProgram("a", 1, 1, 1, listOf(SkillStep("pullup", 2))),
            SkillProgram("b", 1, 1, 2, listOf(SkillStep("assisted_pullup", 2))),
        ), listOf(spec))
        assertEquals(listOf("assisted_pullup", "pullup"), early.families.single().skills.map { it.id }.take(2))
        assertEquals("a", early.skill("pullup")?.expected?.id)
    }

    @Test
    fun aProgramDayCountsItsTicksUpToThePrescriptionAtTheirDoses() {
        fun pullup(credit: SkillCredit) = book.progress(listOf(credit)).status("pullup")
        // An older class without ticks or doses counts the prescription: two sets of 6.
        pullup(SkillCredit("p3")).let { assertEquals(listOf(2, 6, 2), listOf(it.sets, it.best, it.atTargets[0])) }
        pullup(SkillCredit("p3", mapOf("pullup" to 1))).let { assertEquals(listOf(1, 6), listOf(it.sets, it.best)) }
        // Logged doses count as logged: a 12 reaches the Intermediate target, a 4 only Beginner's.
        pullup(SkillCredit("p3", mapOf("pullup" to 2), doses = mapOf("pullup" to listOf(12, 4)))).let {
            assertEquals(listOf(2, 12, 2, 1), listOf(it.sets, it.best, it.atTargets[0], it.atTargets[1]))
        }
        // More ticks than the class prescribes count as the prescription; none count nothing.
        assertEquals(2, pullup(SkillCredit("p3", mapOf("pullup" to 99))).sets)
        assertEquals(0, pullup(SkillCredit("p3", emptyMap())).sets)
    }

    @Test
    fun anEasierOptionCreditsTheEasierMovement() {
        // Pull-ups done on the archer day: the pull-up gets the sets, the archer doesn't.
        val progress = book.progress(listOf(SkillCredit("p4", mapOf("pullup" to 2), doses = mapOf("pullup" to listOf(8, 8)))))
        assertEquals(2, progress.status("pullup").sets)
        assertEquals(8, progress.status("pullup").best)
        assertEquals(0, progress.status("archer_pullup").sets)
    }

    @Test
    fun aClassCountsOnlyWhenItRanLongEnough() {
        // p6 plans 50 minutes: 20% is 10.
        assertEquals(0, book.progress(listOf(SkillCredit("p6", seconds = 9 * 60L))).status("pullup").sets)
        assertEquals(2, book.progress(listOf(SkillCredit("p6", seconds = 10 * 60L))).status("pullup").sets)
        // Classes without a planned length always count.
        assertEquals(2, book.progress(listOf(SkillCredit("p3", seconds = 0))).status("pullup").sets)
    }

    @Test
    fun anOwnDayCountsItsTicksAsTheyAre() {
        // Not in the program: four pull-up sets count, with no prescription to cap them.
        assertEquals(4, book.progress(listOf(SkillCredit("custom:a", mapOf("pullup" to 4), seconds = 600, minutes = 20))).status("pullup").sets)
        // Without doses its sets count toward no target.
        assertEquals(0, book.progress(listOf(SkillCredit("custom:a", mapOf("pullup" to 40)))).status("pullup").atTargets[0])
        // It still has to run 20% of its length, and an own day without ticks counts nothing.
        assertEquals(0, book.progress(listOf(SkillCredit("custom:a", mapOf("pullup" to 4), seconds = 200, minutes = 20))).status("pullup").sets)
        assertEquals(0, book.progress(listOf(SkillCredit("custom:b"))).status("pullup").sets)
        // Unknown movements are left out.
        assertEquals(book.progress(emptyList()), book.progress(listOf(SkillCredit("custom:c", mapOf("burpee" to 9)))))
    }

    @Test
    fun aHarderRungsSetsCountOnTheEasierRungs() {
        // The archer pull-up's Beginner target is 2 reps each side: each such set is a Beginner set of every easier rung
        // measured in reps too (review 11), never a set of a higher level there.
        val beginner = book.progress(listOf(sets("archer_pullup", 10, 2)))
        assertEquals(SkillLevel.BEGINNER, beginner.status("archer_pullup").level)
        assertEquals(SkillLevel.BEGINNER, beginner.status("pullup").level)
        assertEquals(listOf(10, 0, 0, 0, 0), beginner.status("pullup").atTargets)
        assertEquals(SkillLevel.BEGINNER, beginner.status("assisted_pullup").level)
        assertEquals(0, beginner.status("pullup").sets)
        // The next level takes sets at its own target, the pull-up's or the archer's.
        assertEquals(SkillLevel.INTERMEDIATE, beginner.status("pullup").next)
        assertEquals(15, beginner.status("pullup").setsToGo)
        assertEquals(SkillLevel.NONE, book.progress(listOf(sets("archer_pullup", 9, 2))).status("pullup").level)
        // Sets of both rungs add up: 9 archer sets and a pull-up set make a Beginner.
        assertEquals(SkillLevel.BEGINNER, book.progress(listOf(sets("archer_pullup", 9, 2), sets("pullup", 1, 3))).status("pullup").level)
        // The planche: 24 full-planche sets of 6 s (its Advanced; Master takes 25) and six 8 s straddle sets make the
        // straddle planche Elite, and the tuck planche's too.
        val planche = everyRung.progress(listOf(sets("full_planche", 24, 6), sets("straddle_planche", 6, 8)))
        assertEquals(SkillLevel.ADVANCED, planche.status("full_planche").level)
        assertEquals(SkillLevel.ELITE, planche.status("straddle_planche").level)
        assertEquals(SkillLevel.ELITE, planche.status("tuck_planche").level)
    }

    @Test
    fun aHarderRungsLevelHoldsOnTheEasierRungs() {
        // A rung measured the other way gets no sets, only the level, never higher (round 10, 29 October 2026), and names
        // the rung it comes through: pull-ups in reps for the dead hang in seconds, holds for the pseudo-planche push-up.
        val pullups = everyRung.progress(listOf(sets("pullup", 10, 3)))
        assertEquals(SkillLevel.BEGINNER, pullups.status("dead_hang").level)
        assertEquals("pullup", pullups.status("dead_hang").carriedBy?.id)
        assertEquals(listOf(0, 0, 0, 0, 0), pullups.status("dead_hang").atTargets)
        assertEquals(SkillLevel.NONE, everyRung.progress(listOf(sets("pullup", 9, 3))).status("dead_hang").level)
        val planche = everyRung.progress(listOf(sets("tuck_planche", 30, 15)))
        assertEquals(SkillLevel.ELITE, planche.status("pseudo_planche_pushup").level)
        assertEquals("tuck_planche", planche.status("pseudo_planche_pushup").carriedBy?.id)
        // A rung's own higher level stays its own.
        val both = everyRung.progress(listOf(sets("pullup", 10, 3), sets("dead_hang", 15, 45)))
        assertEquals(SkillLevel.INTERMEDIATE, both.status("dead_hang").level)
        assertNull(both.status("dead_hang").carriedBy)
    }

    @Test
    fun readinessChecksCountNoSet() {
        val front = book.skill("full_front")!!
        assertEquals("p2", front.expected?.id)
        assertTrue(front.beyondProgram)
        assertEquals(0, book.progress(listOf(SkillCredit("p1"), SkillCredit("p2"))).status("full_front").sets)
        assertFalse(book.skill("pullup")!!.beyondProgram)
    }

    @Test
    fun closestListsOnlySkillsTheAthleteStarted() {
        val progress = book.progress(listOf(SkillCredit("p1")))
        assertEquals(listOf("assisted_pullup"), progress.closest(3).map { it.skill.id })
        // The archer's sets make the rungs below Intermediate; the pull-up, with no sets of its own, has nothing to raise
        // yet, while the assisted pull-up's own sets keep it open.
        val both = book.progress(listOf(sets("archer_pullup", 30, 3), SkillCredit("p1")))
        assertEquals(SkillLevel.INTERMEDIATE, both.status("pullup").level)
        assertEquals(setOf("archer_pullup", "assisted_pullup"), both.closest(3).map { it.skill.id }.toSet())
    }

    @Test
    fun levelsAddUpAcrossSkills() {
        val progress = book.progress(listOf(sets("pullup", 30, 5)))
        assertEquals(4 * 5, progress.totalLevels)
        // Beginner pull-up, held on the assisted pull-up below it too.
        assertEquals(2 * SkillLevel.BEGINNER.ordinal, progress.levels)
    }

    @Test
    fun theBeltsAreTheFortySkillsOfRoundEleven() {
        val skillIds = everyRung.families.flatMap { family -> family.skills.map { it.id } }
        assertEquals(40, skillIds.size)
        assertEquals(16, everyRung.families.size)
        assertEquals("each skill appears once", skillIds.size, skillIds.toSet().size)
        assertEquals(SkillStandards.ALL.keys, skillIds.toSet())
        // The skills that left the belts are movements now, kept as foundations so they can't rejoin a ladder.
        val foundations = SkillBook.DEFAULT_FAMILIES.flatMap { it.foundations }.toSet()
        DROPPED.forEach { id ->
            assertFalse("$id earns no belt", id in skillIds)
            assertTrue("$id stays a movement of its family", id in foundations)
        }
        // Round 11's progressions are movements of their families too, never rungs.
        ROUND_11_STEPS.forEach { id ->
            assertFalse("$id earns no belt", id in skillIds)
            assertTrue("$id is a foundation", id in foundations)
        }
        assertEquals("no family is left without a rung", SkillBook.DEFAULT_FAMILIES.map { it.id }, everyRung.families.map { it.spec.id })
        fun ladder(id: String) = everyRung.families.first { it.spec.id == id }.skills.map { it.id }
        assertEquals(listOf("dead_hang", "pullup", "archer_pullup", "one_arm_pullup"), ladder("pullup"))
        assertEquals(listOf("pseudo_planche_pushup", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"),
            ladder("planche"))
        assertEquals(listOf("squat", "pistol_squat"), ladder("pistol"))
        assertEquals(listOf("wall_handstand", "freestanding_handstand", "one_arm_handstand"), ladder("handstand"))
        assertEquals(listOf("shrimp_squat"), ladder("shrimp"))
        assertEquals(listOf("hefesto"), ladder("hefesto"))
        assertEquals(SkillGroup.LEGS, everyRung.families.first { it.spec.id == "shrimp" }.spec.group)
        assertEquals(SkillGroup.PULL, everyRung.families.first { it.spec.id == "hefesto" }.spec.group)
        assertEquals(listOf("hollow_body_hold", "dragon_flag"), ladder("dragon_flag"))
        assertEquals(listOf("parallel_dip"), ladder("dip"))
        // Working-set targets (a set at one or two reps in reserve), as the owner took them for round 9.
        assertEquals(listOf(30, 45, 60, 75, 90), SkillStandards.of("dead_hang").targets)
        assertEquals(listOf(6, 12, 20, 30, 40), SkillStandards.of("pushup").targets)
        assertEquals(listOf(15, 20, 25, 30, 40), SkillStandards.of("squat").targets)
        assertEquals(listOf(20, 30, 40, 50, 60), SkillStandards.of("hollow_body_hold").targets)
        // The rarest rungs stay reachable inside the program, so two levels may share a target and differ by their sets.
        // Round 11 starts the hardest holds at 3 s.
        assertEquals(listOf(3, 3, 4, 5, 6), SkillStandards.of("full_planche").targets)
        // Review 11 fits the hardest rungs' targets to the classes (the owner).
        assertEquals(listOf(1, 1, 1, 1, 2), SkillStandards.of("one_arm_pullup").targets)
        assertEquals(listOf(1, 2, 4, 6, 7), SkillStandards.of("strict_muscleup").targets)
        assertEquals(listOf(5, 15, 30, 45, 60), SkillStandards.of("freestanding_handstand").targets)
        assertEquals(listOf(3, 4, 5, 6, 7), SkillStandards.of("one_arm_handstand").targets)
        assertEquals(listOf(2, 3, 5, 7, 8), SkillStandards.of("shrimp_squat").targets)
        assertEquals(listOf(1, 1, 2, 2, 3), SkillStandards.of("hefesto").targets)
        assertEquals(listOf(1, 3, 4, 4, 5), SkillStandards.of("one_arm_pushup").targets)
        assertEquals(listOf(3, 4, 5, 5, 6), SkillStandards.of("full_flag").targets)
        // Beginner and Intermediate share the full planche's 3 s and differ by their sets; from Advanced each asks longer.
        assertEquals(SkillLevel.BEGINNER, everyRung.progress(listOf(sets("full_planche", 14, 3))).status("full_planche").level)
        assertEquals(SkillLevel.INTERMEDIATE, everyRung.progress(listOf(sets("full_planche", 30, 3))).status("full_planche").level)
        assertEquals(SkillLevel.ADVANCED, everyRung.progress(listOf(sets("full_planche", 30, 4))).status("full_planche").level)
        // Two levels may share a target and differ by their sets, but Elite always asks more than Master.
        SkillStandards.ALL.forEach { (id, standard) -> assertTrue(id, standard.targets[4] > standard.targets[3]) }
        assertEquals(setOf("archer_pullup", "one_arm_pullup", "one_arm_pushup", "pistol_squat", "shrimp_squat", "one_arm_handstand",
            "tuck_flag", "straddle_flag", "full_flag"), SkillStandards.ALL.filterValues { it.perSide }.keys)
        assertEquals(setOf("dead_hang", "wall_handstand", "freestanding_handstand", "one_arm_handstand", "hollow_body_hold", "lsit", "v_sit",
            "tuck_front_hold", "advanced_tuck_front", "straddle_front", "full_front", "tuck_back_hold", "advanced_tuck_back",
            "straddle_back", "full_back", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche",
            "tuck_flag", "straddle_flag", "full_flag"), SkillStandards.ALL.filterValues { it.seconds }.keys)
        // The freestanding handstand is a plain hold now: no skill's time is a practice window.
        assertTrue(SkillStandards.ALL.values.none { it.window })
        // Every rung has a coach's standard: five targets that never drop, Elite above Beginner, and a grade that never
        // drops up the ladder; and a value.
        SkillStandards.ALL.forEach { (id, standard) ->
            assertEquals(id, 5, standard.targets.size)
            assertTrue("$id ${standard.targets}", standard.targets.zipWithNext().all { (a, b) -> b >= a } && standard.targets[0] > 0)
            assertTrue("$id ${standard.targets}", standard.targets.last() > standard.targets.first())
            assertTrue(id, standard.grade in 1..5)
        }
        assertEquals("rungs without a value", emptyList<String>(), skillIds.filterNot(OverallScore.SKILL_VALUES::containsKey))
        assertEquals("values for no rung", emptyList<String>(), OverallScore.SKILL_VALUES.keys.filterNot(skillIds::contains))
        everyRung.families.forEach { family ->
            val values = family.skills.map { it.value }
            assertEquals("${family.spec.id} values $values", values.sorted(), values)
            assertTrue("${family.spec.id} has a value", family.ceiling > 0)
            val grades = family.skills.map { it.difficulty }
            assertEquals("${family.spec.id} grades $grades", grades.sorted(), grades)
        }
        // The full planche and the one-arm pull-up are the hardest: extreme, and the planche the most valuable.
        assertEquals(5, SkillStandards.of("full_planche").grade)
        assertEquals(5, SkillStandards.of("one_arm_pullup").grade)
        listOf("straddle_planche", "full_front", "full_flag").forEach { assertTrue(it, SkillStandards.of(it).grade in 4..5) }
        listOf("strict_muscleup", "full_back").forEach { assertEquals(it, 4, SkillStandards.of(it).grade) }
        listOf("one_arm_handstand", "hefesto").forEach { assertEquals(it, 5, SkillStandards.of(it).grade) }
        assertEquals(3, SkillStandards.of("shrimp_squat").grade)
        assertEquals("full_planche", OverallScore.SKILL_VALUES.maxBy { it.value }.key)
        // Round 11's values: the one-arm handstand tops the handstand family at the one-arm pull-up's 110.
        assertEquals(mapOf("one_arm_handstand" to 110, "hefesto" to 50, "shrimp_squat" to 14),
            listOf("one_arm_handstand", "hefesto", "shrimp_squat").associateWith { OverallScore.SKILL_VALUES.getValue(it) })
    }

    @Test
    fun shrimpStepsStayOutOfThePistolLadder() {
        // The authoritative movement library, as the next curriculum carries it: the shrimp squat's steps share the
        // pistol's single-leg-squat pattern and regress from the split squat, a pistol foundation, so only their being
        // foundations keeps them off the pistol ladder. Every ladder is exactly as specified.
        val library = org.json.JSONObject(File("../curriculum/movement_library.json").readText()).getJSONArray("movements")
        val book = SkillCurriculumReader.read(org.json.JSONObject().put("movement_library", library))
        book.families.forEach { assertEquals(it.spec.id, it.spec.ladder.filter { id -> book.skill(id) != null }, it.skills.map { s -> s.id }) }
        assertEquals(SkillBook.DEFAULT_FAMILIES.map { it.id }, book.families.map { it.spec.id })
        assertEquals(SkillStandards.ALL.keys, book.families.flatMap { f -> f.skills.map { it.id } }.toSet())
        assertEquals(listOf("squat", "pistol_squat"), book.families.first { it.spec.id == "pistol" }.skills.map { it.id })
        // Every skill-level movement of the library is a rung or a family's foundation.
        val skillCategories = setOf("skill", "skill_strength", "advanced_skill", "mastery_skill", "advanced_strength", "advanced_fundamental")
        val foundations = SkillBook.DEFAULT_FAMILIES.flatMap { it.foundations }.toSet()
        val loose = (0 until library.length()).map(library::getJSONObject)
            .filter { it.optString("category") in skillCategories }.map { it.getString("id") }
            .filterNot { book.skill(it) != null || it in foundations }
        assertEquals("skill movements outside every ladder", emptyList<String>(), loose)
    }

    @Test
    fun theDeployedLeaderboardFunctionAcceptsEveryFamily() {
        // boards.py refuses a family it doesn't know or above its ceiling, and checks the overall against the sum of all
        // its ceilings. Score version 3 (round 11) knows the 16 families and nothing retired: the Nordic curl is gone.
        val boards = File("../firebase/functions/boards.py").readText()
        val ceilings = Regex("\"(\\w+)\": (\\d+)").findAll(boards.substringAfter("FAMILY_CEILINGS = {").substringBefore("}"))
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(everyRung.families.associate { it.spec.id to it.ceiling } + OverallScore.RETIRED_CEILINGS, ceilings)
        assertTrue(OverallScore.RETIRED_CEILINGS.keys.none { id -> everyRung.families.any { it.spec.id == id } })
        assertEquals(ceilings.values.sum(), OverallScore.scoreMax(everyRung.families))
        assertEquals(emptyMap<String, Int>(), OverallScore.RETIRED_CEILINGS)
        assertFalse("nordic" in ceilings)
        assertEquals(887, ceilings.values.sum())
        assertEquals(mapOf("handstand" to 110, "shrimp" to 14, "hefesto" to 50), listOf("handstand", "shrimp", "hefesto").associateWith(ceilings::getValue))
        assertEquals(3, Regex("(?m)^VERSION = (\\d+)").find(boards)?.groupValues?.get(1)?.toInt())
        assertEquals(3, com.justdataplease.streetgym.leaderboard.Score.VERSION)
        // The parallel dip carries the dip family's old top value, the straight-bar dip's 12.
        assertEquals(12, OverallScore.SKILL_VALUES["parallel_dip"])
        // What the app publishes passes the function's check (boards.py _valid): known families, each within its
        // ceiling, and an overall that adds up from them.
        fun elite(vararg ids: String) = everyRung.progress(ids.map { id ->
            val target = SkillStandards.of(id).target(SkillLevel.ELITE)!!
            SkillCredit("custom:$id", mapOf(id to SkillLevel.ELITE.sets), doses = mapOf(id to List(SkillLevel.ELITE.sets) { target }))
        })
        val tops = everyRung.families.map { it.skills.last().id }
        listOf(elite(*tops.toTypedArray()), elite("pullup", "parallel_dip", "squat"), elite("hollow_body_hold")).forEach { progress ->
            val score = Scores.from(progress)
            assertTrue(score.families.toString(), score.families.all { (id, points) -> id in ceilings && points in 0..10 * ceilings.getValue(id) })
            val expected = (OverallScore.MAX * score.families.values.sum() / 10.0 / ceilings.values.sum()).roundToInt()
            assertTrue("${score.overall} published, $expected expected", abs(expected - score.overall) <= 2)
        }
    }

    @Test
    fun theShippedCurriculumCoversEverySkillMovement() {
        // The curriculum is regenerated for round 11: these hold for any curriculum that teaches the 40 skills.
        val file = File("src/main/assets/curriculum.json")
        val real = SkillCurriculumReader.read(file.readText())
        val skillIds = real.families.flatMap { family -> family.skills.map { it.id } }
        // Every skill is in the movement library, and nothing else joined a ladder by its regression.
        assertEquals("skills missing from the catalog", emptyList<String>(), SkillStandards.ALL.keys.filterNot(skillIds::contains))
        assertEquals("rungs without a standard", emptyList<String>(), skillIds.filterNot(SkillStandards.ALL::containsKey))
        real.families.forEach { assertEquals(it.spec.id, it.spec.ladder, it.skills.map { skill -> skill.id }) }
        assertNotNull(real.skill("pullup")?.expected)
        // Every skill-level movement in the library is a rung or a family's foundation.
        val library = org.json.JSONObject(file.readText()).getJSONArray("movement_library")
        val skillCategories = setOf("skill", "skill_strength", "advanced_skill", "mastery_skill", "advanced_strength", "advanced_fundamental")
        val foundations = SkillBook.DEFAULT_FAMILIES.flatMap { it.foundations }.toSet()
        val missing = (0 until library.length()).map(library::getJSONObject)
            .filter { it.optString("category") in skillCategories }
            .map { it.getString("id") }
            .filterNot { it in skillIds || it in foundations }
        assertEquals("skill movements outside every ladder", emptyList<String>(), missing)
        // A skill is done on one's own; assisted, supported and partial versions are progressions toward it (the owner's
        // call, 28 October 2026), and so are the negatives (29 October).
        assertFalse("pullover_lowbar" in skillIds || "bulgarian_split_squat" in skillIds)
        listOf("assisted_pullup", "foot_assisted_hang", "band_muscleup", "wall_pushup", "overhead_line", "assisted_tuck_flag",
            "german_hang", "tuck_back_entry", "tuck_lsit", "wall_kickup", "handstand_exit")
            .forEach { assertFalse("$it is a progression", it in skillIds) }
        listOf("one_arm_pullup_negative", "negative_muscleup", "wall_hspu_negative", "freestanding_hspu_negative",
            "nordic_negative", "bent_dragon_negative", "one_leg_dragon_negative", "full_dragon_negative")
            .forEach { assertFalse("$it is a progression", it in skillIds) }
        ROUND_11_STEPS.forEach { assertFalse("$it is a progression", it in skillIds) }
        listOf("one_arm_pullup", "one_arm_pushup", "one_arm_handstand", "v_sit", "shrimp_squat", "hefesto").forEach { top ->
            val skill = real.skill(top)!!
            assertTrue("$top tops its ladder", real.families.any { it.skills.last() == skill })
            // Levels 8-10 teach every top shape, the one-arm pull-up, push-up and handstand, the V-sit and the Hefesto
            // included.
            assertNotNull("$top is taught", skill.expected)
            assertFalse("$top is trained, not only checked", skill.beyondProgram)
        }
        // The program's prescribed doses count for days saved without them: reps for pull-ups, seconds for holds.
        val programDays = org.json.JSONObject(file.readText()).getJSONArray("levels").let { levels ->
            (0 until levels.length()).map(levels::getJSONObject).flatMap { level ->
                level.getJSONArray("programs").let { p -> (0 until p.length()).map { p.getJSONObject(it).getString("id") } }
            }
        }
        val graduate = real.progress(programDays.map(::SkillCredit))
        // By Level 10 the plan takes a skill's longest set to its Elite target.
        listOf("pullup", "wall_handstand").forEach { id ->
            assertTrue(id, graduate.status(id).best >= SkillStandards.of(id).target(SkillLevel.ELITE)!!)
        }
        // Review 11 (the owner): following every class takes every skill to Master and every Elite at least 21 of its 30
        // sets; the top rungs keep some of it for repeated blocks and the athlete's own training.
        graduate.statuses.forEach { status ->
            assertTrue("${status.skill.id} ${status.level}", status.level >= SkillLevel.MASTER)
            assertTrue("${status.skill.id} Elite sets ${status.setsAt(SkillLevel.ELITE)}",
                status.level == SkillLevel.ELITE || status.setsAt(SkillLevel.ELITE) >= 21)
        }
        assertEquals(SkillLevel.MASTER, graduate.status("full_planche").level)
        assertEquals(SkillLevel.ELITE, graduate.status("toes_to_bar").level)
        // A rung the plan trains 10 times or more never asks for a Beginner set longer than the plan's own longest, unless
        // the plan's time is a practice window: following the classes as written must be able to earn it.
        assertEquals("Beginner beyond the plan", emptyList<String>(), graduate.statuses
            .filter { it.sets >= SkillLevel.BEGINNER.sets && it.level == SkillLevel.NONE && !it.skill.standard.window }
            .filter { it.best < it.skill.standard.target(SkillLevel.BEGINNER)!! }
            .map { it.skill.id })
    }

    /** Every ladder with each of its rungs in the library, as a curriculum that teaches all 40 skills gives them. */
    private val everyRung = SkillBook.build(
        SkillBook.DEFAULT_FAMILIES.flatMap { it.ladder }.associateWith { SkillMovement(it, it, "skill", emptySet(), null) },
        emptyList())

    private companion object {
        /** The 20 skills that left the belts in round 9 (29 October 2026). */
        val DROPPED = listOf("active_hang", "chinup", "typewriter_pullup", "chest_to_bar", "straight_bar_support", "support_hold",
            "straight_bar_dip", "pause_pushup", "close_pushup", "archer_pushup", "elevated_pike_pushup", "planche_lean", "frog_stand",
            "handstand_balance", "one_leg_front", "one_leg_back", "one_leg_lsit", "knee_raise", "sliding_leg_curl", "nordic_curl")

        /** The steps toward round 11's three new skills: movements of their families, never belts. */
        val ROUND_11_STEPS = listOf("straddle_handstand", "supported_handstand_shift", "handstand_weight_shift",
            "fingertip_one_arm_handstand", "one_arm_handstand_hover", "straddle_one_arm_handstand", "assisted_partial_shrimp",
            "assisted_shrimp", "floating_shrimp", "assisted_held_shrimp", "assisted_supinated_german_hang",
            "assisted_behind_bar_support", "assisted_korean_dip", "assisted_partial_hefesto", "assisted_hefesto")
    }

    private fun SkillProgress.status(id: String) = statuses.first { it.skill.id == id }
}
