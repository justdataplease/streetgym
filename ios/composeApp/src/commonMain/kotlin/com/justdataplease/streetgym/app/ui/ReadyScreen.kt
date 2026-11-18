package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.data.ReadyLibrary
import com.justdataplease.streetgym.app.figure.effortColor
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.ready_all_levels
import com.justdataplease.streetgym.app.resources.ready_any_effort
import com.justdataplease.streetgym.app.resources.ready_any_focus
import com.justdataplease.streetgym.app.resources.ready_clear
import com.justdataplease.streetgym.app.resources.ready_count
import com.justdataplease.streetgym.app.resources.ready_focus_back_lever
import com.justdataplease.streetgym.app.resources.ready_focus_flag
import com.justdataplease.streetgym.app.resources.ready_focus_front_lever
import com.justdataplease.streetgym.app.resources.ready_focus_handstand
import com.justdataplease.streetgym.app.resources.ready_focus_lsit
import com.justdataplease.streetgym.app.resources.ready_focus_muscleup
import com.justdataplease.streetgym.app.resources.ready_focus_one_arm
import com.justdataplease.streetgym.app.resources.ready_focus_planche
import com.justdataplease.streetgym.app.resources.ready_group_core
import com.justdataplease.streetgym.app.resources.ready_group_full
import com.justdataplease.streetgym.app.resources.ready_group_legs
import com.justdataplease.streetgym.app.resources.ready_group_mobility
import com.justdataplease.streetgym.app.resources.ready_group_problems
import com.justdataplease.streetgym.app.resources.ready_group_pull
import com.justdataplease.streetgym.app.resources.ready_group_push
import com.justdataplease.streetgym.app.resources.ready_group_short
import com.justdataplease.streetgym.app.resources.ready_group_skills
import com.justdataplease.streetgym.app.resources.ready_intro
import com.justdataplease.streetgym.app.resources.ready_none
import com.justdataplease.streetgym.app.resources.ready_problems_note
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.data.effort.Effort
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Training › Library (android-streetgym, HomeActivity.renderReadyWorkouts): the ready workouts for days off the program,
 * under their groups. Three rows of chips filter them ([filter], kept while the app runs): how hard for this athlete,
 * the grade and the focus. Every tier sees the list; below Pro a Pro workout wears the tag and opens the offer
 * ([onPaywall]), the free ones open as any other ([onOpen]).
 */
@Composable
fun ColumnScope.ReadyWorkouts(
    model: AppModel,
    filter: ReadyFilter,
    onFilter: (ReadyFilter) -> Unit,
    onOpen: (String) -> Unit,
    onPaywall: (Paywall) -> Unit,
) {
    val palette = Street.colors
    BodyText(stringResource(Res.string.ready_intro))
    val efforts = listOf(null) + Effort.entries
    ChipRow(
        efforts.map { it?.let { effort -> effortText(effort) } ?: stringResource(Res.string.ready_any_effort) },
        efforts.indexOf(filter.effort), Modifier.padding(top = 14.dp), colors = efforts.map { it?.let(::effortColor) },
    ) { onFilter(filter.copy(effort = efforts[it])) }
    val grades = listOf(null) + ReadyLibrary.GRADES
    ChipRow(
        grades.map { it?.let { grade -> readyGradeName(grade) } ?: stringResource(Res.string.ready_all_levels) },
        grades.indexOf(filter.grade), Modifier.padding(top = 8.dp),
    ) { onFilter(filter.copy(grade = grades[it])) }
    val focuses = listOf(null) + model.readyFocuses()
    ChipRow(
        focuses.map { it?.let { focus -> readyFocusName(focus) } ?: stringResource(Res.string.ready_any_focus) },
        focuses.indexOf(filter.focus).coerceAtLeast(0), Modifier.padding(top = 8.dp),
    ) { onFilter(filter.copy(focus = focuses[it])) }

    val shown = model.readyShown(filter)
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BodyText(pluralStringResource(Res.plurals.ready_count, shown.size, shown.size), 13f, Modifier.weight(1f), color = palette.primary)
        if (filter.filtered) FilterChip(stringResource(Res.string.ready_clear), selected = false, outlined = true) { onFilter(ReadyFilter()) }
    }
    if (shown.isEmpty()) BodyText(stringResource(Res.string.ready_none), 14f, Modifier.padding(top = 8.dp))
    ReadyLibrary.groups(shown).forEach { (group, workouts) ->
        SectionLabel(readyGroupName(group), Modifier.padding(top = 14.dp))
        // Aches & posture: gentle sessions, not a treatment; when to see someone first.
        if (group == ReadyLibrary.PROBLEMS) BodyText(stringResource(Res.string.ready_problems_note), 13f, Modifier.padding(bottom = 10.dp))
        workouts.forEach { LibraryCard(model, it, Modifier.padding(bottom = 10.dp), onOpen, onPaywall) }
    }
}

/**
 * One row of filter chips (Android: WorkoutListsUi.filterChips): it scrolls sideways, keeping the chosen chip in view;
 * [colors] gives a chip its colour when chosen (an effort's green, orange or red), null the usual one.
 */
@Composable
private fun ChipRow(labels: List<String>, selected: Int, modifier: Modifier = Modifier, colors: List<Color?> = emptyList(), onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val bring = remember { BringIntoViewRequester() }
            if (index == selected && selected > 0) LaunchedEffect(selected) { bring.bringIntoView() }
            FilterChip(label, index == selected, Modifier.padding(end = 8.dp).bringIntoViewRequester(bring), chosenColor = colors.getOrNull(index)) {
                if (index != selected) onSelect(index)
            }
        }
    }
}

/** A focus chip's name: a Library group (Full body, Pull…) or a skill family (Front lever, Flag…) (Android: readyFocusName). */
@Composable
fun readyFocusName(focus: String): String = when (focus) {
    "handstand" -> stringResource(Res.string.ready_focus_handstand)
    "front_lever" -> stringResource(Res.string.ready_focus_front_lever)
    "back_lever" -> stringResource(Res.string.ready_focus_back_lever)
    "planche" -> stringResource(Res.string.ready_focus_planche)
    "flag" -> stringResource(Res.string.ready_focus_flag)
    "muscleup" -> stringResource(Res.string.ready_focus_muscleup)
    "lsit" -> stringResource(Res.string.ready_focus_lsit)
    "one_arm" -> stringResource(Res.string.ready_focus_one_arm)
    else -> readyGroupName(focus)
}

/** A Library group's heading (Android: readyGroupName). */
@Composable
fun readyGroupName(group: String?): String = stringResource(when (group) {
    "full" -> Res.string.ready_group_full
    "pull" -> Res.string.ready_group_pull
    "push" -> Res.string.ready_group_push
    "legs" -> Res.string.ready_group_legs
    "core" -> Res.string.ready_group_core
    "skills" -> Res.string.ready_group_skills
    "short" -> Res.string.ready_group_short
    "problems" -> Res.string.ready_group_problems
    else -> Res.string.ready_group_mobility
})
