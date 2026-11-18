package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.skills_best
import com.justdataplease.streetgym.app.resources.skills_best_none
import com.justdataplease.streetgym.app.resources.skills_beyond_program
import com.justdataplease.streetgym.app.resources.skills_builds_on
import com.justdataplease.streetgym.app.resources.skills_close
import com.justdataplease.streetgym.app.resources.skills_counts_title
import com.justdataplease.streetgym.app.resources.skills_difficulty
import com.justdataplease.streetgym.app.resources.skills_held_through
import com.justdataplease.streetgym.app.resources.skills_help
import com.justdataplease.streetgym.app.resources.skills_how_points
import com.justdataplease.streetgym.app.resources.skills_ladder_rung
import com.justdataplease.streetgym.app.resources.skills_level
import com.justdataplease.streetgym.app.resources.skills_levels_title
import com.justdataplease.streetgym.app.resources.skills_medal_description
import com.justdataplease.streetgym.app.resources.skills_next_level
import com.justdataplease.streetgym.app.resources.skills_on_schedule
import com.justdataplease.streetgym.app.resources.skills_plus
import com.justdataplease.streetgym.app.resources.skills_points_description
import com.justdataplease.streetgym.app.resources.skills_points_kicker
import com.justdataplease.streetgym.app.resources.skills_pro
import com.justdataplease.streetgym.app.resources.skills_ranks
import com.justdataplease.streetgym.app.resources.skills_ranks_description
import com.justdataplease.streetgym.app.resources.skills_readiness_test
import com.justdataplease.streetgym.app.resources.skills_schedule_class
import com.justdataplease.streetgym.app.resources.skills_schedule_level
import com.justdataplease.streetgym.app.resources.skills_sets_done
import com.justdataplease.streetgym.app.resources.skills_switched_off
import com.justdataplease.streetgym.app.resources.skills_switched_off_note
import com.justdataplease.streetgym.app.resources.skills_unlocks_next
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillStatus
import org.jetbrains.compose.resources.stringResource

/** Five blocks to the next rank under a medal, each a fifth of the sets it takes. */
private const val SEGMENTS = 5

/**
 * Progress › Skills (android-streetgym, ui/SkillsScreen.kt skillsContent): the athlete's points, a tap away from how
 * they and the ranks work ([onHelp]); the five rank shields; then one ladder of medals per family, each wearing its rank.
 * A medal opens its skill's sheet ([onOpen], with the skill's id).
 */
@Composable
fun SkillsSection(model: AppModel, onOpen: (String) -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val progress = model.skillProgress() ?: return
    val theme = Street.theme
    val palette = theme.palette
    val points = pointsText(model.points())
    val spoken = stringResource(Res.string.skills_points_description, points)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(top = 4.dp).fillMaxWidth().streetBackground(theme, palette.primaryDark, 24.dp)
                .clickable(role = Role.Button, onClick = onHelp).clearAndSetSemantics { contentDescription = spoken }
                .padding(18.dp),
        ) {
            BasicText(stringResource(Res.string.skills_points_kicker).uppercase(),
                style = TextStyle(color = palette.accent, fontSize = 12.sp, fontFamily = Street.type.bold, letterSpacing = 0.1.em))
            TitleText(points, 38f, Modifier.padding(top = 4.dp), color = Color.White)
        }
        SectionLabel(stringResource(Res.string.skills_ranks), Modifier.padding(top = 14.dp))
        RankLegend(Modifier.padding(top = 6.dp), onHelp)
        progress.families.forEach { (family, statuses) ->
            TitleText(family.spec.name, 16f, Modifier.padding(top = 14.dp))
            // The tags on the medals' corners reach past their cells: room above for Plus or Pro, wider gaps beside.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp)) {
                statuses.forEach { status ->
                    SkillCell(status, model.lockFor(status), model.skillOff(family, status), Modifier.padding(end = 14.dp)) {
                        onOpen(status.skill.id)
                    }
                }
            }
        }
    }
}

