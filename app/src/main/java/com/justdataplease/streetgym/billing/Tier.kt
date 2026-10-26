package com.justdataplease.streetgym.billing

/**
 * What each tier unlocks. Plus is one payment; Pro is a yearly subscription that includes everything in Plus and
 * pays for the server and database behind the account: sign-in, the leaderboard and cloud progress.
 */
enum class Tier(val savedDays: Int) {
    FREE(3), PLUS(30), PRO(300);

    val allLevels: Boolean get() = this >= PLUS
    val adFree: Boolean get() = this >= PLUS
    val renameLevels: Boolean get() = this >= PLUS
    val parks: Boolean get() = this >= PLUS
    /** Plus sees the leaderboard; only Pro appears on it. */
    val leaderboard: Boolean get() = this >= PLUS
    /** Only Pro signs in: its place on the leaderboard and cloud progress need the account. */
    val account: Boolean get() = this == PRO

    companion object {
        fun fromName(name: String?): Tier? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The tier from cached ownership. Debug and GitHub builds can't buy, so they get Pro unless the tester simulates a
 * lower tier; ownership still counts on top. Release builds have no override.
 */
fun tierFor(plusOwned: Boolean, proActive: Boolean, debug: Boolean, simulated: Tier?): Tier {
    val owned = when { proActive -> Tier.PRO; plusOwned -> Tier.PLUS; else -> Tier.FREE }
    return maxOf(owned, if (debug) simulated ?: Tier.PRO else Tier.FREE)
}

/** Saved ready days and custom days share the tier's limit; nothing is removed when a tier ends. */
object SavedLimit {
    fun canAdd(tier: Tier, used: Int): Boolean = used < tier.savedDays
    fun overLimit(tier: Tier, used: Int): Boolean = used > tier.savedDays
}
