/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PROTECTED
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import kotlinx.android.synthetic.main.activity_home.navigationToolbarStub
import kotlinx.coroutines.launch
import mozilla.components.browser.search.SearchEngine
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.EngineView
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import mozilla.components.support.locale.LocaleAwareAppCompatActivity
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.utils.toSafeIntent
import org.mozilla.fenix.browser.UriOpenedObserver
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.browser.browsingmode.BrowsingModeManager
import org.mozilla.fenix.browser.browsingmode.DefaultBrowsingModeManager
import org.mozilla.fenix.components.metrics.BreadcrumbsRecorder
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.exceptions.ExceptionsFragmentDirections
import org.mozilla.fenix.ext.alreadyOnDestination
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.home.HomeFragmentDirections
import org.mozilla.fenix.home.intent.CrashReporterIntentProcessor
import org.mozilla.fenix.home.intent.DeepLinkIntentProcessor
import org.mozilla.fenix.home.intent.OpenBrowserIntentProcessor
import org.mozilla.fenix.home.intent.SpeechProcessingIntentProcessor
import org.mozilla.fenix.home.intent.StartSearchIntentProcessor
import org.mozilla.fenix.library.bookmarks.BookmarkFragmentDirections
import org.mozilla.fenix.library.history.HistoryFragmentDirections
import org.mozilla.fenix.onboarding.FenixOnboarding
import org.mozilla.fenix.perf.HotStartPerformanceMonitor
import org.mozilla.fenix.perf.Performance
import org.mozilla.fenix.search.SearchFragmentDirections
import org.mozilla.fenix.settings.DefaultBrowserSettingsFragmentDirections
import org.mozilla.fenix.settings.SettingsFragmentDirections
import org.mozilla.fenix.settings.TrackingProtectionFragmentDirections
import org.mozilla.fenix.settings.about.AboutFragmentDirections
import org.mozilla.fenix.settings.logins.SavedLoginsFragmentDirections
import org.mozilla.fenix.theme.DefaultThemeManager
import org.mozilla.fenix.theme.ThemeManager
import org.mozilla.fenix.utils.BrowsersCache

@SuppressWarnings("TooManyFunctions", "LargeClass")
open class HomeActivity : LocaleAwareAppCompatActivity() {

    lateinit var themeManager: ThemeManager
    lateinit var browsingModeManager: BrowsingModeManager

    private var sessionObserver: SessionManager.Observer? = null

    private val hotStartMonitor = HotStartPerformanceMonitor()

    private var isToolbarInflated = false

    private val navHost by lazy {
        supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
    }

