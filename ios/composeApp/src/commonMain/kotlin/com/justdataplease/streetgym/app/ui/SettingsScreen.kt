package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.keep_screen_awake
import com.justdataplease.streetgym.app.resources.sound_cues
import com.justdataplease.streetgym.app.resources.training_preferences
import org.jetbrains.compose.resources.stringResource

/**
 * Settings (android-streetgym, HomeActivity.renderSettings), as far as it is built on iPhone: During your workout, with
 * Keep screen awake, Rest timer sound and the workout view. Android's other sections (profile, theme, plans, skills,
 * equipment, your data, start over, train safely, about) come in later rounds, around it in Android's order.
 */
@Composable
fun SettingsScreen(model: AppModel) {
    Page {
        SectionLabel(stringResource(Res.string.training_preferences))
        StreetCard(Modifier.padding(bottom = 16.dp)) {
            SettingSwitch(stringResource(Res.string.keep_screen_awake), model.keepScreenAwake) { model.keepScreenAwake = it }
            SettingSwitch(stringResource(Res.string.sound_cues), model.soundCues) { model.soundCues = it }
            WorkoutViewRow(model)
        }
    }
}
