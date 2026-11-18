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
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.theme.LocalStreetTheme
import com.justdataplease.streetgym.app.theme.LocalStreetType
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.streetType
import com.justdataplease.streetgym.app.ui.BodyText
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Shell
import com.justdataplease.streetgym.app.ui.pageBackground

/**
 * The app from a cold start: reads the program from its files, then shows Today. [store] keeps the athlete's progress;
 * [device] keeps the screen on during a workout and cues the end of a rest.
 */
@Composable
fun App(store: KeyValueStore, device: WorkoutDevice = WorkoutDevice.None) {
    var model by remember { mutableStateOf<AppModel?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(store) {
        runCatching { loadProgram() }
            .onSuccess { model = AppModel(it, StoredProgress(store), device = device) }
            .onFailure { failed = true }
    }
    val loaded = model
    if (loaded != null) StreetGymApp(loaded) else StreetGymTheme(StreetTheme.DEFAULT) {
        Box(Modifier.fillMaxSize().pageBackground(StreetTheme.DEFAULT).padding(24.dp), contentAlignment = Alignment.Center) {
            if (failed) BodyText("The program could not be read. Install the app again.")
        }
    }
}

/**
 * The app over a loaded [model]. [safeArea] is the room the phone needs around the screen (the Dynamic Island, the
 * home indicator); previews pass an iPhone's.
 */
@Composable
fun StreetGymApp(
    model: AppModel,
    safeArea: PaddingValues = WindowInsets.safeDrawing.asPaddingValues(),
    startAt: Screen = Screen.Today,
) {
    StreetGymTheme(model.theme) { Shell(model, safeArea, startAt) }
}

@Composable
fun StreetGymTheme(theme: StreetTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStreetTheme provides theme, LocalStreetType provides streetType(theme), content = content)
}
