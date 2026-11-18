package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.figure.WorkoutTile
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.delete
import com.justdataplease.streetgym.app.resources.ic_lock
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.library_create
import com.justdataplease.streetgym.app.resources.library_custom
import com.justdataplease.streetgym.app.resources.library_empty
import com.justdataplease.streetgym.app.resources.library_intro
import com.justdataplease.streetgym.app.resources.library_over_limit
import com.justdataplease.streetgym.app.resources.library_saved
import com.justdataplease.streetgym.app.resources.library_usage
import com.justdataplease.streetgym.app.resources.lists_create
import com.justdataplease.streetgym.app.resources.lists_default
import com.justdataplease.streetgym.app.resources.lists_delete
import com.justdataplease.streetgym.app.resources.lists_delete_message
import com.justdataplease.streetgym.app.resources.lists_delete_title
import com.justdataplease.streetgym.app.resources.lists_empty
import com.justdataplease.streetgym.app.resources.lists_name_blank
import com.justdataplease.streetgym.app.resources.lists_name_hint
import com.justdataplease.streetgym.app.resources.lists_name_long
import com.justdataplease.streetgym.app.resources.lists_name_taken
import com.justdataplease.streetgym.app.resources.lists_new
import com.justdataplease.streetgym.app.resources.lists_rename
import com.justdataplease.streetgym.app.resources.lists_rename_title
import com.justdataplease.streetgym.app.resources.rename_save
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.billing.SavedLimit
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.ReadyWorkouts
import com.justdataplease.streetgym.data.library.WorkoutLists
import com.justdataplease.streetgym.data.model.TrainingProgram
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The size of a workout card's tile (Android: HomeActivity.TILE_DP). */
private val CARD_TILE = 56.dp

/**
 * Training › My workouts (android-streetgym, HomeActivity.renderMyWorkouts): saved program days and own workouts, which
 * share the tier's limit (3, 30 or 300). Favorites holds them all; Plus and Pro also sort them into lists of their own,
 * picked by the chips ([openList], kept while the app runs); Free keeps its lists, hidden, until Plus brings them back.
 * Create a workout opens the builder ([onCreate]); on Free it wears the Plus sticker and opens the offer. A card opens
 * its day ([onOpen]).
 */
