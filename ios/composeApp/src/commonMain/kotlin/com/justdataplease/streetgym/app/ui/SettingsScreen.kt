package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.about_body
import com.justdataplease.streetgym.app.resources.about_title
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.developer_done
import com.justdataplease.streetgym.app.resources.developer_hide
import com.justdataplease.streetgym.app.resources.developer_inspect_all
import com.justdataplease.streetgym.app.resources.developer_inspect_note
import com.justdataplease.streetgym.app.resources.developer_taps_left
import com.justdataplease.streetgym.app.resources.developer_title
import com.justdataplease.streetgym.app.resources.equipment_count
import com.justdataplease.streetgym.app.resources.equipment_intro
import com.justdataplease.streetgym.app.resources.equipment_section
import com.justdataplease.streetgym.app.resources.equipment_switched_off
import com.justdataplease.streetgym.app.resources.keep_screen_awake
import com.justdataplease.streetgym.app.resources.reset_body
import com.justdataplease.streetgym.app.resources.reset_button
import com.justdataplease.streetgym.app.resources.reset_confirm
import com.justdataplease.streetgym.app.resources.reset_confirm_message
import com.justdataplease.streetgym.app.resources.reset_confirm_title
import com.justdataplease.streetgym.app.resources.reset_title
import com.justdataplease.streetgym.app.resources.safety_body
import com.justdataplease.streetgym.app.resources.safety_title
import com.justdataplease.streetgym.app.resources.sound_cues
import com.justdataplease.streetgym.app.resources.switches_all_on
import com.justdataplease.streetgym.app.resources.switches_always_on
import com.justdataplease.streetgym.app.resources.switches_done
import com.justdataplease.streetgym.app.resources.switches_family_intro
import com.justdataplease.streetgym.app.resources.switches_intro
import com.justdataplease.streetgym.app.resources.switches_off
import com.justdataplease.streetgym.app.resources.switches_off_basics
import com.justdataplease.streetgym.app.resources.switches_rung
import com.justdataplease.streetgym.app.resources.switches_section
import com.justdataplease.streetgym.app.resources.switches_steps
import com.justdataplease.streetgym.app.resources.switches_up_to
import com.justdataplease.streetgym.app.resources.theme_section
import com.justdataplease.streetgym.app.resources.theme_selected
import com.justdataplease.streetgym.app.resources.training_preferences
import com.justdataplease.streetgym.app.resources.version_format
import com.justdataplease.streetgym.app.theme.Corner
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentNeeds
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillProgressions
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Settings (android-streetgym, HomeActivity.renderSettings) in Android's order, without what needs an account or a
 * purchase (Profile, Plus and Pro, Your data, Ad privacy): Theme, During your workout, Skills in your classes, Equipment
 * you have, Start over, Train safely and About. A skill's row opens its steps ([onSwitches], with the family's id);
 * Reset progress asks first ([onReset]); ten taps on the version open the developer settings ([onDeveloper]), counting
 * down in a note ([onNote]).
 */
