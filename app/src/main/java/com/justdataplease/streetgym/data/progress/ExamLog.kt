package com.justdataplease.streetgym.data.progress

import org.json.JSONArray
import org.json.JSONObject

/**
 * One exam taken: when, and which of its targets ([reached] and [missed] hold the tested movements) the athlete
 * reached. It passes when nothing was missed.
 */
data class ExamResult(val programId: String, val at: Long, val reached: List<String>, val missed: List<String>) {
    val passed: Boolean get() = missed.isEmpty()
}

/** Every exam result, retakes included, oldest first. */
data class ExamLog(val results: List<ExamResult> = emptyList()) {
    fun record(result: ExamResult): ExamLog = ExamLog((results + result).sortedBy { it.at })

    /** The latest attempt at [programId], or null when it was never taken. */
    fun latest(programId: String): ExamResult? = results.filter { it.programId == programId }.maxByOrNull { it.at }

    /** Another device's log: every attempt kept once. */
    fun merge(other: ExamLog): ExamLog =
        ExamLog((results + other.results).distinctBy { it.programId to it.at }.sortedBy { it.at })

    fun encode(): String = JSONArray().apply {
        results.forEach { result ->
            put(JSONObject()
                .put("program_id", result.programId)
                .put("at", result.at)
                .put("reached", JSONArray(result.reached))
                .put("missed", JSONArray(result.missed)))
        }
    }.toString()

    companion object {
        fun decode(raw: String?): ExamLog = runCatching {
            if (raw.isNullOrBlank()) return@runCatching ExamLog()
            val array = JSONArray(raw)
            fun JSONArray?.strings(): List<String> =
                if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
            ExamLog((0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                ExamResult(json.getString("program_id"), json.getLong("at"),
                    json.optJSONArray("reached").strings(), json.optJSONArray("missed").strings())
            }.sortedBy { it.at })
        }.getOrDefault(ExamLog())
    }
}
