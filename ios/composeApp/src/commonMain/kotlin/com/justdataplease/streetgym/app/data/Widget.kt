package com.justdataplease.streetgym.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.all_complete_title
import com.justdataplease.streetgym.app.resources.app_name
import com.justdataplease.streetgym.app.resources.streak_days
import com.justdataplease.streetgym.app.resources.widget_all_done_detail
import com.justdataplease.streetgym.app.resources.widget_done
import com.justdataplease.streetgym.app.resources.widget_level_class
import com.justdataplease.streetgym.app.resources.widget_level_class_spoken
import com.justdataplease.streetgym.app.resources.widget_locked
import com.justdataplease.streetgym.app.resources.widget_minutes
import com.justdataplease.streetgym.app.resources.widget_next
import com.justdataplease.streetgym.app.resources.widget_open_app
import com.justdataplease.streetgym.app.resources.widget_open_class
import com.justdataplease.streetgym.app.resources.widget_targets
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.ui.FlameState
import com.justdataplease.streetgym.app.ui.flameColors
import com.justdataplease.streetgym.app.ui.flameState
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Streak
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/*
 * The "Next class" home-screen widget (android-streetgym, widget/): what it shows and when it is written are decided
 * here, in common Kotlin; on iPhone Swift only draws it (iosApp/StreetGymWidget) from the snapshot the app writes into
 * the App Group's folder, and the desktop draws the same snapshot (ui/WidgetCard.kt).
 */

/**
 * What the widget shows: the next class in the sequence, never a date (Android: widget/NextClassCard.kt, line for
 * line). A class title reads "Role · Lead"; the role is the headline and the lead the main part's first step that moves.
 * A recovery day names its theme after the dot; an exam lists how many targets it holds.
 */
data class NextClassCard(
    /** The class the tap opens; null opens Today (every class done, or the strength test still to answer). */
    val programId: String?,
    val levelNumber: Int,
    /** The class's number within its level, as Today counts it ("Class 20"). */
    val classNumber: Int,
    val kind: Kind,
    /** The title before " · ", or the whole title when it has none. */
    val role: String,
    /** The third line before the minutes: the lead step's name, or a recovery day's theme. Empty on an exam. */
    val detail: String,
    /** An exam's targets (its assessment sets); 0 otherwise. */
    val targets: Int,
    val minutes: Int?,
    /** The movement the tile shows: the main part's first step that moves. */
    val movementId: String?,
    val movementName: String,
    /** The class's level is beyond the athlete's tier; the tap still opens it and the app shows the offer. */
    val locked: Boolean,
    /** The lead step's category and equipment, which pick its clip when it has no movement id. */
    val category: String? = null,
    val equipment: List<String> = emptyList(),
) {
    enum class Kind { CLASS, RECOVERY, EXAM, ALL_DONE }

    companion object {
        const val SEPARATOR = " · "
        private const val RECOVERY = "recovery"

        /**
         * The card for [program]; [opens] false sends the tap to Today instead of the class. [moves] says whether an
         * exercise's clip moves; a class of holds only leads with its first.
         */
        fun of(program: TrainingProgram, locked: Boolean, opens: Boolean = true, moves: (TrainingExercise) -> Boolean = { true }): NextClassCard {
            val title = program.name.resolve().trim()
            val split = title.indexOf(SEPARATOR)
            val role = if (split > 0) title.substring(0, split).trim() else title
            val named = if (split > 0) title.substring(split + SEPARATOR.length).trim() else ""
            val lead = lead(program, moves)
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
                targets = if (kind == Kind.EXAM) program.tests.size else 0,
                minutes = program.estimatedMinutes,
                movementId = lead?.movementId,
                movementName = leadName,
                locked = locked,
                category = lead?.category,
                equipment = lead?.equipment.orEmpty(),
            )
        }

        /** Every class is done: the last class's lead stays on the tile, and the tap opens Today. */
        fun allDone(last: TrainingProgram?, moves: (TrainingExercise) -> Boolean = { true }): NextClassCard {
            val lead = last?.let { lead(it, moves) }
            return NextClassCard(null, last?.levelNumber ?: 0, last?.number ?: 0, Kind.ALL_DONE, "", "", 0, null,
                lead?.movementId, lead?.name?.resolve().orEmpty(), locked = false, category = lead?.category,
                equipment = lead?.equipment.orEmpty())
        }

        private fun lead(program: TrainingProgram, moves: (TrainingExercise) -> Boolean): TrainingExercise? =
            program.exercises.firstOrNull(moves) ?: program.exercises.firstOrNull()
    }
}

