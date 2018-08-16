/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.system

import android.os.Bundle
import android.webkit.WebView
import mozilla.components.concept.engine.EngineSession
import mozilla.components.support.ktx.kotlin.toBundle
import java.lang.ref.WeakReference
import kotlinx.coroutines.experimental.launch

/**
 * WebView-based EngineSession implementation.
 */
@Suppress("TooManyFunctions")
class SystemEngineSession : EngineSession() {
    internal var view: WeakReference<SystemEngineView>? = null
    internal var scheduledLoad = ScheduledLoad(null)
    internal var trackingProtectionEnabled = false

    /**
     * See [EngineSession.loadUrl]
     */
    override fun loadUrl(url: String) {
        val internalView = currentView()

        if (internalView == null) {
            // We can't load a URL without a WebView. So let's just remember the URL here until
            // this session gets linked to a WebView. See: EngineView.render(session).
            scheduledLoad = ScheduledLoad(url)
        } else {
            view?.get()?.currentUrl = url
            internalView.loadUrl(url)
        }
    }

    /**
     * See [EngineSession.loadData]
     */
    override fun loadData(data: String, mimeType: String, encoding: String) {
        val internalView = currentView()

        if (internalView == null) {
            // We remember the data that we want to load and when then session gets linked
            // to a WebView we call loadData then.
            scheduledLoad = ScheduledLoad(data, mimeType)
        } else {
            internalView.loadData(data, mimeType, encoding)
        }
    }

    /**
     * See [EngineSession.stopLoading]
     */
    override fun stopLoading() {
        currentView()?.stopLoading()
    }

    /**
     * See [EngineSession.reload]
     */
    override fun reload() {
        currentView()?.reload()
    }

    /**
     * See [EngineSession.goBack]
     */
    override fun goBack() {
        currentView()?.goBack()
    }

    /**
     * See [EngineSession.goForward]
     */
    override fun goForward() {
        currentView()?.goForward()
    }

    /**
     * See [EngineSession.saveState]
     */
    override fun saveState(): Map<String, Any> {
        val state = Bundle()
        currentView()?.saveState(state)

        return mutableMapOf<String, Any>().apply {
            state.keySet().forEach { k -> put(k, state[k]) }
        }
    }

    /**
     * See [EngineSession.restoreState]
     */
    override fun restoreState(state: Map<String, Any>) {
        currentView()?.restoreState(state.toBundle())
    }

    /**
     * See [EngineSession.enableTrackingProtection]
     *
     * Note that specifying tracking protection policies at run-time is
     * not supported by [SystemEngine]. Tracking protection is always active
     * for all URLs provided in domain_blacklist.json and domain_overrides.json,
     * which both support specifying categories.
     */
    override fun enableTrackingProtection(policy: TrackingProtectionPolicy) {
        currentView()?.let {
            // Make sure Url matcher is preloaded now that tracking protection is enabled
            launch { SystemEngineView.getOrCreateUrlMatcher(it.context) }
        }

        trackingProtectionEnabled = true
        notifyObservers { onTrackerBlockingEnabledChange(true) }
    }

    /**
     * See [EngineSession.disableTrackingProtection]
     */
    override fun disableTrackingProtection() {
        trackingProtectionEnabled = false
        notifyObservers { onTrackerBlockingEnabledChange(false) }
    }

    internal fun currentView(): WebView? {
        return view?.get()?.currentWebView
    }

    /**
     * Helper method to notify observers from other classes in this package. This is needed as
     * almost everything is implemented by WebView and its listeners. There is no actual concept of
     * a session when using WebView.
     */
    internal fun internalNotifyObservers(block: Observer.() -> Unit) {
        super.notifyObservers(block)
    }
}
