package com.justdataplease.streetgym.data.skills

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.progress.NextClass
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillSwitchesTest {
    private val families = SkillBook.DEFAULT_FAMILIES.associateBy { it.id }

    @Test
    fun everyLineFollowsItsLadder() {
        SkillProgressions.LINES.forEach { (id, steps) ->
            val ladder = families.getValue(id).ladder
            // Each rung sits in its line at its own index, the steps never go back down the ladder, and no basic is in one.
            ladder.forEachIndexed { rung, skill ->
                if (skill !in SkillProgressions.LOCKED) assertEquals("$id $skill", rung, steps.single { it.movementId == skill }.rung)
            }
            assertEquals(id, steps.map { it.rung }.sorted(), steps.map { it.rung })
            assertTrue(id, steps.none { it.movementId in SkillProgressions.LOCKED })
            assertTrue(id, steps.all { it.rung in ladder.indices })
        }
        // The dip's line is all basics; every other family can be switched off.
        assertEquals(SkillBook.DEFAULT_FAMILIES.map { it.id } - "dip", SkillProgressions.switchable(SkillBook.DEFAULT_FAMILIES).map { it.id })
        assertEquals(2, SkillProgressions.firstSwitchable(families.getValue("pullup")))
        assertEquals(0, SkillProgressions.firstSwitchable(families.getValue("handstand")))
    }

    @Test
    fun aLineGoesOffFromItsRungOn() {
        val noOneArm = SkillSwitches().with("handstand", 2)
        assertTrue(noOneArm.isOff("one_arm_handstand"))
        assertTrue(noOneArm.isOff("straddle_handstand"))
        assertFalse(noOneArm.isOff("freestanding_handstand"))
        assertFalse(noOneArm.isOff("wall_handstand"))
        assertEquals("handstand", noOneArm.offFamily("handstand_weight_shift"))
        assertNull(noOneArm.offFamily("handstand_exit"))
        // The whole family, and the freestanding handstand push-up that needs its freestanding balance.
        val noHandstand = noOneArm.with("handstand", 0)
        assertTrue(noHandstand.isOff("wall_handstand"))
        assertTrue(noHandstand.isOff("freestanding_hspu"))
        assertEquals("handstand", noHandstand.offFamily("freestanding_hspu"))
        assertFalse(noHandstand.isOff("wall_hspu"))
        // The basics never go: no planche keeps the push-up, no flag keeps the pull-up.
        val noPlanche = SkillSwitches().with("planche", 0).with("human_flag", 0)
        listOf("pushup", "pullup", "support_hold", "hollow_body_hold").forEach { assertFalse(it, noPlanche.isOff(it)) }
        assertTrue(noPlanche.isOff("frog_stand"))
        // A step two lines share stays while either is on.
        val noBack = SkillSwitches().with("back_lever", 0)
        assertFalse(noBack.isOff("german_hang"))
        assertTrue(noBack.isOff("tuck_back_hold"))
        assertTrue(noBack.with("hefesto", 0).isOff("german_hang"))
        // Back on.
        assertFalse(noHandstand.with("handstand", null).any)
    }

    @Test
    fun switchesKeepInPreferences() {
        val switches = SkillSwitches().with("planche", 3).with("handstand", 0)
        assertEquals("handstand:0,planche:3", SkillSwitches.format(switches))
        assertEquals(switches, SkillSwitches.parse(SkillSwitches.format(switches)))
        assertEquals(SkillSwitches(), SkillSwitches.parse(null))
        assertEquals(SkillSwitches(), SkillSwitches.parse("junk,planche:x"))
    }

    @Test
    fun everySkillMovementOfTheClassesIsInALineOrShared() {
        // The shipped curriculum: every movement the classes use that carries a switchable family's own pattern is a step
        // of a line or one of the shared basics, so a new movement can't slip past the switches.
        val curriculum = JSONObject(File("src/main/assets/curriculum.json").readText())
        val library = curriculum.getJSONArray("movement_library").let { a -> (0 until a.length()).map(a::getJSONObject) }
        val ids = library.map { it.getString("id") }.toSet()
        SkillProgressions.LINES.values.flatten().forEach { assertTrue(it.movementId, it.movementId in ids) }
        val shared = setOf("straight_bar_support", "straight_bar_dip", "elevated_plank", "kneeling_side_plank", "overhead_line",
            "tuck_back_entry", "one_leg_back")
        val patterns = SkillProgressions.switchable(SkillBook.DEFAULT_FAMILIES)
            .filter { it.id !in setOf("pullup", "pushup", "pistol") }.flatMap { it.tags }.toSet()
        val loose = library.filter { m ->
            val own = m.optJSONArray("patterns")?.let { p -> (0 until p.length()).map(p::getString) }.orEmpty()
            own.any { it in patterns }
        }.map { it.getString("id") }
            .filterNot { it in SkillSwitches.HOMES || it in SkillProgressions.LOCKED || it in shared || it in SkillProgressions.REQUIRES }
        assertEquals("skill movements outside every line", emptyList<String>(), loose)
    }

    @Test
    fun aClassWithNothingLeftToTrainIsPassed() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val ordered = NextClass.ordered(catalog)
        // One skill off never empties a class: every class trains more than one line.
        SkillProgressions.LINES.keys.forEach { family ->
            val one = SkillSwitches().with(family, 0)
            assertEquals(family, emptyList<String>(), ordered.filter { it.switchedOff(one) }.map { it.id })
        }
        // Every skill off: the classes left with only skill work are passed, and Today steps over them.
        val all = SkillProgressions.LINES.keys.fold(SkillSwitches()) { switches, family -> switches.with(family, 0) }
        val empty = ordered.filter { it.switchedOff(all) }
        val passed = NextClass.passed(ordered, emptySet(), all)
        assertEquals(empty.map { it.id }.toSet(), passed)
        val next = NextClass.of(ordered, passed, null)!!
        assertFalse(next.switchedOff(all))
        assertEquals(ordered.first { !it.switchedOff(all) }, next)
        // Nothing switched off passes nothing.
        assertEquals(emptySet<String>(), NextClass.passed(ordered, emptySet(), SkillSwitches()))
    }

    @Test
    fun theScoreIsOutOfTheSkillsTrained() {
        val book = SkillBook.build(SkillBook.DEFAULT_FAMILIES.flatMap { it.ladder }.associateWith { SkillMovement(it, it, "skill", emptySet(), null) },
            emptyList())
        fun elite(id: String): SkillCredit {
            val target = SkillStandards.of(id).target(SkillLevel.ELITE)!!
            return SkillCredit("custom:$id", mapOf(id to SkillLevel.ELITE.sets), doses = mapOf(id to List(SkillLevel.ELITE.sets) { target }))
        }
        val progress = book.progress(listOf(elite("pullup"), elite("parallel_dip")))
        // The planche and handstand switched off: the same points out of a smaller whole.
        val switches = SkillSwitches().with("planche", 0).with("handstand", 0)
        val trained = progress.trained(switches)
        assertTrue(trained.overall.score > progress.overall.score)
        assertEquals(progress.overall.families.sumOf { it.points }, trained.overall.families.sumOf { it.points }, 1e-9)
        assertFalse(trained.families.any { (family, _) -> family.spec.id in setOf("planche", "handstand") })
        // Only the one-arm handstand off: the handstand family stays, topped by the freestanding handstand.
        val noOneArm = progress.trained(SkillSwitches().with("handstand", 2))
        val handstand = noOneArm.families.first { (family, _) -> family.spec.id == "handstand" }.first
        assertEquals(listOf("wall_handstand", "freestanding_handstand"), handstand.skills.map { it.id })
        // Nothing off: the same progress.
        assertEquals(progress, progress.trained(SkillSwitches()))
    }
}
