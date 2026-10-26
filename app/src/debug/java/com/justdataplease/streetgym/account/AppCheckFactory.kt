package com.justdataplease.streetgym.account

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug builds print a debug token to logcat; register it in the Firebase console to pass App Check. */
object AppCheckFactory {
    fun get(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
