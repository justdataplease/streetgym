package com.justdataplease.streetgym.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.justdataplease.streetgym.account.Cloud
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.ReadyWorkouts
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.ui.AppSettings

/** The four Consent Mode v2 types the app sets (Firebase's ConsentType, without Android). */
enum class ConsentKind { AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION, ANALYTICS_STORAGE }

/** Where [GoogleAnalytics] goes: Firebase Analytics in the app ([FirebaseAnalyticsSink]), a fake in tests. */
interface AnalyticsSink {
    fun setConsent(consent: Map<ConsentKind, Boolean>)
    fun setCollectionEnabled(enabled: Boolean)
    fun log(event: String, params: Map<String, Any>)
    fun userProperty(name: String, value: String?)
}

/**
 * Google Analytics 4 through Firebase Analytics (the owner, 26 Nov 2026): the only place the app talks to it. Every event,
 * parameter and user property is named in the companion; values are short codes and numbers, never a name, email,
 * username or free text. Firebase logs `in_app_purchase` (Google Play) and, with AdMob linked, `ad_impression` by
 * itself, so the app logs neither.
 *
 * Consent ([AnalyticsConsent]): the manifest starts with collection off and every consent type denied, so nothing is
 * sent before the consent state is known (Google's basic consent mode). [consentUpdated] passes the state decided from
 * Google's consent form; collection runs only while analytics_storage is granted and "Share my progress" is on
 * ([sharingChanged]). Events and user properties are dropped while it is off. Without Firebase ([sink] null), as in unit
 * tests and builds without a Firebase config, every call does nothing.
 */
