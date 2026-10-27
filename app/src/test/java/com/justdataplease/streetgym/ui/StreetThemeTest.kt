package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreetThemeTest {
    @Test
    fun concreteAndTapeIsTheDefault() {
        assertEquals(StreetTheme.CONCRETE, StreetTheme.DEFAULT)
        assertEquals(listOf(StreetTheme.CONCRETE, StreetTheme.NIGHT_PARK, StreetTheme.WHEATPASTE), StreetTheme.entries)
    }

    @Test
    fun droppedLooksOpenAsTheLookTheyCopied() {
        assertEquals(StreetTheme.CONCRETE, StreetTheme.fromKey("concrete_trees"))
        assertEquals(StreetTheme.NIGHT_PARK, StreetTheme.fromKey("green_park"))
        assertNull(StreetTheme.fromKey("fresh_court"))
        StreetTheme.entries.forEach { assertEquals(it, StreetTheme.fromKey(it.key)) }
    }
}
