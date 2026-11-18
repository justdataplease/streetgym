package com.justdataplease.streetgym.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIKeyboardTypeNumbersAndPunctuation
import com.justdataplease.streetgym.app.data.Beep
import com.justdataplease.streetgym.app.data.DEFAULT_VERSION
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.data.epochMillisNow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableSharedFlow
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
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
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
 * movement figures stand still (Android shows their still frame when the system's animations are off); the setting is
 * read when the app starts, and so is the first day of the week of the phone's region. Each return to the screen tells
 * the app, which redraws Today for a new day, and sets the status bar again. Settings › About shows the app's version
 * from its Info.plist.
 */
fun MainViewController(): UIViewController {
    val resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
        resumed.tryEmit(Unit)
        PhoneDevice.applyStatusBar()
    }
    // NSCalendar numbers the weekdays 1 Sunday to 7 Saturday, as java.util.Calendar does.
    val firstWeekday = LocalDays.isoWeekday(NSCalendar.currentCalendar.firstWeekday.toInt())
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: DEFAULT_VERSION
    return ComposeUIViewController {
        App(UserDefaultsStore(), PhoneDevice, reduceMotion = UIAccessibilityIsReduceMotionEnabled(), firstWeekday = firstWeekday,
            resumed = resumed, version = version, countKeys = countKeys)
    }.also { PhoneDevice.screen = it }
}

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
 * vibrate, announceForAccessibility): the screen stays on, Android's beep plays as a system sound, which mixes with the
 * athlete's music and keeps quiet on silent, a timed set's end vibrates, VoiceOver speaks the cues, and a rest that ends
 * while the app is in the background has a notification with the same beep.
 */
@OptIn(ExperimentalForeignApi::class)
private object PhoneDevice : WorkoutDevice {
    private const val REST = "rest"
    private const val BEEP_FILE = "streetgym-beep.wav"

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** The app's view controller, whose window carries the theme's light or dark look. */
    var screen: UIViewController? = null

    /** The look the theme asks for: dark on Night Park, which gives the status bar light words. */
    private var dark = false

    /** The beep, written once into Library/Sounds, where a notification finds it too, and made a system sound. */
    private val beep: UInt? by lazy { beepFile()?.let(::systemSound) }

    override fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }

    override fun restOver() {
        beep?.let { AudioServicesPlaySystemSound(it) }
    }

    override fun holdOver(sound: Boolean) {
        if (sound) beep?.let { AudioServicesPlaySystemSound(it) }
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
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

    override fun askAlerts() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
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
