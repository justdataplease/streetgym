package com.justdataplease.streetgym.data.model

import java.util.Locale

/** The Android screens pass their language; the shared engine's [LocalizedText.resolve] has only English so far. */
@Suppress("UNUSED_PARAMETER")
fun LocalizedText.resolve(locale: Locale): String = resolve()
