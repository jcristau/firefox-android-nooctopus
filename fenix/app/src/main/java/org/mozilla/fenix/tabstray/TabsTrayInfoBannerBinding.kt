/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray

import android.content.Context
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifChanged
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.infobanner.InfoBanner
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.utils.Settings

class TabsTrayInfoBannerBinding(
    private val context: Context,
    private val store: BrowserStore,
    private val infoBannerView: ViewGroup,
    private val settings: Settings,
    private val navigationInteractor: NavigationInteractor,
    private val metrics: MetricController?
) : LifecycleAwareFeature {
    private var scope: CoroutineScope? = null

    @VisibleForTesting
    internal var banner: InfoBanner? = null

    @ExperimentalCoroutinesApi
    override fun start() {
        scope = store.flowScoped { flow ->
            flow.map { state -> max(state.normalTabs.size, state.privateTabs.size) }
                .ifChanged()
                .collect { tabCount ->
                    if (tabCount >= TAB_COUNT_SHOW_CFR) {
                        displayInfoBannerIfNeeded(settings)
                    }
                }
        }
    }

    override fun stop() {
        scope?.cancel()
    }

    private fun displayInfoBannerIfNeeded(settings: Settings) {
        banner = displayGridViewBannerIfNeeded(settings)
            ?: displayAutoCloseTabsBannerIfNeeded(settings)

        banner?.apply {
            infoBannerView.visibility = VISIBLE
            showBanner()
        }
    }

    private fun displayGridViewBannerIfNeeded(settings: Settings): InfoBanner? {
        return if (
            settings.shouldShowGridViewBanner &&
            settings.canShowCfr &&
            settings.listTabView
        ) {
            InfoBanner(
                context = context,
                message = context.getString(R.string.tab_tray_grid_view_banner_message),
                dismissText = context.getString(R.string.tab_tray_grid_view_banner_negative_button_text),
                actionText = context.getString(R.string.tab_tray_grid_view_banner_positive_button_text),
                container = infoBannerView,
                dismissByHiding = true,
                dismissAction = {
                    metrics?.track(Event.TabsTrayCfrDismissed)
                    settings.shouldShowGridViewBanner = false
                }
            ) {
                navigationInteractor.onTabSettingsClicked()
                settings.shouldShowGridViewBanner = false
            }
        } else {
            null
        }
    }

    private fun displayAutoCloseTabsBannerIfNeeded(settings: Settings): InfoBanner? {
        return if (
            settings.shouldShowAutoCloseTabsBanner &&
            settings.canShowCfr
        ) {
            InfoBanner(
                context = context,
                message = context.getString(R.string.tab_tray_close_tabs_banner_message),
                dismissText = context.getString(R.string.tab_tray_close_tabs_banner_negative_button_text),
                actionText = context.getString(R.string.tab_tray_close_tabs_banner_positive_button_text),
                container = infoBannerView,
                dismissByHiding = true,
                dismissAction = {
                    metrics?.track(Event.TabsTrayCfrDismissed)
                    settings.shouldShowAutoCloseTabsBanner = false
                }
            ) {
                navigationInteractor.onTabSettingsClicked()
                settings.shouldShowAutoCloseTabsBanner = false
            }
        } else {
            null
        }
    }

    companion object {
        @VisibleForTesting
        internal const val TAB_COUNT_SHOW_CFR = 6
    }
}
