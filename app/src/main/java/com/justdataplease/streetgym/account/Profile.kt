package com.justdataplease.streetgym.account

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.justdataplease.streetgym.leaderboard.Score
import com.justdataplease.streetgym.leaderboard.Usernames
import org.json.JSONObject

/**
 * An athlete's public name and country, and whether they show on the leaderboard. Anyone may pick one (2026-10-31);
 * the boards show Plus and Pro.
 */
data class Profile(val username: String, val country: String, val visible: Boolean = true)

/** The account's state on the phone, so no screen waits for Firestore: the profile, the last sync and score. */
class AccountStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun profile(uid: String): Profile? = preferences.getString("profile_$uid", null)?.let {
        runCatching { JSONObject(it).let { json -> Profile(json.getString("username"), json.getString("country"), json.optBoolean("visible", true)) } }.getOrNull()
    }

    fun saveProfile(uid: String, profile: Profile) = preferences.edit().putString("profile_$uid",
        JSONObject().put("username", profile.username).put("country", profile.country).put("visible", profile.visible).toString()).apply()

    var lastSyncAt: Long
        get() = preferences.getLong("last_sync_at", 0L)
        set(value) = preferences.edit().putLong("last_sync_at", value).apply()

    /** The score last published, as of [Score.VERSION]: an older one reads as null, so the new score goes up. */
    fun publishedScore(uid: String): Pair<Score, Long>? = preferences.getString("score_$uid", null)?.let {
        runCatching {
            val json = JSONObject(it)
            if (json.optInt("v") != Score.VERSION) return@runCatching null
            Score(json.getInt("points"), json.getString("month"), json.getInt("month_points")) to json.getLong("at")
        }.getOrNull()
    }

    fun savePublishedScore(uid: String, score: Score?, at: Long) = preferences.edit().apply {
        if (score == null) remove("score_$uid")
        else putString("score_$uid", JSONObject().put("v", Score.VERSION).put("points", score.points).put("month", score.month)
            .put("month_points", score.monthPoints).put("at", at).toString())
    }.apply()

    /** Drops what this phone kept for [uid]: its profile and the score it last published. */
    fun forget(uid: String) = preferences.edit().remove("profile_$uid").remove("score_$uid").apply()

    fun clear() = preferences.edit().clear().apply()

    companion object {
        const val PREFERENCES_NAME = "streetgym_account"
    }
}

object Profiles {
    enum class Claim { Saved, Taken, Failed }

    /** The profile in Firestore (users/{uid}), cached; null when the athlete hasn't picked a username yet. */
    fun load(context: Context, uid: String, done: (Profile?) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(null)
        db.document("users/$uid").get().addOnCompleteListener { task ->
            val doc = if (task.isSuccessful) task.result else null
            val profile = if (doc != null && doc.exists()) runCatching {
                Profile(doc.getString("username")!!, doc.getString("country")!!, doc.getBoolean("visible") ?: true)
            }.getOrNull() else null
            profile?.let { AccountStore(context).saveProfile(uid, it) }
            done(profile ?: AccountStore(context).profile(uid))
        }
    }

    /**
     * Saves the profile, claiming the username (unique ignoring case) in usernames/{key} in the same transaction,
     * and releasing the one the account held. That one is read from the server, not this phone's copy: another device
     * may have changed it since.
     */
    fun save(context: Context, uid: String, profile: Profile, done: (Claim) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(Claim.Failed)
        val key = Usernames.key(profile.username)
        db.runTransaction { transaction ->
            val claim = db.document("usernames/$key")
            val owner = transaction.get(claim).getString("uid")
            if (owner != null && owner != uid) return@runTransaction Claim.Taken
            val held = transaction.get(db.document("users/$uid")).getString("username")
            if (owner == null) transaction.set(claim, mapOf("uid" to uid))
            held?.let(Usernames::key)?.takeIf { it != key }?.let { transaction.delete(db.document("usernames/$it")) }
            transaction.set(db.document("users/$uid"), mapOf("username" to profile.username, "country" to profile.country,
                "visible" to profile.visible, "updated_at" to FieldValue.serverTimestamp()))
            Claim.Saved
        }.addOnCompleteListener { task ->
            val result = if (task.isSuccessful) task.result ?: Claim.Failed else Claim.Failed
            if (result == Claim.Saved) AccountStore(context).saveProfile(uid, profile)
            done(result)
        }
    }

    /**
     * Gives up [uid]'s username, profile and score, for good: the "Delete profile" of a profile kept without Google, or
     * this phone's profile when connecting a Google account that has one of its own. The profile is read from the server
     * first, as on another device it may hold another name. [done] gets the profile that was released (null when there
     * was none), or false when it couldn't (offline).
     */
    fun release(context: Context, uid: String, done: (released: Boolean, profile: Profile?) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(false, null)
        db.document("users/$uid").get().addOnCompleteListener { read ->
            if (!read.isSuccessful) return@addOnCompleteListener done(false, null)
            val doc = read.result
            val profile = if (doc != null && doc.exists()) runCatching {
                Profile(doc.getString("username")!!, doc.getString("country")!!, doc.getBoolean("visible") ?: true)
            }.getOrNull() else null
            val batch = db.batch()
            releasePaths(uid, profile).forEach { batch.delete(db.document(it)) }
            batch.commit().addOnCompleteListener { deleted ->
                if (deleted.isSuccessful) AccountStore(context).forget(uid)
                done(deleted.isSuccessful, profile)
            }
        }
    }

    /**
     * The documents [release] deletes. The username's claim only when there is a profile: the rules refuse deleting a
     * claim that isn't there, and the whole batch with it.
     */
    fun releasePaths(uid: String, profile: Profile?): List<String> =
        listOfNotNull("scores/$uid", profile?.let { "usernames/${Usernames.key(it.username)}" }, "users/$uid")
}
