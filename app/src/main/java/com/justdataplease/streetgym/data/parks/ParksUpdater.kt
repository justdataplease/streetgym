package com.justdataplease.streetgym.data.parks

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object ParksPolicy {
    /** How often Parks asks whether the published file changed (it changes about four times a year). */
    const val CHECK_MILLIS = 7 * 24 * 60 * 60_000L
    /** The largest download accepted, unpacked: the real file is about 5 MB. */
    const val MAX_BYTES = 30 * 1024 * 1024

    fun dueForCheck(checkedAt: Long, now: Long): Boolean = now - checkedAt !in 0 until CHECK_MILLIS

    /** A new file replaces the current parks only if it isn't far smaller (the same 0.8 floor as the job). */
    fun accept(newCount: Int, currentCount: Int): Boolean = newCount > 0 && newCount >= 0.8 * currentCount
}

/**
 * The parks behind Parks near me: the bundled copy, replaced by the quarterly file the parks refresh job publishes
 * (tools/parks_job) when `parks_url` is configured. Every call here does disk or network work: off the main thread.
 */
class ParksUpdater(private val context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "parks/spots.tsv")

    private fun url(): String? = context.resources.getIdentifier("parks_url", "string", context.packageName)
        .takeIf { it != 0 }?.let(context::getString)?.takeIf { it.startsWith("https://") }

    /** The downloaded file when there is a good one, else the bundled copy. */
    fun load(): List<Park> =
        runCatching { if (file.exists()) file.reader(Charsets.UTF_8).use(Parks::read).takeIf { it.isNotEmpty() } else null }.getOrNull()
            ?: context.assets.open(Parks.ASSET).reader(Charsets.UTF_8).use(Parks::read)

    /**
     * At most every [ParksPolicy.CHECK_MILLIS]: one conditional request. Unchanged (304) costs a few bytes; a new file
     * is unpacked, checked against [currentCount] and saved. Returns the new parks, or null when nothing changed.
     */
    fun update(currentCount: Int): List<Park>? {
        val url = url() ?: return null
        val now = System.currentTimeMillis()
        if (!ParksPolicy.dueForCheck(preferences.getLong(KEY_CHECKED_AT, 0L), now)) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            preferences.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    preferences.edit().putLong(KEY_CHECKED_AT, now).apply()
                    return null
                }
                HttpURLConnection.HTTP_OK -> {
                    val bytes = GZIPInputStream(connection.inputStream).use { input ->
                        input.readNBytesCompat(ParksPolicy.MAX_BYTES + 1)
                    }
                    if (bytes.size > ParksPolicy.MAX_BYTES) return null
                    val parks = Parks.read(bytes.toString(Charsets.UTF_8).reader())
                    preferences.edit().putLong(KEY_CHECKED_AT, now).apply()
                    if (!ParksPolicy.accept(parks.size, currentCount)) return null
                    file.parentFile?.mkdirs()
                    val part = File(file.path + ".part")
                    part.writeBytes(bytes)
                    if (!part.renameTo(file)) { file.delete(); if (!part.renameTo(file)) return null }
                    preferences.edit().putString(KEY_ETAG, connection.getHeaderField("ETag")).apply()
                    return parks
                }
                // Anything else is tried again next time Parks opens.
                else -> return null
            }
        } finally {
            connection.disconnect()
        }
    }

    // InputStream.readNBytes needs API 33; the app supports 23.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val PREFERENCES_NAME = "streetgym_parks"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_ETAG = "etag"
    }
}
