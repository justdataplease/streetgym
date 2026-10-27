package com.justdataplease.streetgym.leaderboard

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.justdataplease.streetgym.account.Cloud
import com.justdataplease.streetgym.account.Profile
import org.json.JSONObject

/**
 * Boards from Firestore, kept on the phone for [Boards.CACHE_MILLIS]: one read per board every six hours at most,
 * and none while the cached copy is fresh. A failed read falls back to the cached copy, however old.
 */
class LeaderboardRepository(private val context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** The cached board and when it was read. */
    fun cached(id: String): Pair<Board, Long>? = preferences.getString("board_$id", null)?.let { raw ->
        runCatching { JSONObject(raw).let { Boards.decode(it.getJSONObject("board"))!! to it.getLong("fetched_at") } }.getOrNull()
    }

    /** [done] gets the board: fresh from the cache or the network, else the cached copy, else null. */
    fun board(id: String, done: (Board?) -> Unit) {
        val now = System.currentTimeMillis()
        val cached = cached(id)
        if (cached != null && Boards.fresh(cached.second, now)) return done(cached.first)
        val db = Cloud.firestore(context) ?: return done(cached?.first)
        db.document("boards/$id").get(Source.SERVER).addOnCompleteListener { task ->
            // A failed task throws from result, so it is only read once the task succeeded.
            val doc = if (task.isSuccessful) task.result else null
            if (doc == null) return@addOnCompleteListener done(cached?.first)
            @Suppress("UNCHECKED_CAST")
            val board = if (!doc.exists()) Board(id, emptyList(), 0, 0) else Board(
                id = id,
                entries = (doc.get("entries") as? List<Map<String, Any?>>).orEmpty().map {
                    BoardEntry(it["username"] as? String ?: "", it["country"] as? String ?: "", (it["score"] as? Number)?.toInt() ?: 0)
                },
                players = doc.getLong("players")?.toInt() ?: 0,
                updatedAt = (doc.get("updated_at") as? Timestamp)?.toDate()?.time ?: 0,
                percentiles = (doc.get("percentiles") as? List<Number>).orEmpty().map { it.toInt() },
            )
            preferences.edit().putString("board_$id", JSONObject().put("board", Boards.encode(board)).put("fetched_at", now).toString()).apply()
            done(board)
        }
    }

    /** One write: the athlete's public score (scores/{uid}). The server folds it into the boards every six hours. */
    fun publish(uid: String, profile: Profile, score: Score, done: (Boolean) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(false)
        db.document("scores/$uid").set(mapOf("v" to Score.VERSION, "username" to profile.username, "country" to profile.country,
            "overall" to score.overall, "families" to score.families, "verified" to score.verified,
            "updated_at" to FieldValue.serverTimestamp()))
            .addOnCompleteListener { done(it.isSuccessful) }
    }

    /** Off the leaderboard: the score document goes. */
    fun unpublish(uid: String, done: (Boolean) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(false)
        db.document("scores/$uid").delete().addOnCompleteListener { done(it.isSuccessful) }
    }

    companion object {
        const val PREFERENCES_NAME = "streetgym_boards"
    }
}
