package com.justdataplease.streetgym.data.skills

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads what skills need straight from curriculum.json: the movement library (category, patterns,
 * regression) and every session's main and practice steps in schedule order. Optional readiness
 * checks (tempo "readiness_check") are flagged so they do not count toward targets.
 */
object SkillCurriculumReader {
    fun read(json: String): SkillBook {
        val root = JSONObject(json)
        val movements = buildMap {
            val library = root.optJSONArray("movement_library") ?: JSONArray()
            for (index in 0 until library.length()) {
                val item = library.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                put(id, SkillMovement(
                    id = id,
                    name = item.optJSONObject("name")?.optString("en")?.takeIf(String::isNotBlank) ?: humanize(id),
                    category = item.optString("category"),
                    patterns = item.optJSONArray("patterns").strings().toSet(),
                    regressionId = item.optString("regression_id").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
        val levels = root.optJSONArray("levels").objects().sortedBy { it.optInt("number") }
        val schedule = levels.flatMap { level ->
            level.optJSONArray("programs").objects().sortedBy { it.optInt("number") }.map { program ->
                val schedule = program.optJSONObject("schedule")
                SkillProgram(
                    id = program.optString("id"),
                    level = program.optInt("level", level.optInt("number")),
                    week = schedule?.optInt("week")?.takeIf { it > 0 },
                    day = schedule?.optInt("day")?.takeIf { it > 0 },
                    steps = steps(program, movements.keys),
                )
            }
        }
        return SkillBook.build(movements, schedule)
    }

    private fun steps(program: JSONObject, known: Set<String>): List<SkillStep> {
        val compact = (program.optJSONArray("training_main") ?: program.optJSONArray("main")).tuples() +
            program.optJSONArray("practice").tuples()
        val fromTuples = compact.mapNotNull { tuple ->
            val id = tuple.optString(0).takeIf { it in known } ?: return@mapNotNull null
            SkillStep(id, tuple.optInt(1, 1).coerceAtLeast(1), tuple.optString(5) == READINESS_CHECK)
        }
        if (fromTuples.isNotEmpty()) return fromTuples
        // Authentic sessions list exercises by name; map the recurring ones to library movements.
        val content = program.optJSONObject("training_content") ?: program.optJSONObject("content")
        return content?.optJSONArray("exercises").objects().mapNotNull { exercise ->
            val name = exercise.optJSONObject("name")?.optString("en").orEmpty()
            val id = (exercise.optString("movement_id").takeIf { it in known } ?: AUTHENTIC_NAMES[name])
                ?.takeIf { it in known } ?: return@mapNotNull null
            SkillStep(id, exercise.optJSONArray("sets")?.length()?.coerceAtLeast(1) ?: 1)
        }
    }

    private const val READINESS_CHECK = "readiness_check"

    private val AUTHENTIC_NAMES = mapOf(
        "Pull Up" to "pullup", "Push Up" to "pushup", "Squat" to "squat", "Hanging Leg Raise" to "hanging_leg_raise",
        "Front Support" to "plank", "Hang" to "active_hang", "Dips" to "parallel_dip", "Half Dips" to "assisted_dip",
        "Inverted Row" to "inverted_row", "Pull Over" to "pullover_lowbar", "Assisted Pull Over" to "pullover_lowbar",
        "Hanging Knee Raise" to "knee_raise", "Band-Assisted Muscle-Up" to "band_muscleup", "Bar Dips" to "straight_bar_dip",
        "Inverted Hang" to "skin_cat_partial", "Inverted Hang - Attempt" to "skin_cat_partial",
        "Feet-Assisted Low-Bar Transition" to "low_bar_transition", "Plank" to "plank", "Muscle Up" to "strict_muscleup",
        "Tuck Front-Lever Hold" to "tuck_front_hold", "Side Plank" to "side_plank", "Single Leg Squat" to "assisted_pistol_squat",
        "2/3 Muscle Up" to "jump_muscleup", "Spotted Tuck Back-Lever Entry" to "tuck_back_entry", "Front Lunges" to "reverse_lunge",
    )

    private fun humanize(id: String) = id.split('_').joinToString(" ").replaceFirstChar(Char::uppercase)

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

    private fun JSONArray?.tuples(): List<JSONArray> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONArray)

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
