package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.progress_tab_history
import com.justdataplease.streetgym.app.resources.progress_tab_leaderboard
import com.justdataplease.streetgym.app.resources.progress_tab_skills
import org.jetbrains.compose.resources.stringResource

/** Progress's three tabs, in Android's order (HomeActivity.renderProgress). */
const val PROGRESS_SKILLS = 0
const val PROGRESS_HISTORY = 1
const val PROGRESS_LEADERBOARD = 2

/**
 * Progress (android-streetgym, HomeActivity.renderProgress): the switch between Skills, History and the Leaderboard,
 * kept while the app runs ([tab], [onTab]), then the tab's content. Skills open a skill's sheet ([onSkill]) and the help
 * ([onHelp]); History's rows open their class ([onOpenClass]) and its streak line the streak's sheet ([onStreak]). The
 * Leaderboard needs the account (round 13).
 */
@Composable
fun ProgressScreen(
    model: AppModel,
    tab: Int,
    calendar: HistoryCalendarState,
    onTab: (Int) -> Unit,
    onSkill: (String) -> Unit,
    onHelp: () -> Unit,
    onOpenClass: (String) -> Unit,
    onStreak: () -> Unit,
) {
    Page {
        // Without the skills (a program without them) Android shows History alone.
        val skills = model.skills != null
        val shown = if (skills) tab else PROGRESS_HISTORY
        if (skills) SegmentedSwitch(
            listOf(Res.string.progress_tab_skills, Res.string.progress_tab_history, Res.string.progress_tab_leaderboard).map { stringResource(it) },
            shown,
            onSelect = onTab,
        )
        when (shown) {
            PROGRESS_SKILLS -> SkillsSection(model, onSkill, onHelp, Modifier.padding(top = 14.dp))
            PROGRESS_LEADERBOARD -> ToComeCard(stringResource(Res.string.progress_tab_leaderboard), Modifier.padding(top = 14.dp))
            else -> HistorySection(model, calendar, onOpenClass, onStreak, Modifier.padding(top = 14.dp))
        }
    }
}
