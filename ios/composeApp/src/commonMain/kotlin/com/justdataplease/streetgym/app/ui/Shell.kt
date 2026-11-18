package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.data.WidgetLinks
import com.justdataplease.streetgym.app.resources.account_delete_failed
import com.justdataplease.streetgym.app.resources.account_deleted
import com.justdataplease.streetgym.app.resources.leaderboard_blocked
import com.justdataplease.streetgym.app.resources.profile_delete_failed
import com.justdataplease.streetgym.app.resources.profile_deleted
import kotlinx.coroutines.delay
import com.justdataplease.streetgym.app.resources.builder_title
import com.justdataplease.streetgym.app.resources.lists_limit_pro
import com.justdataplease.streetgym.app.resources.saved_limit_pro
import com.justdataplease.streetgym.app.data.ExamQuestion
import com.justdataplease.streetgym.app.data.HistoryCalendar
import com.justdataplease.streetgym.app.resources.reset_done
import com.justdataplease.streetgym.app.data.Start
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.resources.exam_saved_not_yet
import com.justdataplease.streetgym.app.resources.workouts_title
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.app.resources.ic_view_focus
import com.justdataplease.streetgym.app.resources.ic_view_list
import com.justdataplease.streetgym.app.resources.switch_to_focus
import com.justdataplease.streetgym.app.resources.switch_to_list
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.active_workout
import com.justdataplease.streetgym.app.resources.back
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.dashboard_title
import com.justdataplease.streetgym.app.resources.repeat_active_title
import com.justdataplease.streetgym.app.resources.repeat_points
import com.justdataplease.streetgym.app.resources.replace
import com.justdataplease.streetgym.app.resources.replace_workout_message
import com.justdataplease.streetgym.app.resources.replace_workout_title
import com.justdataplease.streetgym.app.resources.sequence_locked_note
import com.justdataplease.streetgym.app.resources.streak_days
import com.justdataplease.streetgym.app.resources.workout_points
import com.justdataplease.streetgym.app.resources.ic_brand_mark
import com.justdataplease.streetgym.app.resources.ic_nav_progress
import com.justdataplease.streetgym.app.resources.ic_nav_settings
import com.justdataplease.streetgym.app.resources.ic_nav_timer
import com.justdataplease.streetgym.app.resources.ic_nav_today
import com.justdataplease.streetgym.app.resources.ic_nav_training
import com.justdataplease.streetgym.app.resources.levels_title
import com.justdataplease.streetgym.app.resources.nav_levels
import com.justdataplease.streetgym.app.resources.nav_progress
import com.justdataplease.streetgym.app.resources.nav_settings
import com.justdataplease.streetgym.app.resources.nav_timer
import com.justdataplease.streetgym.app.resources.nav_today
import com.justdataplease.streetgym.app.resources.progress_title
import com.justdataplease.streetgym.app.resources.settings_title
import com.justdataplease.streetgym.app.resources.workout_overview
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where the app can be. The five tabs are roots; a level's classes open over Training, a class over Today or a level,
 * and its workout over that.
 */
sealed interface Screen {
    data object Today : Screen
    data object Training : Screen
    data object Progress : Screen
    data object Timer : Screen
    data object Settings : Screen
    /** A level's classes, "Level N workouts". */
    data class Level(val number: Int) : Screen
    data class Class(val programId: String) : Screen
    /** The class in progress, set by set. [askEffort] opens it on the effort question (for the screenshots). */
    data class Workout(val programId: String, val askEffort: Boolean = false) : Screen
    /** The builder, "Your workout", for the own workout [dayId] in [AppModel.draft]. */
    data class EditDay(val dayId: String) : Screen

    val nested: Boolean get() = this is Level || this is Class || this is Workout || this is EditDay
}

