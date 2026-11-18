package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.ClassRow
import com.justdataplease.streetgym.app.data.LevelPage
import com.justdataplease.streetgym.app.figure.WorkoutTile
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.assigned_next
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.complete_level_no_points
import com.justdataplease.streetgym.app.resources.completed
import com.justdataplease.streetgym.app.resources.empty_description
import com.justdataplease.streetgym.app.resources.exam_badge
import com.justdataplease.streetgym.app.resources.exam_list_not_yet
import com.justdataplease.streetgym.app.resources.exam_list_passed
import com.justdataplease.streetgym.app.resources.level_progress_format
import com.justdataplease.streetgym.app.resources.locked_later
import com.justdataplease.streetgym.app.resources.placement_level_one_hint
import com.justdataplease.streetgym.app.resources.replay_confirm
import com.justdataplease.streetgym.app.resources.replay_dialog_message
import com.justdataplease.streetgym.app.resources.replay_dialog_title
import com.justdataplease.streetgym.app.resources.replay_go_back
import com.justdataplease.streetgym.app.resources.switch_off_day
import com.justdataplease.streetgym.app.resources.workout_number
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.stringResource

/** Android's words for Complete level, written in its screen (renderWorkouts) rather than its string files. */
internal const val COMPLETE_LEVEL = "Complete level"
internal const val COMPLETE_LEVEL_TITLE = "Complete this level?"

/** "Mark all 100 days in Foundations complete." (Android: renderWorkouts' Complete level). */
internal fun completeLevelMessage(days: Int, levelName: String) = "Mark all $days days in $levelName complete."

/** A class row's tile, a little larger than the old number so the figure reads (Android: ProgramListAdapter.TILE_DP). */
private val TILE = 52.dp

/**
 * A level's classes (android-streetgym, HomeActivity.renderWorkouts and ProgramListAdapter): the level's name, its whole
 * description and how far through it the athlete is; the tier's tag on a locked level, which opens the offer
 * ([onOffer]); Complete level on the level of today's class ([onCompleted] after it); Go back to this level on an
 * earlier one ([onWentBack] after it); then a row per class. A row opens its class ([onOpenClass]), or the offer for a
 * locked class not done.
 */
@Composable
fun LevelScreen(
    model: AppModel,
    number: Int,
    onOpenClass: (String) -> Unit,
    onOffer: (Int) -> Unit,
    onCompleted: () -> Unit,
    onWentBack: () -> Unit,
) {
    val page = model.levelPage(number) ?: return
    var completing by remember { mutableStateOf(false) }
    var goingBack by remember { mutableStateOf(false) }
    val tier = levelTierName(number)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        item(key = "header") {
            LevelHeader(page, tier, onOffer = { onOffer(number) }, onComplete = { completing = true }, onGoBack = { goingBack = true })
        }
        items(page.rows, key = { it.program.id }) { row ->
            ClassRowView(model, row, tier.takeIf { page.locked }) {
                // Developer inspection opens a locked class too, read-only (Android: renderWorkouts).
                if (page.locked && !row.completed && !model.inspectAllDays) onOffer(number) else onOpenClass(row.program.id)
            }
        }
    }
    val name = displayName(page.level)
    if (completing) StreetDialog(
        title = COMPLETE_LEVEL_TITLE,
        message = completeLevelMessage(page.level.programs.size, name) + "\n\n" + stringResource(Res.string.complete_level_no_points),
        onDismiss = { completing = false },
        confirm = DialogAction(COMPLETE) {
            completing = false
            model.completeLevel(number)
            onCompleted()
        },
        dismiss = DialogAction(stringResource(Res.string.cancel)) { completing = false },
    )
    if (goingBack) StreetDialog(
        title = stringResource(Res.string.replay_dialog_title, number),
        message = stringResource(Res.string.replay_dialog_message, name),
        onDismiss = { goingBack = false },
        confirm = DialogAction(stringResource(Res.string.replay_confirm)) {
            goingBack = false
            model.goBackTo(number)
            onWentBack()
        },
        dismiss = DialogAction(stringResource(Res.string.cancel)) { goingBack = false },
    )
}

