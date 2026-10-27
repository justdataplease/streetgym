package com.justdataplease.streetgym.data.progress

import org.json.JSONObject

/**
 * Skill tests and readiness checks: when each rung was first passed, and when it was last tried. A rung is tested at
 * most once every [ATTEMPT_MILLIS]; a readiness check in a class is answered whenever the class comes.
 */
data class SkillTestLog(val passed: Map<String, Long> = emptyMap(), val attempts: Map<String, Long> = emptyMap()) {
    /** When [skillId] can be tested again, or null when it can be now. A clock moved back allows a test. */
    fun nextAttemptAt(skillId: String, now: Long): Long? {
        val last = attempts[skillId] ?: return null
        return (last + ATTEMPT_MILLIS).takeIf { now in last until it }
    }

    fun record(skillId: String, passed: Boolean, at: Long): SkillTestLog = SkillTestLog(
        passed = if (passed) this.passed + (skillId to minOf(at, this.passed[skillId] ?: at)) else this.passed,
        attempts = attempts + (skillId to maxOf(at, attempts[skillId] ?: at)),
    )

    /** Another device's log: every pass kept at its first time, every rung at its latest attempt. */
    fun merge(other: SkillTestLog): SkillTestLog = SkillTestLog(
        passed = (passed.keys + other.passed.keys).associateWith {
            minOf(passed[it] ?: Long.MAX_VALUE, other.passed[it] ?: Long.MAX_VALUE)
        },
        attempts = (attempts.keys + other.attempts.keys).associateWith { maxOf(attempts[it] ?: 0L, other.attempts[it] ?: 0L) },
    )

    fun encode(): String = JSONObject()
        .put("passed", JSONObject(passed.toSortedMap()))
        .put("attempts", JSONObject(attempts.toSortedMap()))
        .toString()

    companion object {
        const val ATTEMPT_MILLIS = 24 * 60 * 60_000L

        fun decode(raw: String?): SkillTestLog = runCatching {
            if (raw.isNullOrBlank()) return@runCatching SkillTestLog()
            val json = JSONObject(raw)
            fun times(key: String): Map<String, Long> = json.optJSONObject(key)?.let { map ->
                map.keys().asSequence().filter(String::isNotBlank).associateWith { map.optLong(it) }
            }.orEmpty()
            SkillTestLog(times("passed"), times("attempts"))
        }.getOrDefault(SkillTestLog())
    }
}

/** The merged copy of Pro's cloud progress: completed days, history, skill tests and exam results, as uploaded. */
data class CloudProgress(val completed: Set<String>, val sessions: String, val tests: String, val exams: String)
