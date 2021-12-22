/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.recentbookmarks.view

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.compose.ComposeViewHolder
import org.mozilla.fenix.home.HomeFragmentStore
import org.mozilla.fenix.home.recentbookmarks.interactor.RecentBookmarksInteractor

class RecentBookmarksViewHolder(
    composeView: ComposeView,
    viewLifecycleOwner: LifecycleOwner,
    private val store: HomeFragmentStore,
    val interactor: RecentBookmarksInteractor,
    val metrics: MetricController
) : ComposeViewHolder(composeView, viewLifecycleOwner) {

    init {
        metrics.track(Event.RecentBookmarksShown)
    }

    companion object {
        val LAYOUT_ID = View.generateViewId()
    }

    @Composable
    override fun Content() {
        val recentBookmarks = store.observeAsComposableState { state -> state.recentBookmarks }

        RecentBookmarks(
            bookmarks = recentBookmarks.value ?: emptyList(),
            onRecentBookmarkClick = interactor::onRecentBookmarkClicked
        )
    }
}
