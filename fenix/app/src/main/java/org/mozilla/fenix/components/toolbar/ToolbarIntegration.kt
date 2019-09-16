/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import androidx.navigation.fragment.FragmentNavigator
import mozilla.components.browser.domains.autocomplete.DomainAutocompleteProvider
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.session.runWithSession
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.toolbar.ToolbarAutocompleteFeature
import mozilla.components.feature.toolbar.ToolbarFeature
import mozilla.components.feature.toolbar.ToolbarPresenter
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.theme.ThemeManager

class ToolbarIntegration(
    context: Context,
    toolbar: BrowserToolbar,
    browserLayout: ViewGroup,
    toolbarMenu: ToolbarMenu,
    domainAutocompleteProvider: DomainAutocompleteProvider,
    historyStorage: HistoryStorage,
    sessionManager: SessionManager,
    sessionId: String? = null,
    isPrivate: Boolean
) : LifecycleAwareFeature {

    private var renderStyle: ToolbarFeature.RenderStyle = ToolbarFeature.RenderStyle.UncoloredUrl

    init {
        toolbar.setMenuBuilder(toolbarMenu.menuBuilder)
        toolbar.private = isPrivate

        run {
            sessionManager.runWithSession(sessionId) {
                it.isCustomTabSession()
            }.also { isCustomTab ->
                if (isCustomTab) {
                    renderStyle = ToolbarFeature.RenderStyle.RegistrableDomain
                    return@run
                }

                val task = LottieCompositionFactory
                    .fromRawRes(
                        context,
                        ThemeManager.resolveAttribute(R.attr.shieldLottieFile, context)
                    )
                task.addListener { result ->
                    val lottieDrawable = LottieDrawable()
                    lottieDrawable.composition = result
                    toolbar.displayTrackingProtectionIcon =
                        context.settings.shouldUseTrackingProtection && FeatureFlags.etpCategories
                    toolbar.displaySeparatorView =
                        context.settings.shouldUseTrackingProtection && FeatureFlags.etpCategories

                    toolbar.setTrackingProtectionIcons(
                        iconOnNoTrackersBlocked = AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_tracking_protection_enabled
                        )!!,
                        iconOnTrackersBlocked = lottieDrawable,
                        iconDisabledForSite = AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_tracking_protection_disabled
                        )!!
                    )
                }

                val tabsAction = TabCounterToolbarButton(
                    sessionManager,
                    {
                        toolbar.hideKeyboard()
                        // We need to dynamically add the options here because if you do it in XML it overwrites
                        val options = NavOptions.Builder().setPopUpTo(R.id.nav_graph, false)
                            .setEnterAnim(R.anim.fade_in).build()
                        val extras =
                            FragmentNavigator.Extras.Builder()
                                .addSharedElement(
                                    browserLayout,
                                    "$TAB_ITEM_TRANSITION_NAME${sessionManager.selectedSession?.id}"
                                )
                                .build()
                        val navController = Navigation.findNavController(toolbar)
                        if (!navController.popBackStack(
                                R.id.homeFragment,
                                false
                            )
                        ) {
                            navController.nav(
                                R.id.browserFragment,
                                R.id.action_browserFragment_to_homeFragment,
                                null,
                                options,
                                extras
                            )
                        }
                    },
                    isPrivate
                )
                toolbar.addBrowserAction(tabsAction)
            }
        }

        ToolbarAutocompleteFeature(toolbar).apply {
            addDomainProvider(domainAutocompleteProvider)
            if (context.settings.shouldShowHistorySuggestions) {
                addHistoryStorageProvider(historyStorage)
            }
        }
    }

    private val toolbarPresenter: ToolbarPresenter = ToolbarPresenter(
        toolbar,
        context.components.core.store,
        sessionId,
        ToolbarFeature.UrlRenderConfiguration(
            PublicSuffixList(context),
            ThemeManager.resolveAttribute(R.attr.primaryText, context), renderStyle = renderStyle
        )
    )
    private var menuPresenter =
        MenuPresenter(toolbar, context.components.core.sessionManager, sessionId)

    override fun start() {
        menuPresenter.start()
        toolbarPresenter.start()
    }

    override fun stop() {
        menuPresenter.stop()
        toolbarPresenter.stop()
    }

    companion object {
        private const val TAB_ITEM_TRANSITION_NAME = "tab_item"
    }
}
