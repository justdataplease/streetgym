package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.monetization.NativeAds

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import com.justdataplease.streetgym.timer.TimingAlerts
import com.justdataplease.streetgym.ads.WorkoutAdBreak
import com.justdataplease.streetgym.ads.ManagedNativePlacement
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.SavedLimit
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.billing.TierBilling
import com.justdataplease.streetgym.data.library.CustomDay
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.CustomExercise
import com.justdataplease.streetgym.data.library.CustomSet
import com.justdataplease.streetgym.data.library.ExerciseLibrary
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.library.LibraryMovement
import com.justdataplease.streetgym.data.library.LibraryRepository
import com.justdataplease.streetgym.account.AccountStore
import com.justdataplease.streetgym.account.Cloud
import com.justdataplease.streetgym.account.CloudSync
import com.justdataplease.streetgym.leaderboard.Board
import com.justdataplease.streetgym.leaderboard.Boards
import com.justdataplease.streetgym.leaderboard.Countries
import com.justdataplease.streetgym.leaderboard.LeaderboardRepository
import com.justdataplease.streetgym.leaderboard.Score
import com.justdataplease.streetgym.leaderboard.Scores
import com.justdataplease.streetgym.leaderboard.Standing
import com.justdataplease.streetgym.data.parks.NearbyPark
import com.justdataplease.streetgym.data.parks.Park
import com.justdataplease.streetgym.data.parks.Parks
import com.justdataplease.streetgym.data.parks.ParksUpdater
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.BaseAdapter
import android.widget.EditText
import org.json.JSONObject
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.DayAssignment
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.LevelReplay
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.OverallScore
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.data.skills.SkillGroup
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.data.skills.SkillFamilySpec
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff
import com.justdataplease.streetgym.data.skills.trained
import com.justdataplease.streetgym.widget.NextClassWidget
import java.text.DateFormat
import java.text.NumberFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Offline training shell with optional Firebase account access in Settings.
 */
open class HomeActivity : Activity() {
    private sealed interface Screen {
        data object Dashboard : Screen
        data object Levels : Screen
        data class Workouts(val levelNumber: Int) : Screen
        data class Overview(val programId: String) : Screen
        data class Active(val programId: String) : Screen
        /** The day builder for a custom day (new or saved); its unsaved state is [draft]. */
        data class EditDay(val dayId: String) : Screen
        data object Progress : Screen
        data object Settings : Screen
        data object Timing : Screen
    }

    private data class ExerciseSection(
        val key: String,
        val titleRes: Int,
        val exercises: List<TrainingExercise>,
    )

    private lateinit var settings: AppSettings
    /** Counts taps on the Settings version line for the hidden developer settings. */
    private val developerMenu by lazy { DeveloperMenu(this, settings) }
    private lateinit var progressRepository: SharedPreferencesProgressRepository
    private lateinit var library: LibraryRepository
    private var exerciseLibrary: ExerciseLibrary? = null
    /** Training's tabs: 0 Levels, 1 My workouts, 2 Parks. */
    private var trainingTab = 0
    /** The list open in My workouts (Plus); null for Favorites, everything the athlete keeps. */
    private var workoutListId: String? = null
    private var draft: CustomDay? = null
    /** The placement test's counts, one per test (-1 for none), kept through a redraw. */
    private var placementAnswers = IntArray(Placement.Test.entries.size) { -1 }
    /** The level test the athlete would pass, from the placement's skills step; 0 for none. */
    private val placementSkill = IntArray(1)
    private var parks: List<Park>? = null
    private var parksLoading = false
    private var parkOrigin: Pair<Double, Double>? = null
    private var parkQuery = ""
    private var parkResults: List<NearbyPark>? = null
    /** The Parks map once opened; null before that, after low memory, or where MapLibre can't start. */
    private var parksMap: ParksMap? = null
    /** The map is kept, stopped, while another screen is shown ([setBody]); the lifecycle calls leave it alone then. */
    private var parksMapHidden = false
    private var parksMapFailed = false
    private var parksGeoJson: Pair<List<Park>, String>? = null
    private var parksGeoJsonBuilding: List<Park>? = null
    private var parksLocateTried = false
    private var activityResumed = false
    /** Parks ranking and the map's GeoJSON: off the main thread, and not queued behind the parks download. */
    private val parksWorker = Executors.newSingleThreadExecutor()
    private lateinit var appRoot: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarBack: TextView
    private lateinit var toolbarMark: ImageView
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarAction: ImageView
    private lateinit var bodyContainer: FrameLayout
    private lateinit var bottomNavigation: LinearLayout

