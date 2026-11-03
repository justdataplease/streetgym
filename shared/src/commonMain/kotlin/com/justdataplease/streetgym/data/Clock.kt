package com.justdataplease.streetgym.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Now, in epoch milliseconds, on every platform (System.currentTimeMillis() is Java's). */
@OptIn(ExperimentalTime::class)
fun epochMillisNow(): Long = Clock.System.now().toEpochMilliseconds()
