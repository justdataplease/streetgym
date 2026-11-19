package com.justdataplease.streetgym.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.Ads
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.WidgetLinks
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Store
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.data.BundledParks
import com.justdataplease.streetgym.app.data.ParksSource
import com.justdataplease.streetgym.app.data.PhoneLocation
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingClock
import com.justdataplease.streetgym.app.data.TimingStore
import com.justdataplease.streetgym.app.data.WallClock
import com.justdataplease.streetgym.app.ui.LocalParksMaps
import com.justdataplease.streetgym.app.ui.ParksMapHost
import com.justdataplease.streetgym.app.ui.Restore
import com.justdataplease.streetgym.app.ui.Screens
import com.justdataplease.streetgym.app.ui.StandInMaps
import com.justdataplease.streetgym.data.epochMillisNow
import kotlinx.datetime.TimeZone
import com.justdataplease.streetgym.app.figure.LoadingFigure
import com.justdataplease.streetgym.app.figure.LocalMotionLibrary
import com.justdataplease.streetgym.app.figure.LocalMotionWords
import com.justdataplease.streetgym.app.figure.LocalReduceMotion
import com.justdataplease.streetgym.app.figure.motionWords
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.app_name
import com.justdataplease.streetgym.app.resources.app_version_debug_suffix
import com.justdataplease.streetgym.app.resources.app_version_name
import com.justdataplease.streetgym.app.resources.load_error_message
import com.justdataplease.streetgym.app.resources.load_error_title
import com.justdataplease.streetgym.app.resources.loading_catalog
import com.justdataplease.streetgym.app.resources.logo
import com.justdataplease.streetgym.app.resources.logo_content_description
import com.justdataplease.streetgym.app.resources.retry
import com.justdataplease.streetgym.app.theme.LocalStreetTheme
import com.justdataplease.streetgym.app.theme.LocalStreetType
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.streetType
import com.justdataplease.streetgym.app.ui.BodyText
import com.justdataplease.streetgym.app.ui.BrandTitleBar
import com.justdataplease.streetgym.app.ui.LocalCountKeys
import com.justdataplease.streetgym.app.ui.PROGRESS_SKILLS
import com.justdataplease.streetgym.app.ui.PrimaryButton
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Shell
import com.justdataplease.streetgym.app.ui.Shown
import com.justdataplease.streetgym.app.ui.TitleText
import com.justdataplease.streetgym.app.ui.AppleButton
import com.justdataplease.streetgym.app.ui.AppleButtonStandIn
import com.justdataplease.streetgym.app.ui.LocalAppleButton
import com.justdataplease.streetgym.app.ui.WidgetUpdates
import com.justdataplease.streetgym.app.ui.pageBackground
import com.justdataplease.streetgym.data.progress.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/**
 * The app from a cold start: reads the program from its files, showing the loading screen meanwhile, then Today; when
 * the files can't be read, the load error and Try again. [store] keeps the athlete's progress and choices; [device]
 * keeps the screen on during a workout, sounds and speaks its cues; [reduceMotion] holds the movement figures and the
 * park still, as the phone's Reduce Motion asks. [firstWeekday] (1 Monday to 7 Sunday) is where the phone's region
 * starts a week; [resumed] fires each time the app comes back to the screen, which redraws Today for a new day and
 * starts the park scene again (Android: onResume). [version] is the app's, shown in Settings › About (null: the Android
 * app's versionName, written into the resources at build time, with its debug suffix when [simulate]). [countKeys]: the
 * phone's options for the strength test's number keyboard. [load] reads the program (tests make it fail).
 *
 * The Timer runs on [timingClock] (null: the wall clock) and shows times on a 24-hour clock with [hour24]; [events]
 * brings what the phone tells the app (a tap on a Timer alert, a change of the clock or time zone, the app leaving the
 * screen). Parks reads its parks from [parks], the athlete's position from [location], and draws its map with [maps]
 * (null: the list only).
 *
 * Plus and Pro are bought from [purchases] (StoreKit on iPhone), what was bought cached in [store] so the app opens in
 * the right tier offline; [simulate]: a debug build, whose developer settings offer Simulate tier. Free's ads come
 * from [ads] (Google's on iPhone), their full-screen breaks timed on the Timer's elapsed clock. [services] are the
 * home-screen widget, the account and the cloud's documents (WidgetKit and Firebase on iPhone); [appleButton] draws
 * Sign in with Apple (Apple's own on iPhone).
 */
