package com.justdataplease.streetgym.billing

import java.security.MessageDigest
import java.util.Locale

/**
 * Google Play's reviewers must reach every paid part, but they can't buy. A long press on the title in Settings › About
 * takes a review code; the right one acts as Pro on that device for [DAYS] days. Only the code's SHA-256 is in the app
 * (the repository is public); the code itself is kept outside it (docs/TIERS.md, "Play review access").
 */
object ReviewAccess {
    const val DAYS = 30
    const val DURATION_MILLIS = DAYS * 24L * 60 * 60 * 1000
    /** SHA-256 of the normalised code ([normalise]), in lowercase hex. */
    const val CODE_SHA256 = "4f411715f8018f6630db421c1bf5e265449241a35f5412819d4beec4951ec79c"

    /** The code as typed, trimmed and in capitals; hyphens stay. */
    fun normalise(input: String): String = input.trim().uppercase(Locale.ROOT)

    fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun matches(input: String, codeSha256: String = CODE_SHA256): Boolean {
        val code = normalise(input)
        return code.isNotEmpty() &&
            MessageDigest.isEqual(sha256Hex(code).toByteArray(), codeSha256.lowercase(Locale.ROOT).toByteArray())
    }

    /** When access ends for a code typed at [now]: [DAYS] days later for the right code, null for a wrong one. */
    fun unlockUntil(input: String, now: Long, codeSha256: String = CODE_SHA256): Long? =
        if (matches(input, codeSha256)) now + DURATION_MILLIS else null

    /** Whether access that ends at [until] (0 when no code was ever accepted) still runs at [now]. */
    fun active(until: Long, now: Long): Boolean = now < until
}
