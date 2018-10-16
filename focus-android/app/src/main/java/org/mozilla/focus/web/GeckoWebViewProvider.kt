/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.web

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import kotlinx.coroutines.experimental.launch
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.browser.errorpages.ErrorType
import mozilla.components.browser.session.Session
import mozilla.components.support.ktx.android.util.Base64
import org.json.JSONException
import org.mozilla.focus.IO
import org.mozilla.focus.R
import org.mozilla.focus.browser.LocalizedContent
import org.mozilla.focus.ext.savedWebViewState
import org.mozilla.focus.gecko.GeckoViewPrompt
import org.mozilla.focus.gecko.NestedGeckoView
import org.mozilla.focus.telemetry.SentryWrapper
import org.mozilla.focus.telemetry.TelemetryWrapper
import org.mozilla.focus.utils.AppConstants
import org.mozilla.focus.utils.IntentUtils
import org.mozilla.focus.utils.MobileMetricsPingStorage
import org.mozilla.focus.utils.Settings
import org.mozilla.focus.utils.UrlUtils
import org.mozilla.focus.webview.SystemWebView
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.SessionFinder

/**
 * WebViewProvider implementation for creating a Gecko based implementation of IWebView.
 */
@Suppress("TooManyFunctions")
class GeckoWebViewProvider : IWebViewProvider {

    override fun preload(context: Context) {
        sendTelemetryEventOnSwitchToGecko(context)
        createGeckoRuntime(context)
    }

