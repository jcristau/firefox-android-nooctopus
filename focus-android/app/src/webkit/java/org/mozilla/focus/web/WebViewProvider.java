/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import org.mozilla.focus.BuildConfig;
import org.mozilla.focus.R;

import org.mozilla.focus.utils.Settings;
import org.mozilla.focus.webkit.NestedWebView;
import org.mozilla.focus.webkit.TrackingProtectionWebViewClient;

/**
 * WebViewProvider for creating a WebKit based IWebVIew implementation.
 */
public class WebViewProvider {
    /**
     * Preload webview data. This allows the webview implementation to load resources and other data
     * it might need, in advance of intialising the view (at which time we are probably wanting to
     * show a website immediately).
     */
    public static void preload(final Context context) {
        TrackingProtectionWebViewClient.triggerPreload(context);
    }

    public static View create(Context context, AttributeSet attrs) {
        final WebkitView webkitView = new WebkitView(context, attrs);

        setupView(webkitView);
        configureSettings(context, webkitView.getSettings());

        return webkitView;
    }

    private static void setupView(WebView webView) {
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(true);
    }

    @SuppressLint("SetJavaScriptEnabled") // We explicitly want to enable JavaScript
    private static void configureSettings(Context context, WebSettings settings) {
        final Settings appSettings = new Settings(context);

        settings.setJavaScriptEnabled(true);

        // Enabling built in zooming shows the controls by default
        settings.setBuiltInZoomControls(true);

        // So we hide the controls after enabling zooming
        settings.setDisplayZoomControls(false);

        // Disable access to arbitrary local files by webpages - assets can still be loaded
        // via file:///android_asset/res, so at least error page images won't be blocked.
        settings.setAllowFileAccess(false);

        settings.setBlockNetworkImage(appSettings.shouldBlockImages());

        settings.setUserAgentString(buildUserAgentString(context, settings));
    }

    /**
     * Build the browser specific portion of the UA String, based on the webview's existing UA String.
     */
    @VisibleForTesting static String getUABrowserString(final String existingUAString, final String focusToken) {
        // Use the default WebView agent string here for everything after the platform, but insert
        // Focus in front of Chrome.
        // E.g. a default webview UA string might be:
        // Mozilla/5.0 (Linux; Android 7.1.1; Pixel XL Build/NOF26V; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/56.0.2924.87 Mobile Safari/537.36
        // And we reuse everything from AppleWebKit onwards, except for adding Focus.
        int start = existingUAString.indexOf("AppleWebKit");
        if (start == -1) {
            // I don't know if any devices don't include AppleWebKit, but given the diversity of Android
            // devices we should have a fallback: we search for the end of the platform String, and
            // treat the next token as the start:
            start = existingUAString.indexOf(")") + 2;

            // If this was located at the very end, then there's nothing we can do, so let's just
            // return focus:
            if (start >= existingUAString.length()) {
                return focusToken;
            }
        }

        final String[] tokens = existingUAString.substring(start).split(" ");

        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].startsWith("Chrome")) {
                tokens[i] = focusToken + " " + tokens[i];

                return TextUtils.join(" ", tokens);
            }
        }

        // If we didn't find a chrome token, we just append the focus token at the end:
        return TextUtils.join(" ", tokens) + focusToken;
    }

    private static String buildUserAgentString(final Context context, final WebSettings settings) {
        final StringBuilder uaBuilder = new StringBuilder();

        uaBuilder.append("Mozilla/5.0");

        // WebView by default includes "; wv" as part of the platform string, but we're a full browser
        // so we shouldn't include that.
        // Most webview based browsers (and chrome), include the device name AND build ID, e.g.
        // "Pixel XL Build/NOF26V", that seems unnecessary (and not great from a privacy perspective),
        // so we skip that too.
        uaBuilder.append(" (Linux; Android ").append(Build.VERSION.RELEASE).append(") ");

        final String existingWebViewUA = settings.getUserAgentString();

        final String appVersion;
        try {
            appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // This should be impossible - we should always be able to get information about ourselves:
            throw new IllegalStateException("Unable find package details for Focus", e);
        }

        final String focusToken = context.getResources().getString(R.string.useragent_appname) + "/" + appVersion;
        uaBuilder.append(getUABrowserString(existingWebViewUA, focusToken));

        return uaBuilder.toString();
    }

    private static class WebkitView extends NestedWebView implements IWebView {
        private Callback callback;
        private TrackingProtectionWebViewClient client;

        public WebkitView(Context context, AttributeSet attrs) {
            super(context, attrs);

            client = createWebViewClient();

            setWebViewClient(client);
            setWebChromeClient(createWebChromeClient());

            if (BuildConfig.DEBUG) {
                setWebContentsDebuggingEnabled(true);
            }
        }

        @Override
        public void setCallback(Callback callback) {
            this.callback = callback;
        }

        public void loadUrl(String url) {
            super.loadUrl(url);

            client.notifyCurrentURL(url);
        }

        @Override
        public void cleanup() {
            clearFormData();
            clearHistory();
            clearMatches();
            clearSslPreferences();
            clearCache(true);

            // We don't care about the callback - we just want to make sure cookies are gone
            CookieManager.getInstance().removeAllCookies(null);

            WebStorage.getInstance().deleteAllData();

            final WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(getContext());
            // It isn't entirely clear how this differs from WebView.clearFormData()
            webViewDatabase.clearFormData();
            webViewDatabase.clearHttpAuthUsernamePassword();
        }

        private TrackingProtectionWebViewClient createWebViewClient() {
            return new TrackingProtectionWebViewClient(getContext().getApplicationContext()) {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    if (callback != null) {
                        callback.onPageStarted(url);
                    }
                    super.onPageStarted(view, url, favicon);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (callback != null) {
                        callback.onPageFinished(view.getCertificate() != null);
                    }
                    super.onPageFinished(view, url);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    // shouldOverrideUrlLoading() is called for both the main frame, and iframes.
                    // That can get problematic if an iframe tries to load an unsupported URL.
                    // We then try to either handle that URL (ask to open relevant app), or extract
                    // a fallback URL from the intent (or worst case fall back to an error page). In the
                    // latter 2 cases, we explicitly open the fallback/error page in the main view.
                    // Websites probably shouldn't use unsupported URLs in iframes, but we do need to
                    // be careful to handle all valid schemes here to avoid redirecting due to such an iframe
                    // (e.g. we don't want to redirect to a data: URI just because an iframe shows such
                    // a URI).
                    // (The API 24+ version of shouldOverrideUrlLoading() lets us determine whether
                    // the request is for the main frame, and if it's not we could then completely
                    // skip the external URL handling.)
                    if ((!url.startsWith("http://")) &&
                            (!url.startsWith("https://")) &&
                            (!url.startsWith("file://")) &&
                            (!url.startsWith("data:"))) {
                        callback.handleExternalUrl(url);
                        return true;
                    }

                    return super.shouldOverrideUrlLoading(view, url);
                }
            };
        }

        private WebChromeClient createWebChromeClient() {
            return new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (callback != null) {
                        callback.onProgress(newProgress);
                    }
                }
            };
        }
    }
}
