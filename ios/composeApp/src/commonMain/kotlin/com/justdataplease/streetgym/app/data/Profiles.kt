package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject

/**
 * An athlete's public name and country, and whether they show on the leaderboard (Android: account/Profile.kt). Anyone
 * may pick one; the boards show Plus and Pro.
 */
data class Profile(val username: String, val country: String, val visible: Boolean = true)

/** The leaderboard's score as last published: all-time [points], and [monthPoints] of the UTC [month] ("2026-11"). */
data class Score(val points: Int, val month: String, val monthPoints: Int) {
    companion object {
        /** The score document's version; the server drops any other (Android: Score.VERSION). */
        const val VERSION = 4
    }
}

/**
 * The account's state on the phone, so no screen waits for Firestore: the profile, the last sync and the score
 * published (Android: AccountStore, the "streetgym_account" preferences), under the same names and JSON with the
 * preferences' name in front; a time is its number as text. [UIDS] is the iPhone's own list of the users it keeps
 * something for, as a store of texts can't be listed, so [clear] finds them all.
 */
class AccountStore(private val store: KeyValueStore) {
    fun profile(uid: String): Profile? = store.get(PREFIX + "profile_$uid")?.let {
        runCatching { JSONObject(it).let { json -> Profile(json.getString("username"), json.getString("country"), json.optBoolean("visible", true)) } }.getOrNull()
    }

    fun saveProfile(uid: String, profile: Profile) {
        store.set(PREFIX + "profile_$uid", JSONObject().put("username", profile.username).put("country", profile.country)
            .put("visible", profile.visible).toString())
        remember(uid)
    }

    var lastSyncAt: Long
        get() = store.get(PREFIX + "last_sync_at")?.toLongOrNull() ?: 0L
        set(value) = store.set(PREFIX + "last_sync_at", value.toString())

    /** The score last published, as of [Score.VERSION], and when; an older one reads as null, so the new score goes up. */
    fun publishedScore(uid: String): Pair<Score, Long>? = store.get(PREFIX + "score_$uid")?.let {
        runCatching {
            val json = JSONObject(it)
            if (json.optInt("v") != Score.VERSION) return@runCatching null
            Score(json.getInt("points"), json.getString("month"), json.getInt("month_points")) to json.getLong("at")
        }.getOrNull()
    }

    fun savePublishedScore(uid: String, score: Score?, at: Long) {
        if (score == null) store.set(PREFIX + "score_$uid", null)
        else {
            store.set(PREFIX + "score_$uid", JSONObject().put("v", Score.VERSION).put("points", score.points).put("month", score.month)
                .put("month_points", score.monthPoints).put("at", at).toString())
            remember(uid)
        }
    }

    /** Drops what this phone kept for [uid]: its profile and the score it last published. */
    fun forget(uid: String) {
        store.set(PREFIX + "profile_$uid", null)
        store.set(PREFIX + "score_$uid", null)
    }

    /** Everything (Android: clear()): every user's profile and score, and the last sync. */
    fun clear() {
        uids().forEach(::forget)
        store.set(UIDS, null)
        store.set(PREFIX + "last_sync_at", null)
    }

    private fun uids(): Set<String> = runCatching {
        JSONArray(store.get(UIDS) ?: return emptySet()).let { list -> (0 until list.length()).map(list::optString).toSet() }
    }.getOrDefault(emptySet())

    private fun remember(uid: String) {
        val known = uids()
        if (uid !in known) store.set(UIDS, JSONArray((known + uid).sorted()).toString())
    }

    companion object {
        const val PREFIX = "streetgym_account."
        const val UIDS = PREFIX + "ios_uids"
    }
}

/** Usernames (Android: leaderboard/Leaderboard.kt Usernames): 3 to 20 letters, digits, _ or ., unique ignoring case. */
object Usernames {
    private val pattern = Regex("^[A-Za-z0-9_.]{3,20}$")
    fun valid(name: String): Boolean = pattern.matches(name)
    fun key(name: String): String = name.lowercase()
}

/** Countries (Android: leaderboard/Leaderboard.kt Countries), from [COUNTRY_NAMES]. */
object Countries {
    /** The ISO codes with their English names, sorted by name. */
    fun all(): List<Pair<String, String>> = COUNTRY_NAMES.entries.map { it.key to it.value }.sortedBy { it.second }

    fun valid(code: String): Boolean = code in COUNTRY_NAMES

