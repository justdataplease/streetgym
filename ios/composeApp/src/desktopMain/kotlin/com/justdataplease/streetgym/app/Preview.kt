package com.justdataplease.streetgym.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.MemoryStore

/**
 * The iPhone app's screens in a desktop window, at an iPhone 17 Pro Max's size in points: `gradlew :composeApp:run`.
 * Nothing is kept: every run starts as a new install.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Street Gym (iPhone screens)",
        state = rememberWindowState(width = 440.dp, height = 956.dp),
    ) {
        // Weeks start where this computer's region starts them, as the iPhone reads its own.
        App(MemoryStore(), firstWeekday = LocalDays.isoWeekday(java.util.Calendar.getInstance().firstDayOfWeek))
    }
}