/** A question or result the shell shows over every screen; the screenshots open the app on one. */
sealed interface Shown {
    /** "Exam: tick the targets you reached", after an exam class. */
    data class Exam(val question: ExamQuestion) : Shown
    /** "Exam passed". */
    data object ExamPassed : Shown
    /** "Not every target yet": the [missed] targets of [exam], and Repeat N classes. */
    data class RepeatBlock(val exam: TrainingProgram, val missed: List<TrainingExercise>) : Shown
    /** The offer for what [paywall] locks: the plans that unlock it (Android: showPaywall). */
    data class Offer(val paywall: Paywall) : Shown
    /** The streak's sheet; [history] adds See history (from Today). */
    data class Streak(val history: Boolean) : Shown
    /** A skill's sheet, by the skill's id. */
    data class Skill(val id: String) : Shown
    /** "How do points and ranks work?" */
    data object SkillsHelp : Shown
    /** A skill family's steps and their switches, by the family's id. */
    data class Switches(val family: String) : Shown
    /** "Reset all progress?" */
    data object Reset : Shown
    /** The developer settings. */
    data object Developer : Shown
    /** "Your leaderboard profile" for the user [uid]. */
    data class Profile(val uid: String) : Shown
    /** "Delete your profile?" */
    data object DeleteProfile : Shown
    /** "Delete your account?" */
    data object DeleteAccount : Shown
    /** Report or block a name on the leaderboard [board] (Apple 1.2). */
    data class Report(val entry: com.justdataplease.streetgym.app.data.BoardEntry, val board: String) : Shown
}

/** A short note after an action (Android: a Toast). */
private sealed interface Notice {
    /** A finished class: its points, and the streak's days when it lit today's flame. */
    data class Saved(val repeat: Boolean, val points: Int, val streak: Int?) : Notice
    /** A class that can't start now. */
    data object Locked : Notice
    /** One of Android's notes, word for word. */
    data class Text(val resource: StringResource) : Notice
    /** A note already in words. */
    data class Plain(val text: String) : Notice
}

@Composable
private fun noticeText(notice: Notice): String = when (notice) {
    is Notice.Saved -> {
        val saved = stringResource(if (notice.repeat) Res.string.repeat_points else Res.string.workout_points, pointsText(notice.points))
        notice.streak?.let { "$saved · ${stringResource(Res.string.streak_days, it)}" } ?: saved
    }
    Notice.Locked -> stringResource(Res.string.sequence_locked_note)
    is Notice.Text -> stringResource(notice.resource)
    is Notice.Plain -> notice.text
}

private class Tab(val screen: Screen, val label: StringResource, val icon: DrawableResource)

private val TABS = listOf(
    Tab(Screen.Today, Res.string.nav_today, Res.drawable.ic_nav_today),
    Tab(Screen.Training, Res.string.nav_levels, Res.drawable.ic_nav_training),
    Tab(Screen.Progress, Res.string.nav_progress, Res.drawable.ic_nav_progress),
    Tab(Screen.Timer, Res.string.nav_timer, Res.drawable.ic_nav_timer),
    Tab(Screen.Settings, Res.string.nav_settings, Res.drawable.ic_nav_settings),
)

/**
 * The frame every screen sits in (android-streetgym, HomeActivity.buildShell): the page's grain behind everything, the
 * title bar, the screen, and the tab bar. [safeArea] keeps it clear of the Dynamic Island and the home indicator.
 * [showing] opens it with a question already up, [progressTab] on a tab of Progress, [historyDay] with a day picked
 * in History's calendar, [trainingTabAt] on a tab of Training and [workoutListAt] with a list of My workouts open (for
 * the screenshots). On a nested screen a swipe from the left edge goes back.
 */
