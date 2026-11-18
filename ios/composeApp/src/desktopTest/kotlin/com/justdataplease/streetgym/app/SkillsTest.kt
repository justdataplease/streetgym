package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.skills_how_points
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.pointsText
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.points.Points
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillLevel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Progress › Skills (Android: SkillsScreen, SkillMedals, HomeActivity.skillProgress and pointsTotal) over the real
 * program: ranks and points from the engine for a known history, trained classes only; the medals spoken as Android
 * speaks them; a skill's sheet; the help; Plus and Pro on skills a lower tier can't train yet; switched-off skills.
 */
@OptIn(ExperimentalTestApi::class)
class SkillsTest {
    private val time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /**
     * Ten push-up sets at Rank 1's target on an own day worth 300 points, and a marked day that claims thirty pull-up sets
     * and 999 points: marks count for nothing.
     */
    private fun model(tier: Tier = Tier.PRO, store: MemoryStore = MemoryStore()): AppModel {
        val target = book.skill("pushup")!!.standard.target(SkillLevel.BEGINNER)!!
        val sessions = listOf(
            WorkoutSession("custom:test", time - 3_600_000L, time - 600_000L, 3_000L, credits = mapOf("pushup" to 10),
                doses = mapOf("pushup" to List(10) { target }), points = 300),
            WorkoutSession("marked", time, time, 0L, manuallyCompleted = true, credits = mapOf("pullup" to 30),
                doses = mapOf("pullup" to List(30) { 50 }), points = 999),
        )
        store.set("streetgym_progress.session_history", StoredProgress.encodeSessions(sessions))
        return AppModel(program, StoredProgress(store), tier = tier, now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))
    }

    @Test
    fun ranksAndPointsComeFromTrainedClassesOnly() {
        val model = model()
        val progress = model.skillProgress()!!
        val pushup = progress.statuses.first { it.skill.id == "pushup" }
        assertEquals(SkillLevel.BEGINNER, pushup.level)
        assertEquals(10, pushup.sets)
        // The marked day's pull-ups count for nothing.
        assertEquals(SkillLevel.NONE, progress.statuses.first { it.skill.id == "pullup" }.level)
        assertEquals(0, progress.statuses.first { it.skill.id == "pullup" }.sets)
        // 300 for the class, and the rank: 100 times the push-up's difficulty.
        assertEquals(300 + Points.CHEVRON * pushup.skill.difficulty, model.points())
        assertEquals(300 + Points.chevrons(progress.statuses), model.points())
    }

    @Test
    fun theMedalsSpeakTheirSkillDifficultyAndRank() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress) }

        onNodeWithText("Skills").assertIsSelected()
        val points = pointsText(model.points())
        onNodeWithContentDescription("$points points. Double-tap for how points and ranks work.").assertExists()
        onNodeWithText(points, useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Ranks: Rank 1, Rank 2, Rank 3, Rank 4, Rank 5. Double-tap for how they work.").assertExists()

        val pushup = model.skillProgress()!!.statuses.first { it.skill.id == "pushup" }
        val spoken = "${pushup.skill.name}, ${difficulty(pushup.skill.difficulty)} skill, Rank 1"
        medal(spoken).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        // Its sheet: the ladder and rung, the rank, the sets, what the next rank takes and every rank's sets.
        onNodeWithText("Push-up ladder · rung 1 of 2").assertExists()
        onNodeWithText("Sets ticked").assertExists()
        onNodeWithText("✓ Rank 1").assertExists()
        onNodeWithText("Next: Rank 2").assertExists()
        onNodeWithText("Rank 5").performScrollTo().assertExists()
        onNodeWithText("CLOSE").performClick()
        onNodeWithText("Push-up ladder · rung 1 of 2").assertDoesNotExist()
    }

    @Test
    fun thePointsCardExplainsPointsAndRanks() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress) }
        onNodeWithContentDescription("Ranks: Rank 1, Rank 2, Rank 3, Rank 4, Rank 5. Double-tap for how they work.").performClick()
        onNodeWithText("How do points and ranks work?").assertExists()
        val help = text(Res.string.skills_how_points)
        // Android's \n, \" and \' read as a new line, a quote and an apostrophe.
        assertFalse('\\' in help, help)
        assertTrue("\n\n• An exercise counts once you've spent" in help, help)
        assertTrue("Tap \"Did more or fewer?\" under the set." in help, help)
        onNodeWithText(help).assertExists()
        onNodeWithText("OK").performClick()
        onNodeWithText("How do points and ranks work?").assertDoesNotExist()
    }

    @Test
    fun aSkillALowerTierCantTrainYetWearsItsTier() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        val progress = model.skillProgress()!!
        val locked = progress.statuses.first { (it.skill.expected?.level ?: 0) >= 7 && it.level == SkillLevel.NONE }
        assertEquals(Tier.PRO, model.lockFor(locked))
        val plus = progress.statuses.first { (it.skill.expected?.level ?: 0) in 4..6 && it.level == SkillLevel.NONE }
        assertEquals(Tier.PLUS, model.lockFor(plus))
        // A skill with a rank is open, whatever level teaches it.
        assertEquals(null, model.lockFor(progress.statuses.first { it.skill.id == "pushup" }))
        assertEquals(LevelAccess.tierFor(locked.skill.expected!!.level), Tier.PRO)

        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress) }
        val spoken = "${locked.skill.name}, ${difficulty(locked.skill.difficulty)} skill, Part of Pro"
        medal(spoken).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        onAllNodesWithText("PART OF PRO").onFirst().assertExists()
    }

    @Test
    fun switchedOffSkillsAndMissingEquipmentGreyTheirMedals() = runComposeUiTest {
        val model = model()
        model.skillSwitches = model.skillSwitches.with("planche", 2)
        model.equipment = EquipmentSwitches(setOf(Equipment.PULLUP_BAR))
        val progress = model.skillProgress()!!
        val (planche, statuses) = progress.families.first { it.first.spec.id == "planche" }
        // From the third rung on: the first two stay.
        assertEquals(listOf(false, false, true, true, true), statuses.map { model.skillOff(planche, it) })
        // The dead hang needs a pull-up bar.
        val (pullup, hangs) = progress.families.first { it.first.spec.id == "pullup" }
        assertTrue(model.skillOff(pullup, hangs.first { it.skill.id == "dead_hang" }))

        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress) }
        val off = statuses[2]
        val spoken = "${off.skill.name}, ${difficulty(off.skill.difficulty)} skill, Switched off"
        medal(spoken).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        onNodeWithText("Planche ladder · rung 3 of ${statuses.size}").assertExists()
        onNodeWithText("Switched off in Settings: the classes leave it out until you switch it back on.").assertExists()
    }

    /**
     * A medal by its spoken line, brought into view: the page scrolls down to its ladder, then the ladder sideways to it
     * (performScrollTo alone scrolls only the ladder).
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.medal(spoken: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        val page = onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).onFirst()
        val target = onNodeWithContentDescription(spoken)
        val dy = target.fetchSemanticsNode().boundsInRoot.center.y - page.fetchSemanticsNode().boundsInRoot.center.y
        page.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, dy) }
        return target.performScrollTo()
    }

    private fun difficulty(grade: Int): String = listOf("Easy", "Moderate", "Hard", "Very hard", "Extreme")[grade.coerceIn(1, 5) - 1]

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val book by lazy { program.skills!! }
    }
}