@Composable
fun ColumnScope.MyWorkouts(
    model: AppModel,
    openList: String?,
    onOpenList: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onLimit: (Limit) -> Unit,
    onPaywall: (Paywall) -> Unit,
) {
    val palette = Street.colors
    val tier = model.tier
    val state = model.libraryState()
    val lists = model.visibleLists()
    val open = lists.firstOrNull { it.id == openList }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    BodyText(stringResource(Res.string.library_intro))
    BodyText(stringResource(Res.string.library_usage, state.used, tier.savedDays), 13f, Modifier.padding(top = 10.dp), color = palette.primary)
    if (SavedLimit.overLimit(tier, state.used)) {
        BodyText(stringResource(Res.string.library_over_limit, tier.savedDays), 13f, Modifier.padding(top = 4.dp))
    }
    ListChips(
        labels = listOf(stringResource(Res.string.lists_default)) + lists.map { it.name },
        selected = open?.let { lists.indexOf(it) + 1 } ?: 0,
        locked = !tier.lists,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        onSelect = { index -> onOpenList(lists.getOrNull(index - 1)?.id) },
    ) {
        // A new list, opened at once; Free and a full tier see what gives more (Android: requestNewList).
        val limit = model.listLimit()
        if (limit == null) naming = true else onLimit(limit)
    }
    if (naming) ListNameDialog(stringResource(Res.string.lists_new), model, onClose = { naming = false }) { name ->
        naming = false
        onOpenList(model.createList(name).id)
    }

    if (open != null) {
        Row(Modifier.padding(top = 10.dp, bottom = 14.dp).fillMaxWidth()) {
            SecondaryButton(stringResource(Res.string.lists_rename), Modifier.weight(1f)) { renaming = true }
            Spacer(Modifier.width(8.dp))
            SecondaryButton(stringResource(Res.string.lists_delete), Modifier.weight(1f)) { deleting = true }
        }
        if (renaming) ListNameDialog(stringResource(Res.string.lists_rename_title, open.name), model, open.id, open.name,
            onClose = { renaming = false }) { name ->
            renaming = false
            model.renameList(open.id, name)
        }
        if (deleting) StreetDialog(
            title = stringResource(Res.string.lists_delete_title, open.name),
            message = stringResource(Res.string.lists_delete_message),
            onDismiss = { deleting = false },
            confirm = DialogAction(stringResource(Res.string.delete)) {
                deleting = false
                model.deleteList(open.id)
                onOpenList(null)
            },
            dismiss = DialogAction(stringResource(Res.string.cancel)) { deleting = false },
        )
        val days = state.daysIn(open).mapNotNull(model::programById)
        if (days.isEmpty()) BodyText(stringResource(Res.string.lists_empty), 14f)
        days.forEach { LibraryCard(model, it, Modifier.padding(bottom = 10.dp), onOpen, onPaywall) }
        return
    }

    // Free: Create wears the Plus sticker and opens the offer; days made before stay below.
    val create = stringResource(Res.string.library_create)
    if (tier.customDays) PrimaryButton(create, Modifier.padding(top = 14.dp, bottom = 18.dp), onClick = onCreate)
    else TierButton(create, stringResource(Res.string.tier_plus), Modifier.padding(top = 14.dp, bottom = 18.dp)) { onPaywall(Paywall.Custom) }
    SectionLabel(stringResource(Res.string.library_custom))
    if (state.customDays.isEmpty()) BodyText(stringResource(Res.string.library_empty), 14f, Modifier.padding(bottom = 12.dp))
    state.customDays.forEach { LibraryCard(model, CustomDays.toProgram(it), Modifier.padding(bottom = 10.dp), onOpen, onPaywall) }
    SectionLabel(stringResource(Res.string.library_saved), Modifier.padding(top = 8.dp))
    val saved = state.savedIds.mapNotNull(model::programById)
    if (saved.isEmpty()) BodyText(stringResource(Res.string.library_empty), 14f)
    saved.forEach { LibraryCard(model, it, Modifier.padding(bottom = 10.dp), onOpen, onPaywall) }
}

/**
 * A saved or own day in My workouts, or a ready workout: its hardest exercise playing on a tile ringed in how hard the day
 * is for the athlete, its name and what it holds; it opens the day (Android: libraryCard). Below Pro a locked ready
 * workout wears the Pro tag and opens the offer.
 */
