package com.justdataplease.streetgym.widget

import com.justdataplease.streetgym.data.model.TrainingProgram
import kotlin.math.roundToInt

/**
 * What the home-screen widget shows: the next class in the sequence, never a date. A class title reads
 * "Role · Lead" ("Basics & Endurance · Strict Pull-Up"); the widget puts the role in the headline and takes the lead
 * from the main part's first step, which is what the title names. A recovery day names its theme after the dot and
 * leads with an easy walk; an exam leads with its first test and lists how many targets it holds.
 */
data class NextClassCard(
    /** The class the tap opens; null opens the app's home (all done, or the first-run placement still to answer). */
    val programId: String?,
    val levelNumber: Int,
    /** The class's number within its level, as Today counts it ("Class 20"). */
    val classNumber: Int,
    val kind: Kind,
    /** The title before " · ", or the whole title when it has none ("Block 2 Exam", "Level 5 Test: Balance & Levers"). */
    val role: String,
    /** The third line before the minutes: the lead step's name, or a recovery day's theme. Empty on an exam. */
    val detail: String,
    /** An exam's targets (its assessment sets); 0 otherwise. */
    val targets: Int,
    val minutes: Int?,
    /** The movement the tile plays: the main part's first step. */
    val movementId: String?,
    val movementName: String,
    /** The class's level is beyond the athlete's tier; the tap still opens it and the app shows the tiers. */
    val locked: Boolean,
) {
    enum class Kind { CLASS, RECOVERY, EXAM, ALL_DONE }

    companion object {
        const val SEPARATOR = " · "

        /** The card for [program]; [opens] false sends the tap to the app's home instead of the class. */
        fun of(program: TrainingProgram, locked: Boolean, opens: Boolean = true): NextClassCard {
            val title = program.name.resolve().trim()
            val split = title.indexOf(SEPARATOR)
            val role = if (split > 0) title.substring(0, split).trim() else title
            val named = if (split > 0) title.substring(split + SEPARATOR.length).trim() else ""
            val lead = program.exercises.firstOrNull()
            val leadName = lead?.name?.resolve()?.trim().orEmpty()
            val kind = when {
                program.isExam -> Kind.EXAM
                program.dayType == RECOVERY -> Kind.RECOVERY
                else -> Kind.CLASS
            }
            return NextClassCard(
                programId = program.id.takeIf { opens },
                levelNumber = program.levelNumber,
                classNumber = program.number,
                kind = kind,
                role = role,
                detail = when (kind) {
                    Kind.EXAM -> ""
                    Kind.RECOVERY -> named.ifEmpty { leadName }
                    else -> leadName.ifEmpty { named }
                },
                targets = if (kind == Kind.EXAM) program.exercises.count { it.assessment } else 0,
                minutes = program.estimatedMinutes,
                movementId = lead?.movementId,
                movementName = leadName,
                locked = locked,
            )
        }

        /** Every class is done: the last class's lead keeps the tile moving, and the tap opens the app's home. */
        fun allDone(last: TrainingProgram?): NextClassCard {
            val lead = last?.exercises?.firstOrNull()
            return NextClassCard(null, last?.levelNumber ?: 0, last?.number ?: 0, Kind.ALL_DONE, "", "", 0, null,
                lead?.movementId, lead?.name?.resolve().orEmpty(), locked = false)
        }

        private const val RECOVERY = "recovery"
    }
}

/** The card's measures in dp (res/layout/widget_next_class.xml) and the room they leave the headline. */
object WidgetLayout {
    /** The tile is 76 dp with a 2 dp outline; the drawings fill what's inside. */
    const val FIGURE_DP = 72
    /** Drawings stop at 3× (xxhdpi): sharp on every phone and at most 16 × 216² × 2 bytes, about 1.5 MB. */
    const val MAX_FRAME_SCALE = 3f
    const val TAPE_TEXT_DP = 11f
    const val TAPE_PAD_X_DP = 6f
    const val TAPE_HEIGHT_DP = 17f
    /** Concrete's headline size; the other faces follow their theme's title scale against Concrete's [TITLE_SCALE_BASE]. */
    const val TITLE_DP = 19f
    const val TITLE_SCALE_BASE = 1.15f
    /** Everything across the card but the text column: shadow 4, outline 2 + 2, padding 10 + 12, tile 76, gaps 12 + 12, start 44. */
    const val FIXED_DP = 174
    /** A four-cell widget on a typical phone, for a launcher that hasn't said how wide it made the widget. */
    const val DEFAULT_WIDTH_DP = 320
    const val MIN_TITLE_DP = 60
    /** The provider's minimum width (res/xml/widget_next_class_info.xml). */
    const val MIN_WIDTH_DP = 250f
    /** The 96 dp card and its 4 dp shadow; a cell less tall than this gets the compact card. */
    const val CARD_HEIGHT_DP = 100f
    /** The compact card's floor: one cell of the oldest launchers. */
    const val MIN_HEIGHT_DP = 40f
    /** The "LEVEL 4 · CLASS 20" line: 12 sp bold, letter-spaced, 6 dp after the tape; it may shrink to 9 sp. */
    const val META_SP = 12f
    const val META_SPACING = 0.06f
    const val TAPE_GAP_DP = 6f
    const val MIN_META_SCALE = 0.75f

    /** How wide the headline may be in a widget [widthDp] wide (0 when unknown). */
    fun titleWidthDp(widthDp: Int): Int =
        ((if (widthDp > 0) widthDp else DEFAULT_WIDTH_DP) - FIXED_DP).coerceAtLeast(MIN_TITLE_DP)

    /** Before Android 12, where the launcher can't pick per orientation: the compact card for a cell [heightDp] tall. */
    fun compact(heightDp: Int): Boolean = heightDp > 0 && heightDp < CARD_HEIGHT_DP

    /** Whether the tape shows beside the meta line, and how much the line shrinks ([MIN_META_SCALE] at most). */
    class MetaFit(val tape: Boolean, val scale: Float)

    /**
     * Fits a meta line [needed] wide (at full size) into a [column] that also holds the tape ([tape] with its gap). The
     * line keeps its numbers: the tape steps aside first, then the line gets smaller; past that it ellipsizes.
     */
    fun fitMeta(needed: Float, column: Float, tape: Float): MetaFit = when {
        needed <= column - tape -> MetaFit(tape = true, scale = 1f)
        needed <= column -> MetaFit(tape = false, scale = 1f)
        else -> MetaFit(tape = false, scale = (column / needed).coerceIn(MIN_META_SCALE, 1f))
    }
}

/** The tile's flip-book: [COUNT] drawings spread evenly over one loop of the clip. */
object WidgetFrames {
    const val COUNT = 16
    /** A flip faster than this is wasted on a widget; slower than this, the figure stutters instead of moving. */
    const val MIN_INTERVAL_MS = 60
    const val MAX_INTERVAL_MS = 400

    /** How long each drawing shows, so [frames] of them last about one [cycleMs] loop. */
    fun intervalMs(cycleMs: Long, frames: Int = COUNT): Int =
        if (frames <= 0 || cycleMs <= 0) MAX_INTERVAL_MS
        else (cycleMs.toDouble() / frames).roundToInt().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    /** Where in the loop drawing [index] sits, starting from the clip's own [start] pose. */
    fun phase(index: Int, start: Float, frames: Int = COUNT): Float {
        val raw = start + index.toFloat() / frames.coerceAtLeast(1)
        return raw - kotlin.math.floor(raw)
    }
}