/**
 * The words the widget uses, as written in Android's string files (strings_widget.xml, all_complete_title, app_name,
 * streak_days), with their places still in them; [WidgetText] fills them. Read once by [load].
 */
class WidgetWords(
    val next: String,
    val done: String,
    val levelClass: String,
    val levelClassSpoken: String,
    val minutes: String,
    val targetOne: String,
    val targetOther: String,
    val allDoneTitle: String,
    val allDoneDetail: String,
    val locked: String,
    val openClass: String,
    val openApp: String,
    val appName: String,
    val streakDays: String,
) {
    companion object {
        suspend fun load(): WidgetWords = WidgetWords(
            next = getString(Res.string.widget_next),
            done = getString(Res.string.widget_done),
            levelClass = getString(Res.string.widget_level_class),
            levelClassSpoken = getString(Res.string.widget_level_class_spoken),
            minutes = getString(Res.string.widget_minutes),
            targetOne = getPluralString(Res.plurals.widget_targets, 1),
            targetOther = getPluralString(Res.plurals.widget_targets, 2),
            allDoneTitle = getString(Res.string.all_complete_title),
            allDoneDetail = getString(Res.string.widget_all_done_detail),
            locked = getString(Res.string.widget_locked),
            openClass = getString(Res.string.widget_open_class),
            openApp = getString(Res.string.widget_open_app),
            appName = getString(Res.string.app_name),
            streakDays = getString(Res.string.streak_days),
        )

        /** [template] with its numbered places (%1$d, %2$s) filled by [args] in order. */
        fun fill(template: String, vararg args: Any): String {
            var text = template
            args.forEachIndexed { index, value ->
                val place = index + 1
                text = text.replace("%$place\$d", value.toString()).replace("%$place\$s", value.toString())
            }
            return text
        }
    }
}

/**
 * The card's words (Android: WidgetRenderer.WidgetText): the tape, "LEVEL 4 · CLASS 20", the headline, the third line,
 * and what VoiceOver reads for the whole card ([spoken], once per flame, as the streak's days are part of it).
 */
data class WidgetText(val tape: String, val meta: String, val headline: String, val detail: String) {
    companion object {
        fun of(card: NextClassCard, words: WidgetWords): WidgetText {
            if (card.kind == NextClassCard.Kind.ALL_DONE) return WidgetText(words.done, "", words.allDoneTitle, words.allDoneDetail)
            val meta = WidgetWords.fill(words.levelClass, card.levelNumber, card.classNumber)
            val detail = parts(card, words).joinToString(NextClassCard.SEPARATOR)
            val headline = card.role.ifBlank { card.movementName }.ifBlank { words.appName }
            return WidgetText(words.next, meta.uppercase(), headline, detail)
        }

        /** The third line's parts: the lead step or the exam's targets, then the minutes. */
        private fun parts(card: NextClassCard, words: WidgetWords): List<String> {
            val minutes = card.minutes?.let { WidgetWords.fill(words.minutes, it) }
            val first = when (card.kind) {
                NextClassCard.Kind.EXAM -> card.targets.takeIf { it > 0 }
                    ?.let { WidgetWords.fill(if (it == 1) words.targetOne else words.targetOther, it) }
                else -> card.detail
            }
            return listOfNotNull(first?.takeIf(String::isNotBlank), minutes)
        }

        /** "Next class: Level 4, class 20, Basics & Endurance, Strict Pull-Up, 44 min, 12 day streak. Opens the class." */
        fun spoken(card: NextClassCard, streak: Streak, words: WidgetWords): String {
            val days = WidgetWords.fill(words.streakDays, streak.days).takeIf { streak.days > 0 }
            if (card.kind == NextClassCard.Kind.ALL_DONE) {
                return WidgetWords.fill(words.openApp, listOfNotNull(words.allDoneTitle, words.allDoneDetail, days).joinToString(", "))
            }
            val headline = card.role.ifBlank { card.movementName }.ifBlank { words.appName }
            // Spoken with commas, not the dots VoiceOver would read out.
            val summary = (listOf(WidgetWords.fill(words.levelClassSpoken, card.levelNumber, card.classNumber), headline) +
                parts(card, words) + listOfNotNull(words.locked.takeIf { card.locked }, days)).joinToString(", ")
            return WidgetWords.fill(if (card.programId != null) words.openClass else words.openApp, summary)
        }
    }
}

/** One day's flame on the card, shown from [from] (epoch millis, local midnight) until the next one starts. */
data class WidgetFlame(val from: Long, val streak: Streak, val spoken: String) {
    val state: FlameState get() = flameState(streak)
}

