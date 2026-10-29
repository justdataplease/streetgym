package com.justdataplease.streetgym.account

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.library.LibraryRepository
import com.justdataplease.streetgym.data.progress.ExamLog
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import org.json.JSONObject

/**
 * Pro's cloud progress: users/{uid}/data/progress (completed days, history with each set's reps, exam results) and users/{uid}/data/library
 * (My workouts).
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
        // The cached profile too: a username changed on another device would otherwise keep the score from publishing.
        Profiles.load(context, uid) {}
        val progressDoc = db.document("users/$uid/data/progress")
        val libraryDoc = db.document("users/$uid/data/library")
        val reads = listOf(progressDoc.get(), libraryDoc.get())
        Tasks.whenAllComplete(reads).addOnCompleteListener {
            if (reads.any { !it.isSuccessful }) return@addOnCompleteListener finish(Result.Failed, done)
            // A document of an unexpected shape (another app version, a console edit) fails this sync, not the app.
            try {
                merge(db, progressDoc, libraryDoc, reads[0].result as DocumentSnapshot, reads[1].result as DocumentSnapshot, done)
            } catch (e: Exception) {
                finish(Result.Failed, done)
            }
        }
    }

    private fun merge(
        db: FirebaseFirestore,
        progressDoc: DocumentReference,
        libraryDoc: DocumentReference,
        cloudProgress: DocumentSnapshot,
        cloudLibrary: DocumentSnapshot,
        done: (Result) -> Unit,
    ) {
        val cloudSessions = cloudProgress.getString("sessions")
        val cloudExams = cloudProgress.getString("exams")
        @Suppress("UNCHECKED_CAST")
        val cloudCompleted = (cloudProgress.get("completed") as? List<String>).orEmpty()
        // A merge never removes anything, so a reset travels as its time: one made on another device since this one
        // last synced starts this phone over too, and one made here keeps the cloud's older progress out.
        val cloudResetAt = cloudProgress.getLong("reset_at") ?: 0L
        if (cloudResetAt > progress.resetAt()) progress.reset(cloudResetAt, keepActiveWorkout = true)
        val resetAt = progress.resetAt()
        val (completed, sessions, exams) = if (cloudResetAt == resetAt)
            progress.mergeCloud(cloudCompleted, cloudSessions, cloudExams)
            else progress.mergeCloud(emptyList(), null, null)
        val cloudLibraryObject = cloudLibrary.getString("library")?.let { runCatching { JSONObject(it) }.getOrNull() }
        val cloudLibraryState = LibraryCodec.decode(cloudLibraryObject)
        val mergedLibrary = library.state().merge(cloudLibraryState)
        library.replace(mergedLibrary)
        val libraryJson = LibraryCodec.encode(mergedLibrary).toString()
        val batch = db.batch()
        var writes = 0
        if (sessions != cloudSessions || exams != (cloudExams ?: ExamLog().encode()) ||
            completed != cloudCompleted.toSet() || resetAt != cloudResetAt) {
            batch.set(progressDoc, mapOf("completed" to completed.sorted(), "sessions" to sessions, "exams" to exams,
                "reset_at" to resetAt, "updated_at" to FieldValue.serverTimestamp())); writes++
        }
        // Missing or unreadable in the cloud: the merged copy replaces it.
        if (cloudLibraryObject == null || cloudLibraryState != mergedLibrary) {
            batch.set(libraryDoc, mapOf("library" to libraryJson, "updated_at" to FieldValue.serverTimestamp())); writes++
        }
        if (writes == 0) return finish(Result.Synced, done)
        batch.commit().addOnCompleteListener { commit -> finish(if (commit.isSuccessful) Result.Synced else Result.Failed, done) }
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
