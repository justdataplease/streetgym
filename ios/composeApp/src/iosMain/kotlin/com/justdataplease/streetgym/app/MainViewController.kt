package com.justdataplease.streetgym.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIKeyboardTypeNumbersAndPunctuation
import com.justdataplease.streetgym.app.data.Ads
import com.justdataplease.streetgym.app.data.Account
import com.justdataplease.streetgym.app.data.HomeWidget
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Beep
import com.justdataplease.streetgym.app.data.Store
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.Alerts
import com.justdataplease.streetgym.app.ui.RING_ALARM
import com.justdataplease.streetgym.app.ui.TIMER_UP
import com.justdataplease.streetgym.data.epochMillisNow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNCalendarNotificationTrigger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.AudioToolbox.AudioServicesCreateSystemSoundID
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.SystemSoundIDVar
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIAccessibilityAnnouncementNotification
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityPostNotification
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The whole app as one view controller; the Swift entry point (iosApp) puts it on screen. With Reduce Motion on, the
 * movement figures stand still (Android shows their still frame when the system's animations are off); a change of the
 * setting while the app runs reaches every figure at once (Android reads it each time a view is built). The first day
 * of the week of the phone's region and whether it shows times on a 12- or 24-hour clock are read when the app starts.
 * Each return to the screen tells the app, which redraws Today for a new day, rings what ran out on the Timer and sets
 * the status bar again. Settings › About shows the Android app's version, written into the resources at build time.
 * [maps] are the Parks maps Swift makes (MapLibre); without them Parks shows its list. [restored] is where the app was
 * when the phone closed it in the background, which [saver] keeps with the window (SwiftUI's scene storage) on every change.
 * [store] is Swift's StoreKit 2 store, [ads] Swift's Google ads (null: nothing to buy, no ads); [debug] is a debug
 * build (Swift's DEBUG), whose developer settings offer Simulate tier. [widget] writes the home-screen widget's snapshot
 * (null: no widget). [account] and [cloud] are Swift's Firebase Auth and Firestore (null, or without
 * GoogleService-Info.plist: no account, as Android's builds without Firebase). [analytics] is Swift's Firebase Analytics
 * for Google Analytics (null, or not configured: none at all); asking it configures Firebase here, before Google's
 * consent SDK starts, as Google's consent mode needs.
 */
fun MainViewController(
    maps: NativeParksMaps?,
    restored: String?,
    saver: SceneSaver?,
    store: NativeStore?,
    ads: AdsBridge?,
    debug: Boolean,
    widget: NativeWidget?,
    account: NativeAccount?,
    cloud: NativeCloud?,
    analytics: NativeAnalytics?,
): UIViewController {
    PhoneDevice.saver = saver
    val resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
        resumed.tryEmit(Unit)
        PhoneDevice.applyStatusBar()
    }
    // Leaving the screen (a call, the app switcher, the home screen) stops the full-screen ad's clock (Android: onPause).
    NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
        phoneEvents.trySend(PhoneEvent.Away)
    }
    watchClock()
    // Reduce Motion switched on or off while the app runs: the figures follow at once.
    val reduceMotion = mutableStateOf(UIAccessibilityIsReduceMotionEnabled())
    NSNotificationCenter.defaultCenter.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue) {
        reduceMotion.value = UIAccessibilityIsReduceMotionEnabled()
    }
    // NSCalendar numbers the weekdays 1 Sunday to 7 Saturday, as java.util.Calendar does.
    val firstWeekday = LocalDays.isoWeekday(NSCalendar.currentCalendar.firstWeekday.toInt())
    val hosts = maps?.let(::IosParksMaps)
    val purchases = store?.let(::IosStore) ?: Store.None
    val adverts = ads?.let(::IosAds) ?: Ads.None
    val build = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 1
    val services = PhoneServices(
        widget = widget?.let(::IosWidget) ?: HomeWidget.None,
        account = account?.let(::IosAccount) ?: Account.None,
        documents = cloud?.let { native -> IosCloud(native).also { native.attachCloud(it) } },
        region = phoneRegion(),
        appSet = VendorId,
        versionCode = build,
        analytics = analytics?.takeIf { it.configured() }?.let(::IosAnalytics),
    )
    return ComposeUIViewController {
        App(phoneStore, PhoneDevice, reduceMotion = reduceMotion.value, firstWeekday = firstWeekday,
            resumed = resumed, countKeys = countKeys, timingClock = IosTimingClock, hour24 = hour24(),
            events = phoneEvents.receiveAsFlow(), location = IosLocation, maps = hosts, restored = restored,
            purchases = purchases, ads = adverts, simulate = debug, services = services, appleButton = IosAppleButton)
    }.also { PhoneDevice.screen = it }
}

