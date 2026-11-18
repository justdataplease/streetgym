package com.justdataplease.streetgym.app

import androidx.compose.ui.window.ComposeUIViewController
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.WorkoutDevice
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** The whole app as one view controller; the Swift entry point (iosApp) puts it on screen. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(UserDefaultsStore(), PhoneDevice) }

/** The athlete's progress in the app's UserDefaults: it stays on the phone and is part of its backups. */
private class UserDefaultsStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : KeyValueStore {
    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun set(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/**
 * The iPhone during a workout: the screen stays on while it shows (Android: FLAG_KEEP_SCREEN_ON).
 *
 * TODO: the sound at the end of a rest and of a timed set (Android: a beep and a vibration), with AVAudio while the
 * app is open and a scheduled local notification while it is in the background; see docs/iphone-development.md.
 */
private object PhoneDevice : WorkoutDevice {
    override fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }
}
