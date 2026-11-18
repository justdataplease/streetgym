package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.points.PointsTotal
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillCredit
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject

/* The leaderboard (android-streetgym, leaderboard/Leaderboard.kt and LeaderboardRepository.kt), line for line. */

data class BoardEntry(val username: String, val country: String, val score: Int)

/**
 * A precomputed top list: one Firestore document (boards/{id}), so viewing a board costs a single read. [percentiles]
 * holds the score at each 1% step from the top (100 values), so anyone outside the list sees where they stand.
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

object Boards {
    const val WORLD = "world"

    /** "all_world", "all_GR", "month_world", "month_GR": all-time points, or this month's. */
    fun id(month: Boolean, country: String?): String = (if (month) "month" else "all") + "_" + (country ?: WORLD)

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

    /** A board document's fields as the function writes them (Android: LeaderboardRepository.board). */
    fun fromDocument(id: String, fields: Map<String, Any?>?): Board {
        if (fields == null) return Board(id, emptyList(), 0, 0)
        return Board(
            id = id,
            entries = (fields["entries"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map {
                BoardEntry(it["username"] as? String ?: "", it["country"] as? String ?: "", (it["score"] as? Number)?.toInt() ?: 0)
            },
            players = fields.long("players")?.toInt() ?: 0,
            updatedAt = fields.time("updated_at") ?: 0,
            percentiles = (fields["percentiles"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
        )
    }
}

/** The classes that count for ranks and points, and the score's publishing (Android: Leaderboard.kt Scores). */
object Scores {
    /**
     * The classes that count, for ranks and points alike: every class trained with its sets ticked, in the program,
     * repeated, a ready workout or the athlete's own. Marked and placed days earn nothing.
     */
    fun credits(sessions: List<WorkoutSession>): List<SkillCredit> = sessions
        .filter { !it.manuallyCompleted }
        .sortedBy { it.completedAtEpochMillis }
        .map { session -> SkillCredit(session.programId, session.credits, session.doses) }

    /** Program days marked complete without training, by a tap or the strength test: done, but worth no points. */
    fun markedComplete(sessions: List<WorkoutSession>): Int = sessions
        .filter { it.manuallyCompleted && !CustomDays.offProgram(it.programId) }.map { it.programId }.toSet().size

    fun from(total: PointsTotal): Score = Score(total.all, total.monthKey, total.month)

    /** The score goes up after a change, at most every [PUBLISH_MILLIS], so a burst of workouts costs one write. */
    const val PUBLISH_MILLIS = 10 * 60_000L

    fun shouldPublish(published: Score?, publishedAt: Long, current: Score, now: Long): Boolean =
        current != published && (published == null || now - publishedAt >= PUBLISH_MILLIS)
}

/**
 * Boards from Firestore, kept on the phone for [Boards.CACHE_MILLIS] (Android: LeaderboardRepository, the
 * "streetgym_boards" preferences, board_<id> as {"board", "fetched_at"}): one read per board every six hours at most,
 * and a failed read falls back to the cached copy, however old. The score goes up as scores/{uid}, which the server
 * folds into the boards every six hours.
 */
class LeaderboardRepository(private val docs: CloudDocuments?, private val store: KeyValueStore, private val now: () -> Long) {
    /** The cached board and when it was read. */
    fun cached(id: String): Pair<Board, Long>? = store.get(PREFIX + "board_$id")?.let { raw ->
        runCatching { JSONObject(raw).let { Boards.decode(it.getJSONObject("board"))!! to it.getLong("fetched_at") } }.getOrNull()
    }

    /** [done] gets the board: fresh from the cache or the server, else the cached copy, else null. */
    fun board(id: String, done: (Board?) -> Unit) {
        val at = now()
        val cached = cached(id)
        if (cached != null && Boards.fresh(cached.second, at)) return done(cached.first)
        val docs = docs ?: return done(cached?.first)
        val path = "boards/$id"
        docs.read(listOf(path)) { read ->
            if (read == null) return@read done(cached?.first)
            val board = Boards.fromDocument(id, read[path])
            store.set(PREFIX + "board_$id", JSONObject().put("board", Boards.encode(board)).put("fetched_at", at).toString())
            done(board)
        }
    }

    /** One write: the athlete's public score (scores/{uid}), version 4, under their profile's name and country. */
    fun publish(uid: String, profile: Profile, score: Score, done: (Boolean) -> Unit) {
        val docs = docs ?: return done(false)
        docs.write(listOf(CloudWrite.Put("scores/$uid", scoreFields(profile, score))), done)
    }

    /** Off the leaderboard: the score document goes. */
    fun unpublish(uid: String, done: (Boolean) -> Unit) {
        val docs = docs ?: return done(false)
        docs.write(listOf(CloudWrite.Remove("scores/$uid")), done)
    }

    companion object {
        const val PREFIX = "streetgym_boards."

        fun scoreFields(profile: Profile, score: Score): Map<String, Any?> = mapOf(
            "v" to Score.VERSION, "username" to profile.username, "country" to profile.country,
            "points" to score.points, "month" to score.month, "month_points" to score.monthPoints, "updated_at" to ServerTime,
        )
    }
}

/**
 * Names the athlete blocked on the leaderboard (Apple 1.2), kept on this phone only, by their lower-case key under
 * "streetgym_boards.blocked"; the boards leave them out. An iPhone addition: Android has no block.
 */
class BlockedNames(private val store: KeyValueStore) {
    fun all(): Set<String> = runCatching {
        JSONArray(store.get(KEY) ?: return emptySet()).let { list -> (0 until list.length()).map(list::optString).filter(String::isNotBlank).toSet() }
    }.getOrDefault(emptySet())

    fun blocked(username: String): Boolean = Usernames.key(username) in all()

    fun block(username: String) = save(all() + Usernames.key(username))

    fun clear() = store.set(KEY, null)

    private fun save(names: Set<String>) = store.set(KEY, if (names.isEmpty()) null else JSONArray(names.sorted()).toString())

    companion object {
        const val KEY = LeaderboardRepository.PREFIX + "blocked"
    }
}

/**
 * Reporting a name on the leaderboard (Apple 1.2): a message to the app's contact address, written for the athlete to
 * send from their mail app, naming the board and the name. Android has no report; see docs/checklist.md §16.
 */
object NameReport {
    /**
     * The mail link: [subject] and [body] as the athlete's mail app opens them, to [address], the app's contact address.
     * The address is a build value (the string contact_address, written by composeApp/build.gradle.kts), not in git.
     */
    fun mailto(address: String, subject: String, body: String): String =
        "mailto:$address?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 128 && (char.isLetterOrDigit() || char in "-._~")) append(char)
            else append('%').append((code or 0x100).toString(16).substring(1).uppercase())
        }
    }
}
