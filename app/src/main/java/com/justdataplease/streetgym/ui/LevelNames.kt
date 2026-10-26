package com.justdataplease.streetgym.ui

import android.content.Context

/** The athlete's own names for levels (Plus). A name set once stays shown, whatever the tier later. */
class LevelNames(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun name(levelNumber: Int): String? = preferences.getString(key(levelNumber), null)?.takeIf(String::isNotBlank)

    fun set(levelNumber: Int, name: String?) {
        preferences.edit().apply { if (name.isNullOrBlank()) remove(key(levelNumber)) else putString(key(levelNumber), name) }.apply()
    }

    private fun key(levelNumber: Int) = "level_$levelNumber"

    companion object {
        const val PREFERENCES_NAME = "streetgym_level_names"
    }
}
