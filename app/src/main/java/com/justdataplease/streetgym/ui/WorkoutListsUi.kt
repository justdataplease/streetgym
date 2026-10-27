package com.justdataplease.streetgym.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.SavedLimit
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.LibraryRepository
import com.justdataplease.streetgym.data.library.LibraryState
import com.justdataplease.streetgym.data.library.WorkoutLists

/**
 * The lists above My workouts: Favorites first, then the athlete's own, then New list (locked on Free). The row
 * scrolls sideways once the lists don't fit.
 */
fun Context.listChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    locked: Boolean,
    onNew: () -> Unit,
): HorizontalScrollView = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    clipToPadding = false
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    labels.forEachIndexed { index, label ->
        row.addView(chip(label, index == selected) { if (index != selected) onSelect(index) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
    }
    row.addView(chip("+ " + getString(R.string.lists_new), selected = false, dashed = true, onClick = onNew).apply {
        if (locked) {
            val icon = getDrawable(R.drawable.ic_lock)?.mutate()?.apply { setTint(AppColors.TEXT_SECONDARY); setBounds(0, 0, dp(13), dp(13)) }
            setCompoundDrawablesRelative(null, null, icon, null)
            compoundDrawablePadding = dp(6)
            contentDescription = getString(R.string.lists_new) + ", " + getString(R.string.tier_plus)
        }
    })
    addView(row)
    // The chosen list stays in view when the row is wider than the screen.
    if (selected > 0) post { row.getChildAt(selected)?.let { smoothScrollTo((it.left - dp(24)).coerceAtLeast(0), 0) } }
}

private fun Context.chip(label: String, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
    text = label
    textSize = 14f
    typeface = AppType.label
    gravity = Gravity.CENTER
    minHeight = dp(40)
    maxLines = 1
    setPadding(dp(16), dp(8), dp(16), dp(8))
    setTextColor(if (selected) AppColors.contentOn(AppColors.PRIMARY) else AppColors.TEXT)
    background = when {
        selected -> roundedDrawable(AppColors.PRIMARY, dp(20).toFloat())
        dashed -> roundedDrawable(AppColors.SURFACE, dp(20).toFloat(), AppColors.DIVIDER, dp(1))
        else -> roundedDrawable(AppColors.INFO_SURFACE, dp(20).toFloat())
    }
    isClickable = true
    isFocusable = true
    isSelected = selected
    contentDescription = label
    setOnClickListener { onClick() }
}

private fun Context.nameField(initial: String) = EditText(this).apply {
    hint = getString(R.string.lists_name_hint)
    setText(initial)
    setSelectAllOnFocus(initial.isNotEmpty())
    isSingleLine = true
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    filters = arrayOf(InputFilter.LengthFilter(WorkoutLists.MAX_NAME_LENGTH))
    setTextColor(AppColors.TEXT)
    setHintTextColor(AppColors.TEXT_SECONDARY)
}

/** Shows why [name] can't be a list name on [field]; true when it can. */
private fun Context.checkListName(field: EditText, state: LibraryState, except: String? = null): Boolean {
    val name = WorkoutLists.clean(field.text.toString())
    val favorites = getString(R.string.lists_default)
    val problem = WorkoutLists.nameProblem(name, state, favorites, except)
    field.error = when (problem) {
        null -> null
        WorkoutLists.NameProblem.Blank -> getString(R.string.lists_name_blank)
        WorkoutLists.NameProblem.TooLong -> getString(R.string.lists_name_long, WorkoutLists.MAX_NAME_LENGTH)
        // The name as the list already spells it.
        WorkoutLists.NameProblem.Taken -> getString(R.string.lists_name_taken,
            (listOf(favorites) + state.activeLists.map { it.name }).firstOrNull { it.equals(name, ignoreCase = true) } ?: name)
    }
    return problem == null
}

/** Names a new list or renames one ([listId]); the dialog stays open until the name works. */
fun Activity.listNameDialog(title: String, library: LibraryRepository, listId: String? = null, initial: String = "", onName: (String) -> Unit) {
    val field = nameField(initial)
    val dialog = AlertDialog.Builder(this).setTitle(title)
        .setView(LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(if (listId == null) R.string.lists_create else R.string.rename_save, null)
        .create()
    fun submit() {
        if (!checkListName(field, library.state(), listId)) return
        dialog.dismiss()
        onName(WorkoutLists.clean(field.text.toString()))
    }
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() } }
    field.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_DONE).also { if (it) submit() } }
    field.imeOptions = EditorInfo.IME_ACTION_DONE
    dialog.show()
}

