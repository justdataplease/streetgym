package com.justdataplease.streetgym.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The part of Android's org.json the engine reads and writes its JSON with, for every platform: the same names and the
 * same lenient reading (a missing key gives the default, a number may come as a string, a whole number stays an Int).
 * Values are kept as org.json keeps them: String, Int, Long, Double, Boolean, JSONObject, JSONArray or [JSONObject.NULL],
 * so data written by the Android app before the engine moved here (2.11.5) reads back unchanged.
 */

class JSONException(message: String) : RuntimeException(message)

class JSONObject private constructor(private val map: LinkedHashMap<String, Any?>) {
    constructor() : this(LinkedHashMap())
    constructor(text: String) : this(
        (parse(text) as? JSONObject ?: throw JSONException("A JSONObject text must begin with '{'")).map,
    )

    fun length(): Int = map.size
    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key].let { it == null || it === NULL }
    fun keys(): Iterator<String> = map.keys.iterator()
    fun keySet(): Set<String> = map.keys
    fun opt(key: String): Any? = map[key]
    fun get(key: String): Any = map[key] ?: throw JSONException("JSONObject[\"$key\"] not found.")
    fun remove(key: String): Any? = map.remove(key)

    fun optString(key: String, fallback: String = ""): String = text(map[key]) ?: fallback
    fun getString(key: String): String = get(key) as? String ?: throw JSONException("JSONObject[\"$key\"] is not a string.")
    fun optInt(key: String, fallback: Int = 0): Int = number(map[key])?.toInt() ?: fallback
    fun getInt(key: String): Int = number(get(key))?.toInt() ?: throw JSONException("JSONObject[\"$key\"] is not an int.")
    fun optLong(key: String, fallback: Long = 0L): Long = number(map[key])?.toLong() ?: fallback
    fun getLong(key: String): Long = number(get(key))?.toLong() ?: throw JSONException("JSONObject[\"$key\"] is not a long.")
    fun optDouble(key: String, fallback: Double = Double.NaN): Double = number(map[key])?.toDouble() ?: fallback
    fun getDouble(key: String): Double = number(get(key))?.toDouble() ?: throw JSONException("JSONObject[\"$key\"] is not a number.")
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = bool(map[key]) ?: fallback
    fun getBoolean(key: String): Boolean = bool(get(key)) ?: throw JSONException("JSONObject[\"$key\"] is not a Boolean.")
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun getJSONObject(key: String): JSONObject = get(key) as? JSONObject ?: throw JSONException("JSONObject[\"$key\"] is not a JSONObject.")
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun getJSONArray(key: String): JSONArray = get(key) as? JSONArray ?: throw JSONException("JSONObject[\"$key\"] is not a JSONArray.")

    /** Like org.json, a null value takes the key out. */
    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else map[key] = wrap(value)
        return this
    }

    override fun toString(): String = element(this).toString()

    companion object {
        /** org.json's JSONObject.NULL: a key that is there with a null value. */
        val NULL: Any = Null
    }

    internal fun entries(): Set<Map.Entry<String, Any?>> = map.entries
}

class JSONArray private constructor(private val list: MutableList<Any?>) {
    constructor() : this(mutableListOf())
    constructor(text: String) : this(
        (parse(text) as? JSONArray ?: throw JSONException("A JSONArray text must start with '['")).list,
    )
    constructor(values: Collection<*>) : this(values.mapTo(mutableListOf()) { it?.let(::wrap) ?: JSONObject.NULL })

    fun length(): Int = list.size
    fun isNull(index: Int): Boolean = list.getOrNull(index).let { it == null || it === JSONObject.NULL }
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("JSONArray[$index] not found.")

    fun optString(index: Int, fallback: String = ""): String = text(opt(index)) ?: fallback
    fun getString(index: Int): String = get(index) as? String ?: throw JSONException("JSONArray[$index] is not a String.")
    fun optInt(index: Int, fallback: Int = 0): Int = number(opt(index))?.toInt() ?: fallback
    fun getInt(index: Int): Int = number(get(index))?.toInt() ?: throw JSONException("JSONArray[$index] is not a number.")
    fun optLong(index: Int, fallback: Long = 0L): Long = number(opt(index))?.toLong() ?: fallback
    fun getLong(index: Int): Long = number(get(index))?.toLong() ?: throw JSONException("JSONArray[$index] is not a number.")
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = number(opt(index))?.toDouble() ?: fallback
    fun getDouble(index: Int): Double = number(get(index))?.toDouble() ?: throw JSONException("JSONArray[$index] is not a number.")
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = bool(opt(index)) ?: fallback
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("JSONArray[$index] is not a JSONObject.")
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("JSONArray[$index] is not a JSONArray.")

    fun put(value: Any?): JSONArray {
        list.add(value?.let(::wrap) ?: JSONObject.NULL)
        return this
    }

    override fun toString(): String = element(this).toString()

    internal fun values(): List<Any?> = list
}

private object Null {
    override fun toString(): String = "null"
}

private fun text(value: Any?): String? = if (value == null || value === JSONObject.NULL) null else value.toString()

private fun number(value: Any?): Number? = when (value) {
    is Number -> value
    is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull() }
    else -> null
}

private fun bool(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
    }
    else -> null
}

/** What a put value is kept as: maps and collections become objects and arrays, as org.json wraps them. */
private fun wrap(value: Any): Any = when (value) {
    is JSONObject, is JSONArray, is String, is Boolean, is Int, is Long, is Double -> value
    is Float -> value.toDouble()
    is Short, is Byte -> (value as Number).toInt()
    is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), v) } }
    is Collection<*> -> JSONArray(value)
    is Array<*> -> JSONArray(value.toList())
    else -> if (value === JSONObject.NULL) value else value.toString()
}

private fun parse(text: String): Any? = try {
    value(Json.parseToJsonElement(text))
} catch (e: IllegalArgumentException) {
    throw JSONException(e.message ?: "Invalid JSON")
}

private fun value(element: JsonElement): Any? = when (element) {
    // A null value is kept as NULL, which put stores, as org.json keeps a key read with null.
    is JsonObject -> JSONObject().apply { element.forEach { (key, child) -> put(key, value(child)) } }
    is JsonArray -> JSONArray().apply { element.forEach { put(value(it)) } }
    is JsonNull -> JSONObject.NULL
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" -> true
        element.content == "false" -> false
        else -> element.content.let { raw ->
            if (raw.any { it == '.' || it == 'e' || it == 'E' }) raw.toDouble()
            else raw.toIntOrNull() ?: raw.toLongOrNull() ?: raw.toDouble()
        }
    }
}

private fun element(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JSONObject -> JsonObject(value.entries().associate { (key, child) -> key to element(child) })
    is JSONArray -> JsonArray(value.values().map(::element))
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> if (value.isFinite() && value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> if (value === JSONObject.NULL) JsonNull else JsonPrimitive(value.toString())
}