@Composable
fun LibraryCard(model: AppModel, program: TrainingProgram, modifier: Modifier = Modifier, onOpen: (String) -> Unit, onPaywall: (Paywall) -> Unit) {
    val effort = model.effortOf(program.id)
    val ready = ReadyWorkouts.isReady(program.id)
    val locked = model.readyLocked(program.id)
    val name = displayName(program)
    val meta = listOfNotNull(
        when {
            CustomDays.isCustom(program.id) -> customMeta(program.exercises.size, program.estimatedMinutes ?: 1)
            ready -> readyMeta(program)
            else -> listOf(stringResource(Res.string.level_number, program.levelNumber), programMeta(program)).joinToString(" • ")
        },
        effort?.let { effortText(it) },
    ).joinToString(" · ")
    StreetCard(
        modifier.clearAndSetSemantics {
            contentDescription = "$name, $meta"
            role = Role.Button
        },
        onClick = { if (locked) onPaywall(Paywall.Ready) else onOpen(program.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WorkoutTile(model.tileOf(program.id), effort, CARD_TILE, Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                TitleText(name, 18f)
                if (locked) LockBadge(stringResource(Res.string.tier_pro), Modifier.padding(top = 4.dp))
                BodyText(meta, 13f, Modifier.padding(top = 4.dp))
                if (ready) program.description.resolve().takeIf(String::isNotBlank)?.let { BodyText(it, 13f, Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

/**
 * The lists above My workouts (Android: WorkoutListsUi.listChips): Favorites first, then the athlete's own, then
 * "+ New list" with a lock on Free. The row scrolls sideways once the lists don't fit, keeping the chosen one in view.
 */
@Composable
private fun ListChips(
    labels: List<String>,
    selected: Int,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
) {
    val scroll = rememberScrollState()
    Row(modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val bring = remember { BringIntoViewRequester() }
            if (index == selected && selected > 0) LaunchedEffect(selected) { bring.bringIntoView() }
            FilterChip(label, index == selected, Modifier.padding(end = 8.dp).bringIntoViewRequester(bring)) { if (index != selected) onSelect(index) }
        }
        val new = "+ " + stringResource(Res.string.lists_new)
        FilterChip(new, selected = false, outlined = true, lock = locked,
            description = if (locked) "${stringResource(Res.string.lists_new)}, ${stringResource(Res.string.tier_plus)}" else new,
            onClick = onNew)
    }
}

/**
 * A chip of a row of choices (Android: WorkoutListsUi.chip): the label face at 14 sp, at least 40 dp tall, on the info
 * colour; chosen, on the theme's colour or [chosenColor]; [outlined] on the surface with a hairline, for actions such as
 * New list. [lock] puts the tier's lock after the label.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    chosenColor: Color? = null,
    lock: Boolean = false,
    description: String = label,
    onClick: () -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val chosen = chosenColor ?: palette.primary
    val text = if (selected) palette.contentOn(chosen) else palette.text
    val face = when {
        selected -> Modifier.streetBackground(theme, chosen, 20.dp)
        outlined -> Modifier.streetBackground(theme, palette.surface, 20.dp, palette.divider, 1.dp)
        else -> Modifier.streetBackground(theme, palette.infoSurface, 20.dp)
    }
    Row(
        modifier.heightIn(min = 40.dp).then(face)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = TextStyle(color = text, fontSize = 14.sp, fontFamily = Street.type.label, textAlign = TextAlign.Center),
            maxLines = 1)
        if (lock) Image(painterResource(Res.drawable.ic_lock), contentDescription = null, colorFilter = ColorFilter.tint(palette.textSecondary),
            modifier = Modifier.padding(start = 6.dp).size(13.dp))
    }
}

/**
 * Names a new list, or renames [listId] (Android: WorkoutListsUi.listNameDialog): a field of up to 30 characters; the
 * dialog stays open, saying why, until the name is free, not blank and not too long.
 */
@Composable
fun ListNameDialog(
    title: String,
    model: AppModel,
    listId: String? = null,
    initial: String = "",
    onClose: () -> Unit,
    onName: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initial)) }
    var problem by remember { mutableStateOf<WorkoutLists.NameProblem?>(null) }
    val favorites = stringResource(Res.string.lists_default)
    val error = listNameError(problem, field.text, model, favorites)
    fun submit() {
        problem = model.listNameProblem(field.text, favorites, listId)
        if (problem == null) onName(WorkoutLists.clean(field.text))
    }
    StreetDialog(
        title = title,
        onDismiss = onClose,
        confirm = DialogAction(stringResource(if (listId == null) Res.string.lists_create else Res.string.rename_save), ::submit),
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        EntryField(field, stringResource(Res.string.lists_name_hint), Modifier.padding(horizontal = 20.dp), maxLength = WorkoutLists.MAX_NAME_LENGTH,
            capitalize = true, error = error, focusFirst = true, imeAction = ImeAction.Done, onAction = ::submit) {
            field = it
            problem = null
        }
    }
}

/** Why a list name can't be used, in Android's words; the taken name as the list already spells it. */
@Composable
fun listNameError(problem: WorkoutLists.NameProblem?, name: String, model: AppModel, favorites: String): String? = when (problem) {
    null -> null
    WorkoutLists.NameProblem.Blank -> stringResource(Res.string.lists_name_blank)
    WorkoutLists.NameProblem.TooLong -> stringResource(Res.string.lists_name_long, WorkoutLists.MAX_NAME_LENGTH)
    WorkoutLists.NameProblem.Taken -> {
        val cleaned = WorkoutLists.clean(name)
        stringResource(Res.string.lists_name_taken,
            (listOf(favorites) + model.libraryState().activeLists.map { it.name }).firstOrNull { it.equals(cleaned, ignoreCase = true) } ?: cleaned)
    }
}