/**
 * Plus and Pro: where a day goes when its heart is tapped. Favorites is always ticked (every saved day is in it);
 * the athlete's lists can be ticked, and a new one made, on top. A saved program day can also be removed here.
 * [onMoreLists] runs when the tier has made all of its lists; [onDone] gets whether the day is still saved.
 */
fun Activity.showListPicker(library: LibraryRepository, dayId: String, tier: Tier, onMoreLists: () -> Unit, onDone: (Boolean) -> Unit) {
    val state = library.state()
    val custom = CustomDays.isCustom(dayId)
    val saved = custom || state.saved[dayId]?.saved == true
    val checked = state.listIdsWith(dayId).toMutableSet()
    val boxes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun box(label: String, ticked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Boolean = { true }) = CheckBox(this).apply {
        text = label
        textSize = 16f
        isChecked = ticked
        isEnabled = enabled
        alpha = if (enabled) 1f else .7f
        setTextColor(AppColors.TEXT)
        buttonTintList = ColorStateList.valueOf(AppColors.PRIMARY)
        minHeight = dp(48)
        setOnCheckedChangeListener { view, value -> if (!onChange(value)) view.isChecked = !value }
    }
    fun listLabel(name: String, days: Int) = "$name · " + resources.getQuantityString(R.plurals.lists_days, days, days)
    boxes.addView(box(getString(R.string.lists_default), ticked = true, enabled = false))
    state.activeLists.forEach { list ->
        val days = state.daysIn(list).size
        boxes.addView(box(listLabel(list.name, days), list.id in checked) { ticked ->
            when {
                !ticked -> checked -= list.id
                dayId in list.dayIds || SavedLimit.canAddToList(tier, days) -> checked += list.id
                else -> {
                    Toast.makeText(this, if (tier == Tier.PRO) getString(R.string.lists_full, list.name, tier.daysPerList)
                        else getString(R.string.lists_full_plus, list.name, tier.daysPerList, Tier.PRO.daysPerList), Toast.LENGTH_LONG).show()
                    return@box false
                }
            }
            true
        })
    }
    val field = nameField("")
    val add = secondaryButton(getString(R.string.lists_add)) {}
    fun addList() {
        val now = library.state()
        if (!SavedLimit.canAddList(tier, now.activeLists.size)) return onMoreLists()
        if (!checkListName(field, now)) return
        val list = library.createList(field.text.toString())
        checked += list.id
        boxes.addView(box(listLabel(list.name, 0), ticked = true) { if (it) checked += list.id else checked -= list.id; true })
        field.setText("")
        field.error = null
    }
    add.setOnClickListener { addList() }
    field.imeOptions = EditorInfo.IME_ACTION_DONE
    field.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_DONE).also { if (it) addList() } }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), dp(4))
        addView(bodyText(getString(if (custom) R.string.lists_picker_custom else R.string.lists_picker_favorites), 13f).apply {
            setPadding(dp(4), 0, 0, 0)
        })
        addWithMargins(boxes, topDp = 6)
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        }, topDp = 6)
    }
    var dialog: AlertDialog? = null
    if (saved && !custom) content.addWithMargins(secondaryButton(getString(R.string.lists_picker_remove)) {
        library.setSaved(dayId, false)
        Toast.makeText(this, R.string.day_removed, Toast.LENGTH_SHORT).show()
        dialog?.dismiss()
        onDone(false)
    }, topDp = 14)
    dialog = AlertDialog.Builder(this).setTitle(R.string.lists_picker_title)
        .setView(ScrollView(this).apply { addView(content) })
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.rename_save) { _, _ ->
            val after = library.state()
            val chosen = checked.filterTo(mutableSetOf()) { after.lists[it]?.deleted == false }
            library.saveToLists(dayId, chosen)
            val names = after.activeLists.filter { it.id in chosen }.map { it.name }
            Toast.makeText(this, when {
                saved -> getString(R.string.day_lists_saved)
                names.isEmpty() -> getString(R.string.day_saved)
                else -> getString(R.string.day_saved_lists, names.joinToString(", "))
            }, Toast.LENGTH_SHORT).show()
            onDone(true)
        }
        .show()
}

/** Rename and Delete for the list on screen, side by side. */
fun Context.listActions(onRename: () -> Unit, onDelete: () -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    fun button(label: Int, action: () -> Unit): Button = secondaryButton(getString(label), action)
    addView(button(R.string.lists_rename, onRename), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
    addView(button(R.string.lists_delete, onDelete), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
}
