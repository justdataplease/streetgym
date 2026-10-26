package com.justdataplease.streetgym.account

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Play builds prove they're the genuine app with Play Integrity. */
object AppCheckFactory {
    fun get(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
