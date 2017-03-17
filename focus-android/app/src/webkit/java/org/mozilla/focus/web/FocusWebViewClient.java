/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.web;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.mozilla.focus.webkit.ErrorPage;
import org.mozilla.focus.webkit.TrackingProtectionWebViewClient;

/**
 * WebViewClient layer that handles browser specific WebViewClient functionality, such as error pages
 * and external URL handling.
 */
public class FocusWebViewClient extends TrackingProtectionWebViewClient {
    final static String ERROR_PROTOCOL = "error:";

    public FocusWebViewClient(Context context) {
        super(context);
    }

    private IWebView.Callback callback;

    public void setCallback(IWebView.Callback callback) {
        this.callback = callback;
    }

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
                (!url.startsWith("data:")) &&
                (!url.startsWith("error:"))) {
            callback.handleExternalUrl(url);
            return true;
        }

        return super.shouldOverrideUrlLoading(view, url);
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

}
