package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.ReadyWorkouts
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.skills.SkillLevel

/*
 * Google Analytics 4 in the app (android-streetgym 2.15.0, analytics/GoogleAnalytics.kt, line for line): the same
 * events, parameters, user properties and values, so Google Analytics reports both phones together. Firebase Analytics
 * is Swift on iPhone (iosApp/StreetGym/GoogleAnalyticsBridge.swift, through iosMain's IosAnalytics); nothing here is
 * Firebase's own. Android's one instance (GoogleAnalytics.get and install) is the model's here ([AppModel.googleAnalytics]).
 */

/** The four Consent Mode v2 types the app sets (Firebase's ConsentType, without the phone's SDK). */
enum class ConsentKind { AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION, ANALYTICS_STORAGE }

/** Where [GoogleAnalytics] goes: Firebase Analytics on iPhone (iosMain's IosAnalytics), a fake in tests. */
interface AnalyticsSink {
    fun setConsent(consent: Map<ConsentKind, Boolean>)
    fun setCollectionEnabled(enabled: Boolean)
    fun log(event: String, params: Map<String, Any>)
    fun userProperty(name: String, value: String?)
}

/**
 * Google Analytics 4 through Firebase Analytics (Android: GoogleAnalytics, the owner, 26 Nov 2026): the only place the
 * app talks to it. Every event, parameter and user property is named in the companion; values are short codes and
 * numbers, never a name, email, username or free text. Firebase logs `in_app_purchase` (the App Store) and, with AdMob
 * linked, `ad_impression` by itself, so the app logs neither.
 *
 * Consent ([AnalyticsConsent]): the Info.plist starts with collection off and every consent type denied (iosApp/
 * project.yml, as Android's manifest), so nothing is sent before the consent state is known (Google's basic consent
 * mode). [consentUpdated] passes the state decided from Google's consent form; collection runs only while
 * analytics_storage is granted and "Share my progress" is on ([sharingChanged]). Events and user properties are dropped
 * while it is off. Without Firebase ([sink] null), as in tests and builds without GoogleService-Info.plist, every call
 * does nothing. Kept under Android's names ("streetgym_google_analytics" › analytics_storage_granted, tutorial_begun) in
 * [store], a switch as "true".
 */
class GoogleAnalytics(
    private val sink: AnalyticsSink?,
    private val store: KeyValueStore?,
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
    val storageGranted: Boolean get() = store?.get(KEY_STORAGE)?.toBooleanStrictOrNull() ?: false

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
        store?.set(KEY_STORAGE, (consent[ConsentKind.ANALYTICS_STORAGE] == true).toString())
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
        if (store == null || store.get(KEY_TUTORIAL_BEGUN)?.toBooleanStrictOrNull() == true) return
        store.set(KEY_TUTORIAL_BEGUN, "true")
        log(TUTORIAL_BEGIN)
    }

    /** The strength test's start was taken: [method] is [METHOD_TEST], [METHOD_SKILL] or [METHOD_SKIP]. */
    fun tutorialComplete(level: Int, classNumber: Int, method: String) {
        store?.set(KEY_TUTORIAL_BEGUN, null)
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

        // Android's preferences "streetgym_google_analytics", with their name in front as the rest of the app keeps them.
        const val PREFIX = "streetgym_google_analytics."
        const val KEY_STORAGE = PREFIX + "analytics_storage_granted"
        const val KEY_TUTORIAL_BEGUN = PREFIX + "tutorial_begun"

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
    }
}

/**
 * What opened the offer, as Google Analytics names it ([GoogleAnalytics.paywallView]); Settings' plans for null
 * (Android: TiersPanel.analyticsSource).
 */
val Paywall?.analyticsSource: String get() = when (this) {
    null -> GoogleAnalytics.SOURCE_SETTINGS
    is Paywall.Level -> "level"
    Paywall.Parks -> "parks"
    Paywall.Leaderboard -> "leaderboard"
    Paywall.Ready -> "library"
    is Paywall.Saved -> "saved_limit"
    Paywall.Custom -> "own_workouts"
    is Paywall.Lists -> "lists"
    Paywall.Ads -> "remove_ads"
}