/**
 * Everything the widget draws, for one moment of the app: the [card] in [theme] with its words, where a tap goes
 * ([link]) and the flame of today and the next days ([flames]): Android redraws the widget as each day starts, which an
 * iPhone widget can't do by running the app's code, so the coming days' flames are worked out now (the streak changes
 * only with a workout, which writes the snapshot again).
 */
data class WidgetSnapshot(
    val theme: StreetTheme,
    val card: NextClassCard,
    val text: WidgetText,
    val link: String,
    val flames: List<WidgetFlame>,
) {
    /** The theme's display face, which the widget reads from the App Group's folder (font/<file> in the app). */
    val fontFile: String get() = displayFontFile(theme)

    /**
     * The snapshot as JSON for Swift (iosApp/StreetGymWidget): the words ready to draw, the theme's colours as
     * #AARRGGBB, its shape (outlines, capitals, the tape's spacing, the headline's size), the figure's and the face's
     * file names, the flames with their colours, and the link.
     */
    fun encode(): String {
        val palette = theme.palette
        val colors = JSONObject()
            .put("page", hex(palette.background))
            .put("surface", hex(palette.surface))
            .put("border", hex(palette.border))
            .put("shadow", hex(if (theme == StreetTheme.WHEATPASTE) palette.spot else palette.border))
            .put("tile", hex(palette.infoSurface))
            .put("text", hex(palette.text))
            .put("muted", hex(palette.textSecondary))
            .put("tape", hex(palette.accent))
            .put("onTape", hex(palette.contentOn(palette.accent)))
            .put("action", hex(palette.action))
            .put("onAction", hex(palette.contentOn(palette.action)))
        val flameArray = JSONArray()
        flames.forEach { flame ->
            val look = flameColors(theme, flame.state)
            flameArray.put(JSONObject()
                .put("from", flame.from)
                .put("days", flame.streak.days)
                .put("state", flame.state.name.lowercase())
                .put("spoken", flame.spoken)
                .put("body", hex(look.body))
                .put("core", hex(look.core))
                .put("outline", look.outline?.let(::hex) ?: JSONObject.NULL)
                .put("shadow", look.shadow?.let(::hex) ?: JSONObject.NULL)
                .put("number", hex(look.number))
                .put("dashed", look.dashed))
        }
        return JSONObject()
            .put("v", VERSION)
            .put("theme", theme.key)
            .put("colors", colors)
            .put("upper", theme.shape.upperTitles)
            .put("tapeSpacing", theme.shape.labelSpacing.toDouble())
            .put("titleSize", (TITLE_PT * theme.shape.titleScale / TITLE_SCALE_BASE).toDouble())
            .put("digits", displayDigits(theme).toDouble())
            .put("font", fontFile)
            .put("figure", FIGURE_FILE)
            .put("tape", text.tape)
            .put("meta", text.meta)
            .put("headline", text.headline)
            .put("detail", text.detail)
            .put("locked", card.locked)
            .put("link", link)
            .put("flames", flameArray)
            .toString()
    }

    companion object {
        const val VERSION = 1
        const val FIGURE_FILE = "figure.png"
        /** Concrete's headline size (Android: WidgetLayout.TITLE_DP); the others follow their theme's title scale. */
        const val TITLE_PT = 19f
        const val TITLE_SCALE_BASE = 1.15f
        /** The figure's tile: 72 points inside its 2-point outline (Android: WidgetLayout.FIGURE_DP), drawn at 3x. */
        const val FIGURE_PT = 72
        const val FIGURE_SCALE = 3f
        /** How many days of flames the snapshot carries: today and the next three, by when any streak is out. */
        const val FLAME_DAYS = 4

        fun displayFontFile(theme: StreetTheme): String = when (theme) {
            StreetTheme.CONCRETE -> "anton_regular.ttf"
            StreetTheme.NIGHT_PARK -> "big_shoulders_black.ttf"
            StreetTheme.WHEATPASTE -> "archivo_black_regular.ttf"
        }

        /** How tall each theme's display face draws its digits, in em (ui/StreakViews.kt). */
        fun displayDigits(theme: StreetTheme): Float = when (theme) {
            StreetTheme.CONCRETE -> 0.884f
            StreetTheme.NIGHT_PARK -> 0.82f
            StreetTheme.WHEATPASTE -> 0.712f
        }

        private fun hex(color: Color): String {
            val argb = color.toArgb().toLong() and 0xFFFFFFFFL
            return "#" + argb.toString(16).uppercase().padStart(8, '0')
        }

        /**
         * The flames from the local day of [now] for [FLAME_DAYS] days, from the local days with a workout ([trained]);
         * a day whose flame is the same as the day before's adds nothing.
         */
        fun flames(trained: Set<Long>, now: Long, zone: TimeZone, card: NextClassCard, words: WidgetWords): List<WidgetFlame> {
            val today = LocalDays.epochDay(now, zone)
            val days = (0 until FLAME_DAYS).map { offset ->
                val day = today + offset
                val streak = Streak.of(trained, day)
                WidgetFlame(LocalDate.fromEpochDays(day.toInt()).atStartOfDayIn(zone).toEpochMilliseconds(), streak,
                    WidgetText.spoken(card, streak, words))
            }
            return days.filterIndexed { index, flame ->
                index == 0 || days[index - 1].let { it.streak.days != flame.streak.days || it.state != flame.state }
            }
        }
    }
}