@Composable
fun Shell(
    model: AppModel,
    safeArea: PaddingValues,
    startAt: Screen = Screen.Today,
    showing: Shown? = null,
    progressTab: Int = PROGRESS_SKILLS,
    historyDay: Int = 0,
    trainingTabAt: Int = 0,
    workoutListAt: String? = null,
    readyFilterAt: ReadyFilter = ReadyFilter(),
    restored: Screens? = null,
) {
    val theme = Street.theme
    // The screens under the one showing; the first is always a tab. A level's page opens over Training, a class over Today.
    // After the phone closed the app in the background, the screens it had (Android: restoredScreen).
    val stack = remember {
        restored?.let { mutableStateListOf(*it.stack.toTypedArray()) }
            ?: mutableStateListOf<Screen>(if (startAt.nested) (if (startAt is Screen.Level) Screen.Training else Screen.Today) else startAt)
                .apply { if (startAt.nested) add(startAt) }
    }
    val screen = stack.last()
    val overlay = remember { Overlay() }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var shown by remember { mutableStateOf(showing) }
    // Training's tab (Levels, My workouts, Library, Parks), kept while the app runs (Android: trainingTab).
    var trainingTab by remember { mutableIntStateOf(restored?.trainingTab ?: trainingTabAt) }
    // The list open in My workouts, null for Favorites (Android: workoutListId).
    var workoutList by remember { mutableStateOf(restored?.workoutList ?: workoutListAt) }
    // The Library's filters, kept while the app runs (Android: readyEffort, readyGrade, readyFocus).
    var readyFilter by remember { mutableStateOf(readyFilterAt) }
    // Progress's tab (Skills, History, Leaderboard) and History's month and day, kept while the app runs (Android:
    // progressTab, HistoryCalendarState).
    var progressShown by remember { mutableIntStateOf(restored?.progressTab ?: progressTab) }
    val calendar = remember { HistoryCalendarState(month = historyDay.takeIf { it != 0 }?.let(HistoryCalendar::monthOf), day = historyDay) }
    // The leaderboard's All time / This month and World / country, kept while the app runs (Android: boardMonth, boardInCountry).
    val leaderboard = remember { LeaderboardState() }
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    // A class asked to start while another is in progress: starting it ends the other, so the athlete is asked.
    var replacing by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // A change of screen or tab is a break for the full-screen ad on Free (Android: adBreakNow): never over an offer,
    // whether a dialog or Settings with its plans, and never between a workout and its Timer.
    fun adBreak() { model.adBreaks.atBreak(covered = shown is Shown.Offer || stack.last() == Screen.Settings) }
    fun openTab(tab: Screen) {
        val midWorkout = stack.last() is Screen.Workout && tab == Screen.Timer
        stack.clear()
        stack.add(tab)
        if (!midWorkout) adBreak()
    }
    fun open(next: Screen) {
        stack.add(next)
        adBreak()
    }
    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            adBreak()
        }
    }
    fun offer(level: Int) { shown = Shown.Offer(Paywall.Level(level)) }
    fun paywall(paywall: Paywall) { shown = Shown.Offer(paywall) }
    // A limit of My workouts: the offer of a higher tier, or on Pro that it is full (Android: roomForAnother, roomForAnotherList).
    fun limit(limit: Limit) {
        when (limit) {
            is Limit.Offer -> paywall(limit.paywall)
            is Limit.ProFull -> notice = Notice.Text(if (limit.lists) Res.string.lists_limit_pro else Res.string.saved_limit_pro)
        }
    }
    // "Create a workout": the builder on a new own workout, if one more fits (Android: requestNewDay).
    fun create() {
        val full = model.savedLimit()
        if (full != null) limit(full) else open(Screen.EditDay(model.newDraft().id))
    }
    // Start workout, Repeat this day and Resume (Android: requestStart).
    fun start(id: String, repeat: Boolean = false, replace: Boolean = false) {
        when (model.start(id, repeat = repeat, replace = replace)) {
            Start.STARTED -> open(Screen.Workout(id))
            Start.ASK_REPLACE -> replacing = id to repeat
            Start.LOCKED -> notice = Notice.Locked
        }
    }
    val listView = screen is Screen.Workout && model.workoutView == WorkoutView.LIST
    // A Timer clock or the alarm that runs out rings on any screen; a second is soon enough to notice (Android:
    // timingTick, every second off the Timer).
    val timing = model.timing
    LaunchedEffect(timing) {
        while (true) {
            timing.checkDue()
            // The full-screen ad is kept ready on Free (Android: timingTick's adBreak.tick), and a section finished in
            // a workout is a break once its rest is over (Android: FocusWorkoutView's breakPending).
            model.adBreaks.tick()
            if (model.training.takeSectionBreak()) adBreak()
            // A change to save settles, then the cloud copy and the score follow (Android: cloudChanged's 3 s).
            model.cloud.tick()
            delay(1_000L)
        }
    }
    // Where the app is, for the phone to keep in case it closes the app in the background (Android: onSaveInstanceState).
    LaunchedEffect(model) {
        snapshotFlow { Restore.encode(Screens(stack.toList(), trainingTab, workoutList, progressShown), model) }
            .collect { model.device.saveScreens(it) }
    }
    // A tap on the widget opens its class's page over Today, or Today (Android: onNewIntent's NextClassWidget.opened).
    model.opening?.let { opening ->
        LaunchedEffect(opening) {
            model.opening = null
            openTab(Screen.Today)
            if (opening is WidgetLinks.Opening.Class && model.programById(opening.programId) != null) open(Screen.Class(opening.programId))
        }
    }
    // A tap on a Timer alert opens the Timer tab (Android: onNewIntent's OPEN_TIMER).
    if (timing.openRequested) LaunchedEffect(Unit) {
        timing.openRequested = false
        openTab(Screen.Timer)
    }

    CompositionLocalProvider(LocalOverlay provides overlay, LocalSafeArea provides safeArea) {
        Box(Modifier.fillMaxSize().backSwipe(enabled = screen.nested && !overlay.shown, onBack = ::back)) {
            Column(
                Modifier.fillMaxSize().pageBackground(theme).padding(safeArea)
                    .then(if (overlay.shown) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                TitleBar(model, screen, onBack = ::back, onRemoveAds = { paywall(Paywall.Ads) })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        Screen.Today -> TodayScreen(model, onOpenClass = { open(Screen.Class(it)) },
                            onStart = { id, repeat -> start(id, repeat) }, onOpenProgress = { openTab(Screen.Progress) }, onOffer = ::offer,
                            onStreak = { shown = Shown.Streak(history = true) })
                        Screen.Training -> TrainingScreen(model, trainingTab, onTab = { trainingTab = it; adBreak() },
                            onOpenLevel = { open(Screen.Level(it)) }, onReturned = { openTab(Screen.Training) },
                            workoutList = workoutList, onOpenList = { workoutList = it }, onOpenDay = { open(Screen.Class(it)) },
                            onCreate = ::create, onLimit = ::limit, onPaywall = ::paywall,
                            readyFilter = readyFilter, onReadyFilter = { readyFilter = it }, onNote = { notice = Notice.Plain(it) })
                        // Complete level returns to Training and Go back to Today, as Android opens them as the root.
                        is Screen.Level -> LevelScreen(model, screen.number, onOpenClass = { open(Screen.Class(it)) }, onOffer = ::offer,
                            onCompleted = { openTab(Screen.Training) }, onWentBack = { openTab(Screen.Today) })
                        // Complete day returns to Today, as Android's opens it as the root.
                        is Screen.Class -> ClassScreen(model, screen.programId, onStart = { start(it) },
                            onRepeat = { start(it, repeat = true) }, onDone = { openTab(Screen.Today) }, onOffer = ::offer,
                            onPaywall = ::paywall, onLimit = ::limit,
                            onNote = { notice = Notice.Plain(it) },
                            onEdit = { id -> if (model.editDraft(id) != null) open(Screen.EditDay(id)) },
                            onDeleted = ::back)
                        // Save workout opens the workout's page in the builder's place (Android: showScreen without pushing).
                        is Screen.EditDay -> BuilderScreen(model, screen.dayId, onSaved = { id ->
                            stack.removeAt(stack.lastIndex)
                            if (stack.lastOrNull() == Screen.Class(id)) stack.removeAt(stack.lastIndex)
                            stack.add(Screen.Class(id))
                        }, onNote = { notice = Notice.Plain(it) })
                        // Progress opens after a class, as on Android; an exam class then asks which targets were reached.
                        is Screen.Workout -> WorkoutScreen(
                            model, screen.askEffort,
                            onFinished = { finished ->
                                notice = Notice.Saved(finished.session.repeat, finished.points, finished.streak)
                                openTab(Screen.Progress)
                                finished.exam?.let { shown = Shown.Exam(it) }
                            },
                            onEnded = ::back,
                            onCompleted = { openTab(Screen.Today) },
                            onGone = ::back,
                        )
                        Screen.Progress -> ProgressScreen(
                            model, progressShown, calendar, onTab = { progressShown = it; adBreak() },
                            onSkill = { shown = Shown.Skill(it) }, onHelp = { shown = Shown.SkillsHelp },
                            onOpenClass = { open(Screen.Class(it)) }, onStreak = { shown = Shown.Streak(history = false) },
                            onPaywall = ::paywall,
                            leaderboard = leaderboard,
                            onReport = { entry, board -> shown = Shown.Report(entry, board) },
                        )
                        Screen.Timer -> TimerScreen(model) { notice = Notice.Plain(it) }
                        Screen.Settings -> SettingsScreen(
                            model, onSwitches = { shown = Shown.Switches(it) }, onReset = { shown = Shown.Reset },
                            onDeveloper = { shown = Shown.Developer }, onNote = { notice = Notice.Plain(it) },
                            onProfile = { shown = Shown.Profile(it) }, onDeleteProfile = { shown = Shown.DeleteProfile },
                            onDeleteAccount = { shown = Shown.DeleteAccount },
                        )
                    }
                }
                // The focus view has the whole screen (Android hides its bottom navigation there); the list keeps it.
                // A level, a class and a workout belong to Training, as on Android (rootFor).
                if (screen !is Screen.Workout || listView) {
                    TabBar(selected = if (screen.nested) Screen.Training else stack.first(), onSelect = ::openTab)
                }
            }
            replacing?.let { (id, repeat) ->
                StreetDialog(
                    title = stringResource(Res.string.replace_workout_title),
                    message = stringResource(Res.string.replace_workout_message),
                    onDismiss = { replacing = null },
                    confirm = DialogAction(stringResource(Res.string.replace)) {
                        replacing = null
                        start(id, repeat, replace = true)
                    },
                    dismiss = DialogAction(stringResource(Res.string.cancel)) { replacing = null },
                )
            }
            when (val open = shown) {
                // Android: askExam. A full pass says so; a missed headline, or three or more, offers the block again
                // (not after a repeated exam); anything else is tested again at the next exam.
                is Shown.Exam -> ExamDialog(open.question) { reached ->
                    val question = open.question
                    val result = model.saveExam(question, reached)
                    shown = when {
                        result.passed -> Shown.ExamPassed
                        question.offerRepeat && result.offersRepeat(question.exam.headline) ->
                            Shown.RepeatBlock(question.exam, question.tests.filterIndexed { index, _ -> !reached[index] }.map { it.first })
                        else -> {
                            notice = Notice.Text(Res.string.exam_saved_not_yet)
                            null
                        }
                    }
                }
                Shown.ExamPassed -> ExamPassedDialog { shown = null }
                is Shown.RepeatBlock -> model.examBlock(open.exam)?.let { (first, count) ->
                    RepeatBlockDialog(open.missed, first, count, onRepeat = {
                        shown = null
                        model.repeatBlock(open.exam)
                        openTab(Screen.Today)
                    }) { shown = null }
                }
                is Shown.Offer -> OfferDialog(model, open.paywall) { shown = null }
                // Android: todayLine's sheet, whose See history opens Progress on History.
                is Shown.Streak -> StreakSheet(model.streak(), onHistory = if (!open.history) null else ({
                    shown = null
                    progressShown = PROGRESS_HISTORY
                    openTab(Screen.Progress)
                })) { shown = null }
                is Shown.Skill -> SkillSheet(model, open.id) { shown = null }
                Shown.SkillsHelp -> SkillsHelpDialog { shown = null }
                is Shown.Switches -> FamilySwitchesSheet(model, open.family) { shown = null }
                // Android: confirmReset. Today then asks where to start.
                Shown.Reset -> ResetDialog(onClose = { shown = null }) {
                    shown = null
                    model.reset()
                    notice = Notice.Text(Res.string.reset_done)
                    openTab(Screen.Today)
                }
                Shown.Developer -> DeveloperDialog(model) { shown = null }
                is Shown.Profile -> ProfileDialog(model.cloud, open.uid, model.services.region) { shown = null }
                // Android: confirmDeleteProfile and confirmDelete, with their notes.
                Shown.DeleteProfile -> DeleteProfileDialog(onClose = { shown = null }) {
                    shown = null
                    model.cloud.deleteProfile { deleted ->
                        notice = Notice.Text(if (deleted) Res.string.profile_deleted else Res.string.profile_delete_failed)
                    }
                }
                Shown.DeleteAccount -> DeleteAccountDialog(model.cloud.signedIn()?.provider == SignInProvider.APPLE, onClose = { shown = null }) {
                    shown = null
                    model.cloud.deleteAccount { deleted ->
                        notice = Notice.Text(if (deleted) Res.string.account_deleted else Res.string.account_delete_failed)
                    }
                }
                is Shown.Report -> ReportNameDialog(open.entry, open.board, onBlock = {
                    shown = null
                    model.cloud.block(open.entry.username)
                    notice = Notice.Text(Res.string.leaderboard_blocked)
                }, onReport = { link, noMail ->
                    shown = null
                    if (runCatching { links.openUri(link) }.isFailure) notice = Notice.Plain(noMail)
                }) { shown = null }
                null -> Unit
            }
            // Over everything, as Android's dialog: the beep when the phone's alert stays silent.
            val ringing = timing.state
            if (ringing.ringing) {
                LaunchedEffect(Unit) { if (!timing.alertsOn) model.device.timingTone() }
                RingingDialog(alarm = ringing.alarmRinging, onStop = timing::stop)
            }
            OverlayLayers(overlay)
            notice?.let { note -> Note(noticeText(note)) { if (notice == note) notice = null } }
        }
    }
}

