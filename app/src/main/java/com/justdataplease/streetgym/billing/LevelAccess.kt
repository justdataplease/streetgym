package com.justdataplease.streetgym.billing

/** Levels 1–3 are free; Plus and Pro unlock the rest. */
object LevelAccess {
    const val FIRST_PAID_LEVEL = 4
    fun locked(level: Int, owned: Boolean): Boolean = level >= FIRST_PAID_LEVEL && !owned
}
