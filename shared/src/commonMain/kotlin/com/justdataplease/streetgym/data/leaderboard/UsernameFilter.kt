package com.justdataplease.streetgym.data.leaderboard

/**
 * Usernames the leaderboard won't take (Apple 1.2 asks apps with names others see for a filter of objectionable
 * material; the owner, 28 Nov 2026: one word list in the engine, so Android and iPhone refuse exactly the same names).
 * The words are in [UsernameWords]: obscenities, slurs and hate terms, and names that pass for the app or its staff.
 *
 * A name is read the way it is meant to be read before it is matched:
 * - split into parts at `_`, `.` (and any other mark) and where a capital follows a small letter or a digit, so
 *   "BigBoss_7" reads "big", "boss", "7"; then lower-cased;
 * - look-alikes read as letters: 0 o, 1 i or l (both are tried), 3 e, 4 a, 5 s, 7 t, @ a, $ s; numbers such as 1488
 *   are also matched as written;
 * - a run of one letter reads as that letter written once or more, but never fewer times than the word writes it:
 *   "fuuuck" is "fuck" and "asss" is "ass", while "as" is not "ass".
 *
 * [UsernameWords.ANYWHERE] (long, unambiguous words) are then looked for anywhere in the name with its parts joined,
 * after the ordinary words that contain one ([UsernameWords.ORDINARY]: Scunthorpe, snigger) are taken out.
 * [UsernameWords.WHOLE] (short or ambiguous words, which ordinary words contain) count only as the whole name or a whole
 * part, also without the digits at its ends: "assassin", "class", "therapist", "cockerel", "dickson" and "grape" stay
 * allowed, while "big_ass", "BigDick69" and "a.s.s" don't.
 */
object UsernameFilter {
    /** Whether [username] may be shown on the boards: none of [UsernameWords] in it, read as above. */
    fun allowed(username: String): Boolean {
        val parts = parts(username)
        if (parts.isEmpty()) return true
        val joined = parts.joinToString("")
        for (reading in readings(joined)) {
            val text = Runs.of(UsernameWords.ORDINARY.fold(reading) { left, word -> left.replace(word, "-") })
            if (anywhere.any(text::contains)) return false
        }
        for (piece in (parts + joined).flatMap { listOf(it, it.trim(Char::isDigit)) }.toSet()) {
            for (reading in readings(piece)) {
                if (reading.isEmpty() || reading in ordinary) continue
                val text = Runs.of(reading)
                if (whole[text.letters]?.any(text::isWord) == true) return false
            }
        }
        return true
    }

    private val anywhere: List<Runs> = UsernameWords.ANYWHERE.map(Runs::of)
    private val whole: Map<String, List<Runs>> = UsernameWords.WHOLE.map(Runs::of).groupBy { it.letters }
    private val ordinary: Set<String> = UsernameWords.ORDINARY.toSet()

    /**
     * The name's parts, lower-case: split at anything but a letter, a digit, @ or $, and before a capital that follows
     * a small letter or a digit ("DickSon" is "dick" and "son", "Dickson" one part).
     */
    private fun parts(name: String): List<String> {
        val parts = mutableListOf<String>()
        val part = StringBuilder()
        var previous = ' '
        for (char in name) {
            val kept = char.isLetterOrDigit() || char == '@' || char == '$'
            if (!kept || (char.isUpperCase() && (previous.isLowerCase() || previous.isDigit()))) {
                if (part.isNotEmpty()) parts += part.toString()
                part.clear()
            }
            if (kept) part.append(char.lowercaseChar())
            previous = if (kept) char else ' '
        }
        if (part.isNotEmpty()) parts += part.toString()
        return parts
    }

    /** [text] as written (for 1488 and the like), and with its look-alikes read as letters, 1 as i and as l. */
    private fun readings(text: String): Set<String> = setOf(text, letters(text, one = 'i'), letters(text, one = 'l'))

    private fun letters(text: String, one: Char): String = buildString(text.length) {
        for (char in text) append(when (char) {
            '0' -> 'o'
            '1' -> one
            '3' -> 'e'
            '4' -> 'a'
            '5' -> 's'
            '7' -> 't'
            '@' -> 'a'
            '$' -> 's'
            else -> char
        })
    }

    /** A text as runs of one character: "fuuck" is f×1 u×2 c×1 k×1, its [letters] "fuck". */
    private class Runs(val letters: String, val counts: IntArray) {
        /** [word] somewhere in this text: its letters in order, each run at least as long as the word's. */
        fun contains(word: Runs): Boolean = (0..letters.length - word.letters.length).any { at -> matchesAt(word, at) }

        /** This text is [word]: the same letters, each run at least as long as the word's. */
        fun isWord(word: Runs): Boolean = letters.length == word.letters.length && matchesAt(word, 0)

        private fun matchesAt(word: Runs, at: Int): Boolean {
            for (i in word.letters.indices) {
                if (letters[at + i] != word.letters[i] || counts[at + i] < word.counts[i]) return false
            }
            return true
        }

        companion object {
            fun of(text: String): Runs {
                val letters = StringBuilder()
                val counts = mutableListOf<Int>()
                for (char in text) {
                    if (letters.isNotEmpty() && letters.last() == char) counts[counts.lastIndex]++
                    else {
                        letters.append(char)
                        counts += 1
                    }
                }
                return Runs(letters.toString(), counts.toIntArray())
            }
        }
    }
}
