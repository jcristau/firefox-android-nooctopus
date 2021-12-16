/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.library.history

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import org.mozilla.fenix.R
import org.mozilla.fenix.selection.SelectionHolder
import org.mozilla.fenix.library.history.viewholders.HistoryListItemViewHolder
import java.util.Calendar
import java.util.Date

enum class HistoryItemTimeGroup {
    Today, Yesterday, ThisWeek, ThisMonth, Older;

    fun humanReadable(context: Context): String = when (this) {
        Today -> context.getString(R.string.history_today)
        Yesterday -> context.getString(R.string.history_yesterday)
        ThisWeek -> context.getString(R.string.history_7_days)
        ThisMonth -> context.getString(R.string.history_30_days)
        Older -> context.getString(R.string.history_older)
    }
}

class HistoryAdapter(
    private val historyInteractor: HistoryInteractor,
) : PagedListAdapter<History, HistoryListItemViewHolder>(historyDiffCallback),
    SelectionHolder<History> {

    private var mode: HistoryFragmentState.Mode = HistoryFragmentState.Mode.Normal
    override val selectedItems get() = mode.selectedItems
    var pendingDeletionIds = emptySet<Long>()
    private val itemsWithHeaders: MutableMap<HistoryItemTimeGroup, Int> = mutableMapOf()

    override fun getItemViewType(position: Int): Int = HistoryListItemViewHolder.LAYOUT_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryListItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)

        return HistoryListItemViewHolder(view, historyInteractor, this)
    }

    fun updateMode(mode: HistoryFragmentState.Mode) {
        this.mode = mode
        // Update the delete button alpha that the first item holds
        if (itemCount > 0) notifyItemChanged(0)
    }

    override fun onBindViewHolder(holder: HistoryListItemViewHolder, position: Int) {
        val current = getItem(position) ?: return
        val headerForCurrentItem = timeGroupForHistoryItem(current)
        val isPendingDeletion = pendingDeletionIds.contains(current.visitedAt)
        var timeGroup: HistoryItemTimeGroup? = null

        // Add or remove the header and position to the map depending on it's deletion status
        if (itemsWithHeaders.containsKey(headerForCurrentItem)) {
            if (isPendingDeletion && itemsWithHeaders[headerForCurrentItem] == position) {
                itemsWithHeaders.remove(headerForCurrentItem)
            } else if (isPendingDeletion && itemsWithHeaders[headerForCurrentItem] != position) {
                // do nothing
            } else {
                if (position <= itemsWithHeaders[headerForCurrentItem] as Int) {
                    itemsWithHeaders[headerForCurrentItem] = position
                    timeGroup = headerForCurrentItem
                }
            }
        } else if (!isPendingDeletion) {
            itemsWithHeaders[headerForCurrentItem] = position
            timeGroup = headerForCurrentItem
        }

        holder.bind(current, timeGroup, position == 0, mode, isPendingDeletion)
    }

    fun updatePendingDeletionIds(pendingDeletionIds: Set<Long>) {
        this.pendingDeletionIds = pendingDeletionIds
    }

    companion object {
        private const val zeroDays = 0
        private const val oneDay = 1
        private const val sevenDays = 7
        private const val thirtyDays = 30
        private val today = getDaysAgo(zeroDays).time
        private val yesterday = getDaysAgo(oneDay).time
        private val sevenDaysAgo = getDaysAgo(sevenDays).time
        private val thirtyDaysAgo = getDaysAgo(thirtyDays).time
        private val todayRange = LongRange(today, Long.MAX_VALUE) // all future time is considered today
        private val yesterdayRange = LongRange(yesterday, today)
        private val lastWeekRange = LongRange(sevenDaysAgo, yesterday)
        private val lastMonthRange = LongRange(thirtyDaysAgo, sevenDaysAgo)

        private fun getDaysAgo(daysAgo: Int): Date {
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }.time
        }

        @VisibleForTesting
        internal fun timeGroupForHistoryItem(item: History): HistoryItemTimeGroup {
            return when {
                todayRange.contains(item.visitedAt) -> HistoryItemTimeGroup.Today
                yesterdayRange.contains(item.visitedAt) -> HistoryItemTimeGroup.Yesterday
                lastWeekRange.contains(item.visitedAt) -> HistoryItemTimeGroup.ThisWeek
                lastMonthRange.contains(item.visitedAt) -> HistoryItemTimeGroup.ThisMonth
                else -> HistoryItemTimeGroup.Older
            }
        }

        private val historyDiffCallback = object : DiffUtil.ItemCallback<History>() {
            override fun areItemsTheSame(oldItem: History, newItem: History): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: History, newItem: History): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: History, newItem: History): Any? {
                return newItem
            }
        }
    }
}