/**
 * A link that opened the app or came to it (SwiftUI's onOpenURL): the widget's tap to a class or Today. Swift calls it as
 * `MainViewControllerKt.openLink(url:)`; it waits until the app has read its program.
 */
fun openLink(url: String) {
    phoneEvents.trySend(PhoneEvent.Open(url))
}

/** Swift's keeper of where the app is (SwiftUI's @SceneStorage); Swift sees it as the protocol `SceneSaver`. */
interface SceneSaver {
    fun save(state: String)
}

/** Where the app keeps its progress and choices on this phone. */
internal val phoneStore: KeyValueStore by lazy { UserDefaultsStore() }

/**
 * The strength test's counters: digits with a return key, which reads Next and Done as Android's number keyboard does
 * (the iPhone's number pad has no return key).
 */
@OptIn(ExperimentalComposeUiApi::class)
private val countKeys = PlatformImeOptions {
    keyboardType(UIKeyboardTypeNumbersAndPunctuation)
}

/** The athlete's progress in the app's UserDefaults: it stays on the phone and is part of its backups. */
private class UserDefaultsStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : KeyValueStore {
    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun set(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/**
 * The iPhone during a workout (Android: FLAG_KEEP_SCREEN_ON, the rest's ToneGenerator beep, TimingAlerts.tone and
 * vibrate, announceForAccessibility): the screen stays on; the rest's beep (Android's notification stream) plays as a
 * system sound, which mixes with the athlete's music and keeps quiet on silent; a timed set's end and the in-app "Time's
 * up" play the beep as Android's alarm stream does, heard on silent too; a timed set's end vibrates; VoiceOver speaks
 * the cues; and a rest that ends while the app is in the background has a notification with the same beep.
 */
@OptIn(ExperimentalForeignApi::class)
internal object PhoneDevice : WorkoutDevice {
    const val REST = "rest"
    /** The Timer's and the alarm's alerts, and their Stop (Android: TimingAlerts' TIMER_ID, ALARM_ID and its Stop action). */
    const val TIMER = "timer"
    const val ALARM = "alarm"
    const val TIMING = "timing"
    const val STOP = "stop"
    private const val BEEP_FILE = "streetgym-beep.wav"

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** The app's view controller, whose window carries the theme's light or dark look. */
    var screen: UIViewController? = null

    /** Keeps where the app is with its window, for state restoration. */
    var saver: SceneSaver? = null

    override fun saveScreens(state: String) {
        saver?.save(state)
    }

    /** The look the theme asks for: dark on Night Park, which gives the status bar light words. */
    private var dark = false

    /** The beep, written once into Library/Sounds, where a notification finds it too, and made a system sound. */
    private val beep: UInt? by lazy { beepFile()?.let(::systemSound) }

    override fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }

    /** The rest's beep: Android plays it on the notification stream, which the phone's silent mode mutes, as a system sound is. */
    override fun restOver() {
        beep?.let { AudioServicesPlaySystemSound(it) }
    }

    /**
     * A timed set's end: the beep on the alarm stream (Android: TimingAlerts.tone), which sounds on silent too, and the
     * alert's vibration, twice (Android: TimingAlerts.vibrate, 400 ms on, 200 off, 400 on).
     */
    override fun holdOver(sound: Boolean) {
        if (sound) alarmBeep()
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, VIBRATION_GAP_NANOS), dispatch_get_main_queue()) {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    /** One buzz (about 400 ms) and Android's 200 ms pause: the second buzz starts 600 ms after the first. */
    private const val VIBRATION_GAP_NANOS = 600_000_000L

    /** The player of the alarm stream's beep, kept while it plays. */
    private var alarmPlayer: AVAudioPlayer? = null

    /**
     * Android's alarm-stream beep (ToneGenerator on STREAM_ALARM at 80): the same samples, played as media in the
     * playback category, which the silent switch doesn't mute, mixed with the athlete's music rather than stopping it
     * (Android's tone takes no audio focus), at the gain Android gives volume 80. The category is set again on each beep,
     * as Google's ads may change it while a video ad plays.
     */
    private fun alarmBeep() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, withOptions = AVAudioSessionCategoryOptionMixWithOthers, error = null)
        session.setActive(true, error = null)
        val player = alarmPlayer ?: run {
            val bytes = Beep.wav()
            val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
            AVAudioPlayer(data = data, error = null).also { alarmPlayer = it }
        }
        player.volume = Beep.gain(Beep.ALARM_VOLUME)
        player.currentTime = 0.0
        player.play()
    }

    override fun announce(text: String) {
        UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, text)
    }

    override fun restAlert(at: Long?, text: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REST))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(REST))
        val seconds = ((at ?: return) - epochMillisNow()) / 1_000.0
        if (seconds < 1.0 || beep == null) return
        val content = UNMutableNotificationContent()
        content.setBody(text)
        content.setSound(UNNotificationSound.soundNamed(BEEP_FILE))
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(REST, content, trigger), withCompletionHandler = null)
    }

    /**
     * The status bar's words follow the window's look: light on a dark one. The window takes the theme's (Android:
     * configureWindow); it also gives Night Park the dark keyboard. Before the window exists, [applyStatusBar] runs
     * again when the app becomes active.
     */
    override fun statusBar(dark: Boolean) {
        this.dark = dark
        applyStatusBar()
    }

    fun applyStatusBar() {
        val window = screen?.view?.window ?: return
        window.overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        window.rootViewController?.setNeedsStatusBarAppearanceUpdate()
    }

    override fun askAlerts(done: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            onMain { done(granted) }
        }
    }

    override fun alerts(result: (Alerts) -> Unit) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusNotDetermined -> Alerts.NOT_ASKED
                UNAuthorizationStatusDenied -> Alerts.DENIED
                else -> Alerts.ALLOWED
            }
            onMain { result(status) }
        }
    }

    override fun openAlertSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    /**
     * "Time’s up" with the phone's alert sound and Stop, [inMillis] from now, on the elapsed clock (a change of the wall
     * clock doesn't move it). Shorter than a second, the app rings it itself.
     */
    override fun timerAlert(inMillis: Long?) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(TIMER))
        val seconds = (inMillis ?: return) / 1_000.0
        if (seconds < 1.0) return
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(TIMER, timingContent(TIMER_UP), trigger), withCompletionHandler = null)
    }

    /**
     * "Alarm" at [at] on the phone's wall clock: date parts without a time zone, so the alert follows a change of the
     * clock or the time zone by itself.
     */
    override fun alarmAlert(at: LocalDateTime?) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ALARM))
        val local = at ?: return
        val parts = NSDateComponents()
        parts.year = local.year.toLong()
        parts.month = (local.month.ordinal + 1).toLong()
        parts.day = local.day.toLong()
        parts.hour = local.hour.toLong()
        parts.minute = local.minute.toLong()
        parts.second = 0L
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ALARM, timingContent(RING_ALARM), trigger), withCompletionHandler = null)
    }

    override fun clearTimingAlerts() {
        center.removeDeliveredNotificationsWithIdentifiers(listOf(TIMER, ALARM))
    }

    /** "Time's up" or "Alarm" on screen with alerts off: Android's alarm-stream beep (timingTick's TimingAlerts.tone). */
    override fun timingTone() {
        alarmBeep()
    }

    /** An alert's words as Android's: the title, and the app's name under it; the alert sound; Stop (Android: TimingAlerts.notify). */
    private fun timingContent(title: String): UNMutableNotificationContent = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String ?: "Street Gym")
        setSound(UNNotificationSound.defaultSound)
        setCategoryIdentifier(TIMING)
    }

    private fun beepFile(): NSURL? {
        val library = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return null
        val folder = "$library/Sounds"
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$folder/$BEEP_FILE"
        val bytes = Beep.wav()
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        return if (data.writeToFile(path, atomically = true)) NSURL.fileURLWithPath(path) else null
    }

    private fun systemSound(file: NSURL): UInt? = memScoped {
        val id = alloc<SystemSoundIDVar>()
        val url = CFBridgingRetain(file)
        val status = AudioServicesCreateSystemSoundID(url?.reinterpret(), id.ptr)
        CFBridgingRelease(url)
        if (status == 0) id.value else null
    }
}