@Composable
private fun screenTitle(model: AppModel, screen: Screen): String = when (screen) {
    is Screen.Level -> stringResource(Res.string.workouts_title, screen.number)
    else -> stringResource(when (screen) {
        Screen.Today -> Res.string.dashboard_title
        Screen.Training, is Screen.Level -> Res.string.levels_title
        is Screen.Class -> Res.string.workout_overview
        is Screen.EditDay -> Res.string.builder_title
        is Screen.Workout -> if (model.training.active()?.repeat == true) Res.string.repeat_active_title else Res.string.active_workout
        Screen.Progress -> Res.string.progress_title
        Screen.Settings -> Res.string.settings_title
        Screen.Timer -> Res.string.nav_timer
    })
}

/** The title bar of a root screen with nothing else in it: the brand mark and [title] (Android: renderLoadError's). */
@Composable
fun BrandTitleBar(title: String) = TitleBarRow(title, nested = false, onBack = {}) {}

@Composable
private fun TitleBar(model: AppModel, screen: Screen, onBack: () -> Unit, onRemoveAds: () -> Unit) {
    val palette = Street.colors
    TitleBarRow(screenTitle(model, screen), screen.nested, onBack) {
        // On Free, on every screen but a workout (Android: toolbarRemoveAds, "keep remove ads in every screen top").
        if (screen !is Screen.Workout && !model.plans.adFree) RemoveAdsButton(onRemoveAds)
        // During a workout, the other view: the list icon in Focus, the focus icon in the List (Android: toolbarAction).
        if (screen is Screen.Workout) {
            val focus = model.workoutView == WorkoutView.FOCUS
            val switch = stringResource(if (focus) Res.string.switch_to_list else Res.string.switch_to_focus)
            Image(
                painterResource(if (focus) Res.drawable.ic_view_list else Res.drawable.ic_view_focus),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.text),
                modifier = Modifier.size(56.dp)
                    .clickable(role = Role.Button) { model.workoutView = if (focus) WorkoutView.LIST else WorkoutView.FOCUS }
                    .semantics { contentDescription = switch }
                    .padding(16.dp),
            )
        }
    }
}

