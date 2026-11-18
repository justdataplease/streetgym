package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.all_complete_body
import com.justdataplease.streetgym.app.resources.all_complete_title
import com.justdataplease.streetgym.app.resources.brand_kicker
import com.justdataplease.streetgym.app.resources.brand_line
import com.justdataplease.streetgym.app.resources.complete_day_no_points
import com.justdataplease.streetgym.app.resources.intensity_easier
import com.justdataplease.streetgym.app.resources.intensity_harder
import com.justdataplease.streetgym.app.resources.intensity_normal
import com.justdataplease.streetgym.app.resources.intensity_note
import com.justdataplease.streetgym.app.resources.intensity_title
import com.justdataplease.streetgym.app.resources.progress_title
import com.justdataplease.streetgym.app.resources.resume_workout
import com.justdataplease.streetgym.app.resources.start_today
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.app.resources.today_section
import com.justdataplease.streetgym.app.resources.workouts_completed_format
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.progress.Intensity
import org.jetbrains.compose.resources.stringResource

/** "Complete day" is written in the Android screens, not in its string files. */
internal const val COMPLETE_DAY = "Complete day"

/**
 * Today (android-streetgym, HomeActivity.renderDashboard): the brand card, the streak, the class the sequence offers
 * next, and how far through the program the athlete is. The card opens the class's page ([onOpenClass]); Start
 * workout, or Resume while it is in progress, opens its workout ([onStart]).
 */
@Composable
fun TodayScreen(model: AppModel, onOpenClass: (String) -> Unit, onStart: (String) -> Unit, onOpenProgress: () -> Unit) {
    val palette = Street.colors
    val today = model.today()
    val next = today.next
    Page {
        StreetCard(fill = palette.primaryDark, modifier = Modifier.padding(bottom = 20.dp), outlined = false) {
            BodyText(stringResource(Res.string.brand_kicker), 12f, color = palette.heroKicker)
            TitleText(stringResource(Res.string.brand_line), 30f, Modifier.padding(top = 8.dp), color = palette.heroText)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            val roomy = maxWidth >= 320.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(Res.string.today_section), Modifier.weight(1f))
                StreakStrip(today.streak, roomy)
            }
        }

        StreetCard(Modifier.padding(bottom = 16.dp), onClick = next?.takeIf { !today.locked }?.let { { onOpenClass(it.id) } }) {
            if (next == null) {
                TitleText(stringResource(Res.string.all_complete_title), 22f)
                BodyText(stringResource(Res.string.all_complete_body), modifier = Modifier.padding(top = 8.dp))
                return@StreetCard
            }
            today.level?.let { level -> TodayHeader(level.number, displayName(level), next.number, level.programs.size) }
            TitleText(displayName(next), 23f)
            BodyText(programMeta(next), 13f, Modifier.padding(top = 6.dp))
            if (today.locked) {
                // The level's tier; the offer opens here once purchases exist on iPhone.
                val tier = if (LevelAccess.tierFor(next.levelNumber) == Tier.PRO) Res.string.tier_pro else Res.string.tier_plus
                Badge(stringResource(tier), Modifier.padding(top = 16.dp), color = palette.textSecondary)
                return@StreetCard
            }
            IntensityRow(model, Modifier.padding(top = 14.dp))
            PrimaryButton(
                stringResource(if (today.resume) Res.string.resume_workout else Res.string.start_today),
                Modifier.padding(top = 16.dp),
            ) { onStart(next.id) }
            SecondaryButton(COMPLETE_DAY, Modifier.padding(top = 8.dp)) { model.completeDay(next.id) }
            BodyText(stringResource(Res.string.complete_day_no_points), 12f, Modifier.padding(top = 6.dp))
        }

        StreetCard(Modifier.padding(bottom = 12.dp), onClick = onOpenProgress) {
            TitleText(stringResource(Res.string.progress_title), 18f)
            BodyText(stringResource(Res.string.workouts_completed_format, today.done, today.total), 14f, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            ProgressBar(if (today.total == 0) 0 else today.done * 100 / today.total)
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