/** Every shield a skill can earn, each with its name; a tap explains them (Android: rankLegend). */
@Composable
private fun RankLegend(modifier: Modifier, onHelp: () -> Unit) {
    val theme = Street.theme
    val levels = SkillLevel.entries.filter { it > SkillLevel.NONE }
    val names = levels.map { levelName(it) }
    val spoken = stringResource(Res.string.skills_ranks_description, names.joinToString(", "))
    Row(
        modifier.fillMaxWidth().streetBackground(theme, theme.palette.surface, 18.dp)
            .clickable(role = Role.Button, onClick = onHelp).clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        levels.forEachIndexed { index, level ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RankBadge(level, 0.85f)
                BasicText(names[index], Modifier.padding(top = 4.dp), maxLines = 1,
                    style = TextStyle(color = theme.palette.text, fontSize = 11.sp, fontFamily = Street.type.bold, textAlign = TextAlign.Center))
            }
        }
    }
}

/**
 * One skill on its ladder (Android: skillCell): the medal with its rank shield, Plus or Pro across its top edge while
 * a locked level teaches it, five blocks to the next rank beside the shield, and the name. A switched-off skill is
 * greyed and says so.
 */
@Composable
private fun SkillCell(status: SkillStatus, lock: Tier?, off: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = Street.colors
    val state = when {
        off -> stringResource(Res.string.skills_switched_off)
        lock != null -> partOf(lock)
        else -> levelName(status.level)
    }
    val spoken = stringResource(Res.string.skills_medal_description, status.skill.name, difficultyName(status.skill.difficulty), state)
    Column(
        modifier.width(86.dp).alpha(if (off) 0.4f else 1f).clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(86.dp, medalHeight(78.dp))) {
            SkillMedal(status, 78.dp, Modifier.align(Alignment.TopCenter))
            if (lock != null && !off) LockBadge(
                stringResource(if (lock == Tier.PRO) Res.string.tier_pro else Res.string.tier_plus),
                Modifier.align(Alignment.TopEnd).offset(8.dp, (-10).dp),
                textSize = 10f, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                rim = palette.background,
            )
        }
        // The bar keeps its place when there is nothing to show, so the names start level; beside a shield it starts
        // under the medal's left edge, before the first rank it is centred.
        val shown = status.next != null && lock == null && !off
        SegmentBar(
            if (shown) status.progress * SEGMENTS / 100 else 0,
            Modifier.padding(top = 4.dp).then(if (status.level > SkillLevel.NONE) Modifier.align(Alignment.Start).padding(start = 8.dp)
                else Modifier).size(54.dp, 5.dp).alpha(if (shown) 1f else 0f),
        )
        BasicText(status.skill.name, Modifier.fillMaxWidth().padding(top = 5.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = palette.text, fontSize = 12.sp, fontFamily = Street.type.body, textAlign = TextAlign.Center))
    }
}

/** A bar of [SEGMENTS] blocks, the first [filled] in the primary colour. */
@Composable
private fun SegmentBar(filled: Int, modifier: Modifier) {
    val palette = Street.colors
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val block = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
        for (i in 0 until SEGMENTS) drawRect(if (i < filled) palette.primary else palette.divider, Offset(i * (block + gap), 0f), Size(block, size.height))
    }
}

@Composable
private fun partOf(tier: Tier): String = stringResource(if (tier == Tier.PRO) Res.string.skills_pro else Res.string.skills_plus)

/** "How do points and ranks work?" with the rules, OK (Android: showSkillsHelp). */
@Composable
fun SkillsHelpDialog(onClose: () -> Unit) {
    StreetDialog(
        title = stringResource(Res.string.skills_help),
        message = stringResource(Res.string.skills_how_points),
        onDismiss = onClose,
        confirm = DialogAction(OK, onClose),
    )
}

/**
 * A skill's sheet (Android: showSkillSheet): the movement playing, the medal, its ladder and rung, the tier it waits
 * for, its difficulty, rank, sets and best set, what the next rank takes with a bar, every rank's sets at its target,
 * what counts, where the program teaches it, and its neighbours on the ladder. [id] is the skill's.
 */
