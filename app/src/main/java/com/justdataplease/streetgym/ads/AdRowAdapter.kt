package com.justdataplease.streetgym.ads

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

/**
 * [base]'s rows with one ad card ([ad], a [ManagedNativePlacement] in its row) after the first [after] rows, or at the
 * end of a shorter list. The ad row can't be tapped; [baseIndex] maps a list position back to [base]'s row.
 */
class AdRowAdapter(private val base: BaseAdapter, private val ad: View, private val after: Int) : BaseAdapter() {
    /** [base]'s row at [position], null for the ad. */
    fun baseIndex(position: Int): Int? = adRowBaseIndex(position, base.count, after)

    override fun getCount(): Int = base.count + 1
    override fun getItem(position: Int): Any? = baseIndex(position)?.let(base::getItem)
    override fun getItemId(position: Int): Long = baseIndex(position)?.let(base::getItemId) ?: -1L
    override fun getViewTypeCount(): Int = base.viewTypeCount + 1
    override fun getItemViewType(position: Int): Int = baseIndex(position)?.let(base::getItemViewType) ?: base.viewTypeCount
    override fun areAllItemsEnabled(): Boolean = false
    override fun isEnabled(position: Int): Boolean = baseIndex(position)?.let(base::isEnabled) ?: false
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        baseIndex(position)?.let { base.getView(it, convertView, parent) } ?: ad
}

/** In a list of [count] rows with the ad after the first [after]: the row at [position], null for the ad itself. */
fun adRowBaseIndex(position: Int, count: Int, after: Int): Int? {
    val ad = minOf(after, count)
    return when {
        position < ad -> position
        position == ad -> null
        else -> position - 1
    }
}
