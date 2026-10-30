package com.justdataplease.streetgym.leaderboard

import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillCredit
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillProgress
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BoardEntry(val username: String, val country: String, val score: Int)

/**
 * A precomputed top list: one Firestore document, so viewing a board costs a single read. [percentiles] holds the
 * score at each 1% step from the top (100 values), so anyone outside the list sees where they stand without
 * another read.
 */
data class Board(
    val id: String,
    val entries: List<BoardEntry>,
    val players: Int,
    val updatedAt: Long,
    val percentiles: List<Int> = emptyList(),
)

/** Where an athlete stands: their rank inside the top list, else the top share of players they're in. */
sealed interface Standing {
    data class Rank(val rank: Int) : Standing
    data class TopPercent(val percent: Int) : Standing
}

/**
 * A Plus or Pro athlete's public score ([com.justdataplease.streetgym.data.skills.OverallScore]): the overall score out of 1000,
 * each skill family's points (ten times its raw value, so the server can check the overall), and the skills the
 * athlete holds a level in.
 */
data class Score(val overall: Int, val families: Map<String, Int>, val verified: List<String> = emptyList()) {
    companion object {
        /**
         * The score document's version; the server drops any other. Version 3 (round 11): the one-arm handstand tops the
         * handstand family, the shrimp squat and the Hefesto families join, and the retired Nordic curl family is gone.
         */
        const val VERSION = 3
    }
}

object Boards {
    const val WORLD = "world"

    /** "overall_world", "overall_GR", "family_<family id>_world", "family_<family id>_GR". */
    fun id(familyId: String?, country: String?): String =
        (if (familyId == null) "overall" else "family_$familyId") + "_" + (country ?: WORLD)

    /** How long a board stays on the phone before it's read again; the server rebuilds boards every 6 hours. */
    const val CACHE_MILLIS = 6 * 60 * 60_000L

    fun fresh(fetchedAt: Long, now: Long): Boolean = now - fetchedAt in 0 until CACHE_MILLIS

    /** The athlete's place: exact inside the top list (by username), else from the board's percentiles. */
    fun standing(board: Board, username: String?, score: Int): Standing? {
        if (score <= 0 || board.players == 0) return null
        board.entries.indexOfFirst { username != null && it.username == username }.takeIf { it >= 0 }?.let { return Standing.Rank(it + 1) }
        if (board.percentiles.isEmpty()) return null
        val step = board.percentiles.indexOfFirst { score >= it }
        return Standing.TopPercent(if (step < 0) 100 else step + 1)
    }

    fun encode(board: Board): JSONObject = JSONObject().put("id", board.id).put("players", board.players)
        .put("updated_at", board.updatedAt).put("percentiles", JSONArray(board.percentiles))
        .put("entries", JSONArray().apply {
            board.entries.forEach { put(JSONObject().put("username", it.username).put("country", it.country).put("score", it.score)) }
        })

    fun decode(json: JSONObject): Board? = runCatching {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        val percentiles = json.optJSONArray("percentiles") ?: JSONArray()
        Board(
            id = json.getString("id"),
            players = json.optInt("players"),
            updatedAt = json.optLong("updated_at"),
            entries = (0 until entries.length()).mapNotNull { index ->
                entries.optJSONObject(index)?.let { BoardEntry(it.optString("username"), it.optString("country"), it.optInt("score")) }
            },
            percentiles = (0 until percentiles.length()).map(percentiles::optInt),
        )
    }.getOrNull()
}

object Scores {
    /**
     * The classes that count, for Skills and the leaderboard alike: every class trained with its sets ticked and their
     * reps or seconds, in the program, repeated, a ready workout or the athlete's own ([customMinutes] gives such a day's
     * planned length, when it still exists). "Complete day", "Complete level" and the days the placement test placed past earn nothing,
     * so both reflect training, not taps.
     */
    fun credits(sessions: List<WorkoutSession>, customMinutes: (String) -> Int? = { null }): List<SkillCredit> = sessions
        .filter { !it.manuallyCompleted }
        .sortedBy { it.completedAtEpochMillis }
        .map { session ->
            SkillCredit(session.programId, session.credits, session.durationSeconds,
                if (CustomDays.offProgram(session.programId)) customMinutes(session.programId) else null, session.doses)
        }

    /** Program days marked complete without training, by a tap or the placement test: done, but worth no points. */
    fun markedComplete(sessions: List<WorkoutSession>): Int = sessions
        .filter { it.manuallyCompleted && !CustomDays.offProgram(it.programId) }.map { it.programId }.toSet().size

    fun from(progress: SkillProgress): Score = Score(
        overall = progress.overall.score,
        families = progress.overall.families.filter { it.points > 0 }
            .associate { it.family.spec.id to Math.round(it.points * 10).toInt() },
        verified = progress.statuses.filter { it.level > SkillLevel.NONE }.map { it.skill.id }.sorted(),
    )

    /** The score goes up after a change, at most every [PUBLISH_MILLIS], so a burst of workouts costs one write. */
    const val PUBLISH_MILLIS = 10 * 60_000L

    fun shouldPublish(published: Score?, publishedAt: Long, current: Score, now: Long): Boolean =
        current != published && (published == null || now - publishedAt >= PUBLISH_MILLIS)
}

object Usernames {
    private val pattern = Regex("^[A-Za-z0-9_.]{3,20}$")
    fun valid(name: String): Boolean = pattern.matches(name)
    /** Usernames are unique ignoring case. */
    fun key(name: String): String = name.lowercase(Locale.ROOT)
}

object Countries {
    /** ISO 3166 codes with their English names, sorted by name. */
    fun all(): List<Pair<String, String>> = Locale.getISOCountries()
        .map { it to Locale("", it).getDisplayCountry(Locale.ENGLISH) }.sortedBy { it.second }

    fun valid(code: String): Boolean = code in Locale.getISOCountries()

    fun name(code: String): String = Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }

    /** The flag emoji of a two-letter code. */
    fun flag(code: String): String = if (code.length != 2) "" else
        code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
