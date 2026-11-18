package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.leaderboard_intro
import com.justdataplease.streetgym.app.resources.leaderboard_rank
import com.justdataplease.streetgym.app.resources.leaderboard_unavailable
import com.justdataplease.streetgym.app.resources.progress_tab_history
import com.justdataplease.streetgym.app.resources.progress_tab_leaderboard
import com.justdataplease.streetgym.app.resources.progress_tab_skills
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.stringResource

/** Progress's three tabs, in Android's order (HomeActivity.renderProgress). */
const val PROGRESS_SKILLS = 0
const val PROGRESS_HISTORY = 1
const val PROGRESS_LEADERBOARD = 2

/**
 * Progress (android-streetgym, HomeActivity.renderProgress): the switch between Skills, History and the Leaderboard,
 * kept while the app runs ([tab], [onTab]), then the tab's content and, on Free, the ad card closing it. Skills open a
 * skill's sheet ([onSkill]) and the help ([onHelp]); History's rows open their class ([onOpenClass]) and its streak line
 * the streak's sheet ([onStreak]). The Leaderboard: its intro, then on Free a board's shape under the layer whose Unlock
 * opens the offer ([onPaywall]); Plus and Pro read that it isn't in this build, as Android's builds without Firebase say
 * (the boards come with the account, round 13).
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
    onPaywall: (Paywall) -> Unit = {},
) {
    // Without the skills (a program without them) Android shows History alone.
    val skills = model.skills != null
    val shown = if (skills) tab else PROGRESS_HISTORY
    val tabs: @Composable () -> Unit = {
        if (skills) SegmentedSwitch(
            listOf(Res.string.progress_tab_skills, Res.string.progress_tab_history, Res.string.progress_tab_leaderboard).map { stringResource(it) },
            shown,
            onSelect = onTab,
        )
    }
    if (shown == PROGRESS_LEADERBOARD && !model.tier.leaderboard) {
        // The layer takes what is left of the screen and cuts the rows there, so Unlock sits in the middle of what is
        // seen (Android: renderLeaderboard's layer, its height set to the rest of the screen).
        Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp)) {
            tabs()
            BodyText(stringResource(Res.string.leaderboard_intro), 14f, Modifier.padding(top = 12.dp))
            LockedLayer(stringResource(Res.string.tier_plus), Modifier.padding(top = 12.dp).weight(1f).clipToBounds(),
                onOffer = { onPaywall(Paywall.Leaderboard) }) { LeaderboardSample() }
        }
        return
    }
    Page {
        tabs()
        when (shown) {
            PROGRESS_SKILLS -> SkillsSection(model, onSkill, onHelp, Modifier.padding(top = 14.dp))
            PROGRESS_LEADERBOARD -> {
                BodyText(stringResource(Res.string.leaderboard_intro), 14f, Modifier.padding(top = 12.dp))
                StreetCard(Modifier.padding(top = 12.dp)) { BodyText(stringResource(Res.string.leaderboard_unavailable)) }
            }
            else -> HistorySection(model, calendar, onOpenClass, onStreak, Modifier.padding(top = 14.dp))
        }
        EndAd(model)
    }
}

/**
 * Free's leaderboard behind its layer (Android: leaderboardSample): twenty places with grey bars where the names and
 * points would be, more than a screen holds, from the top of the layer down.
 */
@Composable
private fun LeaderboardSample() {
    val theme = Street.theme
    val palette = theme.palette
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).align(Alignment.TopStart)) {
            SAMPLE_SHARES.forEachIndexed { index, share ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp).streetBackground(theme, palette.surface, 14.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyText(stringResource(Res.string.leaderboard_rank, index + 1), 14f, Modifier.width(48.dp))
                    Box(Modifier.weight(share).height(12.dp).streetBackground(theme, palette.divider, 6.dp))
                    Spacer(Modifier.weight(1f - share))
                    Box(Modifier.width(44.dp).height(12.dp).streetBackground(theme, palette.divider, 6.dp))
                }
            }
        }
    }
}

/** How long each sample row's name bar is (Android: leaderboardSample's shares). */
private val SAMPLE_SHARES = listOf(0.62f, 0.5f, 0.56f, 0.42f, 0.5f, 0.38f, 0.58f, 0.46f, 0.52f, 0.4f, 0.55f, 0.44f, 0.6f, 0.48f,
    0.36f, 0.54f, 0.43f, 0.57f, 0.41f, 0.5f)
