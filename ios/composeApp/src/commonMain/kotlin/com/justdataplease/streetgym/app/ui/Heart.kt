package com.justdataplease.streetgym.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.figure.LocalReduceMotion
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.day_lists_saved
import com.justdataplease.streetgym.app.resources.day_removed
import com.justdataplease.streetgym.app.resources.day_saved
import com.justdataplease.streetgym.app.resources.day_saved_lists
import com.justdataplease.streetgym.app.resources.ic_heart
import com.justdataplease.streetgym.app.resources.ic_heart_outline
import com.justdataplease.streetgym.app.resources.lists_add
import com.justdataplease.streetgym.app.resources.lists_days
import com.justdataplease.streetgym.app.resources.lists_default
import com.justdataplease.streetgym.app.resources.lists_full
import com.justdataplease.streetgym.app.resources.lists_full_plus
import com.justdataplease.streetgym.app.resources.lists_name_hint
import com.justdataplease.streetgym.app.resources.lists_picker_custom
import com.justdataplease.streetgym.app.resources.lists_picker_favorites
import com.justdataplease.streetgym.app.resources.lists_picker_remove
import com.justdataplease.streetgym.app.resources.lists_picker_title
import com.justdataplease.streetgym.app.resources.rename_save
import com.justdataplease.streetgym.app.resources.save_day
import com.justdataplease.streetgym.app.resources.saved_day
import com.justdataplease.streetgym.app.resources.saved_day_lists
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.billing.SavedLimit
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.WorkoutLists
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where the lists' names go in "Saved to Favorites and …", filled once the athlete saves. */
private const val NAMES = "\u0001"

/** Android's OvershootInterpolator: past the end by [tension], then back. */
private class Overshoot(private val tension: Float) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction - 1f
        return t * t * ((tension + 1f) * t + tension) + 1f
    }
}

/**
 * The heart at the top right of a day's page (android-streetgym, HomeActivity.heartButton): an outline, or filled in the
 * theme's heart colour with a pop once saved (still with Reduce Motion). On Free a tap saves the day to Favorites, or
 * takes it out of My workouts; Plus and Pro pick its lists. An own workout is always saved, so its heart only picks
 * lists. A full limit shows what raises it ([onLimit]); [onNote] says what happened.
 */
@Composable
fun HeartButton(model: AppModel, programId: String, modifier: Modifier = Modifier, onNote: (String) -> Unit, onLimit: (Limit) -> Unit) {
    val palette = Street.colors
    val saved = model.isSaved(programId)
    val lists = model.tier.lists
    val reduced = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val savedNote = stringResource(Res.string.day_saved)
    val removedNote = stringResource(Res.string.day_removed)
    fun pop() {
        if (reduced) return
        scope.launch {
            scale.snapTo(0.6f)
            scale.animateTo(1f, tween(260, easing = Overshoot(3f)))
        }
    }
    val description = stringResource(when {
        !saved -> Res.string.save_day
        lists -> Res.string.saved_day_lists
        else -> Res.string.saved_day
    })
    Image(
        painterResource(if (saved) Res.drawable.ic_heart else Res.drawable.ic_heart_outline),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (saved) palette.heart else palette.textSecondary),
        modifier = modifier
            // Android's 48 dp heart sits 8 dp up and 10 dp out into the card's padding.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width - 10.dp.roundToPx(), placeable.height - 8.dp.roundToPx()) { placeable.place(0, -8.dp.roundToPx()) }
            }
            .size(48.dp)
            .clickable(role = Role.Button) {
                val limit = if (saved) null else model.savedLimit()
                when {
                    limit != null -> onLimit(limit)
                    lists -> picking = true
                    else -> {
                        model.setSaved(programId, !saved)
                        if (!saved) pop()
                        onNote(if (saved) removedNote else savedNote)
                    }
                }
            }
            .semantics { contentDescription = description }
            .padding(10.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
    if (picking) ListPickerDialog(model, programId, onNote = onNote, onLimit = onLimit, onClose = { picking = false }) { now ->
        picking = false
        if (now && !saved) pop()
    }
}

