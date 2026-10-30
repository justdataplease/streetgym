package com.justdataplease.streetgym.data.skills

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * The weighted overall score: on every family's ladders as the skills define them, and on the shipped curriculum for
 * the program as written.
 */
class OverallScoreTest {
    /** Each skill in [levels] at that level: 30 sets of it at the level's target (enough for any level), as an own day. */
    private fun score(levels: Map<String, SkillLevel>, credits: List<SkillCredit> = emptyList()): OverallScore =
        book.progress(credits + levels.mapNotNull { (id, level) ->
            SkillStandards.of(id).target(level)?.let { target ->
                SkillCredit("custom:$id", mapOf(id to SkillLevel.ELITE.sets), doses = mapOf(id to List(SkillLevel.ELITE.sets) { target }))
            }
        }).overall

    private fun elite(vararg ids: String) = score(ids.associateWith { SkillLevel.ELITE })

    @Test fun nothingDoneIsZeroAndEliteOfEveryTopRungIsTheWholeScale() {
        assertEquals(0, score(emptyMap()).score)
        assertEquals(OverallScore.Tier.BEGINNER, score(emptyMap()).tier)
        val tops = book.families.map { it.skills.maxBy { skill -> skill.value }.id }
        // Round 11 retires nothing (score version 3 drops the Nordic curl): the 16 families' tops add up to 887, and
        // Elite on all of them is the whole 1000.
        val retired = OverallScore.RETIRED_CEILINGS.values.sum()
        assertEquals(0, retired)
        assertEquals(887, score(emptyMap()).max)
        assertEquals(OverallScore.MAX, elite(*tops.toTypedArray()).score)
        val max = book.families.sumOf { it.ceiling } + retired
        assertEquals(max, score(emptyMap()).max)
        assertEquals((OverallScore.MAX * (max - retired).toDouble() / max).roundToInt(), elite(*tops.toTypedArray()).score)
        assertEquals(OverallScore.Tier.WORLD_CLASS, elite(*tops.toTypedArray()).tier)
    }

    @Test fun harderSkillsWeighMore() {
        val pullup = elite("pullup").score
        val tuckFront = elite("tuck_front_hold").score
        val fullFront = elite("full_front").score
        val fullPlanche = elite("full_planche").score
        assertTrue("$pullup < $tuckFront < $fullFront < $fullPlanche", pullup < tuckFront && tuckFront < fullFront && fullFront < fullPlanche)
        // A full planche is worth twice a full front lever, and a full front lever about twice a full back lever.
        assertEquals(2.0, fullPlanche.toDouble() / fullFront, 0.02)
        assertEquals(2.0, fullFront.toDouble() / elite("full_back").score, 0.05)
    }

    @Test fun eachLevelEarnsAFifthOfTheSkillsValue() {
        fun family(levels: Map<String, SkillLevel>) = score(levels).families.first { it.family.spec.id == "pullup" }
        fun own(level: SkillLevel) = OverallScore.points(book.progress(listOf(SkillCredit("custom:p",
            mapOf("pullup" to SkillLevel.ELITE.sets),
            doses = mapOf("pullup" to List(SkillLevel.ELITE.sets) { SkillStandards.of("pullup").target(level)!! }))))
            .statuses.first { it.skill.id == "pullup" })
        // The pull-up is worth 10: Beginner 2, Intermediate 4, Elite 10.
        assertEquals(2.0, own(SkillLevel.BEGINNER), 1e-9)
        assertEquals(4.0, own(SkillLevel.INTERMEDIATE), 1e-9)
        assertEquals(10.0, own(SkillLevel.ELITE), 1e-9)
        // The family counts its best skill, each earned on itself: a Beginner pull-up gives the dead hang below it nothing.
        assertEquals(2.0, family(mapOf("pullup" to SkillLevel.BEGINNER)).points, 1e-9)
        assertEquals("pullup", family(mapOf("pullup" to SkillLevel.BEGINNER)).best?.skill?.id)
        assertEquals(10.0, family(mapOf("pullup" to SkillLevel.ELITE)).points, 1e-9)
        val levels = SkillLevel.entries.map { score(mapOf("pullup" to it)).score }
        assertEquals(levels.sorted(), levels)
    }

    @Test fun eachFamilyCountsOnlyItsBestSkill() {
        val pullup = elite("pullup").score
        assertEquals(pullup, elite("dead_hang", "pullup").score)
        // Two families add up.
        val dip = elite("parallel_dip").score
        assertEquals((pullup + dip).toDouble(), elite("pullup", "parallel_dip").score.toDouble(), 1.0)
    }

    @Test fun followingTheProgramAsPrescribedScoresSomethingWithTheTopStillAhead() {
        val attended = real.progress(allClasses).overall
        // The classes take every skill to Master (review 11), but each ladder's top rung only most of the way to Elite, so
        // the top stays ahead: about 81 of 100, in the Elite tier with World Class still ahead.
        assertTrue("program as written: ${attended.score}", attended.score in OverallScore.MAX * 4 / 5 until OverallScore.MAX * 17 / 20)
        assertEquals(OverallScore.Tier.ELITE, attended.tier)
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
        val tops = book.families.map { it.skills.maxBy { skill -> skill.value }.id }
        val overall = elite(*tops.toTypedArray())
        val ceilings = SkillGroup.entries.map { overall.group(it) }
        ceilings.forEach { (points, ceiling) -> assertEquals(ceiling, points) }
        // The four groups share the thousand (less the retired families' share, none since round 11).
        assertEquals(overall.scaled(overall.max - OverallScore.RETIRED_CEILINGS.values.sum().toDouble()).toDouble(),
            ceilings.sumOf { it.second }.toDouble(), 2.0)
    }

    private companion object {
        /** Every family with all its rungs, as a curriculum that teaches the 40 skills gives them. */
        val book = SkillBook.build(
            SkillBook.DEFAULT_FAMILIES.flatMap { it.ladder }.associateWith { SkillMovement(it, it, "skill", emptySet(), null) },
            emptyList())
        val curriculum: String by lazy { File("src/main/assets/curriculum.json").readText() }
        val real by lazy { SkillCurriculumReader.read(curriculum) }
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