@Composable
fun App(
    store: KeyValueStore,
    device: WorkoutDevice = WorkoutDevice.None,
    reduceMotion: Boolean = false,
    firstWeekday: Int = Streak.MONDAY,
    resumed: Flow<Unit> = emptyFlow(),
    version: String? = null,
    countKeys: PlatformImeOptions? = null,
    load: suspend () -> Program = ::loadProgram,
    timingClock: TimingClock? = null,
    hour24: Boolean = true,
    events: Flow<PhoneEvent> = emptyFlow(),
    location: PhoneLocation = PhoneLocation.Fixed(),
    maps: ParksMapHost? = StandInMaps,
    parks: ParksSource = BundledParks,
    restored: String? = null,
    purchases: Store = Store.None,
    ads: Ads = Ads.None,
    simulate: Boolean = false,
    services: PhoneServices = PhoneServices(),
    appleButton: AppleButton = AppleButtonStandIn,
) {
    var model by remember { mutableStateOf<AppModel?>(null) }
    // Where the app was when the phone closed it in the background, read once the program is.
    var screens by remember { mutableStateOf<Screens?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Each Try again reads the files once more.
    var attempt by remember { mutableIntStateOf(0) }
    // The look the athlete picked shows from the first screen on (Android sets its theme before the loading screen).
    val theme = remember(store) { AppSettings(store).theme }
    LaunchedEffect(store, attempt) {
        failed = false
        runCatching { load() }
            .onSuccess {
                val clock = timingClock ?: WallClock(::epochMillisNow)
                val timing = Timing(TimingStore(store, clock), device, { TimeZone.currentSystemDefault() }, hour24)
                val app = AppModel(it, StoredProgress(store), device = device, settings = AppSettings(store), firstWeekday = firstWeekday,
                    library = LibraryStore(store), timing = timing, parksSource = parks, location = location,
                    plans = Plans(store, purchases, canSimulate = simulate), ads = ads, adStore = store, elapsed = clock::elapsed,
                    services = services)
                    .apply { this.version = version ?: buildVersion(debug = simulate) }
                screens = Restore.decode(restored, app)
                model = app
                // What ran out while the app was closed rings, and the alerts are scheduled again (Android: onResume).
                timing.resumed()
            }
            .onFailure { failed = true }
    }
    val loaded = model
    LaunchedEffect(loaded, resumed) { loaded?.let { app -> resumed.collect { app.resumed() } } }
    LaunchedEffect(loaded, events) {
        loaded?.let { app ->
            events.collect { event ->
                when (event) {
                    PhoneEvent.OpenTimer -> app.timing.openRequested = true
                    PhoneEvent.TimeChanged -> app.timing.timeChanged()
                    PhoneEvent.Away -> app.paused()
                    is PhoneEvent.Open -> WidgetLinks.parse(event.url)?.let { app.opening = it }
                }
            }
        }
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, LocalCountKeys provides countKeys, LocalParksMaps provides maps,
        LocalAppleButton provides appleButton) {
        when {
            loaded != null -> StreetGymApp(loaded, restored = screens)
            failed -> StreetGymTheme(theme) { LoadErrorScreen { attempt++ } }
            else -> StreetGymTheme(theme) { LoadingScreen() }
        }
    }
}

/**
 * The Android app's version as Android shows it (versionName, and in a debug build its versionNameSuffix, "-debug"), from
 * the values the build reads from the Android app's build file.
 */
suspend fun buildVersion(debug: Boolean): String =
    getString(Res.string.app_version_name) + if (debug) getString(Res.string.app_version_debug_suffix) else ""

/** What the phone tells the running app. */
sealed interface PhoneEvent {
    /** A Timer or alarm alert was tapped: the Timer tab opens (Android: TimingAlerts.OPEN_TIMER). */
    data object OpenTimer : PhoneEvent
    /** The clock or the time zone changed: a set alarm keeps to the wall clock (Android: TimingReceiver). */
    data object TimeChanged : PhoneEvent
    /** The app left the screen (Android: onPause): the full-screen ad's clock stops until it comes back. */
    data object Away : PhoneEvent
    /** A link opened the app: the widget's tap, to a class's page or Today (Android: NextClassWidget.ACTION_OPEN). */
    data class Open(val url: String) : PhoneEvent
}

/**
 * While the program is read (Android: renderLoading): one figure, mostly the walk and one time in three pull-ups, over
 * "Loading your training…", with no title bar or tabs. [pull] picks the pull-ups.
 */
@Composable
fun LoadingScreen(pull: Boolean = remember { Random.nextInt(3) == 0 }) {
    val theme = Street.theme
    Box(Modifier.fillMaxSize().pageBackground(theme).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingFigure(pull, Modifier.size(200.dp, if (pull) 150.dp else 96.dp))
            Spacer(Modifier.height(16.dp))
            BodyText(stringResource(Res.string.loading_catalog), align = TextAlign.Center)
        }
    }
}

