package com.justdataplease.streetgym.data.equipment

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EquipmentTest {
    private val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
    private val ordered = NextClass.ordered(catalog)

    @Test
    fun everyLibraryTagHasADecision() {
        val tags = catalog.movementEquipment.values.flatten().toSet()
        val open = tags - EquipmentNeeds.TAGS.keys - EquipmentNeeds.ANYWHERE
        assertTrue("tags with no decision: $open", open.isEmpty())
        assertTrue(EquipmentNeeds.TAGS.keys.intersect(EquipmentNeeds.ANYWHERE).isEmpty())
        // Every switch leaves something out, so none is a dead switch.
        Equipment.entries.forEach { equipment ->
            assertTrue(equipment.id, catalog.movementEquipment.values.any { tags -> EquipmentNeeds.of(tags).any { equipment in it } })
        }
    }

    @Test
    fun aMovementIsOffWhenNothingServesANeed() {
        val tags = catalog.movementEquipment
        val noBar = EquipmentSwitches().with(Equipment.PULLUP_BAR, have = false)
        assertEquals(Equipment.PULLUP_BAR, noBar.missing(tags.getValue("pullup")))
        assertNull(noBar.missing(tags.getValue("pushup")))
        assertNull(noBar.missing(tags.getValue("inverted_row")))
        // Incline push-ups go on a box, a low bar or the parallel bars: off only when all three are.
        val noBox = EquipmentSwitches(setOf(Equipment.BOX))
        assertNull(noBox.missing(tags.getValue("incline_pushup")))
        assertEquals(Equipment.BOX, EquipmentSwitches(setOf(Equipment.BOX, Equipment.LOW_BAR, Equipment.PARALLEL_BARS))
            .missing(tags.getValue("incline_pushup")))
        // Two needs: the feet-elevated row needs the low bar and the box.
        assertEquals(Equipment.BOX, noBox.missing(tags.getValue("feet_elevated_row")))
        assertEquals(Equipment.WALL, EquipmentSwitches(setOf(Equipment.WALL)).missing(tags.getValue("wall_handstand")))
        assertNull(EquipmentSwitches().missing(tags.getValue("pullup")))
    }

    @Test
    fun switchesRoundTrip() {
        val off = EquipmentSwitches(setOf(Equipment.WALL, Equipment.PULLUP_BAR))
        assertEquals("pullup_bar,wall", EquipmentSwitches.format(off))
        assertEquals(off, EquipmentSwitches.parse("pullup_bar,wall"))
        assertEquals(off, EquipmentSwitches.parse("wall, pullup_bar,rocket"))
        assertFalse(EquipmentSwitches.parse(null).any)
        assertEquals("", EquipmentSwitches.format(EquipmentSwitches()))
        assertTrue(off.with(Equipment.WALL, have = true).has(Equipment.WALL))
    }

    @Test
    fun aClassWithNothingLeftCountsAsPassed() {
        val none = EquipmentSwitches()
        assertTrue(ordered.none { it.switchedOff(SkillSwitches(), none) })
        assertEquals(emptySet<String>(), NextClass.passed(ordered, emptySet(), SkillSwitches(), none))
        // No bars at all: every bar class goes, the floor classes stay.
        val noBars = EquipmentSwitches(setOf(Equipment.PULLUP_BAR, Equipment.LOW_BAR, Equipment.PARALLEL_BARS))
        val passed = NextClass.passed(ordered, emptySet(), SkillSwitches(), noBars)
        assertTrue(passed.isNotEmpty())
        assertTrue(passed.size < ordered.size / 2)
        // One piece of equipment that only a few steps use never empties a class.
        assertTrue(ordered.none { it.switchedOff(SkillSwitches(), EquipmentSwitches(setOf(Equipment.BAND))) })
    }
}
