/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray.browser

import mozilla.components.browser.state.state.TabSessionState
import org.mozilla.fenix.tabstray.TabsTrayInteractor
import org.mozilla.fenix.tabstray.TrayPagerAdapter

/**
 * Interactor for all things related to inactive tabs in the tabs tray.
 */
interface InactiveTabsInteractor {
    /**
     * Invoked when the inactive tabs header is clicked.
     *
     * @param expanded true when the tap should expand the inactive section.
     */
    fun onInactiveTabsHeaderClicked(expanded: Boolean)

    /**
     * Invoked when an inactive tab is clicked.
     *
     * @param tab [TabSessionState] that was clicked.
     */
    fun onInactiveTabClicked(tab: TabSessionState)

    /**
     * Invoked when an inactive tab is closed.
     *
     * @param tab [TabSessionState] that was closed.
     */
    fun onInactiveTabClosed(tab: TabSessionState)

    /**
     * Invoked when the user clicks on the delete all inactive tabs button.
     */
    fun onDeleteAllInactiveTabsClicked()

    /**
     * Invoked when the user clicks the close button in the auto close dialog.
     */
    fun onAutoCloseDialogCloseButtonClicked()

    /**
     * Invoked when the user clicks to enable the inactive tab auto-close feature.
     */
    fun onEnableAutoCloseClicked()
}

/**
 * Interactor to be called for any user interactions with the Inactive Tabs feature.
 *
 * @param controller An instance of [InactiveTabsController] which will be delegated for all
 * user interactions.
 * @param tabsTrayInteractor [TabsTrayInteractor] used to respond to interactions with specific
 * inactive tabs.
 */
class DefaultInactiveTabsInteractor(
    private val controller: InactiveTabsController,
    private val tabsTrayInteractor: TabsTrayInteractor,
) : InactiveTabsInteractor {

    /**
     * See [InactiveTabsInteractor.onInactiveTabsHeaderClicked].
     */
    override fun onInactiveTabsHeaderClicked(expanded: Boolean) {
        controller.handleInactiveTabsHeaderClicked(expanded)
    }

    /**
     * See [InactiveTabsInteractor.onAutoCloseDialogCloseButtonClicked].
     */
    override fun onAutoCloseDialogCloseButtonClicked() {
        controller.handleInactiveTabsAutoCloseDialogDismiss()
    }

    /**
     * See [InactiveTabsInteractor.onEnableAutoCloseClicked].
     */
    override fun onEnableAutoCloseClicked() {
        controller.handleEnableInactiveTabsAutoCloseClicked()
    }

    /**
     * See [InactiveTabsInteractor.onInactiveTabClicked].
     */
    override fun onInactiveTabClicked(tab: TabSessionState) {
        controller.handleInactiveTabClicked(tab)
        tabsTrayInteractor.onTabSelected(tab, TrayPagerAdapter.INACTIVE_TABS_FEATURE_NAME)
    }

    /**
     * See [InactiveTabsInteractor.onInactiveTabClosed].
     */
    override fun onInactiveTabClosed(tab: TabSessionState) {
        controller.handleCloseInactiveTabClicked(tab)
        tabsTrayInteractor.onTabClosed(tab, TrayPagerAdapter.INACTIVE_TABS_FEATURE_NAME)
    }

    /**
     * See [InactiveTabsInteractor.onDeleteAllInactiveTabsClicked].
     */
    override fun onDeleteAllInactiveTabsClicked() {
        controller.handleDeleteAllInactiveTabsClicked()
    }
}