@Composable
fun SettingsScreen(
    model: AppModel,
    onSwitches: (String) -> Unit = {},
    onReset: () -> Unit = {},
    onDeveloper: () -> Unit = {},
    onNote: (String) -> Unit = {},
) {
    val palette = Street.colors
    val scope = rememberCoroutineScope()
    Page {
        SectionLabel(stringResource(Res.string.theme_section))
        ThemePicker(model.theme, Modifier.padding(bottom = 18.dp)) { model.theme = it }

        SectionLabel(stringResource(Res.string.training_preferences))
        StreetCard(Modifier.padding(bottom = 16.dp)) {
            SettingSwitch(stringResource(Res.string.keep_screen_awake), model.keepScreenAwake) { model.keepScreenAwake = it }
            SettingSwitch(stringResource(Res.string.sound_cues), model.soundCues) { model.soundCues = it }
            WorkoutViewRow(model)
        }

        model.skills?.let { book ->
            SectionLabel(stringResource(Res.string.switches_section))
            StreetCard(Modifier.padding(bottom = 16.dp)) {
                BodyText(stringResource(Res.string.switches_intro), 14f)
                SkillProgressions.switchable(book.families.map { it.spec }).forEach { spec ->
                    val family = book.families.first { it.spec.id == spec.id }
                    SkillSwitchRow(model, family, Modifier.padding(top = 6.dp)) { onSwitches(spec.id) }
                }
            }
        }

        SectionLabel(stringResource(Res.string.equipment_section))
        StreetCard(Modifier.padding(bottom = 16.dp)) {
            BodyText(stringResource(Res.string.equipment_intro), 14f)
            Equipment.entries.forEach { EquipmentRow(model, it, Modifier.padding(top = 6.dp)) }
        }

        StreetCard(Modifier.padding(bottom = 16.dp)) {
            TitleText(stringResource(Res.string.reset_title), 19f)
            // With an account Android adds that the leaderboard and the cloud copy reset too; the iPhone has none yet.
            BodyText(stringResource(Res.string.reset_body), 14f, Modifier.padding(top = 7.dp))
            SecondaryButton(stringResource(Res.string.reset_button), Modifier.padding(top = 12.dp), onClick = onReset)
        }

        StreetCard(Modifier.padding(bottom = 16.dp), fill = palette.warningSurface, outlined = false) {
            TitleText(stringResource(Res.string.safety_title), 19f)
            BodyText(stringResource(Res.string.safety_body), 14f, Modifier.padding(top = 7.dp))
        }

        StreetCard {
            TitleText(stringResource(Res.string.about_title), 19f)
            BodyText(stringResource(Res.string.about_body), 14f, Modifier.padding(top = 7.dp))
            BodyText(
                stringResource(Res.string.version_format, model.version), 12f,
                Modifier.padding(top = 10.dp).clickable {
                    if (model.developerUnlocked) onDeveloper()
                    else when (val step = model.versionTaps.tap()) {
                        VersionTaps.Step.Quiet -> Unit
                        is VersionTaps.Step.Countdown -> scope.launch {
                            onNote(getPluralString(Res.plurals.developer_taps_left, step.remaining, step.remaining))
                        }
                        VersionTaps.Step.Open -> {
                            model.developerUnlocked = true
                            onDeveloper()
                        }
                    }
                },
            )
        }
    }
}

/**
 * The theme picker (Android: ThemeDecor.themePicker): a row per look with its swatch, its name in its own display face,
 * its summary and a radio; picking one redraws every screen in it at once.
 */
@Composable
fun ThemePicker(selected: StreetTheme, modifier: Modifier = Modifier, onPick: (StreetTheme) -> Unit) {
    val palette = Street.colors
    val chosen = stringResource(Res.string.theme_selected)
    StreetCard(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        StreetTheme.entries.forEachIndexed { index, option ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            val name = stringResource(option.label)
            val summary = stringResource(option.summary)
            val on = option == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp)
                    .selectable(on, role = Role.RadioButton) { if (!on) onPick(option) }
                    .clearAndSetSemantics {
                        contentDescription = if (on) "$name, $summary, $chosen" else "$name, $summary"
                        role = Role.RadioButton
                        this.selected = on
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeSwatch(option, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    BasicText(name, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 17.sp,
                        fontFamily = FontFamily(Font(option.fonts.display))))
                    BodyText(summary, 13f)
                }
                // Android's radio, tinted in ink on Concrete and in the theme's colour elsewhere.
                val tint = if (Street.theme == StreetTheme.CONCRETE) palette.text else palette.primary
                Canvas(Modifier.size(32.dp)) {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(tint, radius = 9.dp.toPx(), center = centre, style = Stroke(2.dp.toPx()))
                    if (on) drawCircle(tint, radius = 5.dp.toPx(), center = centre)
                }
            }
        }
    }
}

