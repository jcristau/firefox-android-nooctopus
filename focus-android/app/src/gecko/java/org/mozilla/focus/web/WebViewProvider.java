/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.web;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import org.mozilla.focus.session.Session;
import org.mozilla.gecko.GeckoView;
import org.mozilla.gecko.GeckoSession;
import org.mozilla.gecko.GeckoSession.*;
import org.mozilla.gecko.GeckoSessionSettings;

/**
 * WebViewProvider implementation for creating a Gecko based implementation of IWebView.
 */
public class WebViewProvider {
    public static void preload(final Context context) {
        GeckoSession.preload(context);
    }

    public static View create(Context context, AttributeSet attrs) {
        final GeckoView geckoView = new GeckoWebView(context, attrs);

        return geckoView;
    }

    public static void performCleanup(final Context context) {
        // Nothing: does Gecko need extra private mode cleanup?
    }

    public static void performNewBrowserSessionCleanup() {
        // Nothing: a WebKit work-around.
    }

    public static class GeckoWebView extends NestedGeckoView implements IWebView {
        private Callback callback;
        private String currentUrl = "about:blank";
        private boolean canGoBack;
        private boolean canGoForward;
        private boolean isSecure;
        private GeckoSession geckoSession;
        private String webViewTitle;

        public GeckoWebView(Context context, AttributeSet attrs) {
            super(context, attrs);


            final GeckoSessionSettings settings = new GeckoSessionSettings();
            settings.setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, false);
            settings.setBoolean(GeckoSessionSettings.USE_PRIVATE_MODE, true);
            settings.setBoolean(GeckoSessionSettings.USE_TRACKING_PROTECTION, true);

            geckoSession = new GeckoSession(settings);
            geckoSession.setContentListener(createContentListener());
            geckoSession.setProgressListener(createProgressListener());
            geckoSession.setNavigationListener(createNavigationListener());

            // TODO: set long press listener, call through to callback.onLinkLongPress()
        }

        @Override
        public void setCallback(Callback callback) {
            this.callback =  callback;
        }

        @Override
        public void onPause() {

        }

        @Override
        public void goBack() {

        }

        @Override
        public void goForward() {

        }

        @Override
        public void reload() {

        }

        @Override
        public void destroy() {

        }

        @Override
        public void onResume() {

        }

        @Override
        public void stopLoading() {
            geckoSession.stop();
            callback.onPageFinished(isSecure);
        }

        @Override
        public String getUrl() {
            return currentUrl;
        }

        @Override
        public void loadUrl(final String url) {
            currentUrl = url;
            geckoSession.loadUri(currentUrl);
            callback.onProgress(10);
        }

        @Override
        public void cleanup() {
            // We're running in a private browsing window, so nothing to do
        }

        @Override
        public void setBlockingEnabled(boolean enabled) {
//            getSettings().setBoolean(GeckoSessionSettings.USE_TRACKING_PROTECTION, enabled);
        }

        private ContentListener createContentListener() {
            return new ContentListener() {
                @Override
                public void onTitleChange(GeckoSession session, String title) {
                    webViewTitle = title;
                }

                @Override
                public void onFullScreen(GeckoSession session, boolean fullScreen) {
                    if (fullScreen) {
                        callback.onEnterFullScreen(new FullscreenCallback() {
                            @Override
                            public void fullScreenExited() {
                                geckoSession.exitFullScreen();
                            }
                        }, null);
                    } else {
                        callback.onExitFullScreen();
                    }
                }

                @Override
                public void onContextMenu(GeckoSession session, int screenX, int screenY, String uri, String elementSrc) {
                }
            };
        }

        private ProgressListener createProgressListener() {
            return new ProgressListener() {
                @Override
                public void onPageStart(GeckoSession session, String url) {
                    if (callback != null) {
                        callback.onPageStarted(url);
                        callback.onProgress(25);
                        isSecure = false;
                    }
                }

                @Override
                public void onPageStop(GeckoSession session, boolean success) {
                    if (callback != null) {
                        if (success) {
                            callback.onProgress(100);
                            callback.onPageFinished(isSecure);
                        }
                    }
                }

                @Override
                public void onSecurityChange(GeckoSession session,
                                             GeckoSession.ProgressListener.SecurityInformation securityInfo) {
                    // TODO: Split current onPageFinished() callback into two: page finished + security changed
                    isSecure = securityInfo.isSecure;
                }
            };
        }

        private NavigationListener createNavigationListener() {
            return new NavigationListener() {
                public void onLocationChange(GeckoSession session, String url) {
                    currentUrl = url;
                    System.out.println(currentUrl);
                    if (callback != null) {
                        callback.onURLChanged(url);
                    }
                }

                public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                    GeckoWebView.this.canGoBack =  canGoBack;
                }

                public void onCanGoForward(GeckoSession session, boolean canGoForward) {
                    GeckoWebView.this.canGoForward = canGoForward;
                }

                @Override
                public boolean onLoadUri(GeckoSession session, String uri, GeckoSession.NavigationListener.TargetWindow where) {
                    // If this is trying to load in a new tab, just load it in the current one
                    if (where == TargetWindow.NEW) {
                        geckoSession.loadUri(uri);
                        return true;
                    }

                    // Otherwise allow the load to continue normally
                    return false;
                }
            };
        }

        @Override
        public boolean canGoForward() {
            return canGoForward;
        }

        @Override
        public boolean canGoBack() {
            return canGoBack;
        }

        @Override
        public void restoreWebViewState(Session session) {
            // TODO: restore navigation history, and reopen previously opened page
        }

        @Override
        public void saveWebViewState(@NonNull Session session) {
            // TODO: save anything needed for navigation history restoration.
        }

        @Override
        public String getTitle() {
            return webViewTitle;
        }

        @Override
        public void exitFullscreen() {
            geckoSession.exitFullScreen();
        }
    }
}
