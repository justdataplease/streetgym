package com.justdataplease.streetgym.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** A step names up to three easier rungs: its own, then the library's next ones down. */
class EasierChainTest {
    private fun movement(id: String, regression: String?) = JSONObject()
        .put("id", id)
        .put("name", JSONObject().put("en", id.uppercase()))
        .put("description", JSONObject().put("en", "Move $id."))
        .put("safety", JSONObject().put("en", "Stop for pain."))
        .put("regression_id", regression ?: JSONObject.NULL)

    private fun easierOf(step: JSONArray, vararg library: JSONObject): String {
        val program = JSONObject().put("id", "p1").put("number", 1).put("main", JSONArray().put(step))
        val root = JSONObject()
            .put("movement_library", JSONArray(library.toList()))
            .put("levels", JSONArray().put(JSONObject().put("number", 1).put("programs", JSONArray().put(program))))
        val description = CurriculumJsonParser.parse(root).levels.single().programs.single().exercises.single().description
        return description.en.orEmpty().substringAfter("Regression: ", "")
    }

    private fun step(id: String, regression: String? = null) =
        JSONArray(listOf(id, 2, 5, JSONObject.NULL, 60, JSONObject.NULL, regression ?: JSONObject.NULL))

    private val ladder = arrayOf(
        movement("a", "b"), movement("b", "c"), movement("c", "d"), movement("d", "e"), movement("e", null),
    )

    @Test fun followsTheLibraryForThreeRungs() {
        assertEquals("B → C → D", easierOf(step("a"), *ladder))
    }

    @Test fun startsFromTheStepsOwnEasierRung() {
        assertEquals("C → D → E", easierOf(step("a", "c"), *ladder))
    }

    @Test fun stopsAtTheBottomOfTheLadder() {
        assertEquals("E", easierOf(step("d"), *ladder))
        assertEquals("", easierOf(step("e"), *ladder))
    }

    @Test fun aLoopInTheLibraryEndsTheChain() {
        assertEquals("Y → X", easierOf(step("x"), movement("x", "y"), movement("y", "x")))
    }
}
