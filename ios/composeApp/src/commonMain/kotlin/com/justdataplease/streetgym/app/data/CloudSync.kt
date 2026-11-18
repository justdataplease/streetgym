package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.progress.ExamLog
import com.justdataplease.streetgym.json.JSONObject

/**
 * Pro's cloud progress (Android: account/CloudSync.kt, line for line): users/{uid}/data/progress (completed days, the
 * history with each set's reps, the exam results, the last reset) and users/{uid}/data/library (My workouts). A sync
 * reads both documents, merges them into the phone (the engine's ProgressMerge and LibraryState.merge: nothing is ever
 * removed by a merge) and writes back only what changed: two reads and at most two writes. It is forced after a change
 * on the phone (a sign-in, a finished workout, My workouts, Sync now); otherwise opening the app catches up with other
 * phones at most every [MIN_INTERVAL_MILLIS]. Only a signed-in account (never the anonymous user) on Pro syncs.
 *
 * Both phones write the same fields, so one account restores on either: completed as a sorted list of ids, sessions and
 * exams as the JSON text each phone keeps, reset_at, and the server's updated_at.
 */
class CloudSync(
    private val docs: CloudDocuments?,
    private val uid: () -> String?,
    private val progress: StoredProgress,
    private val library: LibraryStore,
    private val store: AccountStore,
    private val profiles: Profiles,
    private val tier: () -> Tier,
    private val now: () -> Long,
) {
    enum class Result { Synced, Skipped, Failed }

    private var running = false
    private var again = false

    fun sync(force: Boolean = false, done: (Result) -> Unit = {}) {
        val uid = uid()
        val docs = docs
        if (uid == null || docs == null || !tier().cloud) return done(Result.Skipped)
        if (!force && now() - store.lastSyncAt < MIN_INTERVAL_MILLIS) return done(Result.Skipped)
        if (running) {
            again = true
            return done(Result.Skipped)
        }
        running = true
        // The cached profile too: a username changed on another phone would otherwise keep the score from publishing.
        profiles.load(uid) {}
        val progressDoc = progressPath(uid)
        val libraryDoc = libraryPath(uid)
        docs.read(listOf(progressDoc, libraryDoc)) { read ->
            if (read == null) return@read finish(Result.Failed, done)
            // A document of an unexpected shape (another app version, a console edit) fails this sync, not the app.
            try {
                merge(docs, progressDoc, libraryDoc, read[progressDoc].orEmpty(), read[libraryDoc].orEmpty(), done)
            } catch (e: Exception) {
                finish(Result.Failed, done)
            }
        }
    }

    private fun merge(
        docs: CloudDocuments,
        progressDoc: String,
        libraryDoc: String,
        cloudProgress: Map<String, Any?>,
        cloudLibrary: Map<String, Any?>,
        done: (Result) -> Unit,
    ) {
        val cloudSessions = cloudProgress.string("sessions")
        val cloudExams = cloudProgress.string("exams")
        val cloudCompleted = (cloudProgress["completed"] as? List<*>).orEmpty().map { it as String }
        // A merge never removes anything, so a reset travels as its time: one made on another phone since this one last
        // synced starts this phone over too, and one made here keeps the cloud's older progress out.
        val cloudResetAt = cloudProgress.long("reset_at") ?: 0L
        if (cloudResetAt > progress.resetAt()) progress.reset(cloudResetAt, keepActiveWorkout = true)
        val resetAt = progress.resetAt()
        val merged = if (cloudResetAt == resetAt) progress.mergeCloud(cloudCompleted, cloudSessions, cloudExams)
            else progress.mergeCloud(emptyList(), null, null)
        val cloudLibraryObject = cloudLibrary.string("library")?.let { runCatching { JSONObject(it) }.getOrNull() }
        val cloudLibraryState = LibraryCodec.decode(cloudLibraryObject)
        val mergedLibrary = library.state().merge(cloudLibraryState)
        library.replace(mergedLibrary)
        val libraryJson = LibraryCodec.encode(mergedLibrary).toString()
        val writes = buildList {
            if (merged.sessions != cloudSessions || merged.exams != (cloudExams ?: ExamLog().encode()) ||
                merged.completed != cloudCompleted.toSet() || resetAt != cloudResetAt) {
                add(CloudWrite.Put(progressDoc, mapOf("completed" to merged.completed.sorted(), "sessions" to merged.sessions,
                    "exams" to merged.exams, "reset_at" to resetAt, "updated_at" to ServerTime)))
            }
            // Missing or unreadable in the cloud: the merged copy replaces it.
            if (cloudLibraryObject == null || cloudLibraryState != mergedLibrary) {
                add(CloudWrite.Put(libraryDoc, mapOf("library" to libraryJson, "updated_at" to ServerTime)))
            }
        }
        if (writes.isEmpty()) return finish(Result.Synced, done)
        docs.write(writes) { ok -> finish(if (ok) Result.Synced else Result.Failed, done) }
    }

    private fun finish(result: Result, done: (Result) -> Unit) {
        if (result == Result.Synced) store.lastSyncAt = now()
        running = false
        done(result)
        if (again) {
            again = false
            sync(force = true)
        }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 6 * 60 * 60_000L

        fun progressPath(uid: String) = "users/$uid/data/progress"
        fun libraryPath(uid: String) = "users/$uid/data/library"
    }
}