@Composable
fun SkillSheet(model: AppModel, id: String, onClose: () -> Unit) {
    val progress = model.skillProgress() ?: return
    val (family, statuses) = progress.families.firstOrNull { (_, statuses) -> statuses.any { it.skill.id == id } } ?: return
    val status = statuses.first { it.skill.id == id }
    SkillSheet(status, family, statuses, model.lockFor(status), model.skillOff(family, status), onClose)
}

@Composable
private fun SkillSheet(status: SkillStatus, family: SkillFamily, statuses: List<SkillStatus>, lock: Tier?, off: Boolean, onClose: () -> Unit) {
    val skill = status.skill
    val standard = skill.standard
    val palette = Street.colors
    StreetDialog(title = null, onDismiss = onClose, confirm = DialogAction(stringResource(Res.string.skills_close), onClose)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp)) {
            ExerciseFigure(skill.id, skill.name, Modifier.fillMaxWidth().height(168.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                // The shield hangs past the medal's corner, so the medal gets the room for it.
                Box(Modifier.size(56.dp + shieldOverhang(56.dp), medalHeight(56.dp) + 8.dp)) { SkillMedal(status, 56.dp) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    TitleText(skill.name, 20f)
                    BodyText(stringResource(Res.string.skills_ladder_rung, family.spec.name, skill.rung + 1, statuses.size), 13f)
                    lock?.let { LockBadge(partOf(it), Modifier.padding(top = 4.dp)) }
                }
            }
            if (off) BodyText(stringResource(Res.string.skills_switched_off_note), 13f, Modifier.padding(top = 8.dp), color = palette.action)
            DetailRow(stringResource(Res.string.skills_difficulty), difficultyName(skill.difficulty), Modifier.padding(top = 10.dp))
            DetailRow(stringResource(Res.string.skills_level), levelName(status.level))
            status.carriedBy?.let { DetailRow(stringResource(Res.string.skills_held_through), it.name) }
            DetailRow(stringResource(Res.string.skills_sets_done), status.sets.toString())
            DetailRow(stringResource(Res.string.skills_best),
                if (status.best > 0) maxText(standard, status.best) else stringResource(Res.string.skills_best_none))
            status.next?.let { next ->
                DetailRow(stringResource(Res.string.skills_next_level, levelName(next)), needText(status, named = false))
                ProgressBar(status.progress, Modifier.padding(top = 4.dp))
            }
            SectionLabel(stringResource(Res.string.skills_levels_title), Modifier.padding(top = 12.dp))
            SkillLevel.entries.drop(1).forEach { level ->
                DetailRow((if (status.level >= level) "✓ " else "") + levelName(level), setsAt(level.sets, standard, standard.target(level) ?: 0))
            }
            skill.counts?.let {
                SectionLabel(stringResource(Res.string.skills_counts_title), Modifier.padding(top = 12.dp))
                BodyText(it, 14f, Modifier.padding(top = 4.dp))
            }
            val expected = skill.expected
            val schedule = when {
                expected == null -> stringResource(Res.string.skills_beyond_program)
                skill.beyondProgram -> stringResource(Res.string.skills_readiness_test, expected.level)
                expected.number != null -> stringResource(Res.string.skills_schedule_class, expected.level, expected.number!!)
                else -> stringResource(Res.string.skills_schedule_level, expected.level)
            }
            DetailRow(stringResource(Res.string.skills_on_schedule), schedule)
            statuses.getOrNull(skill.rung - 1)?.let { DetailRow(stringResource(Res.string.skills_builds_on), it.skill.name) }
            statuses.getOrNull(skill.rung + 1)?.let { DetailRow(stringResource(Res.string.skills_unlocks_next), it.skill.name) }
        }
    }
}

/** A label and its value on one line, the value at the end (Android: detailRow). */
@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        BodyText(label, 13f, Modifier.weight(1f))
        BodyText(value, 13f, Modifier.padding(start = 8.dp), color = Street.colors.text, align = TextAlign.End)
    }
}