/** The bar itself: ‹ Back on a nested screen or the brand mark, the title in the theme's display face, then [trailing]. */
@Composable
private fun TitleBarRow(title: String, nested: Boolean, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    // At least 56 dp: with the largest text the title grows the bar rather than being cut.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nested) {
            val back = stringResource(Res.string.back)
            Box(Modifier.size(56.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = back },
                contentAlignment = Alignment.Center) {
                // An arrow in its 56 dp box, as an icon: it keeps its size with large text.
                val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                BasicText("‹", style = TextStyle(color = palette.text, fontSize = (38f / scale).sp, fontFamily = Street.type.body))
            }
        } else {
            Image(painterResource(Res.drawable.ic_brand_mark), contentDescription = null, colorFilter = ColorFilter.tint(palette.mark),
                modifier = Modifier.size(56.dp).padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp))
        }
        val fontSize = if (theme == StreetTheme.WHEATPASTE) 20.sp else 25.sp
        BasicText(
            text = if (theme.shape.upperTitles) title.uppercase() else title,
            modifier = Modifier.weight(1f).displayPadding(fontSize),
            style = TextStyle(color = palette.text, fontSize = fontSize, fontFamily = Street.type.display),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/** The colour of a tab's icon and label. */
private fun tabColor(theme: StreetTheme, selected: Boolean): Color = when (theme) {
    StreetTheme.CONCRETE -> if (selected) theme.palette.border else Color(0xFFBDBAB3)
    StreetTheme.WHEATPASTE -> if (selected) theme.palette.text else theme.palette.textSecondary
    StreetTheme.NIGHT_PARK -> if (selected) theme.palette.primary else theme.palette.textSecondary
}

@Composable
private fun TabBar(selected: Screen, onSelect: (Screen) -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val bar = when (theme) {
        StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.border, 0.dp)
        // Paper with an ink rule along the top.
        StreetTheme.WHEATPASTE -> Modifier.drawBehind {
            drawRect(palette.background)
            drawRect(palette.border, size = Size(size.width, 3.dp.toPx()))
        }
        StreetTheme.NIGHT_PARK -> Modifier.streetBackground(theme, palette.surface, 24.dp)
    }
    Row(
        Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().height(74.dp).then(bar).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { tab ->
            val on = tab.screen == selected
            val color = tabColor(theme, on)
            val label = stringResource(tab.label)
            val item = when {
                !on -> Modifier
                theme == StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.accent, 0.dp)
                // A rule in the theme's orange along the top of the selected tab.
                theme == StreetTheme.NIGHT_PARK -> Modifier.drawBehind { drawRect(palette.primary, size = Size(size.width, 3.dp.toPx())) }
                else -> Modifier
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 3.dp).height(58.dp).then(item)
                    .clickable(role = Role.Tab) { onSelect(tab.screen) }
                    .semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(tab.icon), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(22.dp))
                // Wheatpaste marks the selected label with a highlighter swipe.
                val swipe = on && theme == StreetTheme.WHEATPASTE
                BasicText(
                    text = label.uppercase(),
                    modifier = Modifier.padding(top = 4.dp)
                        .then(if (swipe) Modifier.streetBackground(theme, palette.spot, 0.dp).padding(horizontal = 4.dp) else Modifier),
                    style = TextStyle(color = color, fontSize = 10.sp, letterSpacing = 0.06.em, textAlign = TextAlign.Center,
                        fontFamily = if (theme == StreetTheme.WHEATPASTE) Street.type.label else Street.type.bold),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A screen's content: one scrolling column with the page's margins. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        content = content,
    )
}

/** The card that stands in for a screen or a tab not built on iPhone yet. */
@Composable
internal fun ToComeCard(title: String, modifier: Modifier = Modifier) {
    StreetCard(modifier) {
        TitleText(title, 22f)
        BodyText("Not on iPhone yet.", modifier = Modifier.padding(top = 8.dp))
    }
}
