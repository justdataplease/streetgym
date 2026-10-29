package com.justdataplease.streetgym.billing

/** Levels 1–3 are free, Plus adds Levels 4 and 5, and Levels 6 to 10 come with Pro. */
object LevelAccess {
    const val FIRST_PAID_LEVEL = 4
    const val FIRST_PRO_LEVEL = 6

    /** The lowest tier that opens [level]. */
    fun tierFor(level: Int): Tier = when {
        level >= FIRST_PRO_LEVEL -> Tier.PRO
        level >= FIRST_PAID_LEVEL -> Tier.PLUS
        else -> Tier.FREE
    }

    fun locked(level: Int, tier: Tier): Boolean = tier < tierFor(level)
}
