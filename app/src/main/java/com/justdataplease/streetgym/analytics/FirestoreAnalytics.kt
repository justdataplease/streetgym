package com.justdataplease.streetgym.analytics

import android.content.Context
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.justdataplease.streetgym.account.Cloud
import java.util.Date

/**
 * analytics/{installId} and its sessions in Firestore (firestore.rules: write and delete only, nobody reads). The
 * install document's fields are replaced one by one, so a field left out (the account's uid while anonymous) keeps its
 * value. The writer is whoever is signed in: the Google account, else an anonymous user ([Cloud.signInAnonymously]).
 */
class FirestoreAnalyticsBackend(context: Context) : AnalyticsBackend {
    private val context = context.applicationContext

    override fun signIn(done: (Writer?) -> Unit) = Cloud.signInAnonymously(context) { user ->
        done(user?.let { Writer(Cloud.account(it, FirebaseUser::isAnonymous)?.uid) })
    }

    override fun write(installId: String, install: Map<String, Any?>, sessions: Map<String, Map<String, Any?>>, done: (Boolean) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(false)
        runCatching {
            val root = db.document("$COLLECTION/$installId")
            val fields = values(install) + ("lastSeen" to FieldValue.serverTimestamp())
            val batch = db.batch().set(root, fields, SetOptions.mergeFields(fields.keys.toList()))
            sessions.forEach { (id, session) ->
                batch.set(root.collection(SESSIONS).document(id), values(session) + ("uploadedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().addOnCompleteListener { done(it.isSuccessful) }
        }.onFailure { done(false) }
    }

    override fun delete(installId: String, sessionIds: Collection<String>, done: (Boolean) -> Unit) {
        val db = Cloud.firestore(context) ?: return done(false)
        runCatching {
            val root = db.document("$COLLECTION/$installId")
            // A batch holds 500 operations; the install document goes in the last.
            val batches = sessionIds.chunked(BATCH).map { chunk ->
                db.batch().apply { chunk.forEach { delete(root.collection(SESSIONS).document(it)) } }
            }.ifEmpty { listOf(db.batch()) }
            batches.last().delete(root)
            Tasks.whenAll(batches.map { it.commit() }).addOnCompleteListener { done(it.isSuccessful) }
        }.onFailure { done(false) }
    }

    /** Epoch millis in [AnalyticsPayload.TIME_FIELDS] become timestamps, [AnalyticsPayload.Delete] a field deletion. */
    private fun values(document: Map<String, Any?>): Map<String, Any?> = document.mapValues { (key, value) ->
        when {
            value === AnalyticsPayload.Delete -> FieldValue.delete()
            key in AnalyticsPayload.TIME_FIELDS && value is Number -> Timestamp(Date(value.toLong()))
            else -> value
        }
    }

    private companion object {
        const val COLLECTION = "analytics"
        const val SESSIONS = "sessions"
        const val BATCH = 450
    }
}

/** The App Set ID from Play services, developer scope when the phone gives it; null when it can't (no Play services). */
class PlayAppSet(context: Context) : AppSetSource {
    private val context = context.applicationContext

    override fun fetch(done: (AppSetId?) -> Unit) {
        val task = runCatching { AppSet.getClient(context).appSetIdInfo }.getOrNull() ?: return done(null)
        task.addOnCompleteListener { result ->
            val info = if (result.isSuccessful) result.result else null
            done(info?.id?.takeIf(String::isNotBlank)?.let {
                AppSetId(it, if (info.scope == AppSetIdInfo.SCOPE_DEVELOPER) "developer" else "app")
            })
        }
    }
}
