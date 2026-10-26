package com.justdataplease.streetgym.account

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.library.LibraryRepository
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import org.json.JSONObject

/**
 * Pro's cloud progress: users/{uid}/data/progress (completed days, history) and users/{uid}/data/library (My workouts).
 * A sync reads both documents, merges them into the phone and writes back only what changed: two reads and at most
 * two writes. It's forced after a change on the phone (sign-in, a finished workout, My workouts, Sync now); otherwise
 * opening the app catches up with other devices at most every [MIN_INTERVAL_MILLIS].
 */
class CloudSync(
    private val context: Context,
    private val progress: SharedPreferencesProgressRepository,
    private val library: LibraryRepository,
) {
    enum class Result { Synced, Skipped, Failed }
    private val store = AccountStore(context)
    private var running = false
    private var again = false

    fun sync(force: Boolean = false, done: (Result) -> Unit = {}) {
        val uid = Cloud.uid(context)
        val db = Cloud.firestore(context)
        if (uid == null || db == null || !Entitlement.tier(context).account) return done(Result.Skipped)
        if (!force && System.currentTimeMillis() - store.lastSyncAt < MIN_INTERVAL_MILLIS) return done(Result.Skipped)
        if (running) { again = true; return done(Result.Skipped) }
        running = true
        val progressDoc = db.document("users/$uid/data/progress")
        val libraryDoc = db.document("users/$uid/data/library")
        val reads = listOf(progressDoc.get(), libraryDoc.get())
        Tasks.whenAllComplete(reads).addOnCompleteListener {
            if (reads.any { !it.isSuccessful }) return@addOnCompleteListener finish(Result.Failed, done)
            val cloudProgress = reads[0].result as DocumentSnapshot
            val cloudLibrary = reads[1].result as DocumentSnapshot
            val cloudSessions = cloudProgress.getString("sessions")
            @Suppress("UNCHECKED_CAST")
            val cloudCompleted = (cloudProgress.get("completed") as? List<String>).orEmpty()
            val (completed, sessions) = progress.mergeCloud(cloudCompleted, cloudSessions)
            val cloudLibraryJson = cloudLibrary.getString("library")
            val mergedLibrary = library.state().merge(LibraryCodec.decode(cloudLibraryJson?.let { runCatching { JSONObject(it) }.getOrNull() }))
            library.replace(mergedLibrary)
            val libraryJson = LibraryCodec.encode(mergedLibrary).toString()
            val batch = db.batch()
            var writes = 0
            if (sessions != cloudSessions || completed != cloudCompleted.toSet()) {
                batch.set(progressDoc, mapOf("completed" to completed.sorted(), "sessions" to sessions,
                    "updated_at" to FieldValue.serverTimestamp())); writes++
            }
            if (cloudLibraryJson == null || LibraryCodec.decode(JSONObject(cloudLibraryJson)) != mergedLibrary) {
                batch.set(libraryDoc, mapOf("library" to libraryJson, "updated_at" to FieldValue.serverTimestamp())); writes++
            }
            if (writes == 0) return@addOnCompleteListener finish(Result.Synced, done)
            batch.commit().addOnCompleteListener { commit -> finish(if (commit.isSuccessful) Result.Synced else Result.Failed, done) }
        }
    }

    private fun finish(result: Result, done: (Result) -> Unit) {
        if (result == Result.Synced) store.lastSyncAt = System.currentTimeMillis()
        running = false
        done(result)
        if (again) { again = false; sync(force = true) }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 6 * 60 * 60_000L
    }
}
