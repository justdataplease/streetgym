package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Board
import com.justdataplease.streetgym.app.data.BoardEntry
import com.justdataplease.streetgym.app.data.Boards
import com.justdataplease.streetgym.app.data.CloudTime
import com.justdataplease.streetgym.app.data.Countries
import com.justdataplease.streetgym.app.data.LeaderboardRepository
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.NameReport
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Profile
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Score
import com.justdataplease.streetgym.app.data.Scores
import com.justdataplease.streetgym.app.data.Standing
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.PROGRESS_LEADERBOARD
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.points.Points
import com.justdataplease.streetgym.data.progress.WorkoutSession
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The leaderboard (Android: leaderboard/Leaderboard.kt, LeaderboardRepository, renderLeaderboard, publishScore): the
 * boards' ids, a board as the function writes it, the athlete's place, the cache's six hours, the score's document as
 * Android writes it and when it goes up, and on iPhone reporting and blocking a name (Apple 1.2).
 */
@OptIn(ExperimentalTestApi::class)
class LeaderboardTest {
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private val entries = listOf(BoardEntry("alpha", "GR", 4_200), BoardEntry("Bravo", "CY", 3_100), BoardEntry("charlie", "GR", 2_050),
        BoardEntry("delta", "US", 1_200))
    private val percentiles = List(100) { 4_200 - it * 40 }

    private fun boardDocument(players: Int = 812) = mapOf(
        "entries" to entries.map { mapOf("username" to it.username, "country" to it.country, "score" to it.score) },
        "players" to players, "updated_at" to CloudTime(time - 3_600_000L), "percentiles" to percentiles,
    )

    private inner class Phone(tier: Tier = Tier.PLUS, val store: MemoryStore = MemoryStore(), share: Boolean = true) {
        val account = FakeAccount()
        val cloud = FakeCloud({ account.user.value }, { time })
        val model = AppSettings(store).let { settings ->
            settings.placementDone = true
            settings.shareProgress = share
            AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = settings, library = LibraryStore(store),
                plans = Plans(store, floor = tier), adStore = store, services = PhoneServices(account = account, documents = cloud, region = "GR"))
        }

        fun pick(name: String, country: String = "GR"): String {
            var uid = ""
            model.cloud.pickUsername { uid = it }
            model.cloud.saveProfile(uid, Profile(name, country))
            return uid
        }

