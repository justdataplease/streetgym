package com.justdataplease.streetgym.analytics

import android.content.Context
import android.content.SharedPreferences
import com.justdataplease.streetgym.account.Cloud
import com.justdataplease.streetgym.ui.AppSettings
import java.util.UUID

/** Who writes: [accountUid] for a Google account, null for the anonymous user. */
data class Writer(val accountUid: String?)

/** Where analytics goes: Firestore in the app ([FirestoreAnalyticsBackend]), a fake in tests. */
interface AnalyticsBackend {
    /** Whoever is signed in, else a new anonymous user; [done] gets null when no one could sign in. */
    fun signIn(done: (Writer?) -> Unit)

    /** One batch: the install document's fields as given (others stay) and [sessions] by id; [done]: the server took it. */
    fun write(installId: String, install: Map<String, Any?>, sessions: Map<String, Map<String, Any?>>, done: (Boolean) -> Unit)

    /** Deletes the install document and the sessions [sessionIds] under it; [done]: the server took it. */
    fun delete(installId: String, sessionIds: Collection<String>, done: (Boolean) -> Unit)
}

/** Google's App Set ID ([PlayAppSet]); [fetch] gives null when Play services can't. */
fun interface AppSetSource {
    fun fetch(done: (AppSetId?) -> Unit)
}

/**
 * Progress analytics (the owner, 2026-10-30): every tier's training progress, to see how athletes do and improve the
 * program, under this install's random code ([installId], a UUID made on first use and gone with the app's data). Google's
 * App Set ID chains the codes of one phone across reinstalls, and a signed-in Pro account's uid chains phones; no
 * Android ID, nothing else about the phone. It runs only while "Share my progress" is on ([sharing]) and the build has
 * Firebase ([backend]); without Firebase every call does nothing.
 * - [sync] writes the install document when the app opens, at most every [SYNC_MILLIS];
 * - [classFinished] keeps a finished class on the phone until a write the server confirmed carries it, in one batch with
 *   the install document. Firestore's queue retries a write it was handed; after a restart the class is simply written
 *   again (the same document);
 * - [shareChanged] off stops all of it and deletes the install document and every class this install wrote, retried
 *   until the server confirms, before anything is written again.
 * One write or deletion at a time, on the main thread.
 */
