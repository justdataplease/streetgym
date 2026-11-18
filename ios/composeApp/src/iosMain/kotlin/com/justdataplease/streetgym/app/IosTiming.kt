package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingClock
import com.justdataplease.streetgym.app.data.TimingStore
import com.justdataplease.streetgym.app.ui.RING_STOP
import com.justdataplease.streetgym.data.epochMillisNow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.Channel
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemClockDidChangeNotification
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.resetSystemTimeZone
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np
import platform.posix.size_tVar
import platform.darwin.sysctlbyname
import platform.posix.timeval

/** What the phone tells the app while it runs: a tap on a Timer alert, a change of the clock or time zone. */
internal val phoneEvents = Channel<PhoneEvent>(Channel.UNLIMITED)

/** Runs [block] on the main thread, where the screens are drawn. */
internal fun onMain(block: () -> Unit) {
    NSOperationQueue.mainQueue.addOperationWithBlock(block)
}

/**
 * The Timer's clocks on iPhone (Android: SystemClock.elapsedRealtime and the boot count): CLOCK_MONOTONIC counts on
 * while the phone sleeps and ignores changes to the wall clock; the phone's start time (kern.boottime) tells one start
 * from the next.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosTimingClock : TimingClock {
    override fun elapsed(): Long = (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()) / 1_000_000uL).toLong()

    override fun wall(): Long = epochMillisNow()

    override fun boot(): Long = bootSeconds

    private val bootSeconds: Long by lazy {
        memScoped {
            val time = alloc<timeval>()
            val size = alloc<size_tVar>()
            size.value = sizeOf<timeval>().toULong()
            if (sysctlbyname("kern.boottime", time.ptr, size.ptr, null, 0u) == 0) time.tv_sec else 0L
        }
    }
}

/** Whether the phone's region and settings show times on a 24-hour clock (the "j" pattern has no AM or PM). */
internal fun hour24(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale.currentLocale)?.contains('a') != true

/**
 * A change of the clock or the time zone while the app runs, or while it waited in the background (iOS hands those over
 * when it comes back): a set alarm keeps to the wall clock (Android: TimingReceiver's ACTION_TIME_CHANGED and
 * ACTION_TIMEZONE_CHANGED).
 */
internal fun watchClock() {
    val center = NSNotificationCenter.defaultCenter
    for (name in listOf(NSSystemClockDidChangeNotification, NSSystemTimeZoneDidChangeNotification)) {
        center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
            NSTimeZone.resetSystemTimeZone()
            phoneEvents.trySend(PhoneEvent.TimeChanged)
        }
    }
}

/**
 * The Timer's alerts, ready before the app has finished starting (Swift calls it from the app's init): their Stop
 * button, and the notification centre's delegate, which hears a tap on an alert or its Stop even when the alert opened
 * the app (Android: TimingAlerts' Stop action and the alert's open intent).
 */
fun startAlerts() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val stop = UNNotificationAction.actionWithIdentifier(PhoneDevice.STOP, RING_STOP, UNNotificationActionOptionNone)
    // A swiped-away alert stops as Stop does (Android: the notification's delete intent).
    val timing = UNNotificationCategory.categoryWithIdentifier(PhoneDevice.TIMING, listOf(stop), emptyList<Any>(),
        UNNotificationCategoryOptionCustomDismissAction)
    center.setNotificationCategories(setOf(timing))
    center.delegate = alertsDelegate
}

/** The delegate, held here for as long as the app runs: the notification centre keeps it weakly. */
private val alertsDelegate = AlertsDelegate()

/**
 * The notification centre's delegate (iOS keeps it weakly; this object lives as long as the app). With the app on
 * screen a Timer or alarm alert still shows with its sound, as Android's does beside its dialog; the rest's alert
 * doesn't, as the app beeps itself. Stop, or swiping the alert away, stops what rings, the app running or not; a tap on
 * it opens the Timer.
 */
private class AlertsDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val id = willPresentNotification.request.identifier
        withCompletionHandler(
            if (id == PhoneDevice.TIMER || id == PhoneDevice.ALARM)
                UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound
            else 0uL,
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val id = didReceiveNotificationResponse.notification.request.identifier
        if (id == PhoneDevice.TIMER || id == PhoneDevice.ALARM) {
            when (didReceiveNotificationResponse.actionIdentifier) {
                PhoneDevice.STOP, UNNotificationDismissActionIdentifier ->
                    Timing(TimingStore(phoneStore, IosTimingClock), PhoneDevice).stop()
                UNNotificationDefaultActionIdentifier -> phoneEvents.trySend(PhoneEvent.OpenTimer)
            }
        }
        withCompletionHandler()
    }
}