    private val externalSourceIntentProcessors by lazy {
        listOf(
            SpeechProcessingIntentProcessor(this, components.analytics.metrics),
            StartSearchIntentProcessor(components.analytics.metrics),
            DeepLinkIntentProcessor(this),
            OpenBrowserIntentProcessor(this, ::getIntentSessionId)
        )
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        components.publicSuffixList.prefetch()

        setupThemeAndBrowsingMode(getModeFromIntentOrLastKnown(intent))
        setContentView(R.layout.activity_home)
        Performance.instrumentColdStartupToHomescreenTime(this)

        externalSourceIntentProcessors.any { it.process(intent, navHost.navController, this.intent) }

        if (intent.getBooleanExtra(EXTRA_FINISH_ONBOARDING, false)) {
            FenixOnboarding(this).finish()
        }

        if (settings().isTelemetryEnabled) {
            lifecycle.addObserver(BreadcrumbsRecorder(components.analytics.crashReporter,
                navHost.navController, ::getBreadcrumbMessage))

            intent
                ?.toSafeIntent()
                ?.let(::getIntentSource)
                ?.also { components.analytics.metrics.track(Event.OpenedApp(it)) }
        }
        supportActionBar?.hide()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            with(components.backgroundServices) {
                // Make sure accountManager is initialized.
                accountManager.initAsync().await()
                // If we're authenticated, kick-off a sync and a device state refresh.
                accountManager.authenticatedAccount()?.let {
                    accountManager.syncNowAsync(SyncReason.Startup, debounce = true)
                }
            }
        }
    }

    final override fun onRestart() {
        hotStartMonitor.onRestartFirstMethodCall()
        super.onRestart()
    }

    final override fun onPostResume() {
        super.onPostResume()
        hotStartMonitor.onPostResumeFinalMethodCall()
    }

    final override fun onPause() {
        super.onPause()

        // Every time the application goes into the background, it is possible that the user
        // is about to change the browsers installed on their system. Therefore, we reset the cache of
        // all the installed browsers.
        //
        // NB: There are ways for the user to install new products without leaving the browser.
        BrowsersCache.resetAll()
    }

    /**
     * Handles intents received when the activity is open.
     */
    final override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return

        val intentProcessors = listOf(CrashReporterIntentProcessor()) + externalSourceIntentProcessors
        intentProcessors.any { it.process(intent, navHost.navController, this.intent) }
        browsingModeManager.mode = getModeFromIntentOrLastKnown(intent)
    }

    /**
     * Overrides view inflation to inject a custom [EngineView] from [components].
     */
    final override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? = when (name) {
        EngineView::class.java.name -> components.core.engine.createView(context, attrs).asView()
        else -> super.onCreateView(parent, name, context, attrs)
    }

    final override fun onBackPressed() {
        supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.forEach {
            if (it is UserInteractionHandler && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    protected open fun getBreadcrumbMessage(destination: NavDestination): String {
        val fragmentName = resources.getResourceEntryName(destination.id)
        return "Changing to fragment $fragmentName, isCustomTab: false"
    }

    @VisibleForTesting(otherwise = PROTECTED)
    internal open fun getIntentSource(intent: SafeIntent): Event.OpenedApp.Source? {
        return when {
            intent.isLauncherIntent -> Event.OpenedApp.Source.APP_ICON
            intent.action == Intent.ACTION_VIEW -> Event.OpenedApp.Source.LINK
            else -> null
        }
    }

    /**
     * External sources such as 3rd party links and shortcuts use this function to enter
     * private mode directly before the content view is created. Returns the mode set by the intent
     * otherwise falls back to the last known mode.
     */
    internal fun getModeFromIntentOrLastKnown(intent: Intent?): BrowsingMode {
        intent?.toSafeIntent()?.let {
            if (it.hasExtra(PRIVATE_BROWSING_MODE)) {
                val startPrivateMode = it.getBooleanExtra(PRIVATE_BROWSING_MODE, false)
                return BrowsingMode.fromBoolean(isPrivate = startPrivateMode)
            }
        }
        return settings().lastKnownMode
    }

    private fun setupThemeAndBrowsingMode(mode: BrowsingMode) {
        settings().lastKnownMode = mode
        browsingModeManager = createBrowsingModeManager(mode)
        themeManager = createThemeManager()
        themeManager.setActivityTheme(this)
        themeManager.applyStatusBarTheme(this)
    }

    /**
     * Returns the [supportActionBar], inflating it if necessary.
     * Everyone should call this instead of supportActionBar.
     */
    fun getSupportActionBarAndInflateIfNecessary(): ActionBar {
        // Add ids to this that we don't want to have a toolbar back button
        if (!isToolbarInflated) {
            val navigationToolbar = navigationToolbarStub.inflate() as Toolbar

            setSupportActionBar(navigationToolbar)

            NavigationUI.setupWithNavController(
                navigationToolbar,
                navHost.navController,
                AppBarConfiguration.Builder().build()
            )
            navigationToolbar.setNavigationOnClickListener {
                onBackPressed()
            }

            isToolbarInflated = true
        }
        return supportActionBar!!
    }

    protected open fun getIntentSessionId(intent: SafeIntent): String? = null

    @Suppress("LongParameterList")
    fun openToBrowserAndLoad(
        searchTermOrURL: String,
        newTab: Boolean,
        from: BrowserDirection,
        customTabSessionId: String? = null,
        engine: SearchEngine? = null,
        forceSearch: Boolean = false
    ) {
        openToBrowser(from, customTabSessionId)
        load(searchTermOrURL, newTab, engine, forceSearch)
    }

    fun openToBrowser(from: BrowserDirection, customTabSessionId: String? = null) {
        if (sessionObserver == null) {
            sessionObserver = UriOpenedObserver(this)
        }

        if (navHost.navController.alreadyOnDestination(R.id.browserFragment)) return
        @IdRes val fragmentId = if (from.fragmentId != 0) from.fragmentId else null
        val directions = getNavDirections(from, customTabSessionId)
        if (directions != null) {
            navHost.navController.nav(fragmentId, directions)
        }
    }

    protected open fun getNavDirections(
        from: BrowserDirection,
        customTabSessionId: String?
    ): NavDirections? = when (from) {
        BrowserDirection.FromGlobal ->
            NavGraphDirections.actionGlobalBrowser(customTabSessionId)
        BrowserDirection.FromHome ->
            HomeFragmentDirections.actionHomeFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromSearch ->
            SearchFragmentDirections.actionSearchFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromSettings ->
            SettingsFragmentDirections.actionSettingsFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromBookmarks ->
            BookmarkFragmentDirections.actionBookmarkFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromHistory ->
            HistoryFragmentDirections.actionHistoryFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromExceptions ->
            ExceptionsFragmentDirections.actionExceptionsFragmentToBrowserFragment(
                customTabSessionId
            )
        BrowserDirection.FromAbout ->
            AboutFragmentDirections.actionAboutFragmentToBrowserFragment(customTabSessionId)
        BrowserDirection.FromTrackingProtection ->
            TrackingProtectionFragmentDirections.actionTrackingProtectionFragmentToBrowserFragment(
                customTabSessionId
            )
        BrowserDirection.FromDefaultBrowserSettingsFragment ->
            DefaultBrowserSettingsFragmentDirections.actionDefaultBrowserSettingsFragmentToBrowserFragment(
                customTabSessionId
            )
        BrowserDirection.FromSavedLoginsFragment ->
            SavedLoginsFragmentDirections.actionSavedLoginsFragmentToBrowserFragment(
                customTabSessionId
            )
    }

    private fun load(
        searchTermOrURL: String,
        newTab: Boolean,
        engine: SearchEngine?,
        forceSearch: Boolean
    ) {
        val mode = browsingModeManager.mode

        val loadUrlUseCase = if (newTab) {
            when (mode) {
                BrowsingMode.Private -> components.useCases.tabsUseCases.addPrivateTab
                BrowsingMode.Normal -> components.useCases.tabsUseCases.addTab
            }
        } else components.useCases.sessionUseCases.loadUrl

        val searchUseCase: (String) -> Unit = { searchTerms ->
            if (newTab) {
                components.useCases.searchUseCases.newTabSearch
                    .invoke(
                        searchTerms,
                        Session.Source.USER_ENTERED,
                        true,
                        mode.isPrivate,
                        searchEngine = engine
                    )
            } else components.useCases.searchUseCases.defaultSearch.invoke(searchTerms, engine)
        }

        if (!forceSearch && searchTermOrURL.isUrl()) {
            loadUrlUseCase.invoke(searchTermOrURL.toNormalizedUrl())
        } else {
            searchUseCase.invoke(searchTermOrURL)
        }
    }

    fun updateThemeForSession(session: Session) {
        val sessionMode = BrowsingMode.fromBoolean(session.private)
        if (sessionMode != browsingModeManager.mode) {
            browsingModeManager.mode = sessionMode
        }
    }

    protected open fun createBrowsingModeManager(initialMode: BrowsingMode): BrowsingModeManager {
        return DefaultBrowsingModeManager(initialMode) { newMode ->
            themeManager.currentTheme = newMode
        }
    }

    protected open fun createThemeManager(): ThemeManager {
        return DefaultThemeManager(browsingModeManager.mode, this)
    }

    companion object {
        const val OPEN_TO_BROWSER = "open_to_browser"
        const val OPEN_TO_BROWSER_AND_LOAD = "open_to_browser_and_load"
        const val OPEN_TO_SEARCH = "open_to_search"
        const val PRIVATE_BROWSING_MODE = "private_browsing_mode"
        const val EXTRA_DELETE_PRIVATE_TABS = "notification_delete_and_open"
        const val EXTRA_OPENED_FROM_NOTIFICATION = "notification_open"
        const val EXTRA_FINISH_ONBOARDING = "finishonboarding"
    }
}