        /** [count] own workouts finished, each worth [points], over the last days. */
        fun trained(count: Int, points: Int) {
            val sessions = (1..count).map { index ->
                val at = time - index * DAY
                WorkoutSession("custom:t$index", at - 1_800_000L, at, 1_800L, effortRating = 6, points = points)
            }
            store.set("streetgym_progress.session_history", StoredProgress.encodeSessions(sessions))
        }
    }

    @Test
    fun boardsAreNamedAsTheServerBuildsThem() {
        assertEquals("all_world", Boards.id(month = false, country = null))
        assertEquals("all_GR", Boards.id(month = false, country = "GR"))
        assertEquals("month_world", Boards.id(month = true, country = null))
        assertEquals("month_CY", Boards.id(month = true, country = "CY"))
    }

    @Test
    fun aBoardReadsAsTheFunctionWritesItAndStaysSixHours() {
        val docs = FakeCloud({ null }, { time })
        docs.documents["boards/all_world"] = boardDocument()
        val store = MemoryStore()
        val repository = LeaderboardRepository(docs, store) { time }
        var board: Board? = null
        repository.board("all_world") { board = it }
        assertEquals(Board("all_world", entries, 812, time - 3_600_000L, percentiles), board)
        // Kept under Android's name with its read time.
        assertTrue(store.get("streetgym_boards.board_all_world")!!.contains("\"fetched_at\":$time"))
        // Within six hours the cached copy answers; offline, the cached copy however old; a missing board is empty.
        docs.documents["boards/all_world"] = boardDocument(players = 900)
        repository.board("all_world") { board = it }
        assertEquals(812, board!!.players)
        time += Boards.CACHE_MILLIS
        docs.online = false
        repository.board("all_world") { board = it }
        assertEquals(812, board!!.players)
        docs.online = true
        repository.board("all_world") { board = it }
        assertEquals(900, board!!.players)
        repository.board("month_GR") { board = it }
        assertEquals(Board("month_GR", emptyList(), 0, 0), board)
    }

    @Test
    fun theAthletesPlaceIsTheirRankOrTheirTopShare() {
        val board = Board("all_world", entries, 812, time, percentiles)
        assertEquals(Standing.Rank(3), Boards.standing(board, "charlie", 2_050))
        assertEquals(Standing.TopPercent(1), Boards.standing(board, "echo", 4_200))
        assertEquals(Standing.TopPercent(31), Boards.standing(board, "echo", 3_000))
        assertEquals(Standing.TopPercent(100), Boards.standing(board, "echo", 5))
        assertNull(Boards.standing(board, "echo", 0))
        assertNull(Boards.standing(board.copy(players = 0), "alpha", 4_200))
        assertNull(Boards.standing(board.copy(percentiles = emptyList()), "echo", 3_000))
    }

    @Test
    fun theScoreGoesUpAsAndroidWritesItAtMostEveryTenMinutes() {
        val phone = Phone()
        phone.trained(3, 150)
        val uid = phone.pick("Scorer")
        val score = phone.cloud.documents["scores/$uid"]!!
        val expected = Scores.from(phone.model.pointsTotal()!!)
        assertEquals(Score(phone.model.points(), Points.month(time), expected.monthPoints), expected)
        assertEquals(mapOf("v" to 4, "username" to "Scorer", "country" to "GR", "points" to expected.points, "month" to "2026-11",
            "month_points" to expected.monthPoints, "updated_at" to CloudTime(time)), score)
        assertEquals(time, phone.model.cloud.store.publishedScore(uid)!!.second)
        // More points within ten minutes wait; after them they go up.
        phone.trained(4, 150)
        time += 5 * 60_000L
        phone.model.cloud.publishScore()
        assertEquals(expected.points, phone.cloud.documents["scores/$uid"]!!["points"])
        time += 5 * 60_000L
        phone.model.cloud.publishScore()
        assertEquals(phone.model.points(), phone.cloud.documents["scores/$uid"]!!["points"])
        assertTrue(Scores.shouldPublish(null, 0, expected, time))
        assertFalse(Scores.shouldPublish(expected, time, expected, time + Scores.PUBLISH_MILLIS))

        // Hidden: the score document goes. Shown again: it comes back at once.
        phone.model.cloud.setVisible(false)
        assertNull(phone.cloud.documents["scores/$uid"])
        phone.model.cloud.setVisible(true)
        assertTrue("scores/$uid" in phone.cloud.documents)

        // Start over: the score goes up again from zero, without the ten minutes.
        phone.model.reset()
        time += 4_000L
        phone.model.cloud.tick()
        phone.model.cloud.publishScore()
        assertEquals(0, phone.cloud.documents["scores/$uid"]!!["points"])
    }

    @Test
    fun freeNeverPublishesAndAPlanThatEndsKeepsTheProfile() {
        val phone = Phone(Tier.FREE)
        phone.trained(2, 100)
        val uid = phone.pick("FreeOne")
        assertNull(phone.cloud.documents["scores/$uid"])
        assertEquals("FreeOne", phone.model.cloud.profile()!!.username)
    }

    @Test
    fun reportingWritesToTheContactAddressAndBlockingHidesTheName() {
        val link = NameReport.mailto("contact@example.com", "Street Gym: report a username", "Username: Bad_Name\nBoard: all_world")
        assertEquals("mailto:contact@example.com?subject=Street%20Gym%3A%20report%20a%20username" +
            "&body=Username%3A%20Bad_Name%0ABoard%3A%20all_world", link)
        val phone = Phone()
        phone.model.cloud.block("Bravo")
        assertEquals("[\"bravo\"]", phone.store.get("streetgym_boards.blocked"))
        assertTrue(phone.model.cloud.blocked.blocked("BRAVO"))
        phone.model.cloud.unblockAll()
        assertNull(phone.store.get("streetgym_boards.blocked"))
    }

    @Test
    fun theBoardShowsRanksYourRowAndReportsOrBlocksOthers() = runComposeUiTest {
        val phone = Phone()
        phone.cloud.documents["boards/all_world"] = boardDocument()
        phone.trained(3, 150)
        phone.pick("charlie")
        val opened = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                StreetGymApp(phone.model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
            }
        }
        waitForIdle()
        // Rows in the board's order with their ranks; the athlete's own line from the board itself.
        onNodeWithContentDescription("#1, alpha, 4,200").assertExists()
        onNodeWithContentDescription("#2, Bravo, 3,100").assertExists()
        onNodeWithContentDescription("#3, charlie, 2,050").assertExists()
        onNodeWithText("You’re #3 · ${com.justdataplease.streetgym.app.ui.pointsText(phone.model.points())} points", ignoreCase = true).assertExists()
        onNodeWithText("812 players", substring = true).performScrollTo().assertExists()
        // The world and the athlete's country.
        onNodeWithText("${Countries.flag("GR")} Greece").assertExists()

        // Another name: report it by mail, or block it.
        onNodeWithContentDescription("#2, Bravo, 3,100").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Report this name if it’s offensive, or block it to hide it on this phone.").assertExists()
        onNodeWithText("REPORT").performClick()
        waitForIdle()
        // The address is a build value (empty in a build without it): the link is a mail link either way.
        assertTrue(opened.single().startsWith("mailto:") && opened.single().contains("?subject="))
        assertTrue(opened.single().contains("Bravo") && opened.single().contains("all_world"))
        onNodeWithContentDescription("#2, Bravo, 3,100").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("BLOCK").performClick()
        waitForIdle()
        assertTrue(onAllNodesWithContentDescription("#2, Bravo, 3,100").fetchSemanticsNodes().isEmpty())
        onNodeWithContentDescription("#3, charlie, 2,050").assertExists()
        onNodeWithText("Names you blocked are hidden on this phone.").performScrollTo().assertExists()
        onNodeWithText("Show them again").performClick()
        waitForIdle()
        onNodeWithContentDescription("#2, Bravo, 3,100").assertExists()
    }

    @Test
    fun theTopListLeavesOutNamesTheFilterRefusesAndKeepsEveryRank() {
        // Android's LeaderboardTest has the same board and the same answers.
        val board = Board("all_world", listOf(BoardEntry("ana", "GR", 900), BoardEntry("Sh1t_Head", "DE", 850),
            BoardEntry("ben", "DE", 800), BoardEntry("admin", "US", 700), BoardEntry("cara", "IE", 600)),
            players = 200, updatedAt = 1, percentiles = (0 until 100).map { 1000 - it * 10 })
        assertEquals(listOf(1 to "ana", 3 to "ben", 5 to "cara"), Boards.rows(board, "ben").map { it.index + 1 to it.value.username })
        assertEquals(listOf(1 to "ana", 3 to "ben", 5 to "cara"), Boards.rows(board, null).map { it.index + 1 to it.value.username })
        // The athlete's own row shows to them whatever its name, and their own line is the board's rank as before.
        assertEquals(listOf(1, 2, 3, 5), Boards.rows(board, "Sh1t_Head").map { it.index + 1 })
        assertEquals(Standing.Rank(2), Boards.standing(board, "Sh1t_Head", 850))
        assertEquals(Standing.Rank(5), Boards.standing(board, "cara", 600))
    }

    @Test
    fun theBoardLeavesOutNamesTheFilterRefuses() = runComposeUiTest {
        val phone = Phone()
        phone.cloud.documents["boards/all_world"] = boardDocument() + ("entries" to listOf(
            mapOf("username" to "alpha", "country" to "GR", "score" to 4_200),
            mapOf("username" to "Malakas_GR", "country" to "GR", "score" to 3_100),
            mapOf("username" to "charlie", "country" to "GR", "score" to 2_050),
            mapOf("username" to "admin", "country" to "US", "score" to 1_200),
        ))
        phone.trained(3, 150)
        phone.pick("charlie")
        setContent { StreetGymApp(phone.model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
        waitForIdle()
        onNodeWithContentDescription("#1, alpha, 4,200").assertExists()
        assertTrue(onAllNodesWithContentDescription("#2, Malakas_GR, 3,100").fetchSemanticsNodes().isEmpty())
        onNodeWithContentDescription("#3, charlie, 2,050").assertExists()
        assertTrue(onAllNodesWithContentDescription("#4, admin, 1,200").fetchSemanticsNodes().isEmpty())
        onNodeWithText("You’re #3 · ${com.justdataplease.streetgym.app.ui.pointsText(phone.model.points())} points", ignoreCase = true).assertExists()
    }

    @Test
    fun withoutAUsernameTheLineSaysWhatToDo() = runComposeUiTest {
        // Sharing progress signs in anonymously as the app opens (Android: analytics' writer), so a user and no name.
        val sharing = Phone()
        sharing.cloud.documents["boards/all_world"] = boardDocument()
        setContent { StreetGymApp(sharing.model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
        waitForIdle()
        onNodeWithText("Choose a username and a country to appear on the leaderboard.").assertExists()
    }

    @Test
    fun withoutAnyUserTheLineSaysToPickAName() = runComposeUiTest {
        val quiet = Phone(share = false)
        quiet.cloud.documents["boards/all_world"] = boardDocument()
        setContent { StreetGymApp(quiet.model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
        waitForIdle()
        assertNull(quiet.account.user.value)
        onNodeWithText("Pick a username in Settings to appear on the leaderboard.").assertExists()
    }

    @Test
    fun aBuildWithoutFirebaseSaysTheLeaderboardIsntInIt() = runComposeUiTest {
        val store = MemoryStore()
        AppSettings(store).placementDone = true
        val model = AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            plans = Plans(store, floor = Tier.PRO), adStore = store)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
        onNodeWithText("The leaderboard isn’t available in this build.").assertExists()
    }

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
