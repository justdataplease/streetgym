package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.PlatformFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Beep
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingStore
import com.justdataplease.streetgym.app.data.WallClock
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.figure.Align
import com.justdataplease.streetgym.app.figure.FigureText
import com.justdataplease.streetgym.app.figure.TextPen
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.app_version_debug_suffix
import com.justdataplease.streetgym.app.resources.app_version_name
import com.justdataplease.streetgym.app.resources.brand_kicker
import com.justdataplease.streetgym.app.resources.reset_body
import com.justdataplease.streetgym.app.resources.reset_confirm_message
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Shown
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The parity round of 19 Nov 2026 ("iOS and Android exactly the same"): Roboto wherever Android draws the system face
 * (Google's own files, the line man's words measured as Android measures them, dialogs, notes, wheels and the back
 * arrow, and only there), the alarm's time on a clock face, the version Android shows, Reduce Motion followed while the
 * app runs, the alarm stream's beep at Android's volume, and Android's landscape layouts (the focus view side by side,
 * Today keeping to the day's class, dialogs at 65% of the width).
 */
@OptIn(ExperimentalTestApi::class)
class ParityTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore(), hour24: Boolean = true): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            timing = Timing(TimingStore(store, WallClock { time }), WorkoutDevice.None, { TimeZone.UTC }, hour24))
    }

    private fun text(resource: org.jetbrains.compose.resources.StringResource) = runBlocking { getString(resource) }

    // --- Roboto -------------------------------------------------------------------------------------------------

    @Test
    fun robotoIsGooglesAndroidBuildWithItsLicence() {
        // Regular and Bold, the two weights the Android app draws the system face in, from Google's Roboto 2.138
        // release for Android (Apache 2.0); no Medium: the themes' fontFamily "sans" makes dialogs Regular too.
        assertEquals(setOf("roboto_bold.ttf", "roboto_regular.ttf"), FONTS.list()!!.toSet())
        val regular = Ttf(File(FONTS, "roboto_regular.ttf").readBytes())
        val bold = Ttf(File(FONTS, "roboto_bold.ttf").readBytes())
        assertEquals("Roboto-Regular", regular.name(6))
        assertEquals("Roboto-Bold", bold.name(6))
        assertTrue(regular.name(5).startsWith("Version 2.138"), regular.name(5))
        val licence = File("src/commonMain/composeResources/files/licenses/roboto-LICENSE.txt").readText()
        assertTrue("Apache License" in licence && "Version 2.0, January 2004" in licence)
        // Android's own fonts' licence ships with them, as Android ships it (assets/licenses).
        assertTrue(File("build/androidResources/files/licenses/fonts-OFL.txt").isFile)
    }

    @Test
    fun theLineMansWordsMeasureAsAndroidPaintsThemInRobotoBold() {
        // Android paints the captions, the phase label and the range line's words with Typeface.DEFAULT_BOLD and
        // measures them with Paint.measureText: the glyphs' advances plus the letter spacing on each. The iPhone's
        // FigureText in Roboto Bold measures the same, so a caption fits, or is left out, where Android's does.
        val density = 3f
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr)
        val roboto = FigureText(measurer, FontFamily(Font(File(FONTS, "roboto_bold.ttf"), FontWeight.Bold)), Density(density))
        val font = Ttf(File(FONTS, "roboto_bold.ttf").readBytes())
        val caption = TextPen(9f * density, 0.12f, androidx.compose.ui.graphics.Color.Black, Align.CENTER)
        val phase = TextPen(11f * density, 0.08f, androidx.compose.ui.graphics.Color.Black)
        for ((word, pen) in listOf("SIDE" to caption, "FRONT" to caption, "END" to caption, "HOLD · BREATHE" to phase)) {
            val android = font.width(word, pen.sizePx) + pen.letterSpacing * pen.sizePx * word.length
            // Compose rounds a layout up to whole pixels.
            assertTrue(abs(roboto.measureText(word, pen) - android) <= 1.5f, "$word: ${roboto.measureText(word, pen)} vs Android's $android")
        }
        // Albert Sans Bold, which the captions were drawn in before, is wider: why they went missing at some widths.
        val albert = FigureText(measurer, FontFamily(Font(ALBERT_BOLD, FontWeight.Bold)), Density(density))
        assertTrue(albert.measureText("FRONT", caption) > roboto.measureText("FRONT", caption) + 2f)
    }

    @Test
    fun aClassPagesDialogNoteAndBackArrowAreInRobotoItsPageInTheThemesFace() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }
        // The title bar's back arrow: Android's title bar is never restyled.
        assertEquals("roboto_regular" to null, onNodeWithText("‹", useUnmergedTree = true).face())
        // The safety line is the page's (Albert Sans), its link Android's StyleSpan(BOLD) over it: a synthesized bold.
        val safety = onNodeWithText("Safety notes ›", substring = true, useUnmergedTree = true)
        assertEquals("albert_sans_regular" to null, safety.face())
        val link = safety.layout().layoutInput.text.spanStyles.single { it.item.fontWeight != null }.item
        assertEquals(FontWeight.Bold, link.fontWeight)
        assertEquals(null, link.fontFamily)
        safety.performClick()
        // The dialog's own words: Roboto Regular (title, message, buttons), as Theme.Material with fontFamily "sans".
        assertEquals("roboto_regular" to null, onNodeWithText("Safety notes", useUnmergedTree = true).face())
        assertEquals("roboto_regular" to null, onNodeWithText("OK", useUnmergedTree = true).face())
    }

    @Test
    fun settingsDialogsSheetsAndNotesAreInRoboto() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        // A page's running text keeps the theme's body face.
        assertEquals("albert_sans_regular" to null, onNodeWithText(text(Res.string.reset_body), useUnmergedTree = true).face())
        onNodeWithText("RESET PROGRESS").performScrollTo().performClick()
        assertEquals("roboto_regular" to null, onNodeWithText("Reset all progress?", useUnmergedTree = true).face())
        assertEquals("roboto_regular" to null, onNodeWithText(text(Res.string.reset_confirm_message), useUnmergedTree = true).face())
        assertEquals("roboto_regular" to null, onNodeWithText("CANCEL", useUnmergedTree = true).face())
        onNodeWithText("RESET").performClick()
        // Android's Toast: the system face.
        assertEquals("roboto_regular" to null, onNodeWithText("Progress reset. Choose where to start.", useUnmergedTree = true).face())
    }

    @Test
    fun theSkillStepsSheetsStepIsRobotoBold() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings, showing = Shown.Switches("planche")) }
        // Android's "Step 1": Typeface.DEFAULT_BOLD in a dialog that is never restyled.
        val (file, weight) = onAllNodesWithText("Step 1", useUnmergedTree = true).onFirst().face()
        assertEquals("roboto_bold", file)
        assertEquals(null, weight)
        assertEquals("roboto_regular" to null, onNodeWithText("DONE", useUnmergedTree = true).face())
    }

    // --- The alarm's time picker --------------------------------------------------------------------------------

    @Test
    fun theAlarmsTimeIsPickedOnAClockFaceOrTypedAsOnAndroid() = runComposeUiTest {
        val model = model(hour24 = false)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Timer) }
        onAllNodesWithContentDescription("Alarm").onFirst().performClick()
        onNodeWithContentDescription("Change alarm time").performClick()
        // Android's TimePickerDialog in Theme.Material: a clock face, CANCEL and OK, no wheels.
        onNodeWithText("Hours").assertDoesNotExist()
        onNodeWithText("CANCEL").assertExists()
        // The hour picked on the face: a tap on its 9 (each number is spoken with its hour, "9 o'clock").
        onAllNodes(hasContentDescription("9", substring = true)).onFirst().performClick()
        waitForIdle()
        onNodeWithText("OK").performClick()
        assertEquals(9 to 0, model.timing.state.alarmHour to model.timing.state.alarmMinute)

        // The keyboard at the bottom left: "Type in time", the hour and the minute typed.
        onNodeWithContentDescription("Change alarm time").performClick()
        // The toggle says what it does in Android's words: to text input from the clock face, and back.
        onNodeWithContentDescription("Switch to text input mode for the time input.").performClick()
        onNodeWithText("Type in time").assertExists()
        onNodeWithContentDescription("Switch to clock mode for the time input.").assertExists()
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("6")
        fields[1].performTextReplacement("45")
        onNodeWithText("OK").performClick()
        assertEquals(6 to 45, model.timing.state.alarmHour to model.timing.state.alarmMinute)

        // The Timer's duration keeps Android's NumberPickers, their numbers in Roboto.
        onAllNodesWithContentDescription("Timer").onFirst().performClick()
        onNodeWithContentDescription("Change timer duration").performClick()
        assertEquals("roboto_regular" to null, onAllNodesWithText("01", useUnmergedTree = true).onFirst().face())
    }

    // --- The version --------------------------------------------------------------------------------------------

    @Test
    fun theVersionIsTheAndroidAppsVersionName() {
        val android = Regex("""\bversionName\s*=\s*"([^"]+)"""").find(ANDROID_BUILD.readText())!!.groupValues[1]
        assertEquals(android, text(Res.string.app_version_name))
        assertEquals("-debug", text(Res.string.app_version_debug_suffix))
        assertEquals("$android-debug", runBlocking { buildVersion(debug = true) })
        assertEquals(android, runBlocking { buildVersion(debug = false) })
        // The iPhone app's own version, CFBundleShortVersionString, is the same number.
        val marketing = Regex("""MARKETING_VERSION:\s*"([^"]+)"""").find(File("../iosApp/project.yml").readText())!!.groupValues[1]
        assertEquals(android, marketing)
    }

    @Test
    fun aboutShowsTheAndroidVersionAndADebugBuildItsSuffix() = runComposeUiTest {
        val android = text(Res.string.app_version_name)
        setContent { App(MemoryStore(), load = { program }, simulate = true) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Settings").onFirst().performClick()
        onNodeWithText("Version $android-debug").performScrollTo().assertExists()
    }

    // --- Reduce Motion ------------------------------------------------------------------------------------------

    @Test
    fun reduceMotionSwitchedOnWhileTheAppRunsStillsTheFiguresAtOnce() = runComposeUiTest {
        val store = MemoryStore().also { AppSettings(it).placementDone = true }
        var reduce by mutableStateOf(false)
        setContent { App(store, reduceMotion = reduce, load = { program }) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text(Res.string.brand_kicker)).fetchSemanticsNodes().isNotEmpty() }
        fun frame() = onRoot().captureToImage().toPixelMap().buffer.copyOf()
        val moving = frame()
        // Reduce Motion switched on while the app runs, as the phone's notification brings it: the park holds its final
        // skill at once (no restart), and stays still as time goes on.
        reduce = true
        val held = frame()
        assertFalse(moving.contentEquals(held), "the park should hold its final skill")
        mainClock.advanceTimeBy(1_000)
        assertContentEquals(held, frame())
        // Switched off again: it plays once more.
        reduce = false
        assertFalse(held.contentEquals(frame()))
    }

    // --- Sounds -------------------------------------------------------------------------------------------------

    @Test
    fun theAlarmStreamsBeepPlaysAtAndroidsVolume() {
        // ToneGenerator(STREAM_ALARM, 80): AudioSystem.linearToLog, half a decibel a step below 100.
        assertEquals(0.3162f, Beep.gain(Beep.ALARM_VOLUME), 0.0005f)
        assertEquals(1f, Beep.gain(100), 0.0001f)
        assertEquals(0.2371f, Beep.gain(75), 0.0005f)
        assertEquals(0f, Beep.gain(0))
    }

    // --- Landscape ----------------------------------------------------------------------------------------------

    @Test
    fun theFocusViewSitsSideBySideInLandscape() = runSkikoComposeUiTest(size = Size(956f, 440f), density = Density(1f)) {
        val model = model()
        val first = model.today().next!!
        model.start(first.id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }
        val figure = onNodeWithContentDescription("Line-figure movement guide", substring = true).fetchSemanticsNode().boundsInRoot
        val details = onNodeWithText("Details").fetchSemanticsNode().boundsInRoot
        val position = onNodeWithText("Set 1 of ${com.justdataplease.streetgym.app.data.Training.flow(first).size}").fetchSemanticsNode().boundsInRoot
        // Android: the strip and the fill in five twelfths of the width less 16 dp, the info and controls in the rest.
        val split = 16f + 5f / 12f * (956f - 32f - 16f)
        assertTrue(figure.right <= split + 1f && position.right <= split + 1f, "figure $figure, strip $position")
        assertTrue(details.left >= split + 16f - 1f, "details $details")
        // The figure has the height the strip leaves, beside the set's name rather than above it.
        assertTrue(figure.top < details.bottom)
    }

    @Test
    fun theFocusViewStacksInPortrait() = runSkikoComposeUiTest(size = Size(440f, 956f), density = Density(1f)) {
        val model = model()
        val first = model.today().next!!
        model.start(first.id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }
        val figure = onNodeWithContentDescription("Line-figure movement guide", substring = true).fetchSemanticsNode().boundsInRoot
        val details = onNodeWithText("Details").fetchSemanticsNode().boundsInRoot
        assertTrue(figure.bottom <= details.top, "figure $figure over details $details")
    }

    @Test
    fun todayKeepsToTheDaysClassInLandscape() = runSkikoComposeUiTest(size = Size(956f, 440f), density = Density(1f)) {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues()) }
        // Android's screenHeightDp < 480: no brand card, no streak line, the class and its Start workout.
        onNodeWithText(text(Res.string.brand_kicker)).assertDoesNotExist()
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
    }

    @Test
    fun todayShowsTheBrandCardInPortrait() = runSkikoComposeUiTest(size = Size(440f, 956f), density = Density(1f)) {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues()) }
        onNodeWithText(text(Res.string.brand_kicker)).assertExists()
    }

    @Test
    fun aDialogTakesAndroidsShareOfTheWidthInLandscape() = runSkikoComposeUiTest(size = Size(956f, 440f), density = Density(1f)) {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings, showing = Shown.Reset) }
        // Theme.Material.Dialog's windowMinWidthMajor, 65%: the message runs the card's width less its padding.
        val message = onNodeWithText(text(Res.string.reset_confirm_message)).fetchSemanticsNode().boundsInRoot
        assertEquals(0.65f * 956f - 16f - 48f, message.width, 1.5f)
    }

    @Test
    fun aDialogTakesAndroidsShareOfTheWidthInPortrait() = runSkikoComposeUiTest(size = Size(440f, 956f), density = Density(1f)) {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings, showing = Shown.Reset) }
        val message = onNodeWithText(text(Res.string.reset_confirm_message)).fetchSemanticsNode().boundsInRoot
        assertEquals(0.95f * 440f - 16f - 48f, message.width, 1.5f)
    }

    /** The text's layout, as the node drew it. */
    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    /** The font file the node's text is drawn in (its name without the extension) and the weight it asks for. */
    private fun SemanticsNodeInteraction.face(): Pair<String, FontWeight?> {
        val style = layout().layoutInput.style
        val fonts = (style.fontFamily as FontListFontFamily).fonts
        val font = fonts.firstOrNull { it.weight == (style.fontWeight ?: FontWeight.Normal) } ?: fonts.first()
        // The identity is the resource's path, then the weight and the font's settings.
        val file = (font as PlatformFont).identity.substringAfterLast('/').substringBefore(".ttf")
        return file to style.fontWeight
    }

    /** What the test reads of a TrueType file: its names, and each character's advance (cmap format 4, hmtx). */
    private class Ttf(private val data: ByteArray) {
        private fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
        private fun s16(at: Int) = u16(at).toShort().toInt()
        private fun u32(at: Int) = (u16(at).toLong() shl 16 or u16(at + 2).toLong()).toInt()
        private val tables: Map<String, Int> = (0 until u16(4)).associate { index ->
            val record = 12 + 16 * index
            String(data, record, 4, Charsets.US_ASCII) to u32(record + 8)
        }

        fun name(id: Int): String {
            val table = tables.getValue("name")
            val strings = table + u16(table + 4)
            for (index in 0 until u16(table + 2)) {
                val record = table + 6 + 12 * index
                if (u16(record) == 3 && u16(record + 6) == id) {
                    return String(data, strings + u16(record + 10), u16(record + 8), Charsets.UTF_16BE)
                }
            }
            error("no name $id")
        }

        private val unitsPerEm = u16(tables.getValue("head") + 18)
        private val longMetrics = u16(tables.getValue("hhea") + 34)

        private fun glyph(char: Char): Int {
            val cmap = tables.getValue("cmap")
            val sub = (0 until u16(cmap + 2)).map { cmap + 4 + 8 * it }
                .first { u16(it) == 3 && u16(it + 2) == 1 }.let { cmap + u32(it + 4) }
            val segments = u16(sub + 6) / 2
            val ends = sub + 14
            val starts = ends + 2 * segments + 2
            val deltas = starts + 2 * segments
            val offsets = deltas + 2 * segments
            val code = char.code
            for (segment in 0 until segments) {
                if (u16(ends + 2 * segment) < code) continue
                val start = u16(starts + 2 * segment)
                if (start > code) return 0
                val offset = u16(offsets + 2 * segment)
                if (offset == 0) return (code + s16(deltas + 2 * segment)) and 0xFFFF
                val glyph = u16(offsets + 2 * segment + offset + 2 * (code - start))
                return if (glyph == 0) 0 else (glyph + s16(deltas + 2 * segment)) and 0xFFFF
            }
            return 0
        }

        /** [text]'s advance at [px] pixels, without kerning. */
        fun width(text: String, px: Float): Float {
            val hmtx = tables.getValue("hmtx")
            return text.sumOf { char -> u16(hmtx + 4 * minOf(glyph(char), longMetrics - 1)) } * px / unitsPerEm
        }
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val FONTS = File("src/commonMain/composeResources/font")
        val ALBERT_BOLD = File("build/androidResources/font/albert_sans_bold.ttf")
        /** The Android app's build file, beside this project in the suite or in the standalone repository. */
        val ANDROID_BUILD = listOf("../../android-streetgym/app/build.gradle.kts", "../../app/build.gradle.kts").map(::File).first { it.isFile }
    }
}