    private fun sendTelemetryEventOnSwitchToGecko(context: Context) {
        val settings = Settings.getInstance(context)
        if (!settings.shouldShowFirstrun() && settings.isFirstGeckoRun()) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putBoolean(PREF_FIRST_GECKO_RUN, false).apply()
            Log.d(javaClass.simpleName, "Sending change to Gecko ping")
            TelemetryWrapper.changeToGeckoEngineEvent()
        }
    }

    override fun create(context: Context, attributeSet: AttributeSet?): View {
        return GeckoWebView(context, attributeSet)
    }

    override fun performCleanup(context: Context) {
        // Nothing: does Gecko need extra private mode cleanup?
    }

    override fun performNewBrowserSessionCleanup() {
        // Nothing: a WebKit work-around.
    }

    private fun createGeckoRuntime(context: Context) {
        if (geckoRuntime == null) {
            val runtimeSettingsBuilder = GeckoRuntimeSettings.Builder()
            runtimeSettingsBuilder.useContentProcessHint(true)
            // Safe browsing is not ready #3309
            runtimeSettingsBuilder.blockMalware(true)
            runtimeSettingsBuilder.blockPhishing(true)
            runtimeSettingsBuilder.nativeCrashReportingEnabled(false)
            runtimeSettingsBuilder.javaCrashReportingEnabled(false)
            geckoRuntime =
                    GeckoRuntime.create(context.applicationContext, runtimeSettingsBuilder.build())
        }
    }

    override fun requestMobileSite(context: Context, webSettings: WebSettings) {
    }

    override fun requestDesktopSite(webSettings: WebSettings) {
    }

    override fun applyAppSettings(
        context: Context,
        webSettings: WebSettings,
        systemWebView: SystemWebView
    ) {
    }

    override fun disableBlocking(webSettings: WebSettings, systemWebView: SystemWebView) {
    }

    override fun getUABrowserString(existingUAString: String, focusToken: String): String {
        return ""
    }

    @Suppress("LargeClass", "TooManyFunctions")
    class GeckoWebView(context: Context, attrs: AttributeSet?) :
        NestedGeckoView(context, attrs),
        IWebView,
        SharedPreferences.OnSharedPreferenceChangeListener {
        private var callback: IWebView.Callback? = null
        private var findListener: IFindListener? = null
        private var currentUrl: String = "about:blank"
        private var canGoBack: Boolean = false
        private var canGoForward: Boolean = false
        private var isSecure: Boolean = false
        private var geckoSession: GeckoSession
        private var webViewTitle: String? = null
        private var isLoadingInternalUrl = false
        private lateinit var finder: SessionFinder
        private var restored = false

        init {
            PreferenceManager.getDefaultSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener(this)
            geckoSession = createGeckoSession()
            applySettingsAndSetDelegates()
            setSession(geckoSession, geckoRuntime)
        }

        private fun applySettingsAndSetDelegates() {
            applyAppSettings()
            updateBlocking()

            geckoSession.contentDelegate = createContentDelegate()
            geckoSession.progressDelegate = createProgressDelegate()
            geckoSession.navigationDelegate = createNavigationDelegate()
            geckoSession.trackingProtectionDelegate = createTrackingProtectionDelegate()
            geckoSession.promptDelegate = createPromptDelegate()
            finder = geckoSession.finder
            finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        }

        private fun createGeckoSession(): GeckoSession {
            val settings = GeckoSessionSettings()
            settings.setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, true)
            settings.setBoolean(GeckoSessionSettings.USE_PRIVATE_MODE, true)
            settings.setBoolean(GeckoSessionSettings.SUSPEND_MEDIA_WHEN_INACTIVE, true)

            return GeckoSession(settings)
        }

        override fun setCallback(callback: IWebView.Callback?) {
            this.callback = callback
        }

        override fun onPause() {
        }

        override fun goBack() {
            geckoSession.goBack()
        }

        override fun goForward() {
            geckoSession.goForward()
        }

        override fun reload() {
            geckoSession.reload()
        }

        override fun destroy() {
        }

        override fun onResume() {
            storeTelemetrySnapshots()
        }

        override fun stopLoading() {
            geckoSession.stop()
            callback?.onPageFinished(isSecure)
        }

        override fun getUrl(): String? {
            return currentUrl
        }

        override fun loadUrl(url: String) {
            currentUrl = url
            geckoSession.loadUri(currentUrl)
        }

        override fun cleanup() {
            geckoSession.close()
        }

        override fun setBlockingEnabled(enabled: Boolean) {
            geckoSession.settings.setBoolean(GeckoSessionSettings.USE_TRACKING_PROTECTION, enabled)
            if (enabled) {
                updateBlocking()
                applyAppSettings()
            } else {
                geckoRuntime!!.settings.javaScriptEnabled = true
                geckoRuntime!!.settings.webFontsEnabled = true
                geckoRuntime!!.settings.cookieBehavior = GeckoRuntimeSettings.COOKIE_ACCEPT_ALL
            }
            callback?.onBlockingStateChanged(enabled)
        }

        override fun setRequestDesktop(shouldRequestDesktop: Boolean) {
            geckoSession.settings.setInt(
                    GeckoSessionSettings.USER_AGENT_MODE,
                    if (shouldRequestDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            callback?.onRequestDesktopStateChanged(shouldRequestDesktop)
        }

        @Suppress("ComplexMethod")
        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences,
            key: String
        ) {
            when (key) {
                context.getString(R.string.pref_key_privacy_block_social),
                context.getString(R.string.pref_key_privacy_block_ads),
                context.getString(R.string.pref_key_privacy_block_analytics),
                context.getString(R.string.pref_key_privacy_block_other) -> updateBlocking()
                context.getString(R.string.pref_key_performance_block_javascript)
                -> geckoRuntime!!.settings.javaScriptEnabled =
                        !Settings.getInstance(context).shouldBlockJavaScript()
                context.getString(R.string.pref_key_performance_block_webfonts)
                -> geckoRuntime!!.settings.webFontsEnabled =
                        !Settings.getInstance(context).shouldBlockWebFonts()
                context.getString(R.string.pref_key_remote_debugging) ->
                    geckoRuntime!!.settings.remoteDebuggingEnabled =
                            Settings.getInstance(context).shouldEnableRemoteDebugging()
                context.getString(R.string.pref_key_performance_enable_cookies) -> {
                    val cookiesValue = if (Settings.getInstance(context).shouldBlockCookies() &&
                        Settings.getInstance(context).shouldBlockThirdPartyCookies()
                    ) {
                        GeckoRuntimeSettings.COOKIE_ACCEPT_NONE
                    } else if (Settings.getInstance(context).shouldBlockThirdPartyCookies()) {
                        GeckoRuntimeSettings.COOKIE_ACCEPT_FIRST_PARTY
                    } else {
                        GeckoRuntimeSettings.COOKIE_ACCEPT_ALL
                    }
                    geckoRuntime!!.settings.cookieBehavior = cookiesValue
                }
                else -> return
            }
            reload()
        }

        private fun applyAppSettings() {
            geckoRuntime!!.settings.javaScriptEnabled =
                    !Settings.getInstance(context).shouldBlockJavaScript()
            geckoRuntime!!.settings.webFontsEnabled =
                    !Settings.getInstance(context).shouldBlockWebFonts()
            geckoRuntime!!.settings.remoteDebuggingEnabled = false
            val cookiesValue = if (Settings.getInstance(context).shouldBlockCookies() &&
                Settings.getInstance(context).shouldBlockThirdPartyCookies()
            ) {
                GeckoRuntimeSettings.COOKIE_ACCEPT_NONE
            } else if (Settings.getInstance(context).shouldBlockThirdPartyCookies()) {
                GeckoRuntimeSettings.COOKIE_ACCEPT_FIRST_PARTY
            } else {
                GeckoRuntimeSettings.COOKIE_ACCEPT_ALL
            }
            geckoRuntime!!.settings.cookieBehavior = cookiesValue
        }

        private fun updateBlocking() {
            val settings = Settings.getInstance(context)

            var categories = 0
            if (settings.shouldBlockSocialTrackers()) {
                categories += GeckoSession.TrackingProtectionDelegate.CATEGORY_SOCIAL
            }
            if (settings.shouldBlockAdTrackers()) {
                categories += GeckoSession.TrackingProtectionDelegate.CATEGORY_AD
            }
            if (settings.shouldBlockAnalyticTrackers()) {
                categories += GeckoSession.TrackingProtectionDelegate.CATEGORY_ANALYTIC
            }
            if (settings.shouldBlockOtherTrackers()) {
                categories += GeckoSession.TrackingProtectionDelegate.CATEGORY_CONTENT
            }

            geckoRuntime!!.settings.trackingProtectionCategories = categories
        }

        @Suppress("ComplexMethod", "ReturnCount")
        private fun createContentDelegate(): GeckoSession.ContentDelegate {
            return object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String) {
                    webViewTitle = title
                    callback?.onTitleChanged(title)
                }

                override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                    if (fullScreen) {
                        callback?.onEnterFullScreen({ geckoSession.exitFullScreen() }, null)
                    } else {
                        callback?.onExitFullScreen()
                    }
                }

                override fun onContextMenu(
                    session: GeckoSession,
                    screenX: Int,
                    screenY: Int,
                    uri: String?,
                    elementType: Int,
                    elementSrc: String?
                ) {
                    if (elementSrc != null && uri != null &&
                        elementType == GeckoSession.ContentDelegate.ELEMENT_TYPE_IMAGE
                    ) {
                        callback?.onLongPress(IWebView.HitTarget(true, uri, true, elementSrc))
                    } else if (elementSrc != null && elementType == GeckoSession.ContentDelegate.ELEMENT_TYPE_IMAGE) {
                        callback?.onLongPress(IWebView.HitTarget(false, null, true, elementSrc))
                    } else if (uri != null) {
                        callback?.onLongPress(IWebView.HitTarget(true, uri, false, null))
                    }
                }

                override fun onExternalResponse(
                    session: GeckoSession,
                    response: GeckoSession.WebResponseInfo
                ) {
                    if (!AppConstants.supportsDownloadingFiles()) {
                        return
                    }

                    val scheme = Uri.parse(response.uri).scheme
                    if (scheme == null || scheme != "http" && scheme != "https") {
                        // We are ignoring everything that is not http or https. This is a limitation of
                        // Android's download manager. There's no reason to show a download dialog for
                        // something we can't download anyways.
                        Log.w(TAG, "Ignoring download from non http(s) URL: " + response.uri)
                        return
                    }

                    val download = Download(
                        response.uri, USER_AGENT,
                        response.filename, response.contentType, response.contentLength,
                        Environment.DIRECTORY_DOWNLOADS, response.filename
                    )
                    callback?.onDownloadStart(download)
                }

                override fun onCrash(session: GeckoSession) {
                    Log.i(TAG, "Crashed, opening new session")
                    SentryWrapper.captureGeckoCrash()
                    geckoSession.close()
                    geckoSession = createGeckoSession()
                    applySettingsAndSetDelegates()
                    geckoSession.open(geckoRuntime!!)
                    setSession(geckoSession)
                    geckoSession.loadUri(currentUrl)
                }

                override fun onFocusRequest(geckoSession: GeckoSession) {}

                override fun onCloseRequest(geckoSession: GeckoSession) {
                    // Ignore this callback
                }
            }
        }

        @Suppress("ComplexMethod")
        private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
            return object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession?, progress: Int) {
                    if (progress == PROGRESS_100) {
                        if (UrlUtils.isLocalizedContent(url)) {
                            // When the url is a localized content, then the page is secure
                            isSecure = true
                        }
                        callback?.onPageFinished(isSecure)
                    } else {
                        callback?.onProgress(progress)
                    }
                }

                override fun onPageStart(session: GeckoSession, url: String) {
                    callback?.onPageStarted(url)
                    callback?.resetBlockedTrackers()
                    isSecure = false
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (success) {
                        if (UrlUtils.isLocalizedContent(url)) {
                            // When the url is a localized content, then the page is secure
                            isSecure = true
                        }

                        callback?.onPageFinished(isSecure)
                    }
                }

                override fun onSecurityChange(
                    session: GeckoSession,
                    securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
                ) {
                    isSecure = securityInfo.isSecure

                    if (UrlUtils.isLocalizedContent(url)) {
                        // When the url is a localized content, then the page is secure
                        isSecure = true
                    }

                    callback?.onSecurityChanged(
                        isSecure,
                        securityInfo.host,
                        securityInfo.issuerOrganization
                    )
                }
            }
        }

        @Suppress("ComplexMethod")
        private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
            return object : GeckoSession.NavigationDelegate {
                override fun onLoadError(
                    session: GeckoSession?,
                    uri: String?,
                    category: Int,
                    error: Int
                ): GeckoResult<String> {
                    ErrorPages.createErrorPage(
                        context,
                        geckoErrorToErrorType(error)
                    ).apply {
                        return GeckoResult.fromValue(Base64.encodeToUriString(this))
                    }
                    return GeckoResult.fromValue(null)
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    uri: String,
                    target: Int,
                    flags: Int
                ): GeckoResult<Boolean>? {
                    val response: GeckoResult<Boolean> = GeckoResult()
                    val urlToURI = Uri.parse(uri)
                    // If this is trying to load in a new tab, just load it in the current one
                    if (target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                        geckoSession.loadUri(uri)
                        response.complete(true)
                    } else if (LocalizedContent.handleInternalContent(
                            uri,
                            this@GeckoWebView,
                            context
                        )
                    ) {
                        response.complete(true)
                    } else if (!UrlUtils.isSupportedProtocol(urlToURI.scheme) && callback != null &&
                        IntentUtils.handleExternalUri(context, this@GeckoWebView, uri)
                    ) {
                        response.complete(true)
                    } else {
                        callback?.onRequest(flags == GeckoSession.NavigationDelegate.LOAD_REQUEST_IS_USER_TRIGGERED)

                        // Otherwise allow the load to continue normally
                        response.complete(false)
                    }
                    return response
                }

                override fun onNewSession(
                    session: GeckoSession,
                    uri: String
                ): GeckoResult<GeckoSession>? {
                    // Prevent new sessions to be created from onLoadRequest
                    throw IllegalStateException()
                }

                override fun onLocationChange(session: GeckoSession, url: String) {
                    var desiredUrl = url
                    // Save internal data: urls we should override to present focus:about, focus:rights
                    if (isLoadingInternalUrl) {
                        if (currentUrl == LocalizedContent.URL_ABOUT) {
                            internalAboutData = desiredUrl
                        } else if (currentUrl == LocalizedContent.URL_RIGHTS) {
                            internalRightsData = desiredUrl
                        }
                        isLoadingInternalUrl = false
                        desiredUrl = currentUrl
                    }

                    // Check for internal data: urls to instead present focus:rights, focus:about
                    if (!TextUtils.isEmpty(internalAboutData) && internalAboutData == desiredUrl) {
                        desiredUrl = LocalizedContent.URL_ABOUT
                    } else if (!TextUtils.isEmpty(internalRightsData) && internalRightsData == desiredUrl) {
                        desiredUrl = LocalizedContent.URL_RIGHTS
                    }

                    currentUrl = desiredUrl
                    callback?.onURLChanged(desiredUrl)
                }

                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    this@GeckoWebView.canGoBack = canGoBack
                }

                override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                    this@GeckoWebView.canGoForward = canGoForward
                }
            }
        }

        private fun createTrackingProtectionDelegate(): GeckoSession.TrackingProtectionDelegate {
            return GeckoSession.TrackingProtectionDelegate { _, _, _ ->
                callback?.countBlockedTracker()
            }
        }

        private fun createPromptDelegate(): GeckoSession.PromptDelegate {
            return GeckoViewPrompt(context as Activity)
        }

        override fun canGoForward(): Boolean {
            return canGoForward
        }

        override fun canGoBack(): Boolean {
            return canGoBack
        }

        override fun restoreWebViewState(session: Session) {
            val stateData = session.savedWebViewState!!
            val savedSession = stateData.getParcelable<GeckoSession>(GECKO_SESSION)!!

            if (geckoSession != savedSession) {
                if (!restored) {
                    // Tab changed, we need to close the default session and restore our saved session
                    geckoSession.close()

                    geckoSession = savedSession
                    canGoBack = stateData.getBoolean(CAN_GO_BACK, false)
                    canGoForward = stateData.getBoolean(CAN_GO_FORWARD, false)
                    isSecure = stateData.getBoolean(IS_SECURE, false)
                    webViewTitle = stateData.getString(WEBVIEW_TITLE, null)
                    currentUrl = stateData.getString(CURRENT_URL, ABOUT_BLANK)
                    applySettingsAndSetDelegates()
                    if (!geckoSession.isOpen) {
                        geckoSession.open(geckoRuntime!!)
                    }
                    setSession(geckoSession)
                } else {
                    // App was backgrounded and restored;
                    // GV restored the GeckoSession itself, but we need to restore our variables
                    canGoBack = stateData.getBoolean(CAN_GO_BACK, false)
                    canGoForward = stateData.getBoolean(CAN_GO_FORWARD, false)
                    isSecure = stateData.getBoolean(IS_SECURE, false)
                    webViewTitle = stateData.getString(WEBVIEW_TITLE, null)
                    currentUrl = stateData.getString(CURRENT_URL, ABOUT_BLANK)
                    applySettingsAndSetDelegates()
                    restored = false
                }
            }
        }

        override fun onRestoreInstanceState(state: Parcelable?) {
            restored = true
            super.onRestoreInstanceState(state)
        }

        override fun saveWebViewState(session: Session) {
            val sessionBundle = Bundle()
            sessionBundle.putParcelable(GECKO_SESSION, geckoSession)
            sessionBundle.putBoolean(CAN_GO_BACK, canGoBack)
            sessionBundle.putBoolean(CAN_GO_FORWARD, canGoForward)
            sessionBundle.putBoolean(IS_SECURE, isSecure)
            sessionBundle.putString(WEBVIEW_TITLE, webViewTitle)
            sessionBundle.putString(CURRENT_URL, currentUrl)
            session.savedWebViewState = sessionBundle
        }

        override fun getTitle(): String? {
            return webViewTitle
        }

        override fun exitFullscreen() {
            geckoSession.exitFullScreen()
        }

        override fun findAllAsync(find: String) {
            finder.find(find, 0).then({ result ->
                if (result != null) {
                    findListener?.onFindResultReceived(result.current, result.total, true)
                }
                GeckoResult<Void>()
            }, { _ ->
                GeckoResult<Void>()
            })
        }

        override fun findNext(forward: Boolean) {
            finder.find(null, if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS)
                .then({ result ->
                    if (result != null) {
                        findListener?.onFindResultReceived(result.current, result.total, true)
                    }
                    GeckoResult<Void>()
                }, { _ ->
                    GeckoResult<Void>()
                })
        }

        override fun clearMatches() {
            finder.clear()
        }

        override fun setFindListener(findListener: IFindListener) {
            this.findListener = findListener
        }

        override fun loadData(
            baseURL: String,
            data: String,
            mimeType: String,
            encoding: String,
            historyURL: String
        ) {
            isLoadingInternalUrl = historyURL == LocalizedContent.URL_RIGHTS || historyURL ==
                    LocalizedContent.URL_ABOUT
            geckoSession.loadData(data.toByteArray(Charsets.UTF_8), mimeType)
            currentUrl = historyURL
        }

        private fun storeTelemetrySnapshots() {
            val storage = MobileMetricsPingStorage(context)
            if (!storage.shouldStoreMetrics()) return

            geckoRuntime!!.telemetry.getSnapshots(true).then({ value ->
                launch(IO) {
                    try {
                        value?.toJSONObject()?.also {
                            storage.save(it)
                        }
                    } catch (e: JSONException) {
                        Log.e("getSnapshots failed", e.message)
                    }
                }

                GeckoResult<Void>()
            }, { _ ->
                GeckoResult<Void>()
            })
        }

        override fun onDetachedFromWindow() {
            PreferenceManager.getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(this)
            releaseSession()
            super.onDetachedFromWindow()
        }

        companion object {
            private const val TAG = "GeckoWebView"
        }
    }

    companion object {
        @Volatile
        private var geckoRuntime: GeckoRuntime? = null
        private var internalAboutData: String? = null
        private var internalRightsData: String? = null
        private const val USER_AGENT =
            "Mozilla/5.0 (Android 8.1.0; Mobile; rv:60.0) Gecko/60.0 Firefox/60.0"
        const val PREF_FIRST_GECKO_RUN: String = "first_gecko_run"
        const val PROGRESS_100 = 100
        const val CAN_GO_BACK = "canGoBack"
        const val CAN_GO_FORWARD = "canGoForward"
        const val GECKO_SESSION = "geckoSession"
        const val IS_SECURE = "isSecure"
        const val WEBVIEW_TITLE = "webViewTitle"
        const val CURRENT_URL = "currentUrl"
        const val ABOUT_BLANK = "about:blank"

        /**
         * Provides an ErrorType corresponding to the error code provided.
         */
        @Suppress("ComplexMethod")
        internal fun geckoErrorToErrorType(@GeckoSession.NavigationDelegate.LoadError errorCode: Int) =
            when (errorCode) {
                NavigationDelegate.ERROR_UNKNOWN -> ErrorType.UNKNOWN
                NavigationDelegate.ERROR_SECURITY_SSL -> ErrorType.ERROR_SECURITY_SSL
                NavigationDelegate.ERROR_SECURITY_BAD_CERT -> ErrorType.ERROR_SECURITY_BAD_CERT
                NavigationDelegate.ERROR_NET_INTERRUPT -> ErrorType.ERROR_NET_INTERRUPT
                NavigationDelegate.ERROR_NET_TIMEOUT -> ErrorType.ERROR_NET_TIMEOUT
                NavigationDelegate.ERROR_CONNECTION_REFUSED -> ErrorType.ERROR_CONNECTION_REFUSED
                NavigationDelegate.ERROR_UNKNOWN_SOCKET_TYPE -> ErrorType.ERROR_UNKNOWN_SOCKET_TYPE
                NavigationDelegate.ERROR_REDIRECT_LOOP -> ErrorType.ERROR_REDIRECT_LOOP
                NavigationDelegate.ERROR_OFFLINE -> ErrorType.ERROR_OFFLINE
                NavigationDelegate.ERROR_PORT_BLOCKED -> ErrorType.ERROR_PORT_BLOCKED
                NavigationDelegate.ERROR_NET_RESET -> ErrorType.ERROR_NET_RESET
                NavigationDelegate.ERROR_UNSAFE_CONTENT_TYPE -> ErrorType.ERROR_UNSAFE_CONTENT_TYPE
                NavigationDelegate.ERROR_CORRUPTED_CONTENT -> ErrorType.ERROR_CORRUPTED_CONTENT
                NavigationDelegate.ERROR_CONTENT_CRASHED -> ErrorType.ERROR_CONTENT_CRASHED
                NavigationDelegate.ERROR_INVALID_CONTENT_ENCODING -> ErrorType.ERROR_INVALID_CONTENT_ENCODING
                NavigationDelegate.ERROR_UNKNOWN_HOST -> ErrorType.ERROR_UNKNOWN_HOST
                NavigationDelegate.ERROR_MALFORMED_URI -> ErrorType.ERROR_MALFORMED_URI
                NavigationDelegate.ERROR_UNKNOWN_PROTOCOL -> ErrorType.ERROR_UNKNOWN_PROTOCOL
                NavigationDelegate.ERROR_FILE_NOT_FOUND -> ErrorType.ERROR_FILE_NOT_FOUND
                NavigationDelegate.ERROR_FILE_ACCESS_DENIED -> ErrorType.ERROR_FILE_ACCESS_DENIED
                NavigationDelegate.ERROR_PROXY_CONNECTION_REFUSED -> ErrorType.ERROR_PROXY_CONNECTION_REFUSED
                NavigationDelegate.ERROR_UNKNOWN_PROXY_HOST -> ErrorType.ERROR_UNKNOWN_PROXY_HOST
                NavigationDelegate.ERROR_SAFEBROWSING_MALWARE_URI -> ErrorType.ERROR_SAFEBROWSING_MALWARE_URI
                NavigationDelegate.ERROR_SAFEBROWSING_UNWANTED_URI -> ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI
                NavigationDelegate.ERROR_SAFEBROWSING_HARMFUL_URI -> ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI
                NavigationDelegate.ERROR_SAFEBROWSING_PHISHING_URI -> ErrorType.ERROR_SAFEBROWSING_PHISHING_URI
                else -> ErrorType.UNKNOWN
            }
    }
}
