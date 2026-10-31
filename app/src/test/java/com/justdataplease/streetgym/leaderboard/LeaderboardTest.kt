package com.justdataplease.streetgym.leaderboard

import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.ProgressMerge
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillCredit
import org.junit.Assert.*
import org.junit.Test

class LeaderboardTest {
    private fun session(id: String, at: Long = 1, manual: Boolean = false, repeat: Boolean = false) =
        WorkoutSession(id, at, at + 60_000, 60, manuallyCompleted = manual, repeat = repeat)

    @Test fun everyTrainedClassEarnsPointsAndCompleteDayAndPlacedDaysNothing() {
        val sessions = listOf(
            session("sg:l01:p0003", at = 9).copy(credits = mapOf("pullup" to 2), intensity = Intensity.HARDER,
                doses = mapOf("pullup" to listOf(8, 10))),
            session("sg:l01:p0001"),
            session("sg:l01:p0002", manual = true),
            session("sg:l01:p0004", at = 0, manual = true).copy(placed = true),
            session("sg:l01:p0001", at = 5, repeat = true),
            session("custom:abc", at = 7).copy(credits = mapOf("pushup" to 3)),
        )
        // In the order they were trained, with their ticks and each set's reps. Complete day and the days placement
        // skipped are left out.
        assertEquals(listOf(
            SkillCredit("sg:l01:p0001", null),
            SkillCredit("sg:l01:p0001", null),
            SkillCredit("custom:abc", mapOf("pushup" to 3)),
            SkillCredit("sg:l01:p0003", mapOf("pullup" to 2), doses = mapOf("pullup" to listOf(8, 10))),
        ), Scores.credits(sessions))
        assertEquals(2, Scores.markedComplete(sessions))
    }

    @Test fun aScoreIsPublishedWhenItChangesAndAtMostEveryTenMinutes() {
        val a = Score(1_000, "2026-10", 400)
        val b = Score(1_200, "2026-10", 600)
        assertTrue(Scores.shouldPublish(null, 0, a, 1_000))
        assertFalse(Scores.shouldPublish(a, 1_000, a, 99_000_000))
        assertFalse(Scores.shouldPublish(a, 1_000, b, 1_000 + Scores.PUBLISH_MILLIS - 1))
        assertTrue(Scores.shouldPublish(a, 1_000, b, 1_000 + Scores.PUBLISH_MILLIS))
    }

    @Test fun boardIdsCoverAllTimeAndThisMonthWorldwideAndByCountry() {
        assertEquals("all_world", Boards.id(month = false, country = null))
        assertEquals("all_GR", Boards.id(month = false, country = "GR"))
        assertEquals("month_world", Boards.id(month = true, country = null))
        assertEquals("month_DE", Boards.id(month = true, country = "DE"))
    }

    @Test fun theAppAndTheServerAgreeOnTheScoreDocument() {
        // boards.py (VERSION, the board ids) and the rules (the keys) are what the published score must match.
        val boards = java.io.File("../firebase/functions/boards.py").readText()
        assertEquals(Score.VERSION, Regex("(?m)^VERSION = (\\d+)").find(boards)?.groupValues?.get(1)?.toInt())
        listOf("all_world", "month_world").forEach { assertTrue(it, boards.contains(it.substringBefore("_") + "_")) }
        val rules = java.io.File("../firebase/firestore.rules").readText()
        assertTrue(rules.contains("['v', 'username', 'country', 'points', 'month', 'month_points', 'updated_at']"))
    }

    @Test fun aCachedBoardIsFreshForSixHours() {
        assertTrue(Boards.fresh(0, Boards.CACHE_MILLIS - 1))
        assertFalse(Boards.fresh(0, Boards.CACHE_MILLIS))
        assertFalse(Boards.fresh(10_000, 5_000))   // a clock moved back reads again
    }

    private val board = Board("all_world", listOf(BoardEntry("ana", "GR", 900), BoardEntry("ben", "DE", 800)),
        players = 200, updatedAt = 1, percentiles = (0 until 100).map { 1000 - it * 10 })

    @Test fun standingIsTheRankInsideTheListElseTheTopShare() {
        assertEquals(Standing.Rank(2), Boards.standing(board, "ben", 800))
        assertEquals(Standing.TopPercent(51), Boards.standing(board, "zoe", 500))
        assertEquals(Standing.TopPercent(100), Boards.standing(board, "zoe", 3))
        assertEquals(Standing.TopPercent(1), Boards.standing(board, "zoe", 2000))
        assertNull(Boards.standing(board, "zoe", 0))
        assertNull(Boards.standing(board.copy(percentiles = emptyList()), "zoe", 5))
    }

    @Test fun theBoardCodecRoundTrips() {
        assertEquals(board, Boards.decode(Boards.encode(board)))
    }

    @Test fun usernamesAreThreeToTwentySafeCharactersAndUniqueIgnoringCase() {
        listOf("ana", "Street_Gym.7", "a".repeat(20)).forEach { assertTrue(it, Usernames.valid(it)) }
        listOf("ab", "a".repeat(21), "has space", "emoji😀", "slash/").forEach { assertFalse(it, Usernames.valid(it)) }
        assertEquals("street_gym.7", Usernames.key("Street_Gym.7"))
    }

    @Test fun countriesHaveNamesAndFlags() {
        assertTrue(Countries.valid("GR") && !Countries.valid("XX"))
        assertEquals("Greece", Countries.name("GR"))
        assertEquals("🇬🇷", Countries.flag("GR"))
        assertTrue(Countries.all().size > 200)
    }

    @Test fun cloudProgressMergesWithoutLosingAnything() {
        val phone = listOf(session("a", at = 1), session("b", at = 2))
        val cloud = listOf(session("b", at = 2), session("c", at = 3))
        assertEquals(listOf("a", "b", "c"), ProgressMerge.sessions(phone, cloud).map { it.programId })
        assertEquals(setOf("a", "b", "c"), ProgressMerge.completed(setOf("a", "b"), listOf("b", "c")))
    }
}