class GoogleAnalytics(
    private val sink: AnalyticsSink?,
    private val preferences: SharedPreferences?,
    private val sharing: () -> Boolean,
) {
    /** What Firebase was last told: null before [start]. */
    private var collectionOn: Boolean? = null
    private var consent: Map<ConsentKind, Boolean>? = null
    /** The screen on view and its key ([screen]); sent again when collection starts during a session. */
    private var screenName: String? = null
    private var screenKey: Any? = null
    private val userProperties = LinkedHashMap<String, String?>()

    /** analytics_storage as last decided ([consentUpdated]); denied until a decision. */
    val storageGranted: Boolean get() = preferences?.getBoolean(KEY_STORAGE, false) ?: false

    /** Whether the build has Firebase: without it nothing here does anything. */
    val available: Boolean get() = sink != null

    /** Whether events go to Firebase now. */
    val collecting: Boolean get() = collectionOn == true

    /** The app opened: collection follows the saved consent and the switch (Firebase keeps both between launches too). */
    fun start() = syncCollection(sharing())

    /**
     * The consent state decided from Google's consent form ([AnalyticsConsent.decide]); null leaves everything as it is
     * (the form's state isn't known yet). The same state again changes nothing.
     */
    fun consentUpdated(consent: Map<ConsentKind, Boolean>?) {
        val sink = sink ?: return
        if (consent == null || consent == this.consent) return
        this.consent = consent
        sink.setConsent(consent)
        preferences?.edit()?.putBoolean(KEY_STORAGE, consent[ConsentKind.ANALYTICS_STORAGE] == true)?.apply()
        syncCollection(sharing())
    }

    /** "Share my progress" switched: off stops Google Analytics too, on starts it again where consent allows. */
    fun sharingChanged(on: Boolean) = syncCollection(on)

    private fun syncCollection(sharingOn: Boolean) {
        val sink = sink ?: return
        val on = sharingOn && storageGranted
        if (on == collectionOn) return
        collectionOn = on
        sink.setCollectionEnabled(on)
        // On during a session (the consent form was just answered, or the switch turned on): who is looking, and what.
        if (on) {
            userProperties.forEach { (name, value) -> sink.userProperty(name, value) }
            screenName?.let { sink.log(SCREEN_VIEW, mapOf(SCREEN_NAME to it)) }
        }
    }

    private fun log(event: String, params: Map<String, Any> = emptyMap()) {
        if (collectionOn == true) sink?.log(event, params)
    }

    /**
     * A screen or tab came into view; [key] tells a redraw of the same screen (not logged again) from a new one with the
     * same name, such as another class's overview.
     */
    fun screen(name: String, key: Any = name) {
        if (key == screenKey && name == screenName) return
        screenKey = key
        screenName = name
        log(SCREEN_VIEW, mapOf(SCREEN_NAME to name))
    }

    /** The first count typed in the strength test; once until the test is done ([tutorialComplete]). */
    fun tutorialBegin() {
        if (preferences == null || preferences.getBoolean(KEY_TUTORIAL_BEGUN, false)) return
        preferences.edit().putBoolean(KEY_TUTORIAL_BEGUN, true).apply()
        log(TUTORIAL_BEGIN)
    }

    /** The strength test's start was taken: [method] is [METHOD_TEST], [METHOD_SKILL] or [METHOD_SKIP]. */
    fun tutorialComplete(level: Int, classNumber: Int, method: String) {
        preferences?.edit()?.remove(KEY_TUTORIAL_BEGUN)?.apply()
        log(TUTORIAL_COMPLETE, mapOf(START_LEVEL to level.toLong(), START_CLASS to classNumber.toLong(), METHOD to method))
    }

    fun classStart(program: TrainingProgram, intensity: Intensity, repeat: Boolean) =
        log(CLASS_START, classParams(program, intensity, repeat))

    fun classComplete(program: TrainingProgram, intensity: Intensity, repeat: Boolean, durationSeconds: Long) =
        log(CLASS_COMPLETE, classParams(program, intensity, repeat) + (DURATION_MIN to durationSeconds.coerceAtLeast(0) / 60))

    fun examComplete(program: TrainingProgram, passed: Boolean) = log(EXAM_COMPLETE, mapOf(
        LEVEL to program.levelNumber.toLong(), CLASS_NUMBER to program.number.toLong(), PASSED to passed.toString()))

    /** A skill reached [rank] with a finished class; Beginner up. */
    fun skillRankUp(skillId: String, rank: SkillLevel) {
        if (rank == SkillLevel.NONE) return
        log(SKILL_RANK_UP, mapOf(SKILL_ID to skillId, RANK to rank.name.lowercase()))
    }

    /** The Plus or Pro offer opened over a screen: [source] what the athlete tapped, [tier] the plan it leads with. */
    fun paywallView(source: String, tier: Tier) = log(PAYWALL_VIEW, mapOf(SOURCE to source, TIER to tier.code))

    /** A buy button on an offer: [plan] is [PLAN_PLUS], [PLAN_PRO_LIFE] or [PLAN_PRO_YEARLY]. */
    fun paywallTap(source: String, plan: String) =
        log(PAYWALL_TAP, mapOf(SOURCE to source, TIER to (if (plan == PLAN_PLUS) Tier.PLUS else Tier.PRO).code, PLAN to plan))

    /** Who is looking: the tier, the level of the next class (1-10; null when every class is done) and the theme's key. */
    fun userProperties(tier: Tier, level: Int?, theme: String) {
        setUserProperty(TIER, tier.code)
        setUserProperty(LEVEL, level?.toString())
        setUserProperty(THEME, theme)
    }

    private fun setUserProperty(name: String, value: String?) {
        if (userProperties.containsKey(name) && userProperties[name] == value) return
        userProperties[name] = value
        if (collectionOn == true) sink?.userProperty(name, value)
    }

    companion object {
        // Events: GA4's recommended names where one fits (screen_view, tutorial_begin, tutorial_complete).
        const val SCREEN_VIEW = "screen_view"
        const val TUTORIAL_BEGIN = "tutorial_begin"
        const val TUTORIAL_COMPLETE = "tutorial_complete"
        const val CLASS_START = "class_start"
        const val CLASS_COMPLETE = "class_complete"
        const val EXAM_COMPLETE = "exam_complete"
        const val SKILL_RANK_UP = "skill_rank_up"
        const val PAYWALL_VIEW = "paywall_view"
        const val PAYWALL_TAP = "paywall_tap"

        // Parameters (screen_name and method are GA4's own).
        const val SCREEN_NAME = "screen_name"
        const val START_LEVEL = "start_level"
        const val START_CLASS = "start_class"
        const val METHOD = "method"
        const val WORKOUT_TYPE = "workout_type"
        const val LEVEL = "level"
        const val CLASS_NUMBER = "class_number"
        const val INTENSITY = "intensity"
        const val REPEAT = "repeat"
        const val DURATION_MIN = "duration_min"
        const val PASSED = "passed"
        const val SKILL_ID = "skill_id"
        const val RANK = "rank"
        const val SOURCE = "source"
        const val TIER = "tier"
        const val PLAN = "plan"

        // User properties: tier ([TIER]), level ([LEVEL]) and theme.
        const val THEME = "theme"

        // Values.
        const val METHOD_TEST = "test"
        const val METHOD_SKILL = "skill"
        const val METHOD_SKIP = "skip"
        const val TYPE_CLASS = "class"
        const val TYPE_EXAM = "exam"
        const val TYPE_OWN = "own"
        const val TYPE_LIBRARY = "library"
        const val PLAN_PLUS = "plus"
        const val PLAN_PRO_LIFE = "pro_life"
        const val PLAN_PRO_YEARLY = "pro_yearly"
        const val SOURCE_SETTINGS = "settings"

        private const val PREFERENCES_NAME = "streetgym_google_analytics"
        private const val KEY_STORAGE = "analytics_storage_granted"
        private const val KEY_TUTORIAL_BEGUN = "tutorial_begun"

        /** A program class, an exam, the athlete's own day or a Library workout. */
        fun workoutType(program: TrainingProgram): String = when {
            CustomDays.isCustom(program.id) -> TYPE_OWN
            ReadyWorkouts.isReady(program.id) -> TYPE_LIBRARY
            program.isExam -> TYPE_EXAM
            else -> TYPE_CLASS
        }

        /** A class's parameters: its type, the level and class number of a program day, the day's pick and a repeat. */
        fun classParams(program: TrainingProgram, intensity: Intensity, repeat: Boolean): Map<String, Any> = buildMap {
            put(WORKOUT_TYPE, workoutType(program))
            if (!CustomDays.offProgram(program.id)) {
                put(LEVEL, program.levelNumber.toLong())
                put(CLASS_NUMBER, program.number.toLong())
            }
            put(INTENSITY, intensity.name.lowercase())
            put(REPEAT, repeat.toString())
        }

        private val Tier.code: String get() = name.lowercase()

        @Volatile private var instance: GoogleAnalytics? = null
        private val NONE = GoogleAnalytics(null, null) { false }

        /** The app's one instance, or one that does nothing before [install] (unit tests, the widget). */
        fun get(): GoogleAnalytics = instance ?: NONE

        /**
         * Sets up the instance once, before Google's consent SDK starts (its consent mode needs Firebase first): Firebase
         * Analytics when the build has Firebase, else nothing at all.
         */
        fun install(context: Context): GoogleAnalytics = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                val settings = AppSettings(app)
                val firebase = if (Cloud.available(app)) runCatching { FirebaseAnalytics.getInstance(app) }.getOrNull() else null
                GoogleAnalytics(firebase?.let(::FirebaseAnalyticsSink), app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
                    { settings.shareProgress }).also { it.start() }
            }.also { instance = it }
        }
    }
}

