package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject

/*
 * Firestore as the app uses it (Android: FirebaseFirestore through account/, leaderboard/ and analytics/): whole
 * documents by path, written in batches and in one transaction. The rules of what is read and written, and the
 * documents' shapes, are here in common Kotlin, the same as Android's; on iPhone Swift only moves documents
 * (iosApp/StreetGym/FirebaseAccount.swift, through iosMain's IosCloud), on the desktop and in tests a fake keeps them.
 *
 * A document's fields are plain values: String, Int, Long, Double, Boolean, null, a List or a Map of them, and the
 * three below.
 */

/** The server's time when the write lands (Firestore: FieldValue.serverTimestamp()). */
object ServerTime

/** Removes the field from a document written with merge fields (Firestore: FieldValue.delete()). */
object DeleteField

/** A time Firestore keeps as a timestamp (Firestore: Timestamp), in epoch milliseconds. */
data class CloudTime(val millis: Long)

/** One write of a batch or a transaction. */
sealed interface CloudWrite {
    val path: String

    /** Writes [fields] as the whole document, or with [mergeFields] only those fields, the others kept (Firestore: SetOptions.mergeFields). */
    data class Put(override val path: String, val fields: Map<String, Any?>, val mergeFields: List<String>? = null) : CloudWrite

    data class Remove(override val path: String) : CloudWrite
}

/** What a transaction decided from the documents it read: its [writes], and the [outcome] it reports. */
class CloudPlan(val writes: List<CloudWrite>, val outcome: String)

/**
 * The documents. Every answer comes back on the main thread. A document read as null doesn't exist; a failed read or
 * write (offline, refused by the rules) answers null or false.
 */
interface CloudDocuments {
    /** Reads [paths] from the server (never a cached copy, as Android's Source.SERVER); [done] gets each path's fields. */
    fun read(paths: List<String>, done: (Map<String, Map<String, Any?>?>?) -> Unit)

    /** Writes [writes] at once, all or none (Firestore: a WriteBatch). */
    fun write(writes: List<CloudWrite>, done: (Boolean) -> Unit)

    /**
     * Reads [paths] in a transaction, lets [decide] plan its writes from them, writes them and gives [done] the plan's
     * outcome, or null when it failed. [decide] may run more than once, as Firestore retries a transaction.
     */
    fun transaction(paths: List<String>, decide: (Map<String, Map<String, Any?>?>) -> CloudPlan, done: (String?) -> Unit)
}

/**
 * Documents as JSON text, the way they cross to Swift: plain values as JSON, and the three special ones as an object
 * with one key: {"$server":"time"}, {"$server":"delete"}, {"$time":<millis>}. Swift writes Firestore's own values from
 * them, and turns a timestamp it reads into {"$time":…}.
 */
object CloudJson {
    private const val SERVER = "\$server"
    private const val TIME = "\$time"

    fun encodeFields(fields: Map<String, Any?>): JSONObject = JSONObject().apply { fields.forEach { (key, value) -> put(key, encode(value)) } }

    fun encode(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        ServerTime -> JSONObject().put(SERVER, "time")
        DeleteField -> JSONObject().put(SERVER, "delete")
        is CloudTime -> JSONObject().put(TIME, value.millis)
        is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> put(key.toString(), encode(item)) } }
        is List<*> -> JSONArray().apply { value.forEach { put(encode(it)) } }
        is Float -> value.toDouble()
        else -> value
    }

    /** The writes as [{"path","op":"set"|"delete","fields","merge":[…]}]. */
    fun encodeWrites(writes: List<CloudWrite>): String = JSONArray().apply {
        writes.forEach { write ->
            put(when (write) {
                is CloudWrite.Put -> JSONObject().put("path", write.path).put("op", "set").put("fields", encodeFields(write.fields))
                    .apply { write.mergeFields?.let { put("merge", JSONArray(it)) } }
                is CloudWrite.Remove -> JSONObject().put("path", write.path).put("op", "delete")
            })
        }
    }.toString()

    /** {"path": fields or null} from Swift; null when the text isn't such an object. */
    fun decodeDocuments(text: String?): Map<String, Map<String, Any?>?>? = runCatching {
        val json = JSONObject(text ?: return null)
        json.keySet().associateWith { path -> json.optJSONObject(path)?.let(::decodeFields) }
    }.getOrNull()

    fun decodeFields(json: JSONObject): Map<String, Any?> = json.keySet().associateWith { decode(json.opt(it)) }

    private fun decode(value: Any?): Any? = when {
        value == null || value === JSONObject.NULL -> null
        value is JSONObject && value.has(TIME) -> CloudTime(value.optLong(TIME))
        value is JSONObject -> decodeFields(value)
        value is JSONArray -> List(value.length()) { decode(value.opt(it)) }
        else -> value
    }
}

/** Reading a document's fields as Android's DocumentSnapshot getters do. */
internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String
internal fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
internal fun Map<String, Any?>.time(key: String): Long? = (this[key] as? CloudTime)?.millis