/** A thumbnail of [option]: its page, a card on it in its own corner style and its action colour (Android: ThemeSwatch). */
@Composable
private fun ThemeSwatch(option: StreetTheme, modifier: Modifier) {
    val divider = Street.colors.divider
    Canvas(modifier) {
        val colors = option.palette
        val w = size.width
        val h = size.height
        fun shape(box: Rect, radius: Float): Path = Path().apply {
            if (option.shape.corner == Corner.SQUARE) addRect(box) else {
                val cut = min(radius.dp.toPx() * 0.8f, min(box.width, box.height) / 2f)
                moveTo(box.left + cut, box.top)
                lineTo(box.right, box.top)
                lineTo(box.right, box.bottom - cut)
                lineTo(box.right - cut, box.bottom)
                lineTo(box.left, box.bottom)
                lineTo(box.left, box.top + cut)
                close()
            }
        }
        val page = shape(Rect(0f, 0f, w, h), 10f)
        drawPath(page, colors.background)
        drawPath(page, divider, style = Stroke(1.dp.toPx()))
        val card = shape(Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.62f), 6f)
        drawPath(card, colors.surface)
        if (option.shape.borderDp >= 2) drawPath(card, colors.border, style = Stroke(1.5.dp.toPx()))
        drawPath(shape(Rect(w * 0.18f, h * 0.7f, w * 0.82f, h * 0.84f), 4f), if (option == StreetTheme.WHEATPASTE) colors.spot else colors.action)
    }
}

/**
 * A skill's row (Android: skillSwitchesCard): its name, what is on, and a switch for the whole skill; a tap on the row
 * opens its steps.
 */
@Composable
private fun SkillSwitchRow(model: AppModel, family: SkillFamily, modifier: Modifier, onOpen: () -> Unit) {
    val palette = Street.colors
    val spec = family.spec
    val from = model.skillSwitches.offFrom[spec.id]
    val first = SkillProgressions.firstSwitchable(spec)
    val state = when {
        from == null -> stringResource(Res.string.switches_all_on)
        from <= first && first == 0 -> stringResource(Res.string.switches_off)
        from <= first -> stringResource(Res.string.switches_off_basics, family.skills[first - 1].name)
        else -> stringResource(Res.string.switches_up_to, family.skills[from - 1].name)
    }
    // On while any step past the basics is in the classes; off switches the whole skill off.
    val on = from == null || from > first
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onOpen).padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TitleText(spec.name, 16f)
            BodyText(state, 13f, color = if (from == null) palette.textSecondary else palette.action)
        }
        BodyText("›", 24f, Modifier.padding(start = 6.dp, end = 10.dp).clearAndSetSemantics { })
        SwitchToggle(on, spec.name) { model.skillSwitches = model.skillSwitches.with(spec.id, if (it) null else first) }
    }
}

/**
 * A piece of equipment (Android: equipmentCard): its name, what it serves and how many exercises need it, or "Switched
 * off: classes leave these out"; a tap anywhere on the row flips it.
 */
@Composable
private fun EquipmentRow(model: AppModel, equipment: Equipment, modifier: Modifier) {
    val palette = Street.colors
    val have = model.equipment.has(equipment)
    val count = model.movementEquipment.values.count { tags -> EquipmentNeeds.of(tags).any { equipment in it } }
    val name = stringResource(equipmentName(equipment))
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .toggleable(have, role = Role.Switch) { model.equipment = model.equipment.with(equipment, it) }
            .semantics { contentDescription = name }
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TitleText(name, 16f)
            BodyText(
                if (have) "${stringResource(equipmentHint(equipment))} · ${pluralStringResource(Res.plurals.equipment_count, count, count)}"
                else stringResource(Res.string.equipment_switched_off),
                13f, color = if (have) palette.textSecondary else palette.action,
            )
        }
        SwitchMark(have)
    }
}

/**
 * A skill's steps (Android: showFamilySwitches): its top rung playing, the intro, then every rung with the steps the
 * classes teach toward it and a switch. Switching a rung off switches off every harder one; switching one on brings back
 * every easier one. The basics show as always on.
 */