/** The widget's tap (Android: NextClassWidget.ACTION_OPEN with its program): a class's page, or Today. */
object WidgetLinks {
    const val SCHEME = "streetgym"
    const val TODAY = "$SCHEME://today"
    private const val CLASS = "$SCHEME://class/"

    fun of(programId: String?): String = if (programId == null) TODAY else CLASS + encode(programId)

    /** What a link opens: a class by its id, [TODAY], or null for a link that isn't the widget's. */
    fun parse(url: String): Opening? = when {
        url == TODAY || url == "$TODAY/" -> Opening.Today
        url.startsWith(CLASS) -> decode(url.removePrefix(CLASS)).takeIf(String::isNotBlank)?.let(Opening::Class)
        else -> null
    }

    /** A class id ("sg:l04:p0020", "ready:neck_at_the_desk", "custom:…") written so a URL keeps it. */
    private fun encode(id: String): String = buildString {
        id.forEach { char ->
            if (char.isLetterOrDigit() && char.code < 128 || char in "-._~:") append(char)
            else char.toString().encodeToByteArray().forEach { byte ->
                append('%').append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }

    private fun decode(text: String): String {
        val bytes = ArrayList<Byte>()
        var index = 0
        while (index < text.length) {
            val hex = if (text[index] == '%' && index + 2 < text.length) text.substring(index + 1, index + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.add(hex.toByte())
                index += 3
            } else {
                text[index].toString().encodeToByteArray().forEach(bytes::add)
                index++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    /** Where a link asks the app to go. */
    sealed interface Opening {
        data object Today : Opening
        data class Class(val programId: String) : Opening
    }
}

/**
 * Where the widget's snapshot goes: on iPhone Swift writes it into the App Group's folder and asks WidgetKit to draw
 * the widget again (iosMain IosWidget.kt, iosApp/StreetGym/WidgetBridge.swift); [None] where there is no widget.
 * [figure] is the tile's drawing, given only when it changed; [font] the display face's file, given with a new theme.
 */
interface HomeWidget {
    fun save(snapshot: String, figure: ImageBitmap?, fontFile: String?, font: ByteArray?)

    object None : HomeWidget {
        override fun save(snapshot: String, figure: ImageBitmap?, fontFile: String?, font: ByteArray?) {}
    }
}

/**
 * When the widget is written (Android: WidgetUpdates, its `shown` check): whenever what it shows changes (a finished,
 * completed or placed class, a replay, a reset, the switches, the tier, the theme, a new day), and never for a change
 * it doesn't show (a set ticked mid-class). The figure is drawn again only for a new lead movement or theme, and the
 * face is handed over only for a new theme.
 */
class WidgetFeed(private val widget: HomeWidget) {
    private var shown: String? = null
    private var figureKey: Any? = null
    private var fontShown: String? = null

    /** Writes [snapshot] if it differs from the last one written; [figure] draws the tile, [font] reads a face. */
    suspend fun offer(snapshot: WidgetSnapshot, figure: () -> ImageBitmap?, font: suspend (String) -> ByteArray?): Boolean {
        val json = snapshot.encode()
        if (json == shown) return false
        val key = listOf(snapshot.theme, snapshot.card.movementId, snapshot.card.movementName, snapshot.card.category)
        val picture = if (key != figureKey) figure() else null
        val file = snapshot.fontFile
        val face = if (file != fontShown) runCatching { font(file) }.getOrNull() else null
        widget.save(json, picture, file.takeIf { face != null }, face)
        shown = json
        if (picture != null) figureKey = key
        if (face != null) fontShown = file
        return true
    }
}
