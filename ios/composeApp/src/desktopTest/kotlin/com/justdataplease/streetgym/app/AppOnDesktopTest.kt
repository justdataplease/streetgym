package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadCatalog
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.readiness_note
import com.justdataplease.streetgym.app.resources.sequence_locked_note
import com.justdataplease.streetgym.app.resources.sets_count
import com.justdataplease.streetgym.app.resources.streak_days
import com.justdataplease.streetgym.app.resources.theme_concrete
import com.justdataplease.streetgym.app.resources.theme_level_line
import com.justdataplease.streetgym.data.progress.Intensity
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The Android app's files as this app reads them: its texts, and its program through the shared engine. */
class AppOnDesktopTest {
    private val now = 20_775 * 86_400_000L + 12 * 3_600_000L // 18 Nov 2026, 12:00 UTC

    @Test
    fun androidTextsReadAsWritten() = runBlocking {
        // An escaped apostrophe, a bare %d, numbered places, an XML entity and a plural.
        assertEquals("Finish today's class first.", getString(Res.string.sequence_locked_note))
        assertTrue(getString(Res.string.readiness_note).startsWith("Can't keep clean form?"))
        assertEquals("12 day streak", getString(Res.string.streak_days, 12))
        assertEquals("Level 1 — Foundations", getString(Res.string.theme_level_line, 1, "Foundations"))
        assertEquals("Concrete & Tape", getString(Res.string.theme_concrete))
        assertEquals("1 set", getPluralString(Res.plurals.sets_count, 1, 1))
        assertEquals("3 sets", getPluralString(Res.plurals.sets_count, 3, 3))
    }

    @Test
    fun todayOffersTheFirstClassAndMovesOnWhenADayIsCompleted() = runBlocking {
        val model = AppModel(loadCatalog(), StoredProgress(MemoryStore()), now = { now }, zone = TimeZone.UTC)
        val first = model.today()

        assertEquals(1_000, first.total)
        assertEquals(0, first.done)
        assertEquals(1, first.next?.levelNumber)
        assertEquals(1, first.next?.number)
        assertFalse(first.locked)

        model.completeDay(first.next!!.id)
        val second = model.today()
        assertEquals(1, second.done)
        assertEquals(2, second.next?.number)
        assertTrue(model.isCompleted(first.next!!.id))
        // Marking a day complete is not training: the streak stays at nothing.
        assertEquals(0, second.streak.streak.days)
    }

    @Test
    fun harderAddsToTheStrengthWorkOfAClass() = runBlocking {
        val model = AppModel(loadCatalog(), StoredProgress(MemoryStore()), now = { now }, zone = TimeZone.UTC)
        val id = model.today().next!!.id
        val normal = model.program(id)!!.exercises.flatMap { it.sets }.mapNotNull { it.repetitions ?: it.durationSeconds }
        model.intensity = Intensity.HARDER
        val harder = model.program(id)!!.exercises.flatMap { it.sets }.mapNotNull { it.repetitions ?: it.durationSeconds }

        assertEquals(normal.size, harder.size)
        assertNotEquals(normal, harder)
        assertTrue(normal.zip(harder).all { (written, scaled) -> scaled >= written })
    }
}