/**
 * The program could not be read (Android: renderLoadError): the title bar with the brand mark and "Street Gym", no tabs;
 * the logo in the theme's mark colour, "Training plan unavailable", its message and Try again ([onRetry]).
 */
@Composable
fun LoadErrorScreen(safeArea: PaddingValues = WindowInsets.safeDrawing.asPaddingValues(), onRetry: () -> Unit) {
    val theme = Street.theme
    Column(Modifier.fillMaxSize().pageBackground(theme).padding(safeArea)) {
        BrandTitleBar(stringResource(Res.string.app_name))
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(Res.drawable.logo), contentDescription = stringResource(Res.string.logo_content_description),
                    colorFilter = ColorFilter.tint(theme.palette.mark), modifier = Modifier.fillMaxWidth().height(100.dp))
                Spacer(Modifier.height(20.dp))
                TitleText(stringResource(Res.string.load_error_title), align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                BodyText(stringResource(Res.string.load_error_message), align = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(Res.string.retry), color = theme.palette.primary, onClick = onRetry)
            }
        }
    }
}

/**
 * The app over a loaded [model]. [safeArea] is the room the phone needs around the screen (the Dynamic Island, the
 * home indicator); previews pass an iPhone's. [startAt], [showing], [progressTab], [historyDay], [trainingTab] and
 * [workoutList] open it on a screen, a question, a tab of Progress, a day of History's calendar, a tab of Training and a
 * list of My workouts (previews). The phone's status bar follows the theme:
 * light on Night Park, dark on the light themes (Android: configureWindow).
 */
@Composable
fun StreetGymApp(
    model: AppModel,
    safeArea: PaddingValues = WindowInsets.safeDrawing.asPaddingValues(),
    startAt: Screen = Screen.Today,
    showing: Shown? = null,
    progressTab: Int = PROGRESS_SKILLS,
    historyDay: Int = 0,
    trainingTab: Int = 0,
    workoutList: String? = null,
    readyFilter: ReadyFilter = ReadyFilter(),
    maps: ParksMapHost? = LocalParksMaps.current,
    restored: Screens? = null,
) {
    val dark = model.theme.dark
    LaunchedEffect(model, dark) { model.device.statusBar(dark) }
    // The app is on screen: the ad clock runs; the store is asked, and its answers followed while the app runs
    // (Android: onResume's billing.connect and adBreak.resume, TierBilling's listener).
    LaunchedEffect(model) {
        model.adBreaks.resume()
        model.plans.connect()
        // The account and analytics catch up as the app opens (Android: onResume's sync and syncAnalytics).
        model.opened()
        model.plans.follow()
    }
    // Firebase's user, followed while the app runs (Android: AccountSettingsView's AuthStateListener).
    LaunchedEffect(model) { model.cloud.follow() }
    CompositionLocalProvider(LocalMotionLibrary provides model.motions, LocalParksMaps provides maps) {
        StreetGymTheme(model.theme) {
            // The home-screen widget follows progress, the tier and the theme from here on (Android: NextClassWidget.watch).
            WidgetUpdates(model)
            Shell(model, safeArea, startAt, showing, progressTab, historyDay, trainingTab, workoutList, readyFilter, restored)
        }
    }
}

/** A theme's colours, faces and the movement guides' words for what is drawn inside. */
@Composable
fun StreetGymTheme(theme: StreetTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStreetTheme provides theme, LocalStreetType provides streetType(theme)) {
        CompositionLocalProvider(LocalMotionWords provides motionWords(), content = content)
    }
}
