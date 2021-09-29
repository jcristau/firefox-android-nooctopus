/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.components.StoreProvider
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.databinding.ComponentTabstray2Binding
import org.mozilla.fenix.databinding.ComponentTabstrayFabBinding
import org.mozilla.fenix.databinding.FragmentTabTrayDialogBinding
import org.mozilla.fenix.databinding.TabsTrayTabCounter2Binding
import org.mozilla.fenix.databinding.TabstrayMultiselectItemsBinding
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.home.HomeScreenViewModel
import org.mozilla.fenix.share.ShareFragment
import org.mozilla.fenix.tabstray.browser.BrowserTrayInteractor
import org.mozilla.fenix.tabstray.browser.DefaultBrowserTrayInteractor
import org.mozilla.fenix.tabstray.browser.SelectionBannerBinding
import org.mozilla.fenix.tabstray.browser.SelectionBannerBinding.VisibilityModifier
import org.mozilla.fenix.tabstray.browser.SelectionHandleBinding
import org.mozilla.fenix.tabstray.ext.anchorWithAction
import org.mozilla.fenix.tabstray.ext.bookmarkMessage
import org.mozilla.fenix.tabstray.ext.collectionMessage
import org.mozilla.fenix.tabstray.ext.make
import org.mozilla.fenix.tabstray.ext.orDefault
import org.mozilla.fenix.tabstray.ext.showWithTheme
import org.mozilla.fenix.utils.allowUndo
import kotlin.math.max

@Suppress("TooManyFunctions", "LargeClass")
class TabsTrayFragment : AppCompatDialogFragment() {
    @VisibleForTesting internal lateinit var tabsTrayStore: TabsTrayStore
    private lateinit var browserTrayInteractor: BrowserTrayInteractor
    private lateinit var tabsTrayInteractor: TabsTrayInteractor
    private lateinit var tabsTrayController: DefaultTabsTrayController
    @VisibleForTesting internal lateinit var trayBehaviorManager: TabSheetBehaviorManager

    private val tabLayoutMediator = ViewBoundFeatureWrapper<TabLayoutMediator>()
    private val tabCounterBinding = ViewBoundFeatureWrapper<TabCounterBinding>()
    private val floatingActionButtonBinding = ViewBoundFeatureWrapper<FloatingActionButtonBinding>()
    private val selectionBannerBinding = ViewBoundFeatureWrapper<SelectionBannerBinding>()
    private val selectionHandleBinding = ViewBoundFeatureWrapper<SelectionHandleBinding>()
    private val tabsTrayCtaBinding = ViewBoundFeatureWrapper<TabsTrayInfoBannerBinding>()
    private val secureTabsTrayBinding = ViewBoundFeatureWrapper<SecureTabsTrayBinding>()
    private val tabsTrayInactiveTabsOnboardingBinding = ViewBoundFeatureWrapper<TabsTrayInactiveTabsOnboardingBinding>()