    fun name(code: String): String = COUNTRY_NAMES[code]?.ifBlank { code } ?: code

    /** The flag emoji of a two-letter code: its two regional indicator letters. */
    fun flag(code: String): String = if (code.length != 2) "" else buildString {
        code.uppercase().forEach { letter ->
            val point = 0x1F1E6 + (letter - 'A') - 0x10000
            append((0xD800 + (point shr 10)).toChar())
            append((0xDC00 + (point and 0x3FF)).toChar())
        }
    }
}

/**
 * The profile in Firestore (Android: Profiles): users/{uid} with the username, country and whether it shows, and the
 * username's claim usernames/{lower-case name} with its owner's uid, so one name has one owner.
 */
class Profiles(private val docs: CloudDocuments?, private val store: AccountStore) {
    enum class Claim { Saved, Taken, Failed }

    /** The profile (users/{uid}), cached; null when the athlete hasn't picked a username. A failed read gives the cached one. */
    fun load(uid: String, done: (Profile?) -> Unit) {
        val docs = docs ?: return done(null)
        docs.read(listOf(user(uid))) { read ->
            val profile = read?.get(user(uid))?.let(::parse)
            profile?.let { store.saveProfile(uid, it) }
            done(profile ?: store.profile(uid))
        }
    }

    /**
     * Saves the profile, claiming the username in usernames/{key} in the same transaction and releasing the one the
     * account held, read from the server, not this phone's copy: another device may have changed it since.
     */
    fun save(uid: String, profile: Profile, done: (Claim) -> Unit) {
        val docs = docs ?: return done(Claim.Failed)
        val key = Usernames.key(profile.username)
        docs.transaction(listOf(claim(key), user(uid)), decide = { read -> plan(uid, profile, read) }) { outcome ->
            val result = Claim.entries.firstOrNull { it.name == outcome } ?: Claim.Failed
            if (result == Claim.Saved) store.saveProfile(uid, profile)
            done(result)
        }
    }

    /**
     * Gives up [uid]'s username, profile and score for good: "Delete profile", or this phone's profile when connecting
     * an account that has one of its own. The profile is read from the server first, as on another device it may hold
     * another name. [done] gets whether it went and the profile that went (null when there was none).
     */
    fun release(uid: String, done: (released: Boolean, profile: Profile?) -> Unit) {
        val docs = docs ?: return done(false, null)
        docs.read(listOf(user(uid))) { read ->
            if (read == null) return@read done(false, null)
            val profile = read[user(uid)]?.let(::parse)
            docs.write(releasePaths(uid, profile).map(CloudWrite::Remove)) { deleted ->
                if (deleted) store.forget(uid)
                done(deleted, profile)
            }
        }
    }

    companion object {
        fun user(uid: String) = "users/$uid"
        fun claim(key: String) = "usernames/$key"

        /** A profile document's fields, or null when it isn't one. */
        fun parse(fields: Map<String, Any?>): Profile? {
            val username = fields.string("username") ?: return null
            val country = fields.string("country") ?: return null
            return Profile(username, country, fields.bool("visible") ?: true)
        }

        /** The transaction's writes from what it read (Android: save's runTransaction body). */
        fun plan(uid: String, profile: Profile, read: Map<String, Map<String, Any?>?>): CloudPlan {
            val key = Usernames.key(profile.username)
            val owner = read[claim(key)]?.string("uid")
            if (owner != null && owner != uid) return CloudPlan(emptyList(), Claim.Taken.name)
            val held = read[user(uid)]?.string("username")
            val writes = buildList {
                if (owner == null) add(CloudWrite.Put(claim(key), mapOf("uid" to uid)))
                held?.let(Usernames::key)?.takeIf { it != key }?.let { add(CloudWrite.Remove(claim(it))) }
                add(CloudWrite.Put(user(uid), mapOf("username" to profile.username, "country" to profile.country,
                    "visible" to profile.visible, "updated_at" to ServerTime)))
            }
            return CloudPlan(writes, Claim.Saved.name)
        }

        /**
         * The documents [release] deletes; the username's claim only when there is a profile: the rules refuse deleting
         * a claim that isn't there, and the whole batch with it.
         */
        fun releasePaths(uid: String, profile: Profile?): List<String> =
            listOfNotNull("scores/$uid", profile?.let { claim(Usernames.key(it.username)) }, user(uid))
    }
}
