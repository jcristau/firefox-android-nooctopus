/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.webkit;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.mozilla.focus.R;
import org.mozilla.focus.webkit.matcher.UrlMatcher;

public class TrackingProtectionWebViewClient extends WebViewClient {

    final static String ERROR_PROTOCOL = "error:";

    private String currentPageURL;

    private static volatile UrlMatcher MATCHER;

    public static void triggerPreload(final Context context) {
        // Only trigger loading if MATCHER is null. (If it's null, MATCHER could already be loading,
        // but we don't have any way of being certain - and there's no real harm since we're not
        // blocking anything else.)
        if (MATCHER == null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    // We don't need the result here - we just want to trigger loading
                    getMatcher(context);
                    return null;
                }
            }.execute();
        }
    }

    @WorkerThread private static synchronized UrlMatcher getMatcher(final Context context) {
        if (MATCHER == null) {
            MATCHER = UrlMatcher.loadMatcher(context, R.raw.blocklist, new int[] { R.raw.google_mapping }, R.raw.entitylist);
        }
        return MATCHER;
    }

    public TrackingProtectionWebViewClient(final Context context) {
        // Hopefully we have loaded background data already. We call triggerPreload() to try to trigger
        // background loading of the lists as early as possible.
        triggerPreload(context);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // shouldInterceptRequest() might be called _before_ onPageStarted or shouldOverrideUrlLoading
        // are called (this happens when the webview is first shown). However we are notified of the URL
        // via notifyCurrentURL in that case.
        final String scheme = request.getUrl().getScheme();

        if (!request.isForMainFrame() &&
                !scheme.equals("http") && !scheme.equals("https")) {
            // Block any malformed non-http(s) URIs. Webkit will already ignore things like market: URLs,
            // but not in all cases (malformed market: URIs, such as market:://... will still end up here).
            // (Note: data: URIs are automatically handled by webkit, and won't end up here either.)
            // file:// URIs are disabled separately by setting WebSettings.setAllowFileAccess()
            return new WebResourceResponse(null, null, null);
        }

        final UrlMatcher matcher = getMatcher(view.getContext());

        // Don't block the main frame from being loaded. This also protects against cases where we
        // open a link that redirects to another app (e.g. to the play store).
        if ((!request.isForMainFrame()) &&
                matcher.matches(request.getUrl().toString(), currentPageURL)) {
            return new WebResourceResponse(null, null, null);
        }

        return super.shouldInterceptRequest(view, request);
    }

    /**
     * Notify that the user has requested a new URL. This MUST be called before loading a new URL
     * into the webview: sometimes content requests might begin before the WebView itself notifies
     * the WebViewClient via onpageStarted/shouldOverrideUrlLoading. If we don't know the current page
     * URL then the entitylist whitelists might not work if we're trying to load an explicitly whitelisted
     * page.
     */
    public void notifyCurrentURL(final String url) {
        currentPageURL = url;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        currentPageURL = url;

        super.onPageStarted(view, url, favicon);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        currentPageURL = url;

        return super.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public void onReceivedError(final WebView webView, int errorCode,
                                final String description, String failingUrl) {

        // This is a hack: onReceivedError(WebView, WebResourceRequest, WebResourceError) is API 23+ only,
        // - the WebResourceRequest would let us know if the error affects the main frame or not. As a workaround
        // we just check whether the failing URL is the current URL, which is enough to detect an error
        // in the main frame.

        // WebView swallows odd pages and only sends an error (i.e. it doesn't go through the usual
        // shouldOverrideUrlLoading), so we need to handle special pages here:
        // about: urls are even more odd: webview doesn't tell us _anything_, hence the use of
        // a different prefix:
        if (failingUrl.startsWith(ERROR_PROTOCOL)) {
            // format: error:<error_code>
            final int errorCodePosition = ERROR_PROTOCOL.length();
            final String errorCodeString = failingUrl.substring(errorCodePosition);

            int desiredErrorCode;
            try {
                desiredErrorCode = Integer.parseInt(errorCodeString);

                if (!ErrorPage.supportsErrorCode(desiredErrorCode)) {
                    // I don't think there's any good way of showing an error if there's an error
                    // in requesting an error page?
                    desiredErrorCode = WebViewClient.ERROR_BAD_URL;
                }
            } catch (final NumberFormatException e) {
                desiredErrorCode = WebViewClient.ERROR_BAD_URL;
            }
            ErrorPage.loadErrorPage(webView, failingUrl, desiredErrorCode);
            return;
        }


        // The API 23+ version also return a *slightly* more usable description, via WebResourceError.getError();
        // e.g.. "There was a network error.", whereas this version provides things like "net::ERR_NAME_NOT_RESOLVED"
        if (failingUrl.equals(currentPageURL) &&
                ErrorPage.supportsErrorCode(errorCode)) {
            ErrorPage.loadErrorPage(webView, currentPageURL, errorCode);
            return;
        }

        super.onReceivedError(webView, errorCode, description, failingUrl);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();

        // Webkit can try to load the favicon for a bad page when you set a new URL. If we then
        // loadErrorPage() again, webkit tries to load the favicon again. We end up in onReceivedSSlError()
        // again, and we get an infinite loop of reloads (we also erroneously show the favicon URL
        // in the toolbar, but that's less noticeable). Hence we check whether this error is from
        // the desired page, or a page resource:
        if (error.getUrl().equals(currentPageURL)) {
            ErrorPage.loadErrorPage(view, error.getUrl(), WebViewClient.ERROR_FAILED_SSL_HANDSHAKE);
        }
    }
}
