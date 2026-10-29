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
        // The pull-up's working-set targets: 3, 6, 10, 15 and 20 reps for Beginner to Elite; 10 sets for Beginner, 20
        // for Intermediate and 30 for each level after.
        fun level(vararg days: SkillCredit) = book.progress(days.toList()).status("pullup").level
        assertEquals(listOf(3, 6, 10, 15, 20), SkillStandards.of("pullup").targets)
        assertEquals(listOf(10, 20, 30, 30, 30), SkillLevel.entries.drop(1).map { it.sets })
        assertEquals(SkillLevel.NONE, level(sets("pullup", 9, 3)))
        assertEquals(SkillLevel.BEGINNER, level(sets("pullup", 10, 3)))
        // Sets under the target count toward nothing; a bigger set counts for the lower targets too.
        assertEquals(SkillLevel.NONE, level(sets("pullup", 300, 2)))
        assertEquals(SkillLevel.BEGINNER, level(sets("pullup", 19, 6)))
        assertEquals(SkillLevel.INTERMEDIATE, level(sets("pullup", 19, 6), sets("pullup", 1, 8)))
        assertEquals(SkillLevel.INTERMEDIATE, level(sets("pullup", 29, 10)))
        assertEquals(SkillLevel.ADVANCED, level(sets("pullup", 30, 10)))
        assertEquals(SkillLevel.MASTER, level(sets("pullup", 30, 15)))
        assertEquals(SkillLevel.ELITE, level(sets("pullup", 30, 25)))
    }

    @Test
    fun whatTheNextLevelTakes() {
        val status = book.progress(listOf(sets("pullup", 10, 5), sets("pullup", 12, 12))).status("pullup")
        assertEquals(SkillLevel.BEGINNER, status.level)
        assertEquals(SkillLevel.INTERMEDIATE, status.next)
        assertEquals(22, status.sets)
        assertEquals(12, status.best)
        assertEquals(8, status.setsToGo)
        assertEquals(12 * 100 / 20, status.progress)
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
    fun everyLevelIsEarnedOnTheSkillItself() {
        // The archer pull-up's Beginner target is 2 reps each side. A harder rung says nothing of an easier one's targets
        // (the coaching review, 29 October 2026): the rungs below keep the levels of their own sets.
        val beginner = book.progress(listOf(sets("archer_pullup", 10, 2)))
        assertEquals(SkillLevel.BEGINNER, beginner.status("archer_pullup").level)
        assertEquals(SkillLevel.NONE, beginner.status("pullup").level)
        assertEquals(SkillLevel.NONE, beginner.status("assisted_pullup").level)
        val both = book.progress(listOf(sets("archer_pullup", 30, 2), sets("pullup", 30, 40)))
        assertEquals(SkillLevel.ELITE, both.status("pullup").level)
        assertEquals(SkillLevel.BEGINNER, both.status("archer_pullup").level)
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
        // A harder rung leaves the easier one to its own sets: both stay open to raise.
        val both = book.progress(listOf(sets("archer_pullup", 30, 3), SkillCredit("p1")))
        assertEquals(setOf("archer_pullup", "assisted_pullup"), both.closest(3).map { it.skill.id }.toSet())
    }

    @Test
    fun levelsAddUpAcrossSkills() {
        val progress = book.progress(listOf(sets("pullup", 30, 5)))
        assertEquals(4 * 5, progress.totalLevels)
        // Beginner pull-up; the assisted pull-up below it has no sets of its own, so no level.
        assertEquals(SkillLevel.BEGINNER.ordinal, progress.levels)
    }

    @Test
    fun theBeltsAreTheThirtySevenSkillsOfRoundNine() {
        val skillIds = everyRung.families.flatMap { family -> family.skills.map { it.id } }
        assertEquals(37, skillIds.size)
        assertEquals("each skill appears once", skillIds.size, skillIds.toSet().size)
        assertEquals(SkillStandards.ALL.keys, skillIds.toSet())
        // The skills that left the belts are movements now, kept as foundations so they can't rejoin a ladder.
        val foundations = SkillBook.DEFAULT_FAMILIES.flatMap { it.foundations }.toSet()
        DROPPED.forEach { id ->
            assertFalse("$id earns no belt", id in skillIds)
            assertTrue("$id stays a movement of its family", id in foundations)
        }
        assertEquals("no family is left without a rung", SkillBook.DEFAULT_FAMILIES.map { it.id }, everyRung.families.map { it.spec.id })
        fun ladder(id: String) = everyRung.families.first { it.spec.id == id }.skills.map { it.id }
        assertEquals(listOf("dead_hang", "pullup", "archer_pullup", "one_arm_pullup"), ladder("pullup"))
        assertEquals(listOf("pseudo_planche_pushup", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"),
            ladder("planche"))
        assertEquals(listOf("squat", "pistol_squat"), ladder("pistol"))
        assertEquals(listOf("hollow_body_hold", "dragon_flag"), ladder("dragon_flag"))
        assertEquals(listOf("parallel_dip"), ladder("dip"))
        // Working-set targets (a set at one or two reps in reserve), as the owner took them for round 9.
        assertEquals(listOf(30, 45, 60, 75, 90), SkillStandards.of("dead_hang").targets)
        assertEquals(listOf(6, 12, 20, 30, 40), SkillStandards.of("pushup").targets)
        assertEquals(listOf(15, 20, 25, 30, 40), SkillStandards.of("squat").targets)
        assertEquals(listOf(20, 30, 40, 50, 60), SkillStandards.of("hollow_body_hold").targets)
        // The rarest rungs stay reachable inside the program, so two levels may share a target and differ by their sets.
        assertEquals(listOf(2, 2, 2, 3, 4), SkillStandards.of("full_planche").targets)
        assertEquals(listOf(1, 1, 1, 2, 3), SkillStandards.of("one_arm_pullup").targets)
        assertEquals(listOf(1, 2, 3, 4, 5), SkillStandards.of("strict_muscleup").targets)
        // Beginner to Advanced share the full planche's 2 s and differ by their sets; Master and Elite each ask longer.
        assertEquals(SkillLevel.INTERMEDIATE, everyRung.progress(listOf(sets("full_planche", 29, 2))).status("full_planche").level)
        assertEquals(SkillLevel.ADVANCED, everyRung.progress(listOf(sets("full_planche", 30, 2))).status("full_planche").level)
        // From Advanced up every level takes 30 sets, so no two of them may share a target.
        SkillStandards.ALL.forEach { (id, standard) -> assertTrue(id, standard.targets.drop(2).zipWithNext().all { (a, b) -> b > a }) }
        assertEquals(setOf("archer_pullup", "one_arm_pullup", "one_arm_pushup", "pistol_squat", "tuck_flag", "straddle_flag", "full_flag"),
            SkillStandards.ALL.filterValues { it.perSide }.keys)
        assertEquals(setOf("dead_hang", "wall_handstand", "freestanding_handstand", "hollow_body_hold", "lsit", "v_sit",
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
        assertEquals("full_planche", OverallScore.SKILL_VALUES.maxBy { it.value }.key)
    }

    @Test
    fun theDeployedLeaderboardFunctionAcceptsEveryFamily() {
        // boards.py refuses a family it doesn't know or above its ceiling, and checks the overall against the sum of all
        // its ceilings. So every family keeps its old top value, and the retired Nordic curl family's stays in the max.
        val boards = File("../firebase/functions/boards.py").readText()
        val ceilings = Regex("\"(\\w+)\": (\\d+)").findAll(boards.substringAfter("FAMILY_CEILINGS = {").substringBefore("}"))
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(everyRung.families.associate { it.spec.id to it.ceiling } + OverallScore.RETIRED_CEILINGS, ceilings)
        assertTrue(OverallScore.RETIRED_CEILINGS.keys.none { id -> everyRung.families.any { it.spec.id == id } })
        assertEquals(ceilings.values.sum(), OverallScore.scoreMax(everyRung.families))
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
        // The curriculum is regenerated for round 9: these hold for any curriculum that teaches the 37 skills.
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
        listOf("one_arm_pullup", "one_arm_pushup", "freestanding_handstand", "v_sit").forEach { top ->
            val skill = real.skill(top)!!
            assertTrue("$top tops its ladder", real.families.any { it.skills.last() == skill })
            // Levels 8-10 teach every top shape, the one-arm pull-up and push-up and the V-sit included.
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
        // A rung the plan trains 10 times or more never asks for a Beginner set longer than the plan's own longest, unless
        // the plan's time is a practice window: following the classes as written must be able to earn it.
        assertEquals("Beginner beyond the plan", emptyList<String>(), graduate.statuses
            .filter { it.sets >= SkillLevel.BEGINNER.sets && it.level == SkillLevel.NONE && !it.skill.standard.window }
            .filter { it.best < it.skill.standard.target(SkillLevel.BEGINNER)!! }
            .map { it.skill.id })
    }

    /** Every ladder with each of its rungs in the library, as a curriculum that teaches all 37 skills gives them. */
    private val everyRung = SkillBook.build(
        SkillBook.DEFAULT_FAMILIES.flatMap { it.ladder }.associateWith { SkillMovement(it, it, "skill", emptySet(), null) },
        emptyList())

    private companion object {
        /** The 20 skills that left the belts in round 9 (29 October 2026). */
        val DROPPED = listOf("active_hang", "chinup", "typewriter_pullup", "chest_to_bar", "straight_bar_support", "support_hold",
            "straight_bar_dip", "pause_pushup", "close_pushup", "archer_pushup", "elevated_pike_pushup", "planche_lean", "frog_stand",
            "handstand_balance", "one_leg_front", "one_leg_back", "one_leg_lsit", "knee_raise", "sliding_leg_curl", "nordic_curl")
    }

    private fun SkillProgress.status(id: String) = statuses.first { it.skill.id == id }
}