    @VisibleForTesting @Suppress("VariableNaming")
    internal var _tabsTrayBinding: ComponentTabstray2Binding? = null
    private val tabsTrayBinding get() = _tabsTrayBinding!!
    @VisibleForTesting @Suppress("VariableNaming")
    internal var _tabsTrayDialogBinding: FragmentTabTrayDialogBinding? = null
    private val tabsTrayDialogBinding get() = _tabsTrayDialogBinding!!
    @VisibleForTesting @Suppress("VariableNaming")
    internal var _fabButtonBinding: ComponentTabstrayFabBinding? = null
    private val fabButtonBinding get() = _fabButtonBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.TabTrayDialogStyle)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        TabsTrayDialog(requireContext(), theme) { browserTrayInteractor }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _tabsTrayDialogBinding = FragmentTabTrayDialogBinding.inflate(
            inflater,
            container,
            false
        )
        _tabsTrayBinding = ComponentTabstray2Binding.inflate(
            inflater,
            tabsTrayDialogBinding.root,
            true
        )
        _fabButtonBinding = ComponentTabstrayFabBinding.inflate(
            LayoutInflater.from(tabsTrayDialogBinding.root.context),
            tabsTrayDialogBinding.root,
            true
        )

        val args by navArgs<TabsTrayFragmentArgs>()
        val initialMode = if (args.enterMultiselect) {
            TabsTrayState.Mode.Select(emptySet())
        } else {
            TabsTrayState.Mode.Normal
        }

        tabsTrayStore = StoreProvider.get(this) {
            TabsTrayStore(
                initialState = TabsTrayState(
                    mode = initialMode
                )
            )
        }

        return tabsTrayDialogBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _tabsTrayBinding = null
        _tabsTrayDialogBinding = null
        _fabButtonBinding = null
    }

    @Suppress("LongMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = activity as HomeActivity

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            fabButtonBinding.newTabButton.accessibilityTraversalAfter =
                tabsTrayBinding.tabLayout.id
        }
        requireComponents.analytics.metrics.track(Event.TabsTrayOpened)

        val navigationInteractor =
            DefaultNavigationInteractor(
                context = requireContext(),
                activity = activity,
                tabsTrayStore = tabsTrayStore,
                browserStore = requireComponents.core.store,
                navController = findNavController(),
                metrics = requireComponents.analytics.metrics,
                dismissTabTray = ::dismissTabsTray,
                dismissTabTrayAndNavigateHome = ::dismissTabsTrayAndNavigateHome,
                bookmarksUseCase = requireComponents.useCases.bookmarksUseCases,
                collectionStorage = requireComponents.core.tabCollectionStorage,
                showCollectionSnackbar = ::showCollectionSnackbar,
                showBookmarkSnackbar = ::showBookmarkSnackbar,
                accountManager = requireComponents.backgroundServices.accountManager,
                ioDispatcher = Dispatchers.IO
            )

        tabsTrayController = DefaultTabsTrayController(
            trayStore = tabsTrayStore,
            browserStore = requireComponents.core.store,
            browsingModeManager = activity.browsingModeManager,
            navController = findNavController(),
            navigateToHomeAndDeleteSession = ::navigateToHomeAndDeleteSession,
            navigationInteractor = navigationInteractor,
            profiler = requireComponents.core.engine.profiler,
            metrics = requireComponents.analytics.metrics,
            tabsUseCases = requireComponents.useCases.tabsUseCases,
            selectTabPosition = ::selectTabPosition,
            dismissTray = ::dismissTabsTray,
            showUndoSnackbarForTab = ::showUndoSnackbarForTab
        )

        tabsTrayInteractor = DefaultTabsTrayInteractor(tabsTrayController)

        browserTrayInteractor = DefaultBrowserTrayInteractor(
            tabsTrayStore,
            tabsTrayInteractor,
            tabsTrayController,
            requireComponents.useCases.tabsUseCases.selectTab,
            requireComponents.analytics.metrics
        )

        setupMenu(navigationInteractor)
        setupPager(
            view.context,
            tabsTrayStore,
            tabsTrayInteractor,
            browserTrayInteractor,
            navigationInteractor
        )

        setupBackgroundDismissalListener {
            requireComponents.analytics.metrics.track(Event.TabsTrayClosed)
            dismissAllowingStateLoss()
        }

        trayBehaviorManager = TabSheetBehaviorManager(
            behavior = BottomSheetBehavior.from(tabsTrayBinding.tabWrapper),
            orientation = resources.configuration.orientation,
            maxNumberOfTabs = max(
                requireContext().components.core.store.state.normalTabs.size,
                requireContext().components.core.store.state.privateTabs.size
            ),
            numberForExpandingTray = if (requireContext().settings().gridTabView) {
                EXPAND_AT_GRID_SIZE
            } else {
                EXPAND_AT_LIST_SIZE
            },
            navigationInteractor = navigationInteractor,
            displayMetrics = requireContext().resources.displayMetrics
        )

        tabsTrayCtaBinding.set(
            feature = TabsTrayInfoBannerBinding(
                context = view.context,
                store = requireComponents.core.store,
                infoBannerView = tabsTrayBinding.infoBanner,
                settings = requireComponents.settings,
                navigationInteractor = navigationInteractor
            ),
            owner = this,
            view = view
        )

        tabLayoutMediator.set(
            feature = TabLayoutMediator(
                tabLayout = tabsTrayBinding.tabLayout,
                interactor = tabsTrayInteractor,
                browsingModeManager = activity.browsingModeManager,
                tabsTrayStore = tabsTrayStore,
                metrics = requireComponents.analytics.metrics
            ),
            owner = this,
            view = view
        )

        val tabsTrayTabCounter2Binding = TabsTrayTabCounter2Binding.bind(
            tabsTrayBinding.tabLayout
        )

        tabCounterBinding.set(
            feature = TabCounterBinding(
                store = requireComponents.core.store,
                counter = tabsTrayTabCounter2Binding.tabCounter
            ),
            owner = this,
            view = view
        )

        floatingActionButtonBinding.set(
            feature = FloatingActionButtonBinding(
                store = tabsTrayStore,
                actionButton = fabButtonBinding.newTabButton,
                browserTrayInteractor = browserTrayInteractor
            ),
            owner = this,
            view = view
        )

        val tabsTrayMultiselectItemsBinding = TabstrayMultiselectItemsBinding.bind(
            tabsTrayBinding.root
        )

        selectionBannerBinding.set(
            feature = SelectionBannerBinding(
                context = requireContext(),
                binding = tabsTrayBinding,
                store = tabsTrayStore,
                navInteractor = navigationInteractor,
                tabsTrayInteractor = tabsTrayInteractor,
                containerView = view,
                backgroundView = tabsTrayBinding.topBar,
                showOnSelectViews = VisibilityModifier(
                    tabsTrayMultiselectItemsBinding.collectMultiSelect,
                    tabsTrayMultiselectItemsBinding.shareMultiSelect,
                    tabsTrayMultiselectItemsBinding.menuMultiSelect,
                    tabsTrayBinding.multiselectTitle,
                    tabsTrayBinding.exitMultiSelect
                ),
                showOnNormalViews = VisibilityModifier(
                    tabsTrayBinding.tabLayout,
                    tabsTrayBinding.tabTrayOverflow,
                    fabButtonBinding.newTabButton
                )
            ),
            owner = this,
            view = view
        )

        selectionHandleBinding.set(
            feature = SelectionHandleBinding(
                store = tabsTrayStore,
                handle = tabsTrayBinding.handle,
                containerLayout = tabsTrayBinding.tabWrapper
            ),
            owner = this,
            view = view
        )

        secureTabsTrayBinding.set(
            feature = SecureTabsTrayBinding(
                store = tabsTrayStore,
                settings = requireComponents.settings,
                fragment = this,
                dialog = dialog as TabsTrayDialog
            ),
            owner = this,
            view = view
        )

        tabsTrayInactiveTabsOnboardingBinding.set(
            feature = TabsTrayInactiveTabsOnboardingBinding(
                context = requireContext(),
                store = requireComponents.core.store,
                tabsTrayBinding = tabsTrayBinding,
                settings = requireComponents.settings,
                navigationInteractor = navigationInteractor
            ),
            owner = this,
            view = view
        )

        setFragmentResultListener(ShareFragment.RESULT_KEY) { _, _ ->
            dismissTabsTray()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        trayBehaviorManager.updateDependingOnOrientation(newConfig.orientation)

        if (requireContext().settings().gridTabView) {
            tabsTrayBinding.tabsTray.adapter?.notifyDataSetChanged()
        }
    }

    @VisibleForTesting
    internal fun showUndoSnackbarForTab(isPrivate: Boolean) {
        val snackbarMessage =
            when (isPrivate) {
                true -> getString(R.string.snackbar_private_tab_closed)
                false -> getString(R.string.snackbar_tab_closed)
            }

        lifecycleScope.allowUndo(
            requireView(),
            snackbarMessage,
            getString(R.string.snackbar_deleted_undo),
            {
                requireComponents.useCases.tabsUseCases.undo.invoke()
                tabLayoutMediator.withFeature {
                    it.selectTabAtPosition(
                        if (isPrivate) Page.PrivateTabs.ordinal else Page.NormalTabs.ordinal
                    )
                }
            },
            operation = { },
            elevation = ELEVATION,
            anchorView = if (fabButtonBinding.newTabButton.isVisible) fabButtonBinding.newTabButton else null
        )
    }

    @VisibleForTesting
    internal fun setupPager(
        context: Context,
        store: TabsTrayStore,
        trayInteractor: TabsTrayInteractor,
        browserInteractor: BrowserTrayInteractor,
        navigationInteractor: NavigationInteractor
    ) {
        tabsTrayBinding.tabsTray.apply {
            adapter = TrayPagerAdapter(
                context,
                store,
                browserInteractor,
                navigationInteractor,
                trayInteractor,
                requireComponents.core.store
            )
            isUserInputEnabled = false
        }
    }

    @VisibleForTesting
    internal fun setupMenu(navigationInteractor: NavigationInteractor) {
        tabsTrayBinding.tabTrayOverflow.setOnClickListener { anchor ->

            requireComponents.analytics.metrics.track(Event.TabsTrayMenuOpened)

            val menu = getTrayMenu(
                context = requireContext(),
                browserStore = requireComponents.core.store,
                tabsTrayStore = tabsTrayStore,
                tabLayout = tabsTrayBinding.tabLayout,
                navigationInteractor = navigationInteractor
            ).build()

            menu.showWithTheme(anchor)
        }
    }

    @VisibleForTesting
    internal fun getTrayMenu(
        context: Context,
        browserStore: BrowserStore,
        tabsTrayStore: TabsTrayStore,
        tabLayout: TabLayout,
        navigationInteractor: NavigationInteractor
    ) = MenuIntegration(context, browserStore, tabsTrayStore, tabLayout, navigationInteractor)

    @VisibleForTesting
    internal fun setupBackgroundDismissalListener(block: (View) -> Unit) {
        tabsTrayDialogBinding.tabLayout.setOnClickListener(block)
        tabsTrayBinding.handle.setOnClickListener(block)
    }

    @VisibleForTesting
    internal fun dismissTabsTrayAndNavigateHome(sessionId: String) {
        navigateToHomeAndDeleteSession(sessionId)
        dismissTabsTray()
    }

    internal val homeViewModel: HomeScreenViewModel by activityViewModels()

    @VisibleForTesting
    internal fun navigateToHomeAndDeleteSession(sessionId: String) {
        homeViewModel.sessionToDelete = sessionId
        val directions = NavGraphDirections.actionGlobalHome()
        findNavController().navigate(directions)
    }

    @VisibleForTesting
    internal fun selectTabPosition(position: Int, smoothScroll: Boolean) {
        tabsTrayBinding.tabsTray.setCurrentItem(position, smoothScroll)
        tabsTrayBinding.tabLayout.getTabAt(position)?.select()
    }

    @VisibleForTesting
    internal fun dismissTabsTray() {
        // This should always be the last thing we do because nothing (e.g. telemetry)
        // is guaranteed after that.
        dismissAllowingStateLoss()
    }

    @VisibleForTesting
    internal fun showCollectionSnackbar(
        tabSize: Int,
        isNewCollection: Boolean = false,
        collectionToSelect: Long?
    ) {
        FenixSnackbar
            .make(requireView())
            .collectionMessage(tabSize, isNewCollection)
            .anchorWithAction(getSnackbarAnchor()) {
                findNavController().navigate(
                    TabsTrayFragmentDirections.actionGlobalHome(
                        focusOnAddressBar = false,
                        focusOnCollection = collectionToSelect.orDefault()
                    )
                )
                dismissTabsTray()
            }.show()
    }

    @VisibleForTesting
    internal fun showBookmarkSnackbar(
        tabSize: Int
    ) {
        FenixSnackbar
            .make(requireView())
            .bookmarkMessage(tabSize)
            .anchorWithAction(getSnackbarAnchor()) {
                findNavController().navigate(
                    TabsTrayFragmentDirections.actionGlobalBookmarkFragment(BookmarkRoot.Mobile.id)
                )
                dismissTabsTray()
            }
            .show()
    }

    private fun getSnackbarAnchor(): View? {
        return if (requireComponents.settings.accessibilityServicesEnabled) {
            null
        } else {
            fabButtonBinding.newTabButton
        }
    }

    companion object {
        // Minimum number of list items for which to show the tabs tray as expanded.
        const val EXPAND_AT_LIST_SIZE = 4

        // Minimum number of grid items for which to show the tabs tray as expanded.
        private const val EXPAND_AT_GRID_SIZE = 3

        // Elevation for undo toasts
        @VisibleForTesting
        internal const val ELEVATION = 80f
    }
}