/** Firebase Analytics behind [AnalyticsSink]. */
private class FirebaseAnalyticsSink(private val firebase: FirebaseAnalytics) : AnalyticsSink {
    override fun setConsent(consent: Map<ConsentKind, Boolean>) = firebase.setConsent(consent.entries.associate { (kind, granted) ->
        when (kind) {
            ConsentKind.AD_STORAGE -> FirebaseAnalytics.ConsentType.AD_STORAGE
            ConsentKind.AD_USER_DATA -> FirebaseAnalytics.ConsentType.AD_USER_DATA
            ConsentKind.AD_PERSONALIZATION -> FirebaseAnalytics.ConsentType.AD_PERSONALIZATION
            ConsentKind.ANALYTICS_STORAGE -> FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE
        } to if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
    })

    override fun setCollectionEnabled(enabled: Boolean) = firebase.setAnalyticsCollectionEnabled(enabled)

    override fun log(event: String, params: Map<String, Any>) = firebase.logEvent(event, Bundle().apply {
        params.forEach { (key, value) ->
            when (value) {
                is Long -> putLong(key, value)
                is Int -> putLong(key, value.toLong())
                is Double -> putDouble(key, value)
                else -> putString(key, value.toString())
            }
        }
    })

    override fun userProperty(name: String, value: String?) = firebase.setUserProperty(name, value)
}
