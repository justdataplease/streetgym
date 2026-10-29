package com.justdataplease.streetgym.widget

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.model.ContentOrigin
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NextClassCardTest {
    private fun step(movement: String, name: String, assessment: Boolean = false) = TrainingExercise(
        id = "x:$movement", movementId = movement, number = 1, name = LocalizedText(name), description = LocalizedText.EMPTY,
        sets = emptyList(), assessment = assessment,
    )

    private fun program(title: String, steps: List<TrainingExercise>, dayType: String? = null, level: Int = 4, number: Int = 20) =
        TrainingProgram(
            id = "sg:l%02d:p%04d".format(level, number), number = number, levelNumber = level, name = LocalizedText(title),
            description = LocalizedText.EMPTY, origin = ContentOrigin.NEW_LEVEL, hasDetails = true, proPlaceholder = false,
            week = null, day = null, estimatedMinutes = 47, exercises = steps, dayType = dayType,
        )

    @Test
    fun aClassShowsItsRoleAndTheMainPartsLead() {
        val card = NextClassCard.of(program("Basics & Endurance · Strict Pull-Up",
            listOf(step("pullup", "Strict Pull-Up"), step("parallel_dip", "Parallel-Bar Dip")), "basics"), locked = false)
        assertEquals(NextClassCard.Kind.CLASS, card.kind)
        assertEquals("Basics & Endurance", card.role)
        assertEquals("Strict Pull-Up", card.detail)
        assertEquals("pullup", card.movementId)
        assertEquals("sg:l04:p0020", card.programId)
        assertEquals(4, card.levelNumber)
        assertEquals(20, card.classNumber)
        assertEquals(47, card.minutes)
        assertFalse(card.locked)
    }

    @Test
    fun theLeadComesFromTheMainPartNotTheTitle() {
        val card = NextClassCard.of(program("Pull & Push Circuit · Something Else", listOf(step("inverted_row", "Inverted Row"))),
            locked = false)
        assertEquals("Inverted Row", card.detail)
        assertEquals("inverted_row", card.movementId)
    }

    @Test
    fun aRecoveryDayNamesItsThemeAndWalks() {
        val card = NextClassCard.of(program("Recovery · Shoulders & Upper Back",
            listOf(step("easy_walk", "Easy Walk"), step("wall_slide", "Wall Slide")), "recovery"), locked = false)
        assertEquals(NextClassCard.Kind.RECOVERY, card.kind)
        assertEquals("Recovery", card.role)
        assertEquals("Shoulders & Upper Back", card.detail)
        assertEquals("easy_walk", card.movementId)
    }

    @Test
    fun anExamCountsItsTargetsAndLeadsWithTheFirstTest() {
        val card = NextClassCard.of(program("Block 2 Exam · L-Sit", listOf(
            step("wall_handstand", "Chest-to-Wall Handstand", assessment = true),
            step("lsit", "L-Sit", assessment = true),
            step("pullup", "Strict Pull-Up", assessment = true),
        ), "exam_block"), locked = false)
        assertEquals(NextClassCard.Kind.EXAM, card.kind)
        assertEquals("Block 2 Exam", card.role)
        assertEquals("", card.detail)
        assertEquals(3, card.targets)
        assertEquals("wall_handstand", card.movementId)
    }

    @Test
    fun aTitleWithoutTheDotIsAllRole() {
        val card = NextClassCard.of(program("Level 5 Test: Balance & Levers",
            listOf(step("freestanding_handstand", "Freestanding Handstand", assessment = true)), "exam_level"), locked = true)
        assertEquals("Level 5 Test: Balance & Levers", card.role)
        assertTrue(card.locked)
    }

    @Test
    fun beforeThePlacementTheTapOpensTheAppsHome() {
        val card = NextClassCard.of(program("Pull & Push Circuit · Inverted Row", listOf(step("inverted_row", "Inverted Row")),
            level = 1, number = 1), locked = false, opens = false)
        assertNull(card.programId)
        assertEquals(1, card.classNumber)
    }

    @Test
    fun allDoneKeepsTheLastLeadMovingAndOpensHome() {
        val last = program("Champion Test: the Full Battle", listOf(step("pullup", "Strict Pull-Up", true)), "exam_level", 10, 100)
        val card = NextClassCard.allDone(last)
        assertEquals(NextClassCard.Kind.ALL_DONE, card.kind)
        assertNull(card.programId)
        assertEquals("pullup", card.movementId)
    }

    @Test
    fun framesSpanAboutOneLoop() {
        assertEquals(125, WidgetFrames.intervalMs(2000))
        assertEquals(200, WidgetFrames.intervalMs(3200))
        assertEquals(WidgetFrames.MIN_INTERVAL_MS, WidgetFrames.intervalMs(400))
        assertEquals(WidgetFrames.MAX_INTERVAL_MS, WidgetFrames.intervalMs(20_000))
        assertEquals(WidgetFrames.MAX_INTERVAL_MS, WidgetFrames.intervalMs(0))
        // One still drawing (system animations off) never flips quickly.
        assertEquals(WidgetFrames.MAX_INTERVAL_MS, WidgetFrames.intervalMs(2000, frames = 1))
    }

    @Test
    fun framesStartAtTheClipsOwnPoseAndStepEvenly() {
        val start = 0.2f
        val phases = (0 until WidgetFrames.COUNT).map { WidgetFrames.phase(it, start) }
        assertEquals(start, phases.first(), 1e-6f)
        assertTrue(phases.all { it >= 0f && it < 1f })
        val sorted = phases.sorted()
        for (i in 1 until sorted.size) assertEquals(1f / WidgetFrames.COUNT, sorted[i] - sorted[i - 1], 1e-5f)
        // The last drawing wraps round to just before the first.
        assertEquals(start - 1f / WidgetFrames.COUNT, WidgetFrames.phase(WidgetFrames.COUNT - 1, start), 1e-5f)
    }

    @Test
    fun theHeadlineGetsWhatTheCardLeaves() {
        assertEquals(WidgetLayout.DEFAULT_WIDTH_DP - WidgetLayout.FIXED_DP, WidgetLayout.titleWidthDp(0))
        assertEquals(380 - WidgetLayout.FIXED_DP, WidgetLayout.titleWidthDp(380))
        assertEquals(WidgetLayout.MIN_TITLE_DP, WidgetLayout.titleWidthDp(200))
    }

    @Test
    fun theMetaLineKeepsItsNumbers() {
        // Room for both: the tape stays and the line keeps its size.
        WidgetLayout.fitMeta(needed = 120f, column = 186f, tape = 46f).let { assertTrue(it.tape); assertEquals(1f, it.scale) }
        // Room for the line alone: the tape steps aside.
        WidgetLayout.fitMeta(needed = 150f, column = 186f, tape = 46f).let { assertFalse(it.tape); assertEquals(1f, it.scale) }
        // Less than that: the line shrinks, never below three quarters.
        WidgetLayout.fitMeta(needed = 150f, column = 135f, tape = 46f).let { assertFalse(it.tape); assertEquals(0.9f, it.scale, 1e-4f) }
        assertEquals(WidgetLayout.MIN_META_SCALE, WidgetLayout.fitMeta(needed = 150f, column = 60f, tape = 46f).scale)
    }

    @Test
    fun aShortCellGetsTheCompactCard() {
        assertFalse(WidgetLayout.compact(0))
        assertFalse(WidgetLayout.compact(104))
        assertFalse(WidgetLayout.compact(100))
        assertTrue(WidgetLayout.compact(62))
    }

    /** Every class in the curriculum gives the widget a headline and a movement, and an exam always has a target. */
    @Test
    fun everyClassMakesACard() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val ordered = NextClass.ordered(catalog)
        assertTrue(ordered.isNotEmpty())
        for (program in ordered) {
            val card = NextClassCard.of(program, locked = false)
            val where = "level ${program.levelNumber} class ${program.number}"
            assertTrue("no headline: $where", card.role.isNotBlank())
            assertTrue("no movement: $where", !card.movementId.isNullOrBlank())
            when (card.kind) {
                NextClassCard.Kind.EXAM -> assertTrue("no targets: $where", card.targets > 0)
                else -> assertTrue("no detail: $where", card.detail.isNotBlank())
            }
            assertFalse("a date word: $where", DATE_WORDS.containsMatchIn("${card.role} ${card.detail}"))
        }
    }

    /** The class sequence has no calendar: the widget's own words never name a day or a date. */
    @Test
    fun theWidgetsWordsNameNoDay() {
        val strings = File("src/main/res/values/strings_widget.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(DATE_WORDS.containsMatchIn(strings))
    }

    private companion object {
        val DATE_WORDS = Regex("\\b(today|tomorrow|tonight|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday|missed|late|overdue)\\b",
            RegexOption.IGNORE_CASE)
    }
}