/** The level's head over its classes: not a card, on the page, as Android's list header. */
@Composable
private fun LevelHeader(page: LevelPage, tier: String, onOffer: () -> Unit, onComplete: () -> Unit, onGoBack: () -> Unit) {
    val level = page.level
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp)) {
        TitleText(displayName(level), 23f)
        level.description.resolve().takeIf(String::isNotBlank)?.let { BodyText(it, 14f, Modifier.padding(top = 4.dp)) }
        BodyText(stringResource(Res.string.level_progress_format, page.complete, level.programs.size), 13f, Modifier.padding(top = 8.dp))
        ProgressBar(percentage(page.complete, level.programs.size), Modifier.padding(top = 6.dp))
        // A locked level wears its tier's tag instead of Complete level, so the lock can't be skipped; it opens the offer.
        if (page.locked) LockBadge(tier, Modifier.padding(top = 12.dp), onClick = onOffer)
        if (page.current) {
            // Level 1 is a gentle on-ramp: someone who already trains can move on in one step.
            if (page.levelOneHint) BodyText(stringResource(Res.string.placement_level_one_hint), 13f, Modifier.padding(top = 12.dp))
            SecondaryButton(COMPLETE_LEVEL, Modifier.padding(top = 10.dp), onClick = onComplete)
            BodyText(stringResource(Res.string.complete_level_no_points), 12f, Modifier.padding(top = 6.dp))
        }
        if (page.canGoBack) SecondaryButton(stringResource(Res.string.replay_go_back), Modifier.padding(top = 10.dp), onClick = onGoBack)
    }
}

/**
 * One class (Android: ProgramListAdapter.Row): its hardest exercise playing on a tile ringed in its effort colour, the
 * class number, the name on up to two lines, the meta with "Easy for you" once reached, an exam's state, and its mark:
 * ✓ done, NEXT, LATER, SWITCHED OFF (the row greyed), or the tier's tag on a locked class not done.
 */
@Composable
private fun ClassRowView(model: AppModel, row: ClassRow, lockedTier: String?, onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val program = row.program
    val name = displayName(program)
    val effort = model.effortOf(program.id)
    val meta = listOfNotNull(programMeta(program, position = false).ifBlank { null }, effort?.let { effortText(it) }).joinToString(" • ")
        .ifBlank { program.description.resolve().ifBlank { stringResource(Res.string.empty_description) } }
    val behindLock = lockedTier != null && !row.completed
    val later = (!row.unlocked || row.off) && !behindLock
    val state = when {
        behindLock -> lockedTier
        row.completed -> stringResource(Res.string.completed)
        row.off -> stringResource(Res.string.switch_off_day)
        row.assigned -> stringResource(Res.string.assigned_next)
        else -> stringResource(Res.string.locked_later)
    }
    val spoken = buildString {
        append(stringResource(Res.string.workout_number, program.number)).append(", ").append(name)
        if (row.unlocked && meta.isNotBlank()) append(", ").append(meta)
        append(", ").append(state)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .alpha(if (row.off) 0.5f else 1f)
                .shadow(1.dp, streetOutline(theme, 24.dp), clip = false)
                .streetBackground(theme, palette.surface, 24.dp)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkoutTile(model.tileOf(program.id), effort, TILE)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The number leads the title, so the meta skips "Workout 12 of 100".
                    BasicText(program.number.toString(), Modifier.padding(end = 8.dp), maxLines = 1,
                        style = TextStyle(color = palette.textSecondary, fontSize = 16.sp, fontFamily = Street.type.bold))
                    BasicText(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = palette.text, fontSize = 16.sp, fontFamily = Street.type.bold))
                }
                if (row.unlocked) BodyText(meta, 13f, maxLines = 2)
                // An exam says so, with how its latest attempt went.
                if (program.isExam) BodyText(stringResource(when (row.exam?.passed) {
                    true -> Res.string.exam_list_passed
                    false -> Res.string.exam_list_not_yet
                    null -> Res.string.exam_badge
                }), 13f, color = palette.action)
            }
            if (row.completed) Box(Modifier.size(36.dp, 44.dp), contentAlignment = Alignment.Center) {
                BasicText("✓", style = TextStyle(color = palette.success, fontSize = 22.sp, fontFamily = Street.type.bold))
            }
            if (!row.completed && row.assigned && !behindLock) Badge(stringResource(Res.string.assigned_next), color = palette.action)
            if (later) Badge(stringResource(if (row.off) Res.string.switch_off_day else Res.string.locked_later), color = palette.textSecondary)
            if (behindLock) LockBadge(lockedTier.orEmpty())
        }
    }
}
