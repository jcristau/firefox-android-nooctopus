/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata.view

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.historymetadata.interactor.HistoryMetadataInteractor
import org.mozilla.fenix.home.HomeFragmentStore
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.view.ViewHolder

/**
 * View holder for a history metadata group item.
 *
 * @param composeView [ComposeView] which will be populated with Jetpack Compose UI content.
 * @param store [HomeFragmentStore] containing the list of history metadata groups to be displayed.
 * @property interactor [HistoryMetadataInteractor] which will have delegated to all user interactions.
 * @property metrics [MetricController] that handles telemetry events.
 */
class HistoryMetadataGroupViewHolder(
    val composeView: ComposeView,
    private val store: HomeFragmentStore,
    private val interactor: HistoryMetadataInteractor,
    private val metrics: MetricController
) : ViewHolder(composeView) {

    init {
        val horizontalPadding = composeView.resources.getDimensionPixelSize(R.dimen.home_item_horizontal_margin)
        composeView.setPadding(horizontalPadding, 0, horizontalPadding, 0)

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            val recentVisits = store.observeAsComposableState { state -> state.historyMetadata }

            FirefoxTheme {
                RecentlyVisited(
                    recentVisits = recentVisits.value ?: emptyList(),
                    menuItems = listOfNotNull(
                        RecentVisitMenuItem(
                            title = stringResource(R.string.recently_visited_menu_item_remove),
                            onClick = { group ->
                                interactor.onRemoveGroup(group.title)
                            }
                        )
                    ),
                    onRecentVisitClick = { historyMetadataGroup, pageNumber ->
                        metrics.track(Event.HistoryRecentSearchesTapped(pageNumber.toString()))
                        interactor.onHistoryMetadataGroupClicked(historyMetadataGroup)
                    }
                )
            }
        }
    }

    companion object {
        val LAYOUT_ID = View.generateViewId()
    }
}
