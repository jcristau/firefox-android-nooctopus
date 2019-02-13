/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy
import mozilla.components.feature.session.HistoryDelegate
import mozilla.components.feature.session.bundling.SessionBundleStorage
import mozilla.components.lib.crash.handler.CrashHandlerService
import org.mozilla.fenix.AppRequestInterceptor
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.util.concurrent.TimeUnit

/**
 * Component group for all core browser functionality.
 */
class Core(private val context: Context) {

    /**
     * The browser engine component initialized based on the build
     * configuration (see build variants).
     */
    val engine: Engine by lazy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val defaultSettings = DefaultSettings(
            requestInterceptor = AppRequestInterceptor(context),
            remoteDebuggingEnabled = false,
            testingModeEnabled = false,
            trackingProtectionPolicy = createTrackingProtectionPolicy(prefs),
            historyTrackingDelegate = HistoryDelegate(historyStorage)
        )

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .crashHandler(CrashHandlerService::class.java)
            .build()

        val runtime = GeckoRuntime.create(context, runtimeSettings)

        GeckoEngine(context, defaultSettings, runtime)
    }

    val sessionStorage by lazy {
        SessionBundleStorage(context, bundleLifetime = Pair(1, TimeUnit.HOURS))
    }

    /**
     * The session manager component provides access to a centralized registry of
     * all browser sessions (i.e. tabs). It is initialized here to persist and restore
     * sessions from the [SessionStorage], and with a default session (about:blank) in
     * case all sessions/tabs are closed.
     */
    val sessionManager by lazy {
        SessionManager(engine).also { sessionManager ->
            // Restore a previous, still active bundle.
            GlobalScope.launch(Dispatchers.Main) {
                val snapshot = async(Dispatchers.IO) {
                    sessionStorage.restore()?.restoreSnapshot(engine)
                }

                // There's an active bundle with a snapshot: Feed it into the SessionManager.
                snapshot.await()?.let { sessionManager.restore(it) }

                // Now that we have restored our previous state (if there's one) let's setup auto saving the state while
                // the app is used.
                sessionStorage.autoSave(sessionManager)
                    .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                    .whenGoingToBackground()
                    .whenSessionsChange()
            }
        }
    }

    /**
     * The storage component to persist browsing history (with the exception of
     * private sessions).
     */
    val historyStorage by lazy { PlacesHistoryStorage(context) }

    /**
     * Constructs a [TrackingProtectionPolicy] based on current preferences.
     *
     * @param prefs the shared preferences to use when reading tracking
     * protection settings.
     * @param normalMode whether or not tracking protection should be enabled
     * in normal browsing mode, defaults to the current preference value.
     * @param privateMode whether or not tracking protection should be enabled
     * in private browsing mode, default to the current preference value.
     * @return the constructed tracking protection policy based on preferences.
     */
    fun createTrackingProtectionPolicy(
        prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context),
        normalMode: Boolean = true,
        privateMode: Boolean = true
    ): TrackingProtectionPolicy {

        return when {
            normalMode && privateMode -> TrackingProtectionPolicy.all()
            normalMode && !privateMode -> TrackingProtectionPolicy.all().forRegularSessionsOnly()
            !normalMode && privateMode -> TrackingProtectionPolicy.all().forPrivateSessionsOnly()
            else -> TrackingProtectionPolicy.none()
        }
    }
}
