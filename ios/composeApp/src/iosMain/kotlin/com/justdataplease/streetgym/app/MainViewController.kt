package com.justdataplease.streetgym.app

import androidx.compose.ui.window.ComposeUIViewController
import com.justdataplease.streetgym.app.data.KeyValueStore
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** The whole app as one view controller; the Swift entry point (iosApp) puts it on screen. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(UserDefaultsStore()) }

/** The athlete's progress in the app's UserDefaults: it stays on the phone and is part of its backups. */
private class UserDefaultsStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : KeyValueStore {
    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun set(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}