    private val worker = Executors.newSingleThreadExecutor()
    private val backStack = ArrayDeque<Screen>()
    private var catalog: CurriculumCatalog? = null
        set(value) {
            field = value
            // Every screen walks the day order and looks days up by id: sort and index them once per catalog.
            orderedPrograms = value?.let(::allPrograms).orEmpty()
            programIndex = orderedPrograms.associateBy(TrainingProgram::id)
        }
    private var orderedPrograms: List<TrainingProgram> = emptyList()
    private var programIndex: Map<String, TrainingProgram> = emptyMap()
    /** The Easier, Normal or Harder [catalog] is scaled to ([syncIntensity]). */
    private var scaledFor = Intensity.NORMAL
    private var skillBook: SkillBook? = null
    /** Progress's tabs: 0 Skills, 1 History, 2 Leaderboard. */
    private var progressTab = 0
    private val historyCalendar = HistoryCalendarState()
    private lateinit var accountStore: AccountStore
    private lateinit var cloudSync: CloudSync
    private lateinit var leaderboard: LeaderboardRepository
    private var boardSkill: String? = null
    private var boardInCountry = false
    private val cloudHandler = Handler(Looper.getMainLooper())
    // A burst of changes (a finished workout, My workouts edits) becomes one sync and at most one score write.
    private val cloudCatchUp = Runnable { cloudSync.sync(force = true, done = ::afterSync) }
    private var skillGroup: SkillGroup? = null
    private var currentScreen: Screen? = null
    private var restTimer: CountDownTimer? = null
    private var restEndsAtEpochMillis: Long? = null
    private var restProgramId: String? = null
    private var restStatusView: TextView? = null
    private var toneGenerator: ToneGenerator? = null
    private var restoredScreen: Screen? = null
    private lateinit var billing: TierBilling
    private var tier = Tier.FREE
    // Purchase, refund, restore or the debug switch: redraw gated screens. Held strongly for SharedPreferences.
    private val entitlementChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val access = Entitlement.tier(this)
        if (access != tier) {
            tier = access
            currentScreen?.takeIf { it != Screen.Settings && it != Screen.Progress && it != Screen.Timing }
                ?.let { showScreen(it, pushCurrent = false) }
        }
    }
    private lateinit var adBreak: WorkoutAdBreak
    private var timingPanel: TimingPanel? = null
    private var timingDialog: AlertDialog? = null
    private val timingHandler = Handler(Looper.getMainLooper())
    private val timingTick = object : Runnable {
        override fun run() {
            adBreak.tick()
            val state = TimingAlerts.checkDue(this@HomeActivity)
            timingPanel?.refresh(state)
            if ((state.timerRinging || state.alarmRinging) && timingDialog == null && !isFinishing) {
                if (!TimingAlerts.notifications(this@HomeActivity)) TimingAlerts.tone()
                timingDialog = AlertDialog.Builder(this@HomeActivity)
                    .setTitle(if (state.alarmRinging) "Alarm" else "Time's up")
                    .setPositiveButton("Stop") { _, _ -> TimingAlerts.stop(this@HomeActivity); timingDialog = null }
                    .setOnCancelListener { TimingAlerts.stop(this@HomeActivity); timingDialog = null }
                    .create().also { it.show() }
            }
            if (!state.timerRinging && !state.alarmRinging) { timingDialog?.dismiss(); timingDialog = null }
            // Tenths only show on the Timer screen; elsewhere a second is soon enough for a due alert or an ad break.
            timingHandler.postDelayed(this, if (timingPanel != null) 100 else 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = AppSettings(this)
        AppColors.configure(settings.theme)
        AppType.init(this)
        setTheme(settings.theme.style)
        super.onCreate(savedInstanceState)
        progressRepository = SharedPreferencesProgressRepository(this)
        library = LibraryRepository(this)
        accountStore = AccountStore(this)
        cloudSync = CloudSync(this, progressRepository, library)
        leaderboard = LeaderboardRepository(this)
        library.onChange = ::cloudChanged
        draft = savedInstanceState?.getString(STATE_DRAFT)?.let { runCatching { LibraryCodec.decodeDay(JSONObject(it)) }.getOrNull() }
        trainingTab = savedInstanceState?.getInt(STATE_TRAINING_TAB) ?: 0
        workoutListId = savedInstanceState?.getString(STATE_WORKOUT_LIST)
        savedInstanceState?.getIntArray(STATE_PLACEMENT)?.takeIf { it.size == placementAnswers.size }?.let { placementAnswers = it }
        billing = TierBilling(this)
        tier = Entitlement.tier(this)
        Entitlement.prefs(this).registerOnSharedPreferenceChangeListener(entitlementChanged)
        // The home-screen widget follows progress, the tier and the theme from here on.
        NextClassWidget.watch(this)
        adBreak = WorkoutAdBreak(this)
        restoredScreen = savedInstanceState?.let(::screenFromBundle)
            ?: if (intent.getBooleanExtra(TimingAlerts.OPEN_TIMER, false)) Screen.Timing
            else NextClassWidget.programToOpen(intent)?.let(Screen::Overview)
        intent.removeExtra(TimingAlerts.OPEN_TIMER)
        intent.removeExtra(NextClassWidget.EXTRA_PROGRAM)
        restEndsAtEpochMillis = savedInstanceState?.getLong(STATE_REST_END)
            ?.takeIf { it > System.currentTimeMillis() }
        restProgramId = savedInstanceState?.getString(STATE_REST_PROGRAM)

        configureWindow()
        buildShell()
        renderLoading()
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        billing.connect()
        // Pro: catch up with other devices at most every six hours; nothing at all without the account.
        cloudSync.sync(done = ::afterSync)
        adBreak.resume()
        TimingAlerts.schedule(this)
        timingHandler.removeCallbacks(timingTick)
        timingHandler.post(timingTick)
        activityResumed = true
        parksMap?.takeUnless { parksMapHidden }?.resume()
    }

    override fun onPause() {
        activityResumed = false
        parksMap?.pause()
        adBreak.pause()
        timingHandler.removeCallbacks(timingTick)
        timingDialog?.dismiss(); timingDialog = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(TimingAlerts.OPEN_TIMER, false)) {
            if (catalog != null) openRoot(Screen.Timing) else restoredScreen = Screen.Timing
        }
        // A tap on the widget: the next class's overview, as Today's card opens it (or Today itself).
        if (NextClassWidget.opened(intent)) {
            val screen = NextClassWidget.programToOpen(intent)?.let(Screen::Overview) ?: Screen.Dashboard
            if (catalog != null) openRoot(screen) else restoredScreen = screen
        }
    }

    private fun enableTimingNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 710)
        } else runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun enableExactTiming() {
        if (Build.VERSION.SDK_INT >= 31) runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }.onFailure { Toast.makeText(this, "Enable alarms in app settings", Toast.LENGTH_SHORT).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        (currentScreen ?: restoredScreen)?.let { screen ->
            outState.putString(STATE_SCREEN, screen.javaClass.simpleName)
            when (screen) {
                is Screen.Workouts -> {
                    outState.putInt(STATE_LEVEL, screen.levelNumber)
                }
                is Screen.Overview -> outState.putString(STATE_PROGRAM, screen.programId)
                is Screen.Active -> outState.putString(STATE_PROGRAM, screen.programId)
                is Screen.EditDay -> outState.putString(STATE_PROGRAM, screen.dayId)
                else -> Unit
            }
        }
        draft?.let { outState.putString(STATE_DRAFT, LibraryCodec.encodeDay(it).toString()) }
        outState.putInt(STATE_TRAINING_TAB, trainingTab)
        workoutListId?.let { outState.putString(STATE_WORKOUT_LIST, it) }
        outState.putIntArray(STATE_PLACEMENT, placementAnswers)
        restEndsAtEpochMillis?.let { outState.putLong(STATE_REST_END, it) }
        restProgramId?.let { outState.putString(STATE_REST_PROGRAM, it) }
    }

    /**
     * Plus sees the boards; Pro also appears on them. A board is one cached document ([LeaderboardRepository]); the
     * athlete's place comes from that same document, so this screen costs at most one read per board every six hours.
     */
    private fun renderLeaderboard(page: LinearLayout, book: SkillBook) {
        val tier = Entitlement.tier(this)
        page.addWithMargins(bodyText(getString(R.string.leaderboard_intro), 14f), topDp = 12)
        if (!tier.leaderboard) {
            page.addWithMargins(TiersPanel(this, billing, Paywall.Leaderboard), topDp = 12)
            return
        }
        if (!Cloud.available(this)) {
            page.addWithMargins(card().apply { addView(bodyText(getString(R.string.leaderboard_unavailable))) }, topDp = 12)
            return
        }
        val uid = Cloud.uid(this)?.takeIf { tier.account }
        val profile = uid?.let(accountStore::profile)
        val country = profile?.country ?: Locale.getDefault().country.takeIf(Countries::valid)
        if (country != null) page.addWithMargins(segmentedSwitch(listOf(getString(R.string.leaderboard_world),
            "${Countries.flag(country)} ${Countries.name(country)}"), if (boardInCountry) 1 else 0) {
            boardInCountry = it == 1; renderProgress()
        }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 12)
        val families = book.families.map { it.spec }
        val boardName = families.firstOrNull { it.id == boardSkill }?.name ?: run { boardSkill = null; getString(R.string.leaderboard_overall) }
        page.addWithMargins(secondaryButton(getString(R.string.leaderboard_board, boardName)) {
            val choices = listOf<Pair<String?, String>>(null to getString(R.string.leaderboard_overall)) + families.map { it.id to it.name }
            AlertDialog.Builder(this).setTitle(R.string.leaderboard_pick)
                .setItems(choices.map { it.second }.toTypedArray()) { _, which -> boardSkill = choices[which].first; renderProgress() }
                .setNegativeButton(R.string.cancel, null).show()
        }, topDp = 10)
        val id = Boards.id(boardSkill, country?.takeIf { boardInCountry })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addWithMargins(holder, topDp = 12)
        page.addWithMargins(bodyText(getString(R.string.leaderboard_how), 12f), topDp = 14)
        val score = if (uid != null && profile != null) currentScore(book).let { if (boardSkill == null) it.overall else it.families[boardSkill] ?: 0 } else 0
        // Family boards rank ten times the family's raw value; they read on the same scale as Skills, out of 100.
        val scoreMax = OverallScore.scoreMax(book.families)
        val overallBoard = boardSkill == null
        fun shown(points: Int): String = scoreText(if (overallBoard || scoreMax == 0) points else points * 100.0 / scoreMax)
        fun show(board: Board?, loading: Boolean) {
            holder.removeAllViews()
            if (board == null) { holder.addView(bodyText(getString(if (loading) R.string.leaderboard_loading else R.string.leaderboard_offline), 14f)); return }
            holder.addView(card().apply {
                when {
                    uid == null && tier.account -> addView(bodyText(getString(R.string.leaderboard_sign_in), 14f))
                    !tier.account -> {
                        addView(bodyText(getString(R.string.leaderboard_join), 14f))
                        addWithMargins(secondaryButton(getString(R.string.pro_buy)) { showPaywall(Paywall.Account) }, topDp = 8)
                    }
                    profile == null -> addView(bodyText(getString(R.string.account_pick_name), 14f))
                    !profile.visible -> addView(bodyText(getString(R.string.leaderboard_hidden), 14f))
                    else -> addView(titleText(when (val standing = Boards.standing(board, profile.username, score)) {
                        is Standing.Rank -> getString(R.string.leaderboard_you_rank, standing.rank, shown(score))
                        is Standing.TopPercent -> getString(R.string.leaderboard_you_top, standing.percent, shown(score))
                        null -> getString(R.string.leaderboard_you_none)
                    }, 16f))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            if (board.entries.isEmpty()) { holder.addView(bodyText(getString(R.string.leaderboard_empty), 14f)); return }
            board.entries.forEachIndexed { index, entry ->
                val mine = profile != null && entry.username == profile.username
                holder.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = roundedDrawable(if (mine) AppColors.INFO_SURFACE else AppColors.SURFACE, dp(14).toFloat())
                    addView(bodyText(getString(R.string.leaderboard_rank, index + 1), 14f), LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(oneLineText("${Countries.flag(entry.country)} ${entry.username}", bold = mine),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (overallBoard) addView(bodyText(tierName(OverallScore.Tier.of(entry.score)), 12f).apply { setPadding(0, 0, dp(10), 0) })
                    addView(titleText(shown(entry.score), 15f))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) })
            }
            if (board.updatedAt > 0) holder.addView(bodyText(getString(R.string.leaderboard_updated,
                NumberFormat.getIntegerInstance().format(board.players),
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(board.updatedAt))), 12f).apply { setPadding(0, dp(8), 0, 0) })
        }
        show(leaderboard.cached(id)?.first, loading = true)
        leaderboard.board(id) { board ->
            // Only if the athlete is still looking at this board.
            if (holder.isAttachedToWindow && Boards.id(boardSkill, country?.takeIf { boardInCountry }) == id) show(board, loading = false)
        }
    }

    /** Another device's progress may have arrived: redraw the overview screens (never a workout or the builder). */
    private fun afterSync(result: CloudSync.Result) {
        publishScore()
        if (result == CloudSync.Result.Synced && !isFinishing && catalog != null)
            currentScreen?.takeIf { it == Screen.Dashboard || it == Screen.Levels || it == Screen.Progress }
                ?.let { showScreen(it, pushCurrent = false) }
    }

    /** Something to save changed on the phone: sync and update the score shortly, once for a burst of changes. */
    private fun cloudChanged() {
        if (!Entitlement.tier(this).account || Cloud.uid(this) == null) return
        cloudHandler.removeCallbacks(cloudCatchUp)
        cloudHandler.postDelayed(cloudCatchUp, 3_000L)
    }

    /**
     * Pro with a visible profile: the score from finished workouts goes to the leaderboard when it changed, at most
     * every ten minutes ([Scores.shouldPublish]). A hidden profile takes its score off.
     */
    private fun publishScore() {
        val book = skillBook ?: return
        if (!Entitlement.tier(this).account) return
        val uid = Cloud.uid(this) ?: return
        val profile = accountStore.profile(uid) ?: return
        val published = accountStore.publishedScore(uid)
        if (!profile.visible) {
            if (published != null) leaderboard.unpublish(uid) { ok -> if (ok) accountStore.savePublishedScore(uid, null, 0) }
            return
        }
        val now = System.currentTimeMillis()
        val score = currentScore(book)
        if (!Scores.shouldPublish(published?.first, published?.second ?: 0, score, now)) return
        leaderboard.publish(uid, profile, score) { ok -> if (ok) accountStore.savePublishedScore(uid, score, now) }
    }

    /** Skills from the classes that count ([Scores.credits]): the Skills tab and the board agree. */
    private fun skillProgress(book: SkillBook, history: List<WorkoutSession> = progressRepository.sessionHistory()): SkillProgress =
        book.progress(Scores.credits(history) { id -> library.customDay(id)?.let(CustomDays::estimatedMinutes) })

    private fun currentScore(book: SkillBook): Score = Scores.from(skillProgress(book))

    /**
     * After an exam class: the athlete ticks the targets reached (a fully ticked set starts ticked). The result is
     * logged and synced; a missed target offers to repeat the block since the previous exam, when [offerRepeat].
     */
    private fun askExam(exam: TrainingProgram, tests: List<Pair<TrainingExercise, Boolean>>, offerRepeat: Boolean) {
        if (tests.isEmpty()) return
        val locale = uiLocale()
        val reached = BooleanArray(tests.size) { tests[it].second }
        AlertDialog.Builder(this)
            .setTitle(R.string.exam_results_title)
            .setMultiChoiceItems(tests.map { examTarget(it.first, locale) }.toTypedArray(), reached) { _, which, checked ->
                reached[which] = checked
            }
            .setCancelable(false)
            .setPositiveButton(R.string.exam_save) { _, _ ->
                val ids = tests.map { (exercise, _) -> exercise.movementId ?: exercise.id }
                val result = ExamResult(exam.id, System.currentTimeMillis(),
                    ids.filterIndexed { index, _ -> reached[index] }, ids.filterIndexed { index, _ -> !reached[index] })
                progressRepository.recordExam(result)
                cloudChanged()
                val missed = tests.filterIndexed { index, _ -> !reached[index] }.map { displayName(it.first, locale) }
                when {
                    result.passed -> AlertDialog.Builder(this).setTitle(R.string.exam_passed_title)
                        .setMessage(R.string.exam_passed_message).setPositiveButton(android.R.string.ok, null).show()
                    offerRepeat && result.offersRepeat(exam.headline) -> askRepeatBlock(exam, missed)
                    else -> Toast.makeText(this, R.string.exam_saved_not_yet, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /** A missed target: Today can go back to the first class after the previous exam and come through this exam again. */
    private fun askRepeatBlock(exam: TrainingProgram, missed: List<String>) {
        val index = orderedPrograms.indexOfFirst { it.id == exam.id }.takeIf { it >= 0 } ?: return
        val start = (index - 1 downTo 0).firstOrNull { orderedPrograms[it].isExam }?.plus(1) ?: 0
        val first = orderedPrograms[start]
        val count = index - start + 1
        AlertDialog.Builder(this)
            .setTitle(R.string.exam_missed_title)
            .setMessage(getString(R.string.exam_missed_message, missed.joinToString(", "), count,
                getString(R.string.exam_class_label, first.levelNumber, first.number)))
            .setPositiveButton(getString(R.string.exam_repeat_block, count)) { _, _ ->
                progressRepository.startLevelReplay(first.levelNumber, fromProgramId = first.id)
                clearRest(); openRoot(Screen.Dashboard)
            }
            .setNegativeButton(R.string.exam_continue, null)
            .show()
    }

    /** An exam day's page: what the test asks, or how the last attempt went. */
    private fun examCard(exam: TrainingProgram, locale: Locale): LinearLayout = card().apply {
        addView(titleText(getString(R.string.exam_badge), 18f))
        val latest = progressRepository.examLog().latest(exam.id)
        val text = if (latest == null) getString(R.string.exam_card_untaken) else {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(latest.at))
            val total = latest.reached.size + latest.missed.size
            if (latest.passed) getString(R.string.exam_card_passed, date) + " " +
                getString(R.string.exam_card_result, date, latest.reached.size, total)
            else {
                val names = exam.exercises.filter { (it.movementId ?: it.id) in latest.missed }.map { displayName(it, locale) }
                getString(R.string.exam_card_result, date, latest.reached.size, total) + " " +
                    getString(R.string.exam_card_missed, names.joinToString(", "))
            }
        }
        addWithMargins(bodyText(text, 14f), topDp = 6)
    }

    override fun onDestroy() {
        cloudHandler.removeCallbacks(cloudCatchUp)
        Entitlement.prefs(this).unregisterOnSharedPreferenceChangeListener(entitlementChanged)
        billing.close()
        adBreak.destroy()
        restTimer?.cancel()
        toneGenerator?.release()
        worker.shutdownNow()
        parksMap?.destroy(); parksMap = null; parksMapHidden = false
        parksWorker.shutdownNow()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        parksMap?.takeUnless { parksMapHidden }?.start()
    }

    override fun onStop() {
        parksMap?.stop()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        // A map kept off screen goes when memory runs short; Parks builds a new one when it's opened again.
        if (parksMapHidden) { parksMap?.destroy(); parksMap = null; parksMapHidden = false } else parksMap?.lowMemory()
    }

    @Deprecated("Uses platform back navigation for minSdk 23 compatibility")
    override fun onBackPressed() {
        when (val screen = currentScreen) {
            Screen.Dashboard, null -> super.onBackPressed()
            Screen.Levels, Screen.Progress, Screen.Settings, Screen.Timing -> openRoot(Screen.Dashboard)
            else -> {
                val prior = backStack.pollLast()
                if (prior != null) showScreen(prior, pushCurrent = false) else openRoot(Screen.Dashboard)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        window.statusBarColor = AppColors.BACKGROUND
        window.navigationBarColor = AppColors.BACKGROUND
        window.decorView.systemUiVisibility = if (AppColors.darkMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun buildShell() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pageBackground()
        }

        toolbarBack = TextView(this).apply {
            text = "‹"
            gravity = Gravity.CENTER
            textSize = 38f
            setTextColor(AppColors.TEXT)
            contentDescription = getString(R.string.back)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackPressed() }
        }
        toolbarTitle = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 20f
            setTextColor(AppColors.TEXT)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            styleToolbarTitle()
        }
        toolbarMark = ImageView(this).apply {
            setImageResource(R.drawable.ic_brand_mark)
            imageTintList = ColorStateList.valueOf(AppColors.MARK)
            setPadding(dp(14), dp(12), dp(10), dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        toolbarAction = ImageView(this).apply {
            imageTintList = ColorStateList.valueOf(AppColors.TEXT)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { switchWorkoutView() }
        }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // appRoot paints the page (with its grain) behind the toolbar too.
            elevation = 0f
            addView(toolbarMark, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(toolbarBack, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(toolbarTitle, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(toolbarAction, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(View(context), LinearLayout.LayoutParams(dp(16), dp(56)))
        }

        // No background of its own: appRoot already paints the page colour behind every screen.
        bodyContainer = FrameLayout(this)
        bottomNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            elevation = 0f
            background = navBarBackground()
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        appRoot.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        appRoot.addView(bodyContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        appRoot.addView(
            bottomNavigation,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)).apply {
                setMargins(dp(16), dp(8), dp(16), dp(12))
            },
        )
        setContentView(appRoot)
        applySystemBarInsets()
    }

    private fun applySystemBarInsets() {
        appRoot.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = bars.top
                bottom = bars.bottom
                left = bars.left
                right = bars.right
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
            }
            toolbar.setPadding(0, top, 0, 0)
            toolbar.layoutParams = (toolbar.layoutParams as LinearLayout.LayoutParams).apply {
                height = dp(56) + top
            }
            bottomNavigation.setPadding(dp(8), dp(8), dp(8), dp(8))
            appRoot.setPadding(left, 0, right, bottom)
            bottomNavigation.layoutParams =
                (bottomNavigation.layoutParams as LinearLayout.LayoutParams).apply {
                    height = dp(74)
                }
            insets
        }
        appRoot.requestApplyInsets()
    }

    private fun loadCatalog() {
        loaded?.let { return showLoaded(it) }
        toolbar.visibility = View.GONE
        bottomNavigation.visibility = View.GONE
        renderLoading()
        worker.execute {
            // The catalog asset is read and parsed once, for the program, the exercise library and Skills.
            val root = AssetCurriculumRepository(this).readRoot()
            // On a second thread meanwhile, as neither reads what the other makes: the clips that Today's park scene
            // and every movement guide use, then Skills, which read the movement library and schedule from the same
            // JSON (both only read it). A Skills failure only hides the Skills tab.
            val side = parksWorker.submit<SkillBook?> {
                runCatching { MotionClip.preload(this) }
                root.getOrNull()?.let { runCatching { SkillCurriculumReader.read(it) }.getOrNull() }
            }
            val result = root.mapCatching(CurriculumJsonParser::parse)
            val exercises = result.getOrNull()?.let { runCatching { ExerciseLibrary.from(it) }.getOrNull() }
            val skills = runCatching { side.get() }.getOrNull()
            val parsed = result.getOrNull()?.let { LoadedCatalog(it, exercises, skills) }?.also { loaded = it }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (parsed != null) showLoaded(parsed) else renderLoadError()
            }
        }
    }

    private fun showLoaded(parsed: LoadedCatalog) {
        skillBook = parsed.skills
        exerciseLibrary = parsed.exercises
        scaledFor = currentIntensity()
        catalog = scaledFor.scale(parsed.catalog)
        val destination = restoredScreen?.takeIf(::isValidScreen) ?: Screen.Dashboard
        restoredScreen = null
        showScreen(destination, pushCurrent = false)
    }

    /**
     * A movement's notes for the overview and list workout: the description, then safety and the easier rungs on lines
     * of their own, as the focus view's details show them, instead of the parser's "…; Safety: …" join.
     */
    private fun exerciseNotes(exercise: TrainingExercise, locale: Locale): String {
        val notes = ExerciseNotes.parse(exercise.description.resolve(locale))
        return listOfNotNull(
            notes.description.takeIf(String::isNotBlank),
            notes.safety.takeIf(String::isNotBlank)?.let { "${getString(R.string.focus_details_safety)}: $it" },
            notes.easier.takeIf(String::isNotBlank)?.let { "${getString(R.string.focus_details_easier)}: $it" },
        ).joinToString("\n")
    }

    /** What [loadCatalog] parses from the assets, which never change while the app runs. */
    private class LoadedCatalog(val catalog: CurriculumCatalog, val exercises: ExerciseLibrary?, val skills: SkillBook?)

    private fun renderLoading() {
        bodyContainer.removeAllViews()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            // One figure: mostly the walk, now and then pull-ups.
            val pull = Random.nextInt(3) == 0
            addView(ParkSceneView(context, if (pull) ParkSceneView.Mode.PULL else ParkSceneView.Mode.WALK),
                LinearLayout.LayoutParams(dp(200), dp(if (pull) 150 else 96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(spacer(16))
            addView(bodyText(getString(R.string.loading_catalog)).apply { gravity = Gravity.CENTER })
        }
        bodyContainer.addView(
            layout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun renderLoadError() {
        toolbar.visibility = View.VISIBLE
        bottomNavigation.visibility = View.GONE
        toolbarBack.visibility = View.GONE
        toolbarMark.visibility = View.VISIBLE
        toolbarTitle.text = getString(R.string.app_name)
        bodyContainer.removeAllViews()
        val page = verticalPage().apply {
            gravity = Gravity.CENTER
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.logo)
                    imageTintList = ColorStateList.valueOf(AppColors.MARK)
                    adjustViewBounds = true
                    contentDescription = getString(R.string.logo_content_description)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(100)),
            )
            addView(spacer(20))
            addView(titleText(getString(R.string.load_error_title)).apply { gravity = Gravity.CENTER })
            addView(spacer(8))
            addView(bodyText(getString(R.string.load_error_message)).apply { gravity = Gravity.CENTER })
            addView(spacer(20))
            addView(primaryButton(getString(R.string.retry), AppColors.PRIMARY) { loadCatalog() })
        }
        setBody(page)
    }

    private fun showScreen(screen: Screen, pushCurrent: Boolean = true) {
        if (!isValidScreen(screen)) {
            openRoot(Screen.Dashboard)
            return
        }
        if (pushCurrent) currentScreen?.let(backStack::addLast)
        currentScreen = screen
        if (screen !is Screen.EditDay) draft = null
        if (screen != Screen.Timing) timingPanel = null
        toolbar.visibility = View.VISIBLE
        bottomNavigation.visibility = View.VISIBLE
        toolbarBack.visibility = if (isNested(screen)) View.VISIBLE else View.GONE
        toolbarMark.visibility = if (isNested(screen)) View.GONE else View.VISIBLE
        toolbarTitle.text = screenTitle(screen)
        bindWorkoutViewAction(screen)
        rebuildBottomNavigation(screen)

        val active = screen is Screen.Active
        if (active && settings.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!active) {
            restTimer?.cancel()
            restStatusView = null
        }

        syncIntensity()
        when (screen) {
            Screen.Dashboard -> renderDashboard()
            Screen.Levels -> renderLevels()
            is Screen.Workouts -> renderWorkouts(screen.levelNumber)
            is Screen.Overview -> renderOverview(screen.programId)
            is Screen.Active -> if (settings.workoutView == WorkoutView.FOCUS) renderFocus(screen.programId)
                else renderActive(screen.programId)
            is Screen.EditDay -> renderEditDay(screen.dayId)
            Screen.Progress -> renderProgress()
            Screen.Settings -> renderSettings()
            Screen.Timing -> {
                timingPanel = TimingPanel(this, ::enableTimingNotifications, ::enableExactTiming)
                setBody(timingPanel!!)
                // Back to ten ticks a second at once, not after the slower tick of the other screens.
                if (activityResumed) { timingHandler.removeCallbacks(timingTick); timingHandler.post(timingTick) }
            }
        }
    }

    private fun openRoot(screen: Screen) {
        val leavingWorkout = currentScreen is Screen.Active && screen != Screen.Timing
        backStack.clear()
        showScreen(screen, pushCurrent = false)
        if (leavingWorkout && restEndsAtEpochMillis == null) adBreak.atNaturalBreak()
    }

    private fun navigate(screen: Screen) = showScreen(screen, pushCurrent = true)

    private fun rebuildBottomNavigation(screen: Screen) {
        bottomNavigation.removeAllViews()
        val selected = rootFor(screen)
        listOf(
            Triple(Screen.Dashboard, R.string.nav_today, R.drawable.ic_nav_today),
            Triple(Screen.Levels, R.string.nav_levels, R.drawable.ic_nav_training),
            Triple(Screen.Progress, R.string.nav_progress, R.drawable.ic_nav_progress),
            Triple(Screen.Timing, R.string.nav_timer, R.drawable.ic_nav_timer),
            Triple(Screen.Settings, R.string.nav_settings, R.drawable.ic_nav_settings),
        ).forEach { (destination, labelRes, icon) ->
            val isSelected = selected == destination
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = getString(labelRes)
                background = navItemBackground(isSelected)
                setOnClickListener { openRoot(destination) }
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(navItemColor(isSelected))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
                addWithMargins(TextView(context).apply {
                    text = getString(labelRes)
                    gravity = Gravity.CENTER
                    textSize = 11f
                    maxLines = 1
                    typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(navItemColor(isSelected))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    styleNavLabel(isSelected)
                }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 4)
            }
            bottomNavigation.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
    }

    private fun renderDashboard() {
        if (catalog == null) return
        val all = orderedPrograms
        val completed = progressRepository.completedProgramIds()
        val active = progressRepository.activeWorkout()
        val next = active?.takeIf(::canRun)?.programId?.let(::programById) ?: assignedProgram()
        // A repeat in progress takes Today's card until it is finished or ended, as any active workout does.
        val repeating = active?.repeat == true && active.programId == next?.id
        val compact = resources.configuration.screenHeightDp < 480
        val page = verticalPage()
        val hero = card().apply {
            background = roundedDrawable(AppColors.PRIMARY_DARK, dp(24).toFloat())
            addView(bodyText(getString(R.string.brand_kicker), 12f).apply { setTextColor(AppColors.HERO_KICKER) })
            addWithMargins(titleText(getString(R.string.brand_line), 30f).apply { setTextColor(AppColors.HERO_TEXT) }, topDp = 8)
            addWithMargins(ParkSceneView(context), height = dp(180), topDp = 4)
        }
        // A new athlete first says where to start; that screen brings its own header, and Today's day card follows once
        // it's answered or skipped.
        val placing = askPlacement(completed, active)
        if (!compact && !placing) page.addWithMargins(hero, bottomDp = 20)
        if (!compact && !placing) page.addView(sectionLabel(getString(R.string.today_section)))
        val today = card(clickable = next != null)
        val paywallLevel = next?.levelNumber?.takeIf(::levelLocked)
        if (next != null) {
            if (!compact) catalog?.levels?.firstOrNull { it.number == next.levelNumber }?.let { level ->
                todayHeader(level.number, displayName(level, uiLocale()), next.number, level.programs.size)
            }?.let(today::addView)
            val label = if (repeating) getString(R.string.repeat_note)
                else levelReplay()?.let {
                    if (it.fromProgramId != null) getString(R.string.exam_replay_today) else getString(R.string.replay_today, it.levelNumber)
                }
            label?.let {
                today.addWithMargins(bodyText(it, 12f).apply { setTextColor(AppColors.PRIMARY) }, bottomDp = 4)
            }
            today.addView(titleText(displayName(next, uiLocale()), if (compact) 20f else 23f))
            today.addWithMargins(bodyText(programMeta(next), 13f), topDp = 6)
            if (paywallLevel == null) {
                if (!CustomDays.isCustom(next.id)) today.addWithMargins(intensityRow(::renderDashboard), topDp = 14)
                today.addWithMargins(primaryButton(getString(if (active?.programId == next.id) R.string.resume_workout else R.string.start_today)) {
                    requestStart(next, repeat = repeating)
                }, topDp = 16)
                if (canStart(next.id) && !repeating) {
                    today.addWithMargins(secondaryButton("Complete day") { requestCompleteDay(next) }, topDp = 8)
                    today.addWithMargins(noPointsNote(R.string.complete_day_no_points), topDp = 6)
                }
            }
            today.setOnClickListener { navigate(Screen.Overview(next.id)) }
        } else {
            today.addView(titleText(getString(R.string.all_complete_title), 22f))
            today.addWithMargins(bodyText(getString(R.string.all_complete_body)), topDp = 8)
        }
        page.addWithMargins(if (placing) placementCard(orderedPrograms, catalog?.levels.orEmpty(),
            uiLocale(), placementAnswers, placementSkill, Entitlement.tier(this), settings, ::startPlacement) else today, bottomDp = 16)
        if (paywallLevel != null) page.addWithMargins(TiersPanel(this, billing, Paywall.Level(paywallLevel)), bottomDp = 16)
        val done = all.count { it.id in completed }
        // Nothing done yet while placing: the placement screen ends with its sharing note.
        if (!placing) page.addWithMargins(card(clickable = true).apply {
            addView(titleText(getString(R.string.progress_title), 18f))
            addWithMargins(bodyText(getString(R.string.workouts_completed_format, done, all.size), 14f), topDp = 6)
            addWithMargins(horizontalProgress(percentage(done, all.size)), height = dp(8), topDp = 14)
            setOnClickListener { openRoot(Screen.Progress) }
        }, bottomDp = 12)
        page.addView(ManagedNativePlacement(this),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setBody(scroll(page))
    }

    /** True for a new athlete ([Placement.shouldAsk]). Any progress, even from before placement existed, settles it for good. */
    private fun askPlacement(completed: Set<String>, active: ActiveWorkout?): Boolean {
        val ask = Placement.shouldAsk(settings.placementDone, completed.size, active != null)
        if (!ask && !settings.placementDone) settings.placementDone = true
        return ask
    }

    /**
     * The placement's answer. A start past Level 1, day 1 marks every earlier day complete, worth nothing like
     * "Complete level" ([Scores.credits]), with the same sync.
     */
    private fun startPlacement(start: Placement.Start) {
        val skipped = Placement.skipped(orderedPrograms, start, TrainingProgram::levelNumber, TrainingProgram::number)
            .map(TrainingProgram::id)
        if (skipped.isNotEmpty()) {
            progressRepository.completePrograms(skipped, placed = true)
            cloudChanged()
        }
        settings.placementDone = true
        placementAnswers.fill(-1)
        placementSkill[0] = 0
        clearRest(); openRoot(Screen.Dashboard)
    }

    /** The day's Easier, Normal or Harder: the active workout's, or today's pick for the next class. */
    private fun currentIntensity(): Intensity = progressRepository.activeWorkout()?.intensity ?: settings.intensityToday()

    /** Shows the program at [currentIntensity]: rescaled only when it changed (a pick, a start or finish, a new day). */
    private fun syncIntensity() {
        val now = currentIntensity()
        if (now == scaledFor) return
        scaledFor = now
        loaded?.let { catalog = now.scale(it.catalog) }
    }

    /** "How do you feel today?": today's pick, and the active workout's too, so its reps follow at once. */
    private fun setIntensity(value: Intensity) {
        settings.setIntensityToday(value)
        if (progressRepository.activeWorkout() != null) progressRepository.setIntensity(value)
        syncIntensity()
    }

    /** The day's Easier, Normal or Harder over a class that can be started; [redraw] shows the new reps. */
    private fun intensityRow(redraw: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(bodyText(getString(R.string.intensity_title), 13f).apply { setTextColor(AppColors.TEXT) })
        val selected = currentIntensity().ordinal
        addWithMargins(segmentedSwitch(intensityLabels(), selected) { index ->
            setIntensity(Intensity.entries[index])
            redraw()
        }.apply { getChildAt(selected).isSelected = true }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 6)
        addWithMargins(bodyText(getString(R.string.intensity_note), 12f), topDp = 4)
    }

    private fun intensityLabels(): List<String> =
        listOf(R.string.intensity_easier, R.string.intensity_normal, R.string.intensity_harder).map(::getString)

    private fun renderLevels() {
        val catalog = catalog ?: return
        val locale = uiLocale()
        val completed = passedIds()
        val page = verticalPage()
        page.addWithMargins(segmentedSwitch(listOf(getString(R.string.training_tab_levels),
            getString(R.string.training_tab_workouts), getString(R.string.training_tab_parks)), trainingTab) { index ->
            trainingTab = index
            renderLevels()
        }, width = ViewGroup.LayoutParams.WRAP_CONTENT, bottomDp = 14)
        if (trainingTab != 0) {
            val map = if (trainingTab == 1) { renderMyWorkouts(page, locale); false } else renderParks(page)
            // The Parks map fills the screen instead of scrolling with the page.
            setBody(if (map) page else scroll(page))
            return
        }
        page.addWithMargins(bodyText(getString(R.string.empty_description)), bottomDp = 16)
        levelReplay()?.let { page.addWithMargins(replayCard(it, locale), bottomDp = 12) }
        val levels = catalog.levels.sortedBy { it.number }
        val currentLevel = assignedProgram()?.levelNumber
        levels.forEachIndexed { index, level ->
            val state = when {
                level.number == currentLevel -> LevelState.CURRENT
                level.programs.all { it.id in completed } -> LevelState.DONE
                else -> LevelState.UPCOMING
            }
            page.addWithMargins(decorateLevel(levelCard(level, locale, completed), state, index, index == 0, index == levels.lastIndex),
                bottomDp = if (levelsOnRoute) 0 else 12)
        }
        setBody(scroll(page))
    }

    /**
     * Saved program days and custom days, which share the tier's limit. Favorites holds them all; Plus and Pro also
     * sort them into lists of their own. On Free the lists are kept but hidden, and come back with Plus.
     */
    private fun renderMyWorkouts(page: LinearLayout, locale: Locale) {
        val tier = Entitlement.tier(this)
        val state = library.state()
        val lists = if (tier.lists) state.activeLists else emptyList()
        val open = lists.firstOrNull { it.id == workoutListId }.also { if (it == null) workoutListId = null }
        page.addView(bodyText(getString(R.string.library_intro)))
        page.addWithMargins(bodyText(getString(R.string.library_usage, state.used, tier.savedDays), 13f).apply {
            setTextColor(AppColors.PRIMARY)
        }, topDp = 10)
        if (SavedLimit.overLimit(tier, state.used)) {
            page.addWithMargins(bodyText(getString(R.string.library_over_limit, tier.savedDays), 13f), topDp = 4)
        }
        page.addWithMargins(listChips(listOf(getString(R.string.lists_default)) + lists.map { it.name },
            open?.let { lists.indexOf(it) + 1 } ?: 0, { index -> workoutListId = lists.getOrNull(index - 1)?.id; renderLevels() },
            locked = !tier.lists, onNew = ::requestNewList), topDp = 14, bottomDp = 4)
        if (open != null) {
            page.addWithMargins(listActions(onRename = {
                listNameDialog(getString(R.string.lists_rename_title, open.name), library, open.id, open.name) { name ->
                    library.renameList(open.id, name); renderLevels()
                }
            }, onDelete = {
                AlertDialog.Builder(this).setTitle(getString(R.string.lists_delete_title, open.name))
                    .setMessage(R.string.lists_delete_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ -> library.deleteList(open.id); workoutListId = null; renderLevels() }
                    .show()
            }), topDp = 10, bottomDp = 14)
            val days = state.daysIn(open).mapNotNull(::programById)
            if (days.isEmpty()) page.addView(bodyText(getString(R.string.lists_empty), 14f))
            days.forEach { page.addWithMargins(libraryCard(it, locale), bottomDp = 10) }
            return
        }
        page.addWithMargins(primaryButton(getString(R.string.library_create)) { requestNewDay() }, topDp = 14, bottomDp = 18)
        page.addView(sectionLabel(getString(R.string.library_custom)))
        if (state.customDays.isEmpty()) page.addWithMargins(bodyText(getString(R.string.library_empty), 14f), bottomDp = 12)
        state.customDays.forEach { page.addWithMargins(libraryCard(CustomDays.toProgram(it), locale), bottomDp = 10) }
        page.addWithMargins(sectionLabel(getString(R.string.library_saved)), topDp = 8)
        val saved = state.savedIds.mapNotNull(::programById)
        if (saved.isEmpty()) page.addView(bodyText(getString(R.string.library_empty), 14f))
        saved.forEach { page.addWithMargins(libraryCard(it, locale), bottomDp = 10) }
    }

    /** A saved or custom day in My workouts: its name and what it holds, opening its overview. */
    private fun libraryCard(program: TrainingProgram, locale: Locale): LinearLayout = card(clickable = true).apply {
        addView(titleText(displayName(program, locale), 18f))
        addWithMargins(bodyText(if (CustomDays.isCustom(program.id)) customMeta(program.exercises.size, program.estimatedMinutes ?: 1)
            else listOf(getString(R.string.level_number, program.levelNumber), programMeta(program)).joinToString(" • "), 13f), topDp = 4)
        setOnClickListener { navigate(Screen.Overview(program.id)) }
    }

    /** A new list from My workouts, opened at once; Free and a full tier see what gives more. */
    private fun requestNewList() {
        if (!roomForAnotherList()) return
        listNameDialog(getString(R.string.lists_new), library) { name ->
            workoutListId = library.createList(name).id
            renderLevels()
        }
    }

    private fun roomForAnotherList(): Boolean {
        val tier = Entitlement.tier(this)
        if (SavedLimit.canAddList(tier, library.state().activeLists.size)) return true
        if (tier == Tier.PRO) Toast.makeText(this, R.string.lists_limit_pro, Toast.LENGTH_LONG).show()
        else showPaywall(Paywall.Lists(tier.workoutLists))
        return false
    }

    /**
     * Parks near the athlete or by name (Plus), on a map (the default) or as a list; true when the page holds the map.
     * The parks ship with the app; the location never leaves the phone.
     */
    private fun renderParks(page: LinearLayout): Boolean {
        if (!Entitlement.tier(this).parks) {
            page.addWithMargins(bodyText(getString(R.string.parks_teaser)), bottomDp = 12)
            page.addView(TiersPanel(this, billing, Paywall.Parks))
            return false
        }
        val all = parks
        if (all == null) {
            page.addView(bodyText(getString(R.string.parks_loading)))
            loadParks()
            return false
        }
        val prefs = getSharedPreferences(ParksUpdater.PREFERENCES_NAME, MODE_PRIVATE)
        val map = if (prefs.getBoolean(KEY_PARKS_LIST, false) || parksMapFailed) null else parksMap ?: openParksMap()
        page.addWithMargins(segmentedSwitch(listOf(getString(R.string.parks_view_map), getString(R.string.parks_view_list)),
            if (map != null) 0 else 1) { index ->
            prefs.edit().putBoolean(KEY_PARKS_LIST, index == 1).apply()
            renderLevels()
        }, width = ViewGroup.LayoutParams.WRAP_CONTENT, bottomDp = 6)
        val search = EditText(this).apply {
            hint = getString(R.string.parks_search_hint)
            setText(parkQuery)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(AppColors.TEXT)
            setHintTextColor(AppColors.TEXT_SECONDARY)
        }
        fun runSearch() {
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
            searchParks(all, search.text.toString().trim())
        }
        search.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_SEARCH).also { if (it) runSearch() } }
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(secondaryButton(getString(R.string.parks_search)) { runSearch() })
        })
        if (map != null) {
            (map.view.parent as? ViewGroup)?.removeView(map.view)
            page.addView(map.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
            // One line, so the map keeps the height; its ⓘ button carries the OpenFreeMap and OpenStreetMap credit.
            page.addWithMargins(bodyText(getString(R.string.parks_privacy), 12f), topDp = 6)
            showParksOnMap(all, map)
            val origin = parkOrigin
            if (origin != null) map.showMe(origin.first, origin.second)
            else if (!parksLocateTried) {
                // The map opens on the athlete's location: asked for once, then only from its location button.
                parksLocateTried = true
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locateForParks()
                else if (!prefs.getBoolean(KEY_LOCATION_ASKED, false)) {
                    prefs.edit().putBoolean(KEY_LOCATION_ASKED, true).apply()
                    requestParksNearMe()
                }
            }
            return true
        }
        page.addWithMargins(bodyText(getString(R.string.parks_intro, NumberFormat.getIntegerInstance().format(all.size))), topDp = 10)
        page.addWithMargins(primaryButton(getString(R.string.parks_near_me)) { requestParksNearMe() }, topDp = 12)
        parkResults?.let { results ->
            page.addWithMargins(sectionLabel(if (parkQuery.isBlank()) getString(R.string.parks_nearest)
                else getString(R.string.parks_matches, parkQuery)), topDp = 14)
            if (results.isEmpty()) page.addView(bodyText(getString(R.string.parks_none), 14f))
            else page.addWithMargins(bodyText(getString(R.string.parks_directions), 13f), bottomDp = 8)
            results.forEach { nearby ->
                page.addWithMargins(card(clickable = true).apply {
                    addView(titleText(nearby.park.name, 16f))
                    if (nearby.kilometres >= 0) addWithMargins(bodyText(
                        if (nearby.kilometres < 1) getString(R.string.parks_m, (nearby.kilometres * 1000).toInt())
                        else getString(R.string.parks_km, nearby.kilometres), 13f), topDp = 4)
                    setOnClickListener { openPark(nearby.park) }
                }, bottomDp = 8)
            }
        }
        page.addWithMargins(bodyText(getString(R.string.parks_credit), 12f), topDp = 16)
        return false
    }

    /** A new map, or null (and the list from then on) where MapLibre can't start, such as a phone without its native library. */
    private fun openParksMap(): ParksMap? = runCatching {
        ParksMap(this, ::requestParksNearMe, ::openPark, parkOrigin).also { if (activityResumed) it.resume() }
    }.onFailure {
        Log.w("ParksMap", "The map can't start here; showing the list", it)
        parksMapFailed = true
    }.getOrNull().also { parksMap = it }

    private fun showParksOnMap(all: List<Park>, map: ParksMap) {
        parksGeoJson?.takeIf { it.first === all }?.let { map.showParks(all, it.second); return }
        if (parksGeoJsonBuilding === all) return
        parksGeoJsonBuilding = all
        parksWorker.execute {
            val json = Parks.geoJson(all)
            runOnUiThread {
                parksGeoJson = all to json
                if (parksGeoJsonBuilding === all) parksGeoJsonBuilding = null
                if (parks === all) parksMap?.showParks(all, json)
            }
        }
    }

    /** Nearest parks for a blank query (when the location is known), else the matches: on the map or in the list. */
    private fun searchParks(all: List<Park>, query: String) {
        parkQuery = query
        val origin = parkOrigin
        parksWorker.execute {
            val results = if (query.isBlank() && origin != null) Parks.nearest(all, origin.first, origin.second)
                else Parks.search(all, query, origin)
            runOnUiThread {
                parkResults = results
                // A map kept off screen (the list, another tab) doesn't take the results; the list shows them.
                val map = parksMap?.takeUnless { parksMapHidden }
                when {
                    map == null -> if (currentScreen == Screen.Levels && trainingTab == 2) renderLevels()
                    query.isBlank() -> origin?.let { map.showNearby(it.first, it.second, results.take(NEARBY_ON_MAP).map(NearbyPark::park)) }
                    results.isEmpty() -> Toast.makeText(this, R.string.parks_none, Toast.LENGTH_SHORT).show()
                    else -> map.focus(results.map { it.park })
                }
            }
        }
    }

    private fun loadParks() {
        if (parksLoading) return
        parksLoading = true
        worker.execute {
            val updater = ParksUpdater(this)
            val loaded = runCatching { updater.load() }.getOrDefault(emptyList())
            runOnUiThread {
                parks = loaded; parksLoading = false
                if (currentScreen == Screen.Levels && trainingTab == 2) renderLevels()
            }
            // The quarterly file, when the job published a new one (at most one small request a week).
            val fresh = runCatching { updater.update(loaded.size) }.getOrNull() ?: return@execute
            runOnUiThread {
                parks = fresh
                if (currentScreen == Screen.Levels && trainingTab == 2) renderLevels()
            }
        }
    }

    private fun requestParksNearMe() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locateForParks()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) locateForParks()
        else Toast.makeText(this, R.string.parks_location_denied, Toast.LENGTH_LONG).show()
    }

    /**
     * The last known approximate fix shows parks at once; a fresh fix then refines them when the last one is older
     * than ten minutes. No Play services needed.
     */
    @SuppressLint("MissingPermission")
    private fun locateForParks() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        fun show(location: Location) {
            val all = parks ?: return
            parkOrigin = location.latitude to location.longitude
            // The dot at once; the camera follows with the nearest parks in view.
            parksMap?.showMe(location.latitude, location.longitude)
            searchParks(all, "")
        }
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        last?.let(::show)
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000L) return
        // "fused" is LocationManager.FUSED_PROVIDER (API 31); older phones just don't list it.
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
        if (provider == null) {
            if (last == null) Toast.makeText(this, R.string.parks_no_location, Toast.LENGTH_LONG).show()
            return
        }
        if (last == null) Toast.makeText(this, R.string.parks_locating, Toast.LENGTH_SHORT).show()
        fun fresh(location: Location?) {
            if (location != null) show(location)
            else if (last == null) Toast.makeText(this, R.string.parks_no_location, Toast.LENGTH_LONG).show()
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) manager.getCurrentLocation(provider, null, mainExecutor, ::fresh)
            else @Suppress("DEPRECATION") manager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) = fresh(location)
                @Deprecated("Required on API < 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }, Looper.getMainLooper())
        }.onFailure { fresh(null) }
    }

    private fun openPark(park: Park) {
        val geo = Uri.parse("geo:${park.lat},${park.lng}?q=${park.lat},${park.lng}(${Uri.encode(park.name)})")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, geo)) }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${park.lat},${park.lng}"))) }
        }
    }

    /** True when one more saved or custom day fits; otherwise shows what raises the limit. */
    private fun roomForAnother(): Boolean {
        val tier = Entitlement.tier(this)
        if (SavedLimit.canAdd(tier, library.state().used)) return true
        if (tier == Tier.PRO) Toast.makeText(this, R.string.saved_limit_pro, Toast.LENGTH_LONG).show()
        else showPaywall(Paywall.Saved(tier.savedDays))
        return false
    }

    private fun showPaywall(paywall: Paywall) {
        AlertDialog.Builder(this).setView(scroll(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(4))
            addView(TiersPanel(this@HomeActivity, billing, paywall))
        })).setNegativeButton(R.string.cancel, null).show()
    }

    private fun requestNewDay() {
        if (!roomForAnother()) return
        val day = CustomDay(CustomDays.newId(), "", emptyList(), System.currentTimeMillis())
        draft = day
        navigate(Screen.EditDay(day.id))
    }

    private fun requestEditDay(id: String) {
        draft = library.customDay(id) ?: return
        navigate(Screen.EditDay(id))
    }

    private fun renderEditDay(dayId: String) {
        val day = draft?.takeIf { it.id == dayId } ?: library.customDay(dayId)?.also { draft = it } ?: return
        val page = verticalPage()
        page.addView(EditText(this).apply {
            hint = getString(R.string.builder_name_hint)
            setText(day.name)
            textSize = 20f
            setTextColor(AppColors.TEXT)
            setHintTextColor(AppColors.TEXT_SECONDARY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(MAX_NAME_LENGTH))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { draft = draft?.copy(name = s?.toString().orEmpty()) }
            })
        })
        day.exercises.forEachIndexed { index, exercise ->
            page.addWithMargins(card().apply {
                addView(titleText("${index + 1}. ${exercise.name}", 17f))
                addWithMargins(bodyText(prescription(exercise), 13f), topDp = 4)
                addWithMargins(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    fun action(label: Int, enabled: Boolean = true, change: (MutableList<CustomExercise>) -> Unit) =
                        addView(secondaryButton(getString(label)) {
                            val list = draft?.exercises.orEmpty().toMutableList()
                            change(list)
                            draft = draft?.copy(exercises = list)
                            renderEditDay(dayId)
                        }.apply { isEnabled = enabled; alpha = if (enabled) 1f else .4f; minWidth = 0; setPadding(dp(10), 0, dp(10), 0) },
                            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    action(R.string.builder_up, index > 0) { it.add(index - 1, it.removeAt(index)) }
                    action(R.string.builder_down, index < day.exercises.lastIndex) { it.add(index + 1, it.removeAt(index)) }
                    addView(secondaryButton(getString(R.string.builder_edit)) {
                        editSets(exercise.name, exercise.sets) { sets ->
                            draft = draft?.copy(exercises = draft?.exercises.orEmpty().toMutableList().also { it[index] = exercise.copy(sets = sets) })
                            renderEditDay(dayId)
                        }
                    }.apply { minWidth = 0; setPadding(dp(10), 0, dp(10), 0) },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    action(R.string.builder_remove) { it.removeAt(index) }
                }, topDp = 10)
            }, topDp = 12)
        }
        page.addWithMargins(secondaryButton(getString(R.string.builder_add)) {
            pickExercise { movement ->
                val sets = List(3) { movement.sample }
                editSets(movement.name, sets) { chosen ->
                    draft = draft?.copy(exercises = draft?.exercises.orEmpty() + CustomExercise(movement.key, movement.name,
                        movement.movementId, movement.category, movement.equipment, chosen))
                    renderEditDay(dayId)
                }
            }
        }, topDp = 16)
        page.addWithMargins(primaryButton(getString(R.string.builder_save)) {
            val current = draft ?: return@primaryButton
            when {
                current.name.isBlank() -> Toast.makeText(this, R.string.builder_needs_name, Toast.LENGTH_SHORT).show()
                current.exercises.isEmpty() -> Toast.makeText(this, R.string.builder_needs_exercise, Toast.LENGTH_SHORT).show()
                else -> {
                    library.saveDay(current.copy(name = current.name.trim(), updatedAt = System.currentTimeMillis()))
                    if (backStack.peekLast() == Screen.Overview(current.id)) backStack.pollLast()
                    showScreen(Screen.Overview(current.id), pushCurrent = false)
                }
            }
        }, topDp = 10)
        setBody(scroll(page))
    }

    private fun customMeta(exercises: Int, minutes: Int): String =
        getString(R.string.custom_meta, resources.getQuantityString(R.plurals.custom_exercises, exercises, exercises), minutes)

    private fun prescription(exercise: CustomExercise): String {
        val set = exercise.sets.firstOrNull() ?: return ""
        return if (set.durationSeconds != null) getString(R.string.builder_seconds, exercise.sets.size, set.durationSeconds, set.breakSeconds)
        else getString(R.string.builder_reps, exercise.sets.size, set.repetitions ?: 0, set.breakSeconds)
    }

    /** Search every exercise of the program and pick one. */
    private fun pickExercise(onPick: (LibraryMovement) -> Unit) {
        val all = exerciseLibrary ?: return
        var shown = all.movements
        val empty = bodyText(getString(R.string.picker_empty), 14f).apply { visibility = View.GONE; setPadding(0, dp(12), 0, 0) }
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
                ((convertView as? PickerRow) ?: PickerRow()).apply { show(shown[position]) }
        }
        val list = ListView(this).apply { this.adapter = adapter; divider = ColorDrawable(AppColors.DIVIDER); dividerHeight = 1 }
        val search = EditText(this).apply {
            hint = getString(R.string.picker_hint)
            isSingleLine = true
            setTextColor(AppColors.TEXT)
            setHintTextColor(AppColors.TEXT_SECONDARY)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    shown = all.search(s?.toString().orEmpty())
                    empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
                    adapter.notifyDataSetChanged()
                }
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(search)
            addView(empty)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.picker_title).setView(content)
            .setNegativeButton(R.string.cancel, null).show()
        list.setOnItemClickListener { _, _, position, _ -> dialog.dismiss(); onPick(shown[position]) }
    }

    /** A picker row: the movement playing in a small tile beside its name and details. The list reuses rows as it scrolls. */
    private inner class PickerRow : LinearLayout(this@HomeActivity) {
        private val thumb = ExerciseAnimationView(context).apply {
            setBadgeStyle(ExerciseAnimationView.BadgeStyle(
                AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
                blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f)), moving = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val name = oneLineText("", bold = true)
        private val details = bodyText("", 13f).apply { maxLines = 1 }
        private var movement: LibraryMovement? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            addView(thumb, LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(name)
                addView(details)
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun show(movement: LibraryMovement) {
            if (movement == this.movement) return
            this.movement = movement
            thumb.bind(movement.movementId, movement.name, movement.category, movement.equipment)
            name.text = movement.name
            details.text = listOfNotNull(movement.category?.let(::humanize), movement.equipment.joinToString(", ", transform = ::humanize).ifBlank { null })
                .joinToString(" · ")
            details.visibility = if (details.text.isBlank()) View.GONE else View.VISIBLE
        }
    }

    /** The same dose for every set: how many, reps or seconds, and rest. */
    private fun editSets(name: String, sets: List<CustomSet>, onApply: (List<CustomSet>) -> Unit) {
        val first = sets.firstOrNull() ?: CustomSet(repetitions = 8)
        var timed = first.durationSeconds != null
        fun field(label: Int, value: Int) = EditText(this).apply {
            hint = getString(label)
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            setTextColor(AppColors.TEXT)
        }
        val count = field(R.string.sets_count, sets.size.coerceAtLeast(1))
        val amount = field(R.string.sets_amount, first.durationSeconds ?: first.repetitions ?: 8)
        val rest = field(R.string.sets_rest, first.breakSeconds)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(bodyText(getString(R.string.sets_count), 13f)); addView(count)
            addWithMargins(segmentedSwitch(listOf(getString(R.string.sets_reps), getString(R.string.sets_seconds)), if (timed) 1 else 0) {
                timed = it == 1
            }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 8, bottomDp = 4)
            addView(bodyText(getString(R.string.sets_amount), 13f)); addView(amount)
            addView(bodyText(getString(R.string.sets_rest), 13f)); addView(rest)
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.sets_title, name)).setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sets_apply) { _, _ ->
                val n = count.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 1
                val value = amount.text.toString().toIntOrNull()?.coerceIn(1, 600) ?: 1
                val pause = rest.text.toString().toIntOrNull()?.coerceIn(0, 600) ?: 60
                onApply(List(n) { if (timed) CustomSet(durationSeconds = value, breakSeconds = pause) else CustomSet(repetitions = value, breakSeconds = pause) })
            }.show()
    }

    private fun renameLevel(level: TrainingLevel) {
        if (!Entitlement.tier(this).renameLevels) { showPaywall(Paywall.Rename); return }
        val names = LevelNames(this)
        val field = EditText(this).apply {
            setText(displayName(level, uiLocale()))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(MAX_NAME_LENGTH))
            setTextColor(AppColors.TEXT)
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.rename_level_title, getString(R.string.level_number, level.number)))
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.rename_reset) { _, _ -> names.set(level.number, null); showScreen(currentScreen ?: Screen.Levels, pushCurrent = false) }
            .setPositiveButton(R.string.rename_save) { _, _ ->
                names.set(level.number, field.text.toString().trim().ifBlank { null })
                showScreen(currentScreen ?: Screen.Levels, pushCurrent = false)
            }.show()
    }

    private fun levelCard(
        level: TrainingLevel,
        locale: Locale,
        completedIds: Set<String>,
    ): LinearLayout = card(clickable = true).apply {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            numberCircle(level.number.toString(), getString(R.string.level_number, level.number)),
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(titleText(displayName(level, locale), 19f))
            val description = level.description.resolve(locale)
            // The level's own page shows it in full.
            if (description.isNotBlank()) addView(bodyText(description, 13f).apply {
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        header.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val locked = levelLocked(level.number)
        val tier = getString(if (LevelAccess.tierFor(level.number) == Tier.PRO) R.string.tier_pro else R.string.tier_plus)
        if (locked) header.addView(lockBadge(tier))
        addView(header)
        val complete = level.programs.count { it.id in completedIds }
        val percent = percentage(complete, level.programs.size)
        addWithMargins(
            bodyText(getString(R.string.level_progress_format, complete, level.programs.size), 13f),
            topDp = 12,
        )
        addWithMargins(horizontalProgress(percent), topDp = 7, height = dp(8))
        contentDescription = "${displayName(level, locale)}, ${getString(R.string.level_progress_format, complete, level.programs.size)}" +
            if (locked) ", $tier" else ""
        setOnClickListener { navigate(Screen.Workouts(level.number)) }
    }

    private fun replayCard(replay: LevelReplay, locale: Locale): LinearLayout = card().apply {
        val block = replay.fromProgramId != null
        addView(titleText(if (block) getString(R.string.exam_replay_title)
            else getString(R.string.replay_card_title, replay.levelNumber), 18f))
        val furthest = furthestProgram()
        val stopped = furthest?.let { "${getString(R.string.level_number, it.levelNumber)} · ${displayName(it, locale)}" }
        addWithMargins(bodyText(
            when {
                stopped == null -> getString(R.string.replay_card_body_complete)
                block -> getString(R.string.exam_replay_body, stopped)
                else -> getString(R.string.replay_card_body, stopped)
            },
            14f,
        ), topDp = 6)
        addWithMargins(secondaryButton(getString(R.string.replay_return)) {
            progressRepository.endLevelReplay()
            clearRest(); openRoot(Screen.Levels)
        }, topDp = 12)
    }

    private fun requestLevelReplay(level: TrainingLevel) {
        AlertDialog.Builder(this).setTitle(getString(R.string.replay_dialog_title, level.number))
            .setMessage(getString(R.string.replay_dialog_message, displayName(level, uiLocale())))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.replay_confirm) { _, _ ->
                progressRepository.startLevelReplay(level.number)
                clearRest(); openRoot(Screen.Dashboard)
            }.show()
    }

    private fun renderWorkouts(levelNumber: Int) {
        val level = catalog?.levels?.firstOrNull { it.number == levelNumber } ?: return
        val locale = uiLocale()
        val completed = progressRepository.completedProgramIds()
        // Classes the skill switches leave with nothing to train count as passed, greyed in the list.
        val switchedOff = passedIds() - completed
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            addView(titleText(displayName(level, locale), 23f))
            level.description.resolve(locale).takeIf(String::isNotBlank)?.let {
                addWithMargins(bodyText(it, 14f), topDp = 4)
            }
            val count = level.programs.count { it.id in completed || it.id in switchedOff }
            addWithMargins(
                bodyText(getString(R.string.level_progress_format, count, level.programs.size), 13f),
                topDp = 8,
            )
            addWithMargins(
                horizontalProgress(percentage(count, level.programs.size)),
                topDp = 6,
                height = dp(8),
            )
        }
        val locked = levelLocked(level.number)
        val assigned = assignedProgram()
        // A locked level offers the purchase instead of "Complete level", so the lock can't be skipped.
        if (locked) summary.addWithMargins(TiersPanel(this, billing, Paywall.Level(level.number)), topDp = 14)
        if (assigned?.levelNumber == level.number && !locked) {
            // Level 1 is a gentle on-ramp: someone who already trains can move on in one step (not while replaying it).
            if (level.number == 1 && levelReplay() == null) {
                summary.addWithMargins(bodyText(getString(R.string.placement_level_one_hint), 13f), topDp = 12)
            }
            summary.addWithMargins(secondaryButton("Complete level") {
                AlertDialog.Builder(this).setTitle("Complete this level?")
                    .setMessage("Mark all ${level.programs.size} days in ${displayName(level, locale)} complete.\n\n" +
                        getString(R.string.complete_level_no_points))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Complete") { _, _ ->
                        progressRepository.completePrograms(level.programs.map { it.id })
                        cloudChanged()
                        clearRest(); openRoot(Screen.Levels)
                    }.show()
            }, topDp = 10)
            summary.addWithMargins(noPointsNote(R.string.complete_level_no_points), topDp = 6)
        }
        // Any level before the furthest day can be trained again; nothing completed is undone.
        val furthestLevel = furthestProgram()?.levelNumber ?: Int.MAX_VALUE
        if (level.number < furthestLevel && assigned?.levelNumber != level.number && !locked) {
            summary.addWithMargins(secondaryButton(getString(R.string.replay_go_back)) { requestLevelReplay(level) }, topDp = 10)
        }
        summary.addWithMargins(secondaryButton(getString(R.string.rename_level)) { renameLevel(level) }, topDp = 10)
        val programs = level.programs.sortedBy { it.number }
        val list = ListView(this).apply {
            addHeaderView(summary, null, false)
            adapter = ProgramListAdapter(context, programs, completed, assigned?.id, locale, progressRepository.examLog(), switchedOff)
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = 0
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(16))
            setOnItemClickListener { _, _, position, _ ->
                programs.getOrNull(position - headerViewsCount)?.let { navigate(Screen.Overview(it.id)) }
            }
        }
        container.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setBody(container)
    }

    private fun renderOverview(programId: String) {
        val program = programById(programId) ?: return
        val locale = uiLocale()
        val page = verticalPage()
        // Developer inspection opens a day not reached yet (or behind the paywall) read-only: no Start, Complete or save.
        val inspecting = settings.inspectAllDays && !progressRepository.isProgramCompleted(program.id) &&
            (levelLocked(program.levelNumber) || !canStart(program.id))
        if (!inspecting && levelLocked(program.levelNumber) && !progressRepository.isProgramCompleted(program.id)) {
            page.addWithMargins(card().apply {
                addView(titleText(displayName(program, locale), 25f))
                addWithMargins(bodyText(listOf(getString(R.string.level_number, program.levelNumber), programMeta(program))
                    .filter(String::isNotBlank).joinToString(" • "), 14f), topDp = 4)
            }, bottomDp = 14)
            page.addView(TiersPanel(this, billing, Paywall.Level(program.levelNumber)))
            setBody(scroll(page))
            return
        }
        if (!inspecting && !canStart(program.id) && !progressRepository.isProgramCompleted(program.id)) {
            page.addView(card().apply {
                addView(titleText(displayName(program, locale), 25f))
                addWithMargins(bodyText("This daily plan is locked."), topDp = 12)
                addWithMargins(primaryButton(getString(R.string.assigned_later), AppColors.TEXT_SECONDARY) {}.apply { isEnabled = false }, topDp = 16)
            })
            setBody(scroll(page))
            return
        }

        val custom = CustomDays.isCustom(program.id)
        val assigned = assignedProgram()
        val active = progressRepository.activeWorkout()
        // Today's day starts as usual (during a level replay too); any other completed day can be repeated.
        val repeatable = !custom && assigned?.id != program.id && canRepeat(program.id)
        val repeatResume = active?.programId == program.id && active.repeat
        val hero = card()
        if (inspecting) hero.addWithMargins(badge(getString(R.string.inspect_only)), width = ViewGroup.LayoutParams.WRAP_CONTENT, bottomDp = 8)
        val title = titleText(displayName(program, locale), 25f)
        if (inspecting || (custom && !Entitlement.tier(this).lists)) hero.addView(title) else hero.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(heartButton(program.id))
        })
        val meta = if (custom) customMeta(program.exercises.size, program.estimatedMinutes ?: 1)
        else buildList {
            add(getString(R.string.level_number, program.levelNumber))
            programMeta(program).takeIf(String::isNotBlank)?.let(::add)
            // Easier or Harder, on a day whose strength work it changed.
            currentIntensity().takeIf { it != Intensity.NORMAL && !program.isExam && program.exercises.any(Intensity::scales) }
                ?.let { add(intensityLabels()[it.ordinal]) }
        }.joinToString(" • ")
        hero.addWithMargins(bodyText(meta, 14f), topDp = 4)
        if (!custom) hero.addWithMargins(
            bodyText(
                program.description.resolve(locale).ifBlank { getString(R.string.empty_description) },
            ),
            topDp = 12,
        )
        hero.addWithMargins(safetyLine(program), topDp = 6)
        if (custom) {
            hero.addWithMargins(secondaryButton(getString(R.string.custom_edit)) { requestEditDay(program.id) }, topDp = 12)
            hero.addWithMargins(secondaryButton(getString(R.string.custom_delete)) {
                AlertDialog.Builder(this).setTitle(R.string.custom_delete_title)
                    .setMessage(getString(R.string.custom_delete_message, displayName(program, locale)))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        if (progressRepository.activeWorkout()?.programId == program.id) { clearRest(); progressRepository.abandonWorkout() }
                        library.deleteDay(program.id)
                        onBackPressed()
                    }.show()
            }, topDp = 8)
        }
        // Also at the top, so a completed day can be repeated without scrolling past every exercise.
        if (repeatable) {
            hero.addWithMargins(bodyText(getString(R.string.repeat_note), 13f).apply {
                setTextColor(AppColors.PRIMARY)
            }, topDp = 12)
            hero.addWithMargins(repeatButton(program, repeatResume), topDp = 10)
        }
        page.addWithMargins(hero, bottomDp = 14)
        if (program.isExam) page.addWithMargins(examCard(program, locale), bottomDp = 14)

        if (!custom && program.switchedOff(settings.skillSwitches) && program.id !in progressRepository.completedProgramIds()) {
            page.addWithMargins(card().apply {
                background = roundedDrawable(AppColors.INFO_SURFACE, dp(24).toFloat())
                addView(bodyText(getString(R.string.switch_off_class), 14f))
            }, bottomDp = 14)
        }
        val sections = sections(program)
        if (sections.all { it.exercises.isEmpty() }) {
            page.addWithMargins(card().apply { addView(bodyText(getString(R.string.no_exercises))) }, bottomDp = 14)
        } else {
            sections.filter { it.exercises.isNotEmpty() }.forEach { section ->
                addExerciseSection(page, program, section, locale)
            }
            if (inspecting) {
                page.addWithMargins(bodyText(getString(R.string.inspect_only_note), 14f), topDp = 8)
                page.addWithMargins(primaryButton(getString(R.string.inspect_only), AppColors.TEXT_SECONDARY) {}.apply {
                    isEnabled = false
                }, topDp = 8)
            } else if (custom || assigned?.id == program.id) {
                val isResume = active?.programId == program.id && !active.repeat
                page.addWithMargins(workoutViewSetting(settings), topDp = 4)
                if (!custom) page.addWithMargins(intensityRow { renderOverview(program.id) }, topDp = 8)
                val start = primaryButton(
                    getString(when {
                        isResume -> R.string.resume_workout
                        else -> R.string.start_workout
                    }),
                ) { requestStart(program) }
                start.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow_white_24dp, 0, 0, 0)
                start.compoundDrawablePadding = dp(8)
                page.addWithMargins(start, topDp = 8)
                // Complete day moves the sequence on, so only the assigned day offers it.
                if (!custom) {
                    page.addWithMargins(secondaryButton("Complete day") { requestCompleteDay(program) }, topDp = 8)
                    page.addWithMargins(noPointsNote(R.string.complete_day_no_points), topDp = 6)
                }
            } else if (repeatable) {
                page.addWithMargins(bodyText(getString(R.string.sequence_completed_note), 14f), topDp = 8)
                page.addWithMargins(workoutViewSetting(settings), topDp = 4)
                page.addWithMargins(intensityRow { renderOverview(program.id) }, topDp = 8)
                page.addWithMargins(repeatButton(program, repeatResume), topDp = 8)
                page.addWithMargins(bodyText(getString(R.string.repeat_note), 13f), topDp = 8)
            } else {
                val completed = progressRepository.isProgramCompleted(program.id)
                page.addWithMargins(
                    bodyText(
                        getString(
                            if (completed) R.string.sequence_completed_note
                            else R.string.sequence_locked_note,
                        ),
                        14f,
                    ),
                    topDp = 8,
                )
                val locked = primaryButton(
                    getString(
                        if (completed) R.string.workout_completed
                        else R.string.assigned_later,
                    ),
                    AppColors.TEXT_SECONDARY,
                ) {}
                locked.isEnabled = false
                page.addWithMargins(locked, topDp = 8)
            }
        }
        setBody(scroll(page))
    }

    private fun addExerciseSection(
        page: LinearLayout,
        program: TrainingProgram,
        section: ExerciseSection,
        locale: Locale,
    ) {
        page.addView(sectionLabel(getString(section.titleRes)))
        section.exercises.forEachIndexed { index, exercise ->
            val exerciseNumber = exercise.number.takeIf { it > 0 } ?: index + 1
            val exerciseName = displayName(exercise, locale)
            val exerciseCard = card()
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    numberCircle(
                        exerciseNumber.toString(),
                        getString(R.string.exercise_number, exerciseNumber),
                    ),
                    LinearLayout.LayoutParams(dp(40), dp(40)),
                )
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                        addView(titleText(exerciseName, 18f))
                        addView(bodyText(resources.getQuantityString(R.plurals.sets_count, exercise.sets.size, exercise.sets.size), 13f))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            exerciseCard.addView(heading)
            leftOutBy(program, section.key, exercise, index)?.let { family ->
                exerciseCard.alpha = 0.45f
                exerciseCard.addWithMargins(badge(getString(R.string.switch_off_badge, family.name), AppColors.TEXT_SECONDARY),
                    width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 8)
            }
            exerciseCard.addWithMargins(
                exerciseAnimation(exercise, exerciseName, locale),
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topDp = 10,
            )
            exerciseNotes(exercise, locale).takeIf(String::isNotBlank)?.let {
                exerciseCard.addWithMargins(bodyText(it, 14f), topDp = 10)
            }
            exercise.sets.forEach { set ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, dp(8), 0, 0)
                    addView(
                        TextView(context).apply {
                            text = NumberFormat.getIntegerInstance(locale).format(set.number)
                            gravity = Gravity.CENTER
                            textSize = 12f
                            setTextColor(AppColors.ON_ACCENT)
                            background = roundedDrawable(AppColors.NUMBER_DARK, dp(13).toFloat())
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        LinearLayout.LayoutParams(dp(26), dp(26)),
                    )
                    addView(
                        bodyText(setPrescription(set, locale), 13f).apply {
                            setPadding(dp(10), dp(2), 0, 0)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                }
                exerciseCard.addView(row)
            }
            page.addWithMargins(exerciseCard, bottomDp = 10)
        }
        page.addWithMargins(ManagedNativePlacement(this, training = true), bottomDp = 14)
    }

    private fun repeatButton(program: TrainingProgram, resume: Boolean): View =
        primaryButton(getString(if (resume) R.string.resume_workout else R.string.repeat_day)) {
            requestStart(program, repeat = true)
        }.apply {
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow_white_24dp, 0, 0, 0)
            compoundDrawablePadding = dp(8)
        }

    /** Today's day, or with [repeat] a completed day again. Another active workout is only replaced on confirmation. */
    private fun requestStart(program: TrainingProgram, repeat: Boolean = false) {
        if (!(if (repeat) canRepeat(program.id) else canStart(program.id))) {
            Toast.makeText(this, R.string.sequence_locked_note, Toast.LENGTH_LONG).show()
            return
        }
        val current = progressRepository.activeWorkout()
        if (current?.programId != program.id) offAllowed.remove(program.id)
        if (current != null && (current.programId != program.id || current.repeat != repeat)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.replace_workout_title)
                .setMessage(R.string.replace_workout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.replace) { _, _ ->
                    clearRest()
                    progressRepository.abandonWorkout()
                    progressRepository.startWorkout(program.id, repeat = repeat, intensity = intensityFor(program))
                    navigate(Screen.Active(program.id))
                }
                .show()
            return
        }
        if (current == null) progressRepository.startWorkout(program.id, repeat = repeat, intensity = intensityFor(program))
        navigate(Screen.Active(program.id))
    }

    /** Today's pick for a program day; an own day runs as the athlete wrote it. */
    private fun intensityFor(program: TrainingProgram): Intensity =
        if (CustomDays.isCustom(program.id)) Intensity.NORMAL else settings.intensityToday()

    private fun renderActive(programId: String) {
        val program = programById(programId) ?: return
        val active = progressRepository.activeWorkout()
        if (active?.programId != program.id || !canRun(active)) {
            openRoot(Screen.Dashboard)
            return
        }
        val locale = uiLocale()
        val sections = sections(program).filter { it.exercises.isNotEmpty() }
        if (restProgramId != program.id) clearRest()
        val flow = workoutFlow(program)
        // Sets of switched-off skills (review 12) are greyed and left out of the count until the athlete does them anyway.
        val leftOut = leftOutKeys(program, flow)
        val allSetKeys = flow.keys
        fun counted() = allSetKeys.filter { it !in leftOut }
        var doneCount = counted().count { it in active.completedSetIds }
        val setChecks = linkedMapOf<String, CheckBox>()
        val sectionUpdates = mutableListOf<(Set<String>) -> Unit>()
        val countLabel = bodyText(getString(R.string.active_sets_progress, doneCount, counted().size), 14f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppColors.NUMBER_DARK)
        }
        val progressBar = horizontalProgress(percentage(doneCount, counted().size))
        fun refreshChecks() {
            val completed = progressRepository.activeWorkout()?.completedSetIds.orEmpty()
            val counted = counted()
            doneCount = counted.count { it in completed }
            countLabel.text = getString(R.string.active_sets_progress, doneCount, counted.size)
            progressBar.progress = percentage(doneCount, counted.size)
            setChecks.forEach { (key, view) ->
                view.isChecked = key in completed
                view.buttonTintList = ColorStateList.valueOf(if (view.isChecked) AppColors.SUCCESS else AppColors.TEXT_SECONDARY)
            }
            sectionUpdates.forEach { it(completed) }
        }

        val page = verticalPage()
        val status = card().apply {
            background = roundedDrawable(AppColors.INFO_SURFACE, dp(24).toFloat())
            if (active.repeat) addWithMargins(bodyText(getString(R.string.repeat_note), 12f).apply {
                setTextColor(AppColors.PRIMARY)
            }, bottomDp = 4)
            addView(titleText(displayName(program, locale), 22f))
            val minutes = ((System.currentTimeMillis() - active.startedAtEpochMillis) / 60_000L)
                .coerceAtLeast(0L)
            addWithMargins(bodyText(getString(R.string.started_minutes_ago, minutes), 13f), topDp = 3)
            addWithMargins(countLabel, topDp = 10)
            addWithMargins(
                progressBar,
                topDp = 7,
                height = dp(9),
            )
            restStatusView = bodyText(getString(R.string.rest_ready), 14f).apply {
                gravity = Gravity.CENTER
                setTextColor(AppColors.ACTION)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedDrawable(AppColors.SURFACE, dp(16).toFloat())
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            addWithMargins(restStatusView!!, topDp = 12)
        }
        // A repeated day is already complete; it ends with Finish or End session.
        if (!active.repeat) {
            status.addWithMargins(secondaryButton("Complete day") { requestCompleteDay(program) }, topDp = 12)
            status.addWithMargins(noPointsNote(R.string.complete_day_no_points), topDp = 6)
        }
        page.addWithMargins(status, bottomDp = 6)
        page.addWithMargins(safetyLine(program), leftDp = 4, rightDp = 4, bottomDp = 10)

        sections.forEach { section ->
            val keys = flow.keys(section.key)
            val sectionName = getString(section.titleRes)
            val sectionCount = bodyText("", 12f).apply { gravity = Gravity.CENTER }
            val sectionCheck = CheckBox(this).apply {
                buttonTintList = ColorStateList.valueOf(AppColors.PRIMARY)
                contentDescription = getString(R.string.section_check_description, sectionName)
                isEnabled = keys.isNotEmpty()
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                addView(sectionLabel(sectionName), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(sectionCount, LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(sectionCheck, LinearLayout.LayoutParams(dp(48), dp(48)))
                setOnClickListener { sectionCheck.performClick() }
            }
            sectionUpdates += { completed ->
                val live = keys.filter { it !in leftOut }
                val count = live.count { it in completed }
                sectionCheck.isChecked = live.isNotEmpty() && count == live.size
                sectionCount.text = "$count/${live.size}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sectionCheck.stateDescription = getString(R.string.active_sets_progress, count, live.size)
                }
            }
            sectionCheck.setOnClickListener {
                progressRepository.setCompleted(keys.filter { it !in leftOut }, sectionCheck.isChecked)
                refreshChecks()
                if (doneCount == counted().size) clearRest()
                scheduleRestUpdates()
                if (sectionCheck.isChecked && restEndsAtEpochMillis == null) adBreak.atNaturalBreak()
            }
            page.addView(header)
            section.exercises.forEachIndexed { exerciseIndex, exercise ->
                val exerciseName = displayName(exercise, locale)
                val exerciseCard = card()
                exerciseCard.addView(titleText(exerciseName, 18f))
                // A switched-off skill's exercise: greyed, and any tap asks before doing it in this workout anyway.
                val exerciseKey = WorkoutFlow.exerciseKey(section.key, exercise, exerciseIndex)
                val offFamily = leftOutBy(program, section.key, exercise, exerciseIndex)
                var off = offFamily != null
                val offBadge = offFamily?.let { family ->
                    badge(getString(R.string.switch_off_badge, family.name), AppColors.TEXT_SECONDARY).also {
                        exerciseCard.addWithMargins(it, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 6)
                    }
                }
                fun doAnyway(then: () -> Unit) = askSwitchedOff(listOfNotNull(offFamily?.name)) {
                    offAllowed.getOrPut(program.id, ::mutableSetOf) += exerciseKey
                    leftOut -= flow.steps.filter { it.exerciseKey == exerciseKey }.map { it.key }.toSet()
                    off = false
                    exerciseCard.alpha = 1f
                    offBadge?.visibility = View.GONE
                    refreshChecks()
                    then()
                }
                if (off) {
                    exerciseCard.alpha = 0.45f
                    exerciseCard.setOnClickListener { if (off) doAnyway {} }
                }
                exerciseCard.addWithMargins(
                    exerciseAnimation(exercise, exerciseName, locale),
                    height = ViewGroup.LayoutParams.WRAP_CONTENT,
                    topDp = 8,
                )
                exerciseNotes(exercise, locale).takeIf(String::isNotBlank)?.let {
                    exerciseCard.addWithMargins(bodyText(it, 13f), topDp = 4)
                }
                // The sets' logged numbers, redrawn when the easier option changes what they log.
                val doneLabels = mutableListOf<() -> Unit>()
                exercise.easier?.takeIf { section.key in WorkoutFlow.CREDITED_SECTIONS && !exercise.readinessCheck }?.let { option ->
                    val key = WorkoutFlow.exerciseKey(section.key, exercise, exerciseIndex)
                    val holder = FrameLayout(this)
                    fun bindEasier() {
                        holder.removeAllViews()
                        val on = key in progressRepository.activeWorkout()?.easierExercises.orEmpty()
                        holder.addView(easierToggle(option.name.resolve(locale), on) { next ->
                            switchEasier(program, key, next)
                            bindEasier()
                            doneLabels.forEach { it() }
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    bindEasier()
                    exerciseCard.addWithMargins(holder, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 8)
                }
                exercise.sets.forEachIndexed { setIndex, set ->
                    val setKey = WorkoutFlow.setKey(program.id, section.key, exercise, exerciseIndex, set, setIndex)
                    val step = WorkoutStep(section.key, exercise, exerciseIndex, set, setIndex, setKey)
                    val checked = setKey in active.completedSetIds
                    val credited = step.credited
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(52)
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    val checkBox = CheckBox(this).apply {
                        isChecked = checked
                        buttonTintList = ColorStateList.valueOf(
                            if (checked) AppColors.SUCCESS else AppColors.TEXT_SECONDARY,
                        )
                        contentDescription = getString(
                            R.string.set_complete_description,
                            set.number.takeIf { it > 0 } ?: setIndex + 1,
                            exerciseName,
                        )
                    }
                    val copy = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            oneLineText(
                                getString(
                                    R.string.set_number,
                                    set.number.takeIf { it > 0 } ?: setIndex + 1,
                                ),
                                bold = true,
                            ),
                        )
                        addView(bodyText(setPrescription(set, locale), 13f))
                    }
                    // What the athlete did, when it differs from the plan: skill levels count sets at a target.
                    val doneText = TextView(this).apply {
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(AppColors.PRIMARY)
                        setPadding(0, dp(4), 0, dp(4))
                        text = setDoneLabel(measureOf(step), active.done[setKey])
                    }
                    fun logDone(amount: Int?) {
                        progressRepository.setDone(setKey, amount)
                        doneText.text = setDoneLabel(measureOf(step), amount)
                        if (!checkBox.isChecked) checkBox.performClick()
                    }
                    if (credited) {
                        doneText.setOnClickListener { askSetDone(measureOf(step), progressRepository.activeWorkout()?.done?.get(setKey), ::logDone) }
                        copy.addView(doneText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        doneLabels += { doneText.text = setDoneLabel(measureOf(step), progressRepository.activeWorkout()?.done?.get(setKey)) }
                    }
                    row.addView(checkBox, LinearLayout.LayoutParams(dp(48), dp(48)))
                    row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    setChecks[setKey] = checkBox
                    checkBox.setOnClickListener {
                        if (off) {
                            checkBox.isChecked = false
                            doAnyway { checkBox.performClick() }
                            return@setOnClickListener
                        }
                        progressRepository.setCompleted(setKey, checkBox.isChecked)
                        refreshChecks()
                        // A "max" set, a practice window or an easier option measured the other way has no number to
                        // count until the athlete gives it.
                        val measure = measureOf(step)
                        if (checkBox.isChecked && credited && measure.planned == null &&
                            progressRepository.activeWorkout()?.done?.get(setKey) == null) askSetDone(measure, null, ::logDone)
                        if (checkBox.isChecked) {
                            if (doneCount >= counted().size) clearRest()
                            else set.breakSeconds?.takeIf { it > 0 }?.let(::startRest)
                        }
                        scheduleRestUpdates()
                    }
                    row.setOnClickListener { checkBox.performClick() }
                    exerciseCard.addView(row)
                }
                page.addWithMargins(exerciseCard, bottomDp = 10)
            }
            page.addWithMargins(ManagedNativePlacement(this, training = true), bottomDp = 14)
        }

        page.addWithMargins(primaryButton(getString(R.string.finish_workout)) {
            requestFinish(program, doneCount, counted().size)
        }, topDp = 8)
        page.addWithMargins(secondaryButton(getString(R.string.abandon_workout)) {
            requestAbandon()
        }, topDp = 6)
        refreshChecks()
        setBody(scroll(page))
        scheduleRestUpdates()
    }

    /** One set per screen. Shares set keys, rest timer, ad breaks and finish flow with renderActive. */
    private fun renderFocus(programId: String) {
        val program = programById(programId) ?: return
        val active = progressRepository.activeWorkout()
        if (active?.programId != program.id || !canRun(active)) {
            openRoot(Screen.Dashboard)
            return
        }
        if (restProgramId != program.id) clearRest()
        restStatusView = null
        bottomNavigation.visibility = View.GONE
        val flow = workoutFlow(program)
        val leftOut = leftOutKeys(program, flow)
        val host = object : FocusWorkoutView.Host {
            override fun completedSets() = progressRepository.activeWorkout()?.completedSetIds.orEmpty()
            override fun setCompleted(key: String, completed: Boolean) = progressRepository.setCompleted(key, completed)
            override fun restEndsAt() = restEndsAtEpochMillis
            override fun startRest(seconds: Int) {
                this@HomeActivity.startRest(seconds)
                scheduleRestUpdates()
            }
            override fun addRest(seconds: Int) {
                restEndsAtEpochMillis = restEndsAtEpochMillis?.plus(seconds * 1_000L)
                scheduleRestUpdates()
            }
            override fun skipRest() = clearRest()
            override fun sectionComplete() {
                if ((restEndsAtEpochMillis ?: 0L) <= System.currentTimeMillis()) adBreak.atNaturalBreak()
            }
            override fun holdFinished() {
                if (settings.soundCues) TimingAlerts.tone()
                TimingAlerts.vibrate(this@HomeActivity)
            }
            override fun finish(completedSets: Int, totalSets: Int) = requestFinish(program, completedSets, totalSets)
            override fun easierExercises() = progressRepository.activeWorkout()?.easierExercises.orEmpty()
            override fun doneAmounts() = progressRepository.activeWorkout()?.done.orEmpty()
            override fun setDoneAmount(key: String, amount: Int?) = progressRepository.setDone(key, amount)
            override fun setEasier(exerciseKey: String, easier: Boolean) = switchEasier(program, exerciseKey, easier)
            override fun measure(step: WorkoutStep) = measureOf(step)
            override fun leftOut() = leftOut.size
            override fun includeLeftOut() {
                val steps = flow.steps.filter { it.key in leftOut }
                askSwitchedOff(steps.mapNotNull { leftOutBy(program, it.section, it.exercise, it.exerciseIndex)?.name }) {
                    offAllowed.getOrPut(program.id, ::mutableSetOf) += steps.map { it.exerciseKey }
                    renderFocus(programId)
                }
            }
        }
        val titles = sections(program).associate { it.key to getString(it.titleRes) }
        setBody(FocusWorkoutView(this, program, flow.without(leftOut), titles, active.startedAtEpochMillis, uiLocale(), host))
        scheduleRestUpdates()
    }

    private fun bindWorkoutViewAction(screen: Screen) {
        val focus = settings.workoutView == WorkoutView.FOCUS
        toolbarAction.visibility = if (screen is Screen.Active) View.VISIBLE else View.GONE
        toolbarAction.setImageResource(if (focus) R.drawable.ic_view_list else R.drawable.ic_view_focus)
        toolbarAction.contentDescription = getString(if (focus) R.string.switch_to_list else R.string.switch_to_focus)
    }

    private fun switchWorkoutView() {
        settings.workoutView = if (settings.workoutView == WorkoutView.FOCUS) WorkoutView.LIST else WorkoutView.FOCUS
        currentScreen?.let { showScreen(it, pushCurrent = false) }
    }

    private fun requestCompleteDay(program: TrainingProgram) {
        if (!canStart(program.id)) return
        AlertDialog.Builder(this).setTitle("Complete this day?")
            .setMessage(displayName(program, uiLocale()) + "\n\n" + getString(R.string.complete_day_no_points))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Complete") { _, _ ->
                progressRepository.completePrograms(listOf(program.id))
                cloudChanged()
                clearRest(); openRoot(Screen.Dashboard)
            }.show()
    }

    /** Under Complete day and Complete level: marking days done skips the training, and with it the skill points. */
    private fun noPointsNote(text: Int): TextView = bodyText(getString(text), 12f)

    private fun requestFinish(program: TrainingProgram, completedSets: Int, totalSets: Int) {
        val finishAction = { askEffortAndFinish(program) }
        if (completedSets < totalSets) {
            AlertDialog.Builder(this)
                .setTitle(R.string.finish_early_title)
                .setMessage(R.string.finish_early_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.finish) { _, _ -> finishAction() }
                .show()
        } else {
            finishAction()
        }
    }

    private fun askEffortAndFinish(program: TrainingProgram) {
        val values = (1..10).toList()
        val labels = values.map { getString(R.string.effort_item, it) }.toTypedArray()
        var selected = (program.targetRpe ?: 7) - 1
        AlertDialog.Builder(this)
            .setTitle(R.string.effort_title)
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.finish) { _, _ ->
                // Only the ticked sets count toward skills, with an easier option credited to the easier movement, each
                // with its reps or seconds.
                val active = progressRepository.activeWorkout()
                val flow = workoutFlow(program)
                val ticked = active?.completedSetIds.orEmpty()
                val easier = active?.easierExercises.orEmpty()
                val book = skillBook
                val credits = if (book == null) emptyMap() else flow.credits(ticked, easier, ::skillMovement)
                val doses = if (book == null) emptyMap()
                    else flow.doses(ticked, easier, active?.done.orEmpty(), ::skillMovement) { id -> book.skill(id)?.standard }
                // A repeat's rating is kept on its own history entry only; see WorkoutSession.
                val session = progressRepository.finishWorkout(effortRating = values[selected], credits = credits, doses = doses)
                cloudChanged()
                clearRest()
                Toast.makeText(this, if (session?.repeat == true) R.string.repeat_saved else R.string.workout_saved,
                    Toast.LENGTH_SHORT).show()
                backStack.clear()
                showScreen(
                    Screen.Progress,
                    pushCurrent = false,
                )
                if (session != null && program.isExam) askExam(program, flow.examTests(ticked, easier, active?.done.orEmpty())
                    .filterNot { (exercise, _) -> exercise.movementId?.let(settings.skillSwitches::isOff) == true }, offerRepeat = !session.repeat)
            }
            .show()
    }

    private fun requestAbandon() {
        AlertDialog.Builder(this)
            .setTitle(R.string.abandon_title)
            .setMessage(R.string.abandon_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.abandon) { _, _ ->
                progressRepository.abandonWorkout()
                clearRest()
                val destination = backStack.pollLast() ?: Screen.Dashboard
                showScreen(destination, pushCurrent = false)
            }
            .show()
    }

    private fun startRest(seconds: Int) {
        restProgramId = progressRepository.activeWorkout()?.programId
        restEndsAtEpochMillis = System.currentTimeMillis() + seconds * 1_000L
    }

    private fun clearRest() {
        restProgramId = null
        restEndsAtEpochMillis = null
        restTimer?.cancel()
        restTimer = null
        restStatusView?.text = getString(R.string.rest_ready)
    }

    private fun scheduleRestUpdates() {
        restTimer?.cancel()
        val end = restEndsAtEpochMillis ?: run {
            restStatusView?.text = getString(R.string.rest_ready)
            return
        }
        val remaining = end - System.currentTimeMillis()
        if (remaining <= 0L) {
            onRestFinished()
            return
        }
        restTimer = object : CountDownTimer(remaining, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1_000L).coerceAtLeast(1L)
                restStatusView?.text = getString(R.string.rest_countdown, seconds)
            }

            override fun onFinish() = onRestFinished()
        }.start()
    }

    private fun onRestFinished() {
        restEndsAtEpochMillis = null
        restStatusView?.text = getString(R.string.rest_ready)
        if (settings.soundCues) {
            runCatching {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            }
        }
    }

    private fun renderProgress() {
        val catalog = catalog ?: return
        val locale = uiLocale()
        val all = orderedPrograms
        val completedIds = progressRepository.completedProgramIds()
        val complete = all.count { it.id in completedIds }
        val percent = percentage(complete, all.size)
        val page = verticalPage()
        val skills = skillBook
        if (skills != null) {
            page.addWithMargins(
                segmentedSwitch(
                    listOf(getString(R.string.progress_tab_skills), getString(R.string.progress_tab_history),
                        getString(R.string.progress_tab_leaderboard)),
                    progressTab,
                ) { index ->
                    progressTab = index
                    renderProgress()
                },
                width = ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            if (progressTab == 2) {
                renderLeaderboard(page, skills)
                setBody(scroll(page))
                return
            }
            if (progressTab == 0) {
                val history = progressRepository.sessionHistory()
                val progress = skillProgress(skills, history)
                page.addWithMargins(
                    skillsContent(
                        progress,
                        Scores.markedComplete(history),
                        skillGroup,
                        onGroup = { group ->
                            skillGroup = group
                            renderProgress()
                        },
                        onOpen = { status, family, statuses ->
                            val switches = settings.skillSwitches
                            showSkillSheet(status, family, statuses, progress.trained(switches).overall,
                                off = switches.isRungOff(family.spec.id, status.skill.rung))
                        },
                        switches = settings.skillSwitches,
                    ),
                    topDp = 14,
                )
                setBody(scroll(page))
                return
            }
        }

        val overall = card().apply {
            addView(titleText("$percent%", 34f))
            addWithMargins(
                bodyText(getString(R.string.workouts_completed_format, complete, all.size)),
                topDp = 3,
            )
            addWithMargins(horizontalProgress(percent), topDp = 12, height = dp(10))
        }
        page.addWithMargins(overall, topDp = 14, bottomDp = 16)

        page.addView(sectionLabel(getString(R.string.level_breakdown)))
        catalog.levels.sortedBy { it.number }.forEach { level ->
            val levelComplete = level.programs.count { it.id in completedIds }
            val row = card(clickable = true).apply {
                addView(titleText(displayName(level, locale), 17f))
                addWithMargins(
                    bodyText(
                        getString(R.string.level_progress_format, levelComplete, level.programs.size),
                        13f,
                    ),
                    topDp = 3,
                )
                addWithMargins(
                    horizontalProgress(percentage(levelComplete, level.programs.size)),
                    topDp = 7,
                    height = dp(7),
                )
                setOnClickListener { navigate(Screen.Workouts(level.number)) }
            }
            page.addWithMargins(row, bottomDp = 8)
        }


        // The list or the month calendar ([historySection]); both use the same rows.
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        page.addView(historySection(settings, progressRepository.sessionHistory(), historyCalendar) { historyRow(it, formatter, locale) })
        setBody(scroll(page))
    }

    /** One history entry: the day and when it was finished. Tapping it opens that day's overview. */
    private fun historyRow(session: WorkoutSession, formatter: DateFormat, locale: Locale): View {
        val program = programById(session.programId)
        return card(clickable = program != null).apply {
            addView(
                titleText(
                    program?.let { displayName(it, locale) }
                        ?: if (CustomDays.isCustom(session.programId)) getString(R.string.custom_deleted) else session.programId,
                    17f,
                ),
            )
            addWithMargins(
                bodyText(
                    if (session.placed) getString(R.string.history_placed, formatter.format(Date(session.completedAtEpochMillis)))
                    else if (session.manuallyCompleted) "Marked complete · ${formatter.format(Date(session.completedAtEpochMillis))}"
                    else getString(
                        R.string.completed_on,
                        formatter.format(Date(session.completedAtEpochMillis)),
                        session.durationSeconds / 60L,
                    ).let { if (session.repeat) getString(R.string.history_repeat, it) else it }
                        .let { if (session.intensity != Intensity.NORMAL) "$it · ${intensityLabels()[session.intensity.ordinal]}" else it },
                    13f,
                ).apply { if (session.repeat) setTextColor(AppColors.PRIMARY) },
                topDp = 3,
            )
            program?.let { target ->
                setOnClickListener { navigate(Screen.Overview(target.id)) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun renderSettings() {
        val page = verticalPage()

        page.addView(sectionLabel(getString(R.string.theme_section)))
        page.addWithMargins(themePicker(settings.theme) { picked ->
            settings.theme = picked
            recreate()
        }, bottomDp = 18)
        // Only Pro signs in; a build without Firebase shows no account at all.
        if (Cloud.signInAvailable(this) && (Entitlement.tier(this).account || Cloud.uid(this) != null)) {
            page.addView(sectionLabel(getString(R.string.account_section)))
            page.addWithMargins(AccountSettingsView(this, cloudSync) { publishScore() }, bottomDp = 16)
        }
        val tiersPanel = TiersPanel(this, billing)
        page.addWithMargins(tiersPanel, bottomDp = 16)
        if (BuildConfig.DEBUG) page.addWithMargins(card().apply {
            addView(titleText(getString(R.string.simulate_tier), 17f))
            addWithMargins(bodyText(getString(R.string.simulate_tier_note), 13f), topDp = 4)
            val choices = listOf(null, Tier.FREE, Tier.PLUS, Tier.PRO)
            val labels = listOf(getString(R.string.simulate_tier_default), getString(R.string.tier_free),
                getString(R.string.tier_plus), getString(R.string.tier_pro))
            addWithMargins(segmentedSwitch(labels, choices.indexOf(Entitlement.simulatedTier(this@HomeActivity))) { index ->
                Entitlement.setSimulatedTier(this@HomeActivity, choices[index])
                renderSettings()
            }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 10)
        }, bottomDp = 16)
        page.addView(sectionLabel(getString(R.string.training_preferences)))
        val preferencesCard = card()
        preferencesCard.addView(settingSwitch(
            getString(R.string.keep_screen_awake),
            settings.keepScreenAwake,
        ) { settings.keepScreenAwake = it })
        preferencesCard.addView(settingSwitch(
            getString(R.string.sound_cues),
            settings.soundCues,
        ) { settings.soundCues = it })
        preferencesCard.addView(workoutViewSetting(settings))
        page.addWithMargins(preferencesCard, bottomDp = 16)
        skillBook?.let { book ->
            page.addView(sectionLabel(getString(R.string.switches_section)))
            page.addWithMargins(skillSwitchesCard(book, settings), bottomDp = 16)
        }
        page.addView(sectionLabel(getString(R.string.share_section)))
        page.addWithMargins(shareProgressNote(settings, inSettings = true), bottomDp = 16)
        page.addWithMargins(card().apply {
            addView(titleText(getString(R.string.reset_title), 19f))
            addWithMargins(bodyText(getString(if (Cloud.uid(this@HomeActivity) != null) R.string.reset_body_account
                else R.string.reset_body), 14f), topDp = 7)
            addWithMargins(secondaryButton(getString(R.string.reset_button)) { confirmReset() }, topDp = 12)
        }, bottomDp = 16)
        val safety = card().apply {
            background = roundedDrawable(AppColors.WARNING_SURFACE, dp(24).toFloat())
            addView(titleText(getString(R.string.safety_title), 19f))
            addWithMargins(bodyText(getString(R.string.safety_body), 14f), topDp = 7)
        }
        page.addWithMargins(safety, bottomDp = 16)

        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        val about = card().apply {
            addView(titleText(getString(R.string.about_title), 19f))
            addWithMargins(bodyText(getString(R.string.about_body), 14f), topDp = 7)
            addWithMargins(bodyText(getString(R.string.version_format, version), 12f).apply {
                setOnClickListener { developerMenu.onVersionTapped() }
            }, topDp = 10)
        }
        about.addWithMargins(secondaryButton("Ad privacy") { NativeAds.showPrivacy(this, BuildConfig.ADMOB_NATIVE_ID) }, topDp = 12)
        page.addView(about)
        setBody(scroll(page))
    }

    /**
     * Settings' Reset progress: after a confirmation every finished class, the history, skill levels and exam results go,
     * and the placement question comes back on Today. My workouts, the theme and purchases stay; signed in, the cloud
     * copy and the leaderboard score follow at the sync this starts.
     */
    private fun confirmReset() {
        AlertDialog.Builder(this).setTitle(R.string.reset_confirm_title).setMessage(R.string.reset_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_confirm) { _, _ ->
                clearRest()
                progressRepository.reset(System.currentTimeMillis())
                settings.placementDone = false
                placementAnswers.fill(-1)
                // The score goes up again from zero, at once rather than after the usual ten minutes.
                Cloud.uid(this)?.let { accountStore.savePublishedScore(it, null, 0) }
                cloudChanged()
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_LONG).show()
                openRoot(Screen.Dashboard)
            }.show()
    }

    private fun settingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            text = label
            textSize = 15f
            setTextColor(AppColors.TEXT)
            isChecked = checked
            minHeight = dp(56)
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }

    private fun sections(program: TrainingProgram): List<ExerciseSection> = listOf(
        ExerciseSection("warmup", R.string.warm_up, program.warmup),
        ExerciseSection("practice", R.string.skill_practice, program.practice),
        ExerciseSection("main",
            R.string.main_training,
            program.exercises),
        ExerciseSection("cooldown", R.string.cool_down, program.cooldown),
    )

    private fun exerciseAnimation(
        exercise: TrainingExercise,
        exerciseName: CharSequence,
        locale: Locale,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val preview = ExerciseAnimationView(context)
        val variations = exercise.sets.map { it.name.resolve(locale) }
            .filter(String::isNotBlank).distinct()
        fun showVariation(index: Int) {
            preview.bind(
                movementId = exercise.movementId,
                exerciseName = exerciseName,
                category = exercise.category,
                equipment = exercise.equipment,
                descriptor = variations.getOrNull(index) ?: exercise.description.resolve(locale),
            )
        }
        showVariation(0)
        addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)))
        if (exercise.movementId == null && variations.size > 1) {
            var variation = 0
            val change = secondaryButton(getString(R.string.animation_variation, 1, variations.size, variations[0])) { }
            change.setOnClickListener {
                variation = (variation + 1) % variations.size
                showVariation(variation)
                change.text = getString(R.string.animation_variation, variation + 1, variations.size, variations[variation])
            }
            addView(change)
        }
    }

    /** One quiet safety line on the overview and during a workout; it opens the class's readiness and safety notes. */
    private fun safetyLine(program: TrainingProgram): TextView {
        val link = getString(R.string.safety_notes) + " ›"
        val text = SpannableStringBuilder(getString(R.string.safety_line)).append(' ').append(link)
        val start = text.length - link.length
        text.setSpan(ForegroundColorSpan(AppColors.PRIMARY), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return bodyText(text, 13f).apply {
            isClickable = true
            isFocusable = true
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener {
                val notes = listOf(getString(R.string.readiness_note), program.readiness.resolve(), program.safety.resolve())
                    .filter(String::isNotBlank).joinToString("\n\n")
                AlertDialog.Builder(this@HomeActivity).setTitle(R.string.safety_notes).setMessage(notes)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }

    /**
     * The heart at the top right of a day's overview. On Free it saves the day to Favorites, or removes it; Plus and
     * Pro pick the lists in [showListPicker] (a custom day is always saved, so its heart only picks lists).
     */
    private fun heartButton(programId: String): ImageView = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { topMargin = -dp(8); marginEnd = -dp(10) }
        setPadding(dp(10), dp(10), dp(10), dp(10))
        val ripple = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) setBackgroundResource(ripple.resourceId)
        val custom = CustomDays.isCustom(programId)
        fun saved() = custom || library.isSaved(programId)
        fun show(saved: Boolean) {
            setImageResource(if (saved) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            imageTintList = ColorStateList.valueOf(if (saved) AppColors.HEART else AppColors.TEXT_SECONDARY)
            contentDescription = getString(when {
                !saved -> R.string.save_day
                Entitlement.tier(this@HomeActivity).lists -> R.string.saved_day_lists
                else -> R.string.saved_day
            })
        }
        fun pop() {
            if (!systemAnimationsEnabled()) return
            scaleX = 0.6f; scaleY = 0.6f
            animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(260).start()
        }
        show(saved())
        setOnClickListener {
            val saved = saved()
            if (!saved && !roomForAnother()) return@setOnClickListener
            val tier = Entitlement.tier(this@HomeActivity)
            if (tier.lists) {
                showListPicker(library, programId, tier, onMoreLists = { roomForAnotherList() }) { now ->
                    show(now)
                    if (now && !saved) pop()
                }
                return@setOnClickListener
            }
            library.setSaved(programId, !saved)
            show(!saved)
            if (!saved) pop()
            Toast.makeText(this@HomeActivity, if (saved) R.string.day_removed else R.string.day_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun workoutFlow(program: TrainingProgram): WorkoutFlow =
        WorkoutFlow.of(program.id, sections(program).map { it.key to it.exercises })

    /** The skill movement [exercise] trains, when the skill book is ready and knows it. */
    private fun skillMovement(exercise: TrainingExercise): String? =
        skillBook?.movementOf(exercise.movementId, exercise.name.resolve(Locale.ENGLISH), exercise.description.resolve(Locale.ENGLISH))

    /** How [step] is logged now, done as planned or as its easier option. */
    private fun measureOf(step: WorkoutStep): SetMeasure {
        val easier = progressRepository.activeWorkout()?.easierExercises.orEmpty()
        val id = WorkoutFlow.movement(step, easier, ::skillMovement)
        return WorkoutFlow.measure(step, id?.let { skillBook?.skill(it)?.standard })
    }

    /** Switches an exercise to or from its easier option; what was logged for its sets was the other movement. */
    private fun switchEasier(program: TrainingProgram, exerciseKey: String, easier: Boolean) {
        progressRepository.setEasier(exerciseKey, easier)
        val done = progressRepository.activeWorkout()?.done.orEmpty()
        workoutFlow(program).steps.filter { it.exerciseKey == exerciseKey && it.key in done }
            .forEach { progressRepository.setDone(it.key, null) }
    }

    private fun programById(id: String): TrainingProgram? =
        if (CustomDays.isCustom(id)) library.customDay(id)?.let(CustomDays::toProgram) else programIndex[id]

    /** Today's assigned day, or one of the athlete's own custom days, which start any time. */
    private fun canStart(id: String): Boolean =
        (assignedProgram()?.id == id || CustomDays.isCustom(id) && library.customDay(id) != null) &&
            programById(id)?.levelNumber?.let(::levelLocked) != true

    /** Any completed day can be trained again, in a level the athlete can open; days not reached yet cannot. */
    private fun canRepeat(id: String): Boolean =
        progressRepository.isProgramCompleted(id) && programById(id)?.levelNumber?.let(::levelLocked) == false

    private fun canRun(active: ActiveWorkout): Boolean =
        if (active.repeat) canRepeat(active.programId) else canStart(active.programId)

    private fun levelLocked(levelNumber: Int): Boolean = LevelAccess.locked(levelNumber, Entitlement.tier(this))

    private fun allPrograms(catalog: CurriculumCatalog): List<TrainingProgram> = NextClass.ordered(catalog)

    /** The next class in the sequence; the home-screen widget shows the same one ([NextClass]). */
    private fun assignedProgram(): TrainingProgram? {
        if (catalog == null) return null
        return NextClass.of(orderedPrograms, passedIds(), levelReplay())
    }

    /** Classes completed, or left with nothing to train by the skill switches: the sequence steps over both. */
    private fun passedIds(): Set<String> =
        NextClass.passed(orderedPrograms, progressRepository.completedProgramIds(), settings.skillSwitches)

    /** The athlete's skill switches for [program]; an own day runs as they built it. */
    private fun switchesFor(program: TrainingProgram): SkillSwitches =
        if (CustomDays.isCustom(program.id)) SkillSwitches() else settings.skillSwitches

    /** The skill that leaves [exercise] out of this workout, or null when it is in (on, or chosen anyway). */
    private fun leftOutBy(program: TrainingProgram, section: String, exercise: TrainingExercise, index: Int): SkillFamilySpec? {
        val family = exercise.movementId?.let(switchesFor(program)::offFamily) ?: return null
        if (WorkoutFlow.exerciseKey(section, exercise, index) in offAllowed[program.id].orEmpty()) return null
        return SkillBook.DEFAULT_FAMILIES.firstOrNull { it.id == family }
    }

    /** The set keys of [flow] that the skill switches leave out. */
    private fun leftOutKeys(program: TrainingProgram, flow: WorkoutFlow): MutableSet<String> =
        flow.steps.filter { leftOutBy(program, it.section, it.exercise, it.exerciseIndex) != null }.mapTo(mutableSetOf()) { it.key }

    /** Asks before a switched-off skill's exercise (the owner's words); [onYes] does it in this workout anyway. */
    private fun askSwitchedOff(families: List<String>, onYes: () -> Unit) {
        val names = families.distinct().joinToString(" & ")
        AlertDialog.Builder(this).setTitle(getString(R.string.switch_off_title, names))
            .setMessage(getString(R.string.switch_off_message, names))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.switch_off_continue) { _, _ -> onYes() }
            .show()
    }

    /** The first day never completed: where the athlete stopped, whatever they repeat. */
    private fun furthestProgram(): TrainingProgram? {
        if (catalog == null) return null
        val completed = passedIds()
        return orderedPrograms.firstOrNull { it.id !in completed }
    }

    /** The level replay in effect, or null. One that has caught up with the furthest day is ended here. */
    private fun levelReplay(): LevelReplay? {
        if (catalog == null) return null
        val replay = progressRepository.levelReplay() ?: return null
        if (DayAssignment.replaying(orderedPrograms, passedIds(), replay,
                TrainingProgram::id, TrainingProgram::levelNumber)) return replay
        progressRepository.endLevelReplay()
        return null
    }

    private fun percentage(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else ((part.toDouble() / whole.toDouble()) * 100.0).toInt().coerceIn(0, 100)

    private fun uiLocale(): Locale = settings.selectedLocale()

    private fun scroll(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun setBody(view: View) {
        view.applyThemeType()
        bodyContainer.removeAllViews()
        bodyContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // Off screen the Parks map is stopped, not destroyed, so coming back to Parks shows it at once, as it was,
        // instead of loading the style and clustering every park again.
        parksMap?.let { map ->
            val shown = generateSequence(map.view as View?) { it.parent as? View }.any { it === view }
            if (!shown && !parksMapHidden) {
                (map.view.parent as? ViewGroup)?.removeView(map.view)
                map.stop()
                parksMapHidden = true
            } else if (shown && parksMapHidden) {
                parksMapHidden = false
                if (activityResumed) map.resume() else map.start()
            }
        }
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Dashboard -> getString(R.string.dashboard_title)
        Screen.Levels -> getString(R.string.levels_title)
        is Screen.Workouts -> getString(R.string.workouts_title, screen.levelNumber)
        is Screen.Overview -> getString(R.string.workout_overview)
        is Screen.Active -> getString(
            if (progressRepository.activeWorkout()?.repeat == true) R.string.repeat_active_title else R.string.active_workout,
        )
        is Screen.EditDay -> getString(R.string.builder_title)
        Screen.Progress -> getString(R.string.progress_title)
        Screen.Settings -> getString(R.string.settings_title)
        Screen.Timing -> getString(R.string.nav_timer)
    }

    private fun isNested(screen: Screen): Boolean =
        screen is Screen.Workouts || screen is Screen.Overview || screen is Screen.Active || screen is Screen.EditDay

    private fun rootFor(screen: Screen): Screen = when (screen) {
        is Screen.Workouts, is Screen.Overview, is Screen.Active, is Screen.EditDay -> Screen.Levels
        else -> screen
    }

    private fun isValidScreen(screen: Screen): Boolean = when (screen) {
        is Screen.Workouts -> catalog?.levels?.any { it.number == screen.levelNumber } == true
        is Screen.Overview -> programById(screen.programId) != null
        is Screen.Active -> programById(screen.programId) != null &&
            progressRepository.activeWorkout()?.takeIf { it.programId == screen.programId }?.let(::canRun) == true
        is Screen.EditDay -> draft?.id == screen.dayId || library.customDay(screen.dayId) != null
        else -> true
    }

    private fun screenFromBundle(bundle: Bundle): Screen? = when (bundle.getString(STATE_SCREEN)) {
        Screen.Dashboard.javaClass.simpleName -> Screen.Dashboard
        Screen.Levels.javaClass.simpleName -> Screen.Levels
        Screen.Progress.javaClass.simpleName -> Screen.Progress
        Screen.Settings.javaClass.simpleName -> Screen.Settings
        Screen.Timing.javaClass.simpleName -> Screen.Timing
        Screen.Workouts::class.java.simpleName -> Screen.Workouts(
            bundle.getInt(STATE_LEVEL, -1),
        )
        Screen.Overview::class.java.simpleName -> bundle.getString(STATE_PROGRAM)?.let(Screen::Overview)
        Screen.Active::class.java.simpleName -> bundle.getString(STATE_PROGRAM)?.let(Screen::Active)
        Screen.EditDay::class.java.simpleName -> bundle.getString(STATE_PROGRAM)?.let(Screen::EditDay)
        else -> null
    }

    companion object {
        /**
         * The parsed catalog, kept for the process: rotating the phone, changing the theme or dark mode recreates the
         * activity, and parsing the whole curriculum again each time would show the loading screen for nothing.
         */
        @Volatile private var loaded: LoadedCatalog? = null
        /** The parsed catalog once the app has read it, so the home-screen widget doesn't parse it again. */
        internal val loadedCatalog: CurriculumCatalog? get() = loaded?.catalog
        /** The longest name for a custom day or a renamed level: room for any built-in one, short enough for a card. */
        private const val MAX_NAME_LENGTH = 60
        private const val REQUEST_LOCATION = 720
        /** In ParksUpdater's preferences: the list instead of the map, and whether the map asked for location yet. */
        private const val KEY_PARKS_LIST = "show_list"
        private const val KEY_LOCATION_ASKED = "location_asked"
        /** How many of the nearest parks the map frames around the athlete. */
        private const val NEARBY_ON_MAP = 5
        private const val STATE_DRAFT = "draft_day"
        private const val STATE_TRAINING_TAB = "training_tab"
        private const val STATE_WORKOUT_LIST = "workout_list"
        private const val STATE_PLACEMENT = "placement_answers"
        private const val STATE_SCREEN = "screen"
        private const val STATE_LEVEL = "level"
        private const val STATE_PROGRAM = "program"
        private const val STATE_REST_END = "rest_end_epoch_millis"
        private const val STATE_REST_PROGRAM = "rest_program"
        /** Switched-off exercises the athlete chose to do anyway, by class: kept for the workout across recreation. */
        private val offAllowed = HashMap<String, MutableSet<String>>()
    }
}