/**
 * Plus and Pro: where a day goes when its heart is tapped (Android: WorkoutListsUi.showListPicker). Favorites is always
 * ticked (every saved day is in it); the athlete's lists can be ticked, each with its days, a full one refused with a
 * note, and a new one made with Add. A saved program day can also be taken out of My workouts. [onDone] gets whether
 * the day is still saved.
 */
@Composable
internal fun ListPickerDialog(
    model: AppModel,
    dayId: String,
    onNote: (String) -> Unit,
    onLimit: (Limit) -> Unit,
    onClose: () -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val tier = model.tier
    val custom = CustomDays.isCustom(dayId)
    val saved = remember { model.isSaved(dayId) }
    var checked by remember { mutableStateOf(model.libraryState().listIdsWith(dayId)) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var problem by remember { mutableStateOf<WorkoutLists.NameProblem?>(null) }
    val favorites = stringResource(Res.string.lists_default)
    // The notes, in words now: the dialog is gone by the time a note shows. The lists' names go in place of NAMES.
    val listsSaved = stringResource(Res.string.day_lists_saved)
    val dayNoteSaved = stringResource(Res.string.day_saved)
    val savedInLists = stringResource(Res.string.day_saved_lists, NAMES)
    val state = model.libraryState()
    fun addList() {
        model.listLimit()?.let { return onLimit(it) }
        problem = model.listNameProblem(field.text, favorites)
        if (problem != null) return
        val list = model.createList(field.text)
        checked = checked + list.id
        field = TextFieldValue("")
    }
    StreetDialog(
        title = stringResource(Res.string.lists_picker_title),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.rename_save)) {
            val after = model.libraryState()
            val chosen = checked.filterTo(HashSet()) { after.lists[it]?.deleted == false }
            model.saveToLists(dayId, chosen)
            val names = after.activeLists.filter { it.id in chosen }.map { it.name }
            onNote(when {
                saved -> listsSaved
                names.isEmpty() -> dayNoteSaved
                else -> savedInLists.replace(NAMES, names.joinToString(", "))
            })
            onDone(true)
        },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        BodyText(stringResource(if (custom) Res.string.lists_picker_custom else Res.string.lists_picker_favorites), 13f,
            Modifier.padding(start = 24.dp, end = 20.dp, top = 4.dp))
        Column(Modifier.padding(top = 6.dp)) {
            // Android's list picker: CheckBoxes at 16 sp in the system face (WorkoutListsUi.showListPicker).
            CheckRow(favorites, checked = true, enabled = false, size = LIST_ROW) {}
            state.activeLists.forEach { list ->
                val days = state.daysIn(list).size
                val full = if (tier == Tier.PRO) stringResource(Res.string.lists_full, list.name, tier.daysPerList)
                    else stringResource(Res.string.lists_full_plus, list.name, tier.daysPerList, Tier.PRO.daysPerList)
                CheckRow("${list.name} · ${pluralStringResource(Res.plurals.lists_days, days, days)}", list.id in checked, size = LIST_ROW) { ticked ->
                    when {
                        !ticked -> checked = checked - list.id
                        dayId in list.dayIds || SavedLimit.canAddToList(tier, days) -> checked = checked + list.id
                        else -> onNote(full)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            EntryField(field, stringResource(Res.string.lists_name_hint), Modifier.weight(1f), maxLength = WorkoutLists.MAX_NAME_LENGTH,
                capitalize = true, error = listNameError(problem, field.text, model, favorites), imeAction = ImeAction.Done, onAction = ::addList,
                family = Street.type.system) {
                field = it
                problem = null
            }
            SecondaryButton(stringResource(Res.string.lists_add), Modifier.padding(start = 8.dp), side = true, onClick = ::addList)
        }
        if (saved && !custom) {
            val removed = stringResource(Res.string.day_removed)
            SecondaryButton(stringResource(Res.string.lists_picker_remove), Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
                model.setSaved(dayId, false)
                onNote(removed)
                onDone(false)
            }
        }
    }
}

/** The list picker's rows, as Android's CheckBoxes there: 16 sp. */
private const val LIST_ROW = 16f
