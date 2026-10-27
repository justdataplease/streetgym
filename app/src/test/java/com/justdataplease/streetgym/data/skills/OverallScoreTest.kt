package com.justdataplease.streetgym.data.skills

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The weighted overall score on the shipped curriculum. */
class OverallScoreTest {
    private fun score(verified: Set<String>, credits: List<SkillCredit> = emptyList()) = book.progress(credits, verified).overall

    @Test fun nothingDoneIsZeroAndEveryTopRungIsTheWholeThousand() {
        assertEquals(0, score(emptySet()).score)
        assertEquals(OverallScore.Tier.BEGINNER, score(emptySet()).tier)
        val tops = book.families.map { it.skills.maxBy { skill -> skill.value }.id }.toSet()
        assertEquals(OverallScore.MAX, score(tops).score)
        assertEquals(OverallScore.Tier.WORLD_CLASS, score(tops).tier)
        assertEquals(book.families.sumOf { it.ceiling }, score(emptySet()).max)
    }

    @Test fun harderSkillsWeighMore() {
        val pullup = score(setOf("pullup")).score
        val tuckFront = score(setOf("tuck_front_hold")).score
        val fullFront = score(setOf("full_front")).score
        val fullPlanche = score(setOf("full_planche")).score
        assertTrue("$pullup < $tuckFront < $fullFront < $fullPlanche", pullup < tuckFront && tuckFront < fullFront && fullFront < fullPlanche)
        // A full planche is worth twice a full front lever, and a full front lever about twice a full back lever.
        assertEquals(2.0, fullPlanche.toDouble() / fullFront, 0.02)
        assertEquals(2.0, fullFront.toDouble() / score(setOf("full_back")).score, 0.05)
    }

    @Test fun eachFamilyCountsOnlyItsBestRung() {
        val pullup = score(setOf("pullup")).score
        assertEquals(pullup, score(setOf("assisted_pullup", "eccentric_pullup", "chinup", "pullup")).score)
        // Two families add up.
        val dip = score(setOf("parallel_dip")).score
        assertEquals((pullup + dip).toDouble(), score(setOf("pullup", "parallel_dip")).score.toDouble(), 1.0)
    }

    @Test fun trainingAloneEarnsAtMostHalfTheGapToTheNextRung() {
        // Every class of the program, followed to the letter, with no test ever passed.
        val attended = score(emptySet(), allClasses)
        assertTrue("attendance only: ${attended.score}", attended.score in 1..300)
        attended.families.forEach { family ->
            val base = family.base?.skill?.value ?: 0
            val next = family.next?.skill?.value ?: base
            assertTrue("${family.family.spec.id} ${family.points}", family.points <= base + OverallScore.PARTIAL * (next - base) + 1e-9)
        }
    }

    @Test fun followingTheProgramAndPassingEachTestReachesEliteWithTheEndSkillsStillAhead() {
        val taught = book.families.flatMap { it.skills }.filter { it.expected != null }.map { it.id }.toSet()
        val graduate = score(taught, allClasses)
        assertTrue("graduate ${graduate.score}", graduate.score in 650..849)
        assertEquals(OverallScore.Tier.ELITE, graduate.tier)
        // What's left: the rungs past the program, the full planche worth the most of them.
        val left = book.families.flatMap { it.skills }.filter { it.id !in taught }.map { it.id }.toSet()
        assertTrue(left.containsAll(setOf("full_planche", "one_arm_pullup", "one_arm_pushup", "v_sit")))
        assertFalse("freestanding_handstand" in left)  // round 6: practised from Level 5 and tested in the exams
    }

    @Test fun aTrainedRungShowsItsTestAsTheNextBigGain() {
        val overall = score(emptySet(), allClasses)
        val best = overall.bestTest()
        assertTrue("best $best", best != null && best.first.trained && best.second > 0)
        assertEquals(overall.gain(best!!.first.skill), best.second)
        // Passing it adds at least that much.
        val after = score(setOf(best.first.skill.id), allClasses).score
        assertTrue("${overall.score} + ${best.second} <= $after", after >= overall.score + best.second - 1)
    }

    @Test fun tiersCoverZeroToAThousand() {
        assertEquals(OverallScore.Tier.BEGINNER, OverallScore.Tier.of(74))
        assertEquals(OverallScore.Tier.NOVICE, OverallScore.Tier.of(75))
        assertEquals(OverallScore.Tier.INTERMEDIATE, OverallScore.Tier.of(424))
        assertEquals(OverallScore.Tier.ADVANCED, OverallScore.Tier.of(425))
        assertEquals(OverallScore.Tier.ELITE, OverallScore.Tier.of(849))
        assertEquals(OverallScore.Tier.WORLD_CLASS, OverallScore.Tier.of(1000))
        assertEquals(null, OverallScore.Tier.WORLD_CLASS.next)
    }

    @Test fun groupsShareTheThousand() {
        val tops = book.families.map { it.skills.maxBy { skill -> skill.value }.id }.toSet()
        val overall = score(tops)
        val ceilings = SkillGroup.entries.map { overall.group(it) }
        ceilings.forEach { (points, ceiling) -> assertEquals(ceiling, points) }
        assertEquals(OverallScore.MAX.toDouble(), ceilings.sumOf { it.second }.toDouble(), 2.0)
    }

    private companion object {
        val curriculum: String by lazy { File("src/main/assets/curriculum.json").readText() }
        val book by lazy { SkillCurriculumReader.read(curriculum) }
        val allClasses: List<SkillCredit> by lazy {
            val levels = JSONObject(curriculum).getJSONArray("levels")
            (0 until levels.length()).map(levels::getJSONObject).sortedBy { it.getInt("number") }.flatMap { level ->
                val programs = level.getJSONArray("programs")
                (0 until programs.length()).map(programs::getJSONObject).sortedBy { it.optInt("number") }
                    .map { SkillCredit(it.getString("id")) }
            }
        }
    }
}
