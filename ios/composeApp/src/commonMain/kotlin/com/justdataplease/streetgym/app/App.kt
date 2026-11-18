package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.DEFAULT_VERSION
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.figure.LocalMotionLibrary
import com.justdataplease.streetgym.app.figure.LocalMotionWords
import com.justdataplease.streetgym.app.figure.LocalReduceMotion
import com.justdataplease.streetgym.app.figure.motionWords
import com.justdataplease.streetgym.app.theme.LocalStreetTheme
import com.justdataplease.streetgym.app.theme.LocalStreetType
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.streetType
import com.justdataplease.streetgym.app.ui.BodyText
import com.justdataplease.streetgym.app.ui.PROGRESS_SKILLS
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Shell
import com.justdataplease.streetgym.app.ui.Shown
import com.justdataplease.streetgym.app.ui.pageBackground
import com.justdataplease.streetgym.data.progress.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The app from a cold start: reads the program from its files, then shows Today. [store] keeps the athlete's progress
 * and choices; [device] keeps the screen on during a workout, sounds and speaks its cues; [reduceMotion] holds the
 * movement figures still, as the phone's Reduce Motion asks. [firstWeekday] (1 Monday to 7 Sunday) is where the
 * phone's region starts a week; [resumed] fires each time the app comes back to the screen, which redraws Today for a
 * new day (Android: onResume). [version] is the app's, shown in Settings › About.
 */
@Composable
fun App(
    store: KeyValueStore,
    device: WorkoutDevice = WorkoutDevice.None,
    reduceMotion: Boolean = false,
    firstWeekday: Int = Streak.MONDAY,
    resumed: Flow<Unit> = emptyFlow(),
    version: String = DEFAULT_VERSION,
) {
    var model by remember { mutableStateOf<AppModel?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(store) {
        runCatching { loadProgram() }
            .onSuccess {
                model = AppModel(it, StoredProgress(store), device = device, settings = AppSettings(store), firstWeekday = firstWeekday)
                    .apply { this.version = version }
            }
            .onFailure { failed = true }
    }
    val loaded = model
    LaunchedEffect(loaded, resumed) { loaded?.let { app -> resumed.collect { app.refreshDay() } } }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        if (loaded != null) StreetGymApp(loaded) else StreetGymTheme(StreetTheme.DEFAULT) {
            Box(Modifier.fillMaxSize().pageBackground(StreetTheme.DEFAULT).padding(24.dp), contentAlignment = Alignment.Center) {
                if (failed) BodyText("The program could not be read. Install the app again.")
            }
        }
    }
}

/**
 * The app over a loaded [model]. [safeArea] is the room the phone needs around the screen (the Dynamic Island, the
 * home indicator); previews pass an iPhone's. [startAt], [showing], [progressTab] and [historyDay] open it on a screen,
 * a question, a tab of Progress and a day of History's calendar (previews). The phone's status bar follows the theme:
 * light on Night Park, dark on the light themes (Android: configureWindow).
 */
@Composable
fun StreetGymApp(
    model: AppModel,
    safeArea: PaddingValues = WindowInsets.safeDrawing.asPaddingValues(),
    startAt: Screen = Screen.Today,
    showing: Shown? = null,
    progressTab: Int = PROGRESS_SKILLS,
    historyDay: Int = 0,
) {
    val dark = model.theme.dark
    LaunchedEffect(model, dark) { model.device.statusBar(dark) }
    CompositionLocalProvider(LocalMotionLibrary provides model.motions) {
        StreetGymTheme(model.theme) { Shell(model, safeArea, startAt, showing, progressTab, historyDay) }
    }
}

/** A theme's colours, faces and the movement guides' words for what is drawn inside. */
@Composable
fun StreetGymTheme(theme: StreetTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStreetTheme provides theme, LocalStreetType provides streetType(theme)) {
        CompositionLocalProvider(LocalMotionWords provides motionWords(), content = content)
    }
}