@Composable
fun FamilySwitchesSheet(model: AppModel, familyId: String, onClose: () -> Unit) {
    val book = model.skills ?: return
    val family = book.families.firstOrNull { it.spec.id == familyId } ?: return
    val spec = family.spec
    val first = SkillProgressions.firstSwitchable(spec)
    val steps = SkillProgressions.LINES[spec.id].orEmpty()
    val from = model.skillSwitches.offFrom[spec.id]
    val palette = Street.colors
    StreetDialog(title = null, onDismiss = onClose, confirm = DialogAction(stringResource(Res.string.switches_done), onClose)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp)) {
            val top = family.skills.last()
            ExerciseFigure(top.id, top.name, Modifier.fillMaxWidth().height(150.dp))
            TitleText(spec.name, 22f, Modifier.padding(top = 8.dp))
            BodyText(stringResource(Res.string.switches_family_intro), 14f, Modifier.padding(top = 4.dp))
            family.skills.forEachIndexed { rung, skill ->
                val toggled = rung >= first
                val on = from == null || rung < from
                Column(Modifier.fillMaxWidth().alpha(if (toggled && !on) 0.5f else 1f).padding(top = 10.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BodyText(stringResource(Res.string.switches_rung, rung + 1), 12f, color = palette.primary, bold = true)
                            TitleText(skill.name, 16f)
                        }
                        if (!toggled) BodyText(stringResource(Res.string.switches_always_on), 12f)
                        else SwitchToggle(on, skill.name) { now ->
                            val next = if (now) (rung + 1).takeIf { it < family.skills.size } else rung
                            model.skillSwitches = model.skillSwitches.with(spec.id, next)
                        }
                    }
                    val names = steps.filter { it.rung == rung && it.movementId != skill.id }.map { book.nameOf(it.movementId) ?: it.movementId }
                    if (names.isNotEmpty()) BodyText(stringResource(Res.string.switches_steps, names.joinToString(" · ")), 13f)
                }
            }
        }
    }
}

/** "Reset all progress?", Cancel or Reset (Android: confirmReset). */
@Composable
fun ResetDialog(onClose: () -> Unit, onReset: () -> Unit) {
    StreetDialog(
        title = stringResource(Res.string.reset_confirm_title),
        message = stringResource(Res.string.reset_confirm_message),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.reset_confirm), onReset),
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    )
}

/**
 * The developer settings (Android: DeveloperMenu): Inspect all days and its note, Hide developer settings (which also
 * switches inspecting off and locks them again) and Done. They live on this phone only.
 */
@Composable
fun DeveloperDialog(model: AppModel, onClose: () -> Unit) {
    StreetDialog(
        title = stringResource(Res.string.developer_title),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.developer_done), onClose),
        neutral = DialogAction(stringResource(Res.string.developer_hide)) {
            model.inspectAllDays = false
            model.developerUnlocked = false
            onClose()
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)) {
            SettingSwitch(stringResource(Res.string.developer_inspect_all), model.inspectAllDays) { model.inspectAllDays = it }
            BodyText(stringResource(Res.string.developer_inspect_note), 13f)
        }
    }
}

/** Android's build-number trick on the version line (DeveloperMenu.VersionTaps): the tenth tap opens the developer settings. */
class VersionTaps {
    private var taps = 0

    /** What one more tap shows: nothing at first, a countdown from the [COUNTDOWN_FROM]th, then the menu. */
    fun tap(): Step {
        taps = minOf(taps + 1, TAPS)
        val remaining = TAPS - taps
        return when {
            remaining == 0 -> Step.Open
            taps >= COUNTDOWN_FROM -> Step.Countdown(remaining)
            else -> Step.Quiet
        }
    }

    sealed interface Step {
        data object Quiet : Step
        data class Countdown(val remaining: Int) : Step
        data object Open : Step
    }

    companion object {
        const val TAPS = 10
        const val COUNTDOWN_FROM = 4
    }
}
