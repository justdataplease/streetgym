package com.justdataplease.streetgym.account

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.justdataplease.streetgym.leaderboard.Score
import com.justdataplease.streetgym.leaderboard.Usernames
import org.json.JSONObject

/** A Pro athlete's public name and country, and whether they show on the leaderboard. */
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

    fun publishedScore(uid: String): Pair<Score, Long>? = preferences.getString("score_$uid", null)?.let {
        runCatching {
            val json = JSONObject(it)
            val skills = json.getJSONObject("skills").let { map -> map.keys().asSequence().associateWith(map::getInt) }
            Score(json.getInt("overall"), skills) to json.getLong("at")
        }.getOrNull()
    }

    fun savePublishedScore(uid: String, score: Score?, at: Long) = preferences.edit().apply {
        if (score == null) remove("score_$uid")
        else putString("score_$uid", JSONObject().put("overall", score.overall).put("skills", JSONObject(score.skills)).put("at", at).toString())
    }.apply()

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
            val doc = task.result
            val profile = if (task.isSuccessful && doc != null && doc.exists()) runCatching {
                Profile(doc.getString("username")!!, doc.getString("country")!!, doc.getBoolean("visible") ?: true)
            }.getOrNull() else null
            profile?.let { AccountStore(context).saveProfile(uid, it) }
            done(profile ?: AccountStore(context).profile(uid))
        }
    }

    /**
     * Saves the profile, claiming the username (unique ignoring case) in usernames/{key} in the same transaction,
     * and releasing the previous one.
     */
    fun save(context: Context, uid: String, profile: Profile, previous: Profile?, done: (Claim) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(Claim.Failed)
        val key = Usernames.key(profile.username)
        db.runTransaction { transaction ->
            val claim = db.document("usernames/$key")
            val owner = transaction.get(claim).getString("uid")
            if (owner != null && owner != uid) return@runTransaction Claim.Taken
            if (owner == null) transaction.set(claim, mapOf("uid" to uid))
            previous?.let { Usernames.key(it.username) }?.takeIf { it != key }?.let { transaction.delete(db.document("usernames/$it")) }
            transaction.set(db.document("users/$uid"), mapOf("username" to profile.username, "country" to profile.country,
                "visible" to profile.visible, "updated_at" to FieldValue.serverTimestamp()))
            Claim.Saved
        }.addOnCompleteListener { task ->
            val result = if (task.isSuccessful) task.result ?: Claim.Failed else Claim.Failed
            if (result == Claim.Saved) AccountStore(context).saveProfile(uid, profile)
            done(result)
        }
    }
}
