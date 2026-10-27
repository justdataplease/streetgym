package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ExamLog
import java.util.Locale

class ProgramListAdapter(
    private val context: Context,
    private val programs: List<TrainingProgram>,
    private val completedIds: Set<String>,
    private val assignedProgramId: String?,
    private val locale: Locale,
    private val exams: ExamLog = ExamLog(),
) : BaseAdapter() {
    override fun getCount(): Int = programs.size

    override fun getItem(position: Int): TrainingProgram = programs[position]

    override fun getItemId(position: Int): Long = position.toLong()

    // A level holds 100 days: the rows that scroll out are refilled for the ones scrolling in, not built again.
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView?.tag as? Row ?: Row()
        row.bind(getItem(position))
        return row.root
    }

    /** One day's row: its views are made once, and [bind] fills them in and shows only what the day needs. */
    private inner class Row {
        private val number = context.numberCircle("", "")
        private val title = context.oneLineText("", bold = true)
        private val meta = context.bodyText("", 13f).apply { maxLines = 2 }
        private val exam = context.bodyText("", 13f).apply { setTextColor(AppColors.ACTION) }
        private val done = TextView(context).apply {
            text = "✓"
            setTextColor(AppColors.SUCCESS)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val next = context.badge(context.getString(R.string.assigned_next), AppColors.ACTION)
        private val later = context.badge(context.getString(R.string.locked_later), AppColors.TEXT_SECONDARY)
        private val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(14))
            background = roundedDrawable(AppColors.SURFACE, context.dp(24).toFloat())
            elevation = context.dp(1).toFloat()
            isFocusable = false
            addView(number, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(14), 0, context.dp(8), 0)
                addView(title)
                addView(meta)
                addView(exam)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(done, LinearLayout.LayoutParams(context.dp(36), context.dp(44)))
            addView(next)
            addView(later)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(4))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            applyThemeType()
            tag = this@Row
        }

        fun bind(program: TrainingProgram) {
            val name = context.displayName(program, locale)
            val completed = program.id in completedIds
            val assigned = program.id == assignedProgramId
            val unlocked = completed || assigned
            val programMeta = context.programMeta(program)
            number.text = program.number.toString()
            number.contentDescription = context.getString(R.string.workout_number, program.number)
            title.text = name
            meta.text = programMeta.ifBlank {
                program.description.resolve(locale).ifBlank { context.getString(R.string.empty_description) }
            }
            meta.visibility = if (unlocked) View.VISIBLE else View.GONE
            // An exam day says so, with how its latest attempt went.
            exam.visibility = if (program.isExam) View.VISIBLE else View.GONE
            if (program.isExam) exam.text = when (exams.latest(program.id)?.passed) {
                true -> context.getString(R.string.exam_list_passed)
                false -> context.getString(R.string.exam_list_not_yet)
                null -> context.getString(R.string.exam_badge)
            }
            done.visibility = if (completed) View.VISIBLE else View.GONE
            next.visibility = if (!completed && assigned) View.VISIBLE else View.GONE
            later.visibility = if (!unlocked) View.VISIBLE else View.GONE
            row.contentDescription = buildString {
                append(name)
                if (unlocked && programMeta.isNotBlank()) append(", ").append(programMeta)
                append(", ").append(context.getString(when {
                    completed -> R.string.completed
                    assigned -> R.string.assigned_next
                    else -> R.string.locked_later
                }))
            }
        }
    }
}
