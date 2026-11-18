package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.TodayLabel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.all_complete_body
import com.justdataplease.streetgym.app.resources.all_complete_title
import com.justdataplease.streetgym.app.resources.brand_kicker
import com.justdataplease.streetgym.app.resources.brand_line
import com.justdataplease.streetgym.app.resources.complete_day_no_points
import com.justdataplease.streetgym.app.resources.exam_replay_today
import com.justdataplease.streetgym.app.resources.intensity_easier
import com.justdataplease.streetgym.app.resources.intensity_harder
import com.justdataplease.streetgym.app.resources.intensity_normal
import com.justdataplease.streetgym.app.resources.intensity_note
import com.justdataplease.streetgym.app.resources.intensity_title
import com.justdataplease.streetgym.app.resources.progress_title
import com.justdataplease.streetgym.app.resources.repeat_note
import com.justdataplease.streetgym.app.resources.replay_today
import com.justdataplease.streetgym.app.resources.resume_workout
import com.justdataplease.streetgym.app.resources.start_today
import com.justdataplease.streetgym.app.resources.today_section
import com.justdataplease.streetgym.app.resources.workouts_completed_format
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.data.progress.Intensity
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** "Complete day" is written in the Android screens, not in its string files. */
internal const val COMPLETE_DAY = "Complete day"

/** How often Today looks at the clock for a new day while it shows (the app also looks each time it comes back). */
private const val DAY_CHECK_MILLIS = 30_000L

/**
 * The streak's roomy size on Today's line: a row of 320 dp or more (Android: a screen 360 dp wide) and text no larger
 * than 115%; otherwise the smaller flame and boxes, so the line still fits (Android: todayLine).
 */
fun roomyStreak(rowWidth: Dp, fontScale: Float): Boolean = rowWidth >= 320.dp && fontScale <= 1.15f

/**
 * Today (android-streetgym, HomeActivity.renderDashboard): the brand card, the streak, the class the sequence offers
 * next, and how far through the program the athlete is. The card opens the class's page ([onOpenClass]); Start
 * workout, or Resume while it is in progress, opens its workout ([onStart]). A class in a level the tier doesn't open
 * shows Unlock, and the card and Unlock open the offer ([onOffer], with the level). A tap on the streak opens its sheet
 * ([onStreak]).
 */
@Composable
fun TodayScreen(
    model: AppModel,
    onOpenClass: (String) -> Unit,
    onStart: (String, Boolean) -> Unit,
    onOpenProgress: () -> Unit,
    onOffer: (Int) -> Unit = {},
    onStreak: () -> Unit = {},
) {
    val palette = Street.colors
    // Back on Today after midnight, the flame is drawn for the new day (Android: onResume and streakDrawnOn).
    LaunchedEffect(model) {
        while (true) {
            model.refreshDay()
            delay(DAY_CHECK_MILLIS)
        }
    }
    val today = model.today()
    val next = today.next
    var completing by remember { mutableStateOf(false) }
    if (completing && next != null) CompleteDayDialog(next, onClose = { completing = false }) {
        completing = false
        model.completeDay(next.id)
    }
    Page {
        StreetCard(fill = palette.primaryDark, modifier = Modifier.padding(bottom = 20.dp), outlined = false) {
            BodyText(stringResource(Res.string.brand_kicker), 12f, color = palette.heroKicker)
            TitleText(stringResource(Res.string.brand_line), 30f, Modifier.padding(top = 8.dp), color = palette.heroText)
        }

        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            val roomy = roomyStreak(maxWidth, fontScale)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(Res.string.today_section), Modifier.weight(1f))
                StreakStrip(today.streak, roomy, onClick = onStreak)
            }
        }

        val offer = next?.levelNumber?.takeIf { today.locked }
        StreetCard(Modifier.padding(bottom = 16.dp), onClick = next?.let { { if (offer != null) onOffer(offer) else onOpenClass(it.id) } }) {
            if (next == null) {
                TitleText(stringResource(Res.string.all_complete_title), 22f)
                BodyText(stringResource(Res.string.all_complete_body), modifier = Modifier.padding(top = 8.dp))
                return@StreetCard
            }
            today.level?.let { level -> TodayHeader(level.number, displayName(level), next.number, level.programs.size) }
            // A repeat in progress, or a replay, says so over the title (Android: renderDashboard's label).
            when (val label = today.label) {
                TodayLabel.Repeat -> stringResource(Res.string.repeat_note)
                TodayLabel.Exam -> stringResource(Res.string.exam_replay_today)
                is TodayLabel.Replay -> stringResource(Res.string.replay_today, label.level)
                null -> null
            }?.let { BodyText(it, 12f, Modifier.padding(bottom = 4.dp), color = palette.primary) }
            TitleText(displayName(next), 23f)
            BodyText(programMeta(next), 13f, Modifier.padding(top = 6.dp))
            if (offer != null) {
                // A class of a level the tier doesn't open: Unlock with the tier's sticker, which opens the offer.
                UnlockButton(levelTierName(offer), Modifier.padding(top = 16.dp)) { onOffer(offer) }
                return@StreetCard
            }
            IntensityRow(model, Modifier.padding(top = 14.dp))
            PrimaryButton(
                stringResource(if (today.resume) Res.string.resume_workout else Res.string.start_today),
                Modifier.padding(top = 16.dp),
            ) { onStart(next.id, today.repeating) }
            if (today.canComplete) {
                SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 8.dp)) { completing = true }
                BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
            }
        }

        StreetCard(Modifier.padding(bottom = 12.dp), onClick = onOpenProgress) {
            TitleText(stringResource(Res.string.progress_title), 18f)
            BodyText(stringResource(Res.string.workouts_completed_format, today.done, today.total), 14f, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            ProgressBar(percentage(today.done, today.total))
        }
    }
}

/** The day's Easier, Normal or Harder over a class that can be started. */
@Composable
fun IntensityRow(model: AppModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        BodyText(stringResource(Res.string.intensity_title), 13f, color = Street.colors.text)
        SegmentedSwitch(
            labels = listOf(Res.string.intensity_easier, Res.string.intensity_normal, Res.string.intensity_harder).map { stringResource(it) },
            selected = model.intensity.ordinal,
            modifier = Modifier.padding(top = 6.dp),
        ) { model.intensity = Intensity.entries[it] }
        BodyText(stringResource(Res.string.intensity_note), 12f, Modifier.padding(top = 4.dp))
    }
}