class Analytics(
    private val preferences: SharedPreferences,
    private val backend: AnalyticsBackend?,
    private val appSet: AppSetSource,
    private val sharing: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var running = false
    private var rerun = false

    /** The install document's latest state, until a write carries it. */
    private var install: InstallSnapshot? = null

    /** This install's random code, made on first use with the time it was made (the install document's firstSeen). */
    val installId: String
        get() = preferences.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_INSTALL_ID, it).putLong(KEY_FIRST_SEEN, clock()).apply()
        }

    /** Classes waiting for a confirmed write, and those written: a deletion removes both. */
    fun pending(): Set<String> = preferences.getStringSet(KEY_PENDING, null).orEmpty().toSet()
    fun uploaded(): Set<String> = preferences.getStringSet(KEY_UPLOADED, null).orEmpty().toSet()

    /** Whether what this install shared still waits to be deleted. */
    val deletePending: Boolean get() = preferences.getBoolean(KEY_DELETE, false)

    /** The app opened: writes the install document unless one was tried in the last [SYNC_MILLIS] ([force] anyway). */
    fun sync(force: Boolean = false, snapshot: () -> InstallSnapshot) {
        if (backend == null) return
        val due = sharing() && (force || clock() - preferences.getLong(KEY_LAST_ATTEMPT, 0L) !in 0 until SYNC_MILLIS)
        if (due) install = snapshot()
        if (due || deletePending) pump()
    }

    /** A class finished: [session] ([AnalyticsPayload.session]) goes up with the install document now. */
    fun classFinished(sessionId: String, session: Map<String, Any?>, snapshot: () -> InstallSnapshot) {
        if (backend == null || !sharing()) return
        preferences.edit().putStringSet(KEY_PENDING, pending() + sessionId)
            .putString(KEY_SESSION + sessionId, AnalyticsPayload.encode(session)).apply()
        install = snapshot()
        pump()
    }

    /** The placement's counts and start, sent with every install document from the next write on. */
    fun placed(placement: PlacementRecord) {
        if (backend == null) return
        preferences.edit().putString(KEY_PLACEMENT, placement.encode()).apply()
    }

    /**
     * "Share my progress" switched: off deletes what this install shared, and nothing goes up until it's on again; on
     * writes at the next [sync].
     */
    fun shareChanged(on: Boolean) {
        if (backend == null) return
        if (on) return preferences.edit().remove(KEY_LAST_ATTEMPT).apply()
        val written = pending() + uploaded()
        val edit = preferences.edit().remove(KEY_PENDING).remove(KEY_UPLOADED).remove(KEY_FORGET_ACCOUNT).remove(KEY_LAST_ATTEMPT)
        written.forEach { edit.remove(KEY_SESSION + it) }
        if (preferences.getBoolean(KEY_SHARED, false)) {
            edit.putBoolean(KEY_DELETE, true).putStringSet(KEY_DELETE_IDS, deleteIds() + written)
        }
        edit.apply()
        install = null
        pump()
    }

    /** The Pro account was deleted: the next write takes its uid off this install's document. */
    fun accountDeleted() {
        if (backend == null || !preferences.getBoolean(KEY_SHARED, false)) return
        preferences.edit().putBoolean(KEY_FORGET_ACCOUNT, true).remove(KEY_LAST_ATTEMPT).apply()
    }

    private fun deleteIds(): Set<String> = preferences.getStringSet(KEY_DELETE_IDS, null).orEmpty().toSet()

    private fun placement(): PlacementRecord? = PlacementRecord.decode(preferences.getString(KEY_PLACEMENT, null))

    /** The next step: a pending deletion first, then a write. */
    private fun pump() {
        val backend = backend ?: return
        if (running) { rerun = true; return }
        val deleting = deletePending
        if (!deleting && (!sharing() || install == null)) return
        running = true
        if (!deleting) preferences.edit().putLong(KEY_LAST_ATTEMPT, clock()).apply()
        backend.signIn { writer ->
            when {
                writer == null -> finish()
                deleting -> backend.delete(installId, deleteIds()) { ok ->
                    if (ok) preferences.edit().remove(KEY_DELETE).remove(KEY_DELETE_IDS).remove(KEY_SHARED).apply()
                    finish(again = ok)
                }
                else -> write(backend, writer)
            }
        }
    }

    private fun write(backend: AnalyticsBackend, writer: Writer) {
        val snapshot = install ?: return finish()
        val id = installId
        appSet.fetch { appSetId ->
            if (!sharing()) return@fetch finish()
            val ids = pending().sorted().take(MAX_SESSIONS)
            val sessions = ids.mapNotNull { session ->
                preferences.getString(KEY_SESSION + session, null)?.let(AnalyticsPayload::decode)?.let { session to it }
            }.toMap()
            val document = AnalyticsPayload.install(snapshot, preferences.getLong(KEY_FIRST_SEEN, 0L), appSetId, writer.accountUid,
                preferences.getBoolean(KEY_FORGET_ACCOUNT, false), placement())
            // From here the documents may reach the server, so switching off deletes them.
            preferences.edit().putBoolean(KEY_SHARED, true).apply()
            backend.write(id, document, sessions) { ok ->
                if (ok && sharing()) {
                    val edit = preferences.edit().putStringSet(KEY_PENDING, pending() - ids.toSet())
                        .putStringSet(KEY_UPLOADED, uploaded() + ids).remove(KEY_FORGET_ACCOUNT)
                    ids.forEach { edit.remove(KEY_SESSION + it) }
                    edit.apply()
                    if (install === snapshot) install = null
                }
                finish()
            }
        }
    }

    private fun finish(again: Boolean = false) {
        running = false
        if (rerun || again) { rerun = false; pump() }
    }

    companion object {
        const val PREFERENCES_NAME = "streetgym_analytics"
        /** The install document goes up at most this often when the app opens: a write is what Firestore bills. */
        const val SYNC_MILLIS = 10 * 60_000L
        /** Classes in one write; more wait for the next. */
        const val MAX_SESSIONS = 50
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_FIRST_SEEN = "first_seen"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val KEY_PENDING = "pending_sessions"
        private const val KEY_UPLOADED = "uploaded_sessions"
        private const val KEY_SESSION = "session_"
        private const val KEY_PLACEMENT = "placement"
        private const val KEY_SHARED = "shared"
        private const val KEY_DELETE = "delete_pending"
        private const val KEY_DELETE_IDS = "delete_sessions"
        private const val KEY_FORGET_ACCOUNT = "forget_account"

        @Volatile private var instance: Analytics? = null

        /** The app's one instance: Firestore when the build has Firebase, else nothing at all. */
        fun get(context: Context): Analytics = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                val settings = AppSettings(app)
                Analytics(app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
                    if (Cloud.available(app)) FirestoreAnalyticsBackend(app) else null, PlayAppSet(app), { settings.shareProgress })
            }.also { instance = it }
        }
    }
}
