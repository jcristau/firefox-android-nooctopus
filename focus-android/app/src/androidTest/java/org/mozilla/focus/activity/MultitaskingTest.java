/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.activity;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.web.webdriver.Locator;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.focus.R;
import org.mozilla.focus.helpers.SessionLoadedIdlingResource;
import org.mozilla.focus.helpers.TestHelper;
import org.mozilla.focus.session.SessionManager;
import org.mozilla.focus.utils.AppConstants;
import org.mozilla.focus.web.IWebView;

import okhttp3.mockwebserver.MockWebServer;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static android.support.test.espresso.web.sugar.Web.onWebView;
import static android.support.test.espresso.web.webdriver.DriverAtoms.findElement;
import static android.support.test.espresso.web.webdriver.DriverAtoms.getText;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.mozilla.focus.fragment.FirstrunFragment.FIRSTRUN_PREF;
import static org.mozilla.focus.helpers.EspressoHelper.navigateToMockWebServer;
import static org.mozilla.focus.helpers.EspressoHelper.onFloatingEraseButton;
import static org.mozilla.focus.helpers.EspressoHelper.onFloatingTabsButton;
import static org.mozilla.focus.helpers.TestHelper.createMockResponseFromAsset;
import static org.mozilla.focus.helpers.WebViewFakeLongPress.injectHitTarget;

/**
 * Open multiple sessions and verify that the UI looks like it should.
 */
@RunWith(AndroidJUnit4.class)
public class MultitaskingTest {

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule
            = new ActivityTestRule<MainActivity>(MainActivity.class) {

        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            Context appContext = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext()
                    .getApplicationContext();

            // This test is for webview only. Debug is defaulted to Webview, and Klar is used for GV testing.
            org.junit.Assume.assumeFalse(AppConstants.INSTANCE.isGeckoBuild());
            org.junit.Assume.assumeFalse(AppConstants.INSTANCE.isKlarBuild());

            PreferenceManager.getDefaultSharedPreferences(appContext)
                    .edit()
                    .putBoolean(FIRSTRUN_PREF, true)
                    .apply();
        }
    };

    private MockWebServer webServer;
    private SessionLoadedIdlingResource loadingIdlingResource;

    @Before
    public void startWebServer() throws Exception {
        webServer = new MockWebServer();

        webServer.enqueue(createMockResponseFromAsset("tab1.html"));
        webServer.enqueue(createMockResponseFromAsset("tab2.html"));
        webServer.enqueue(createMockResponseFromAsset("tab3.html"));
        webServer.enqueue(createMockResponseFromAsset("tab2.html"));

        webServer.start();

        loadingIdlingResource = new SessionLoadedIdlingResource();
        IdlingRegistry.getInstance().register(loadingIdlingResource);
    }

    @After
    public void stopWebServer() throws Exception {
        IdlingRegistry.getInstance().unregister(loadingIdlingResource);
        mActivityTestRule.getActivity().finishAndRemoveTask();
        webServer.shutdown();
    }

    @Test
    public void testVisitingMultipleSites() {
        {
            // Load website: Erase button visible, Tabs button not
            TestHelper.inlineAutocompleteEditText.waitForExists(TestHelper.waitingTime);
            navigateToMockWebServer(webServer, "tab1.html");

            checkTabIsLoaded("Tab 1");

            onFloatingEraseButton()
                    .check(matches(isDisplayed()));
            onFloatingTabsButton()
                    .check(matches(not(isDisplayed())));
        }

        {
            // Open link in new tab: Erase button hidden, Tabs button visible
            longPressLink("tab2", "Tab 2", "tab2.html");
            openInNewTab();

            // verify Tab 1 is still on foreground
            checkTabIsLoaded("Tab 1");
            onFloatingEraseButton()
                    .check(matches(not(isDisplayed())));
            onFloatingTabsButton()
                    .check(matches(isDisplayed()))
                    .check(matches(withContentDescription(is("Tabs open: 2"))));
        }

        {
            // Open link in new tab: Tabs button updated, Erase button still hidden

            longPressLink("tab3", "Tab 3", "tab3.html");
            openInNewTab();

            // verify Tab 1 is still on foreground
            checkTabIsLoaded("Tab 1");
            onFloatingEraseButton()
                    .check(matches(not(isDisplayed())));
            onFloatingTabsButton()
                    .check(matches(isDisplayed()))
                    .check(matches(withContentDescription(is("Tabs open: 3"))));
        }

        {
            // Open tabs tray and switch to second tab.
            onFloatingTabsButton()
                    .perform(click());

            // Tab title would not have the port number, since the site isn't visited yet
            onView(withText(webServer.getHostName() + "/tab2.html"))
                    .perform(click());

            checkTabIsLoaded("Tab 2");
        }

        {
            // Remove all tabs via the menu
            onFloatingTabsButton()
                    .perform(click());

            onView(withText(R.string.tabs_tray_action_erase))
                    .check(matches(isDisplayed()))
                    .perform(click());

            // Now on main view
            assertTrue(TestHelper.inlineAutocompleteEditText.waitForExists(TestHelper.waitingTime));
            assertFalse(SessionManager.getInstance().hasSession());
        }
    }

    private void checkTabIsLoaded(String title) {
        onWebView()
                .withElement(findElement(Locator.ID, "content"))
                .check(webMatches(getText(), equalTo(title)));
    }

    private void longPressLink(String id, String label, String path) {
        onWebView()
                .withElement(findElement(Locator.ID, id))
                .check(webMatches(getText(), equalTo(label)));

        simulateLinkLongPress(path);
    }

    // Webview only method
    private void simulateLinkLongPress(String path) {
        onView(withId(R.id.webview))
                .perform(injectHitTarget(
                        new IWebView.HitTarget(true, webServer.url(path).toString(), false, null)));
    }

    private void openInNewTab() {
        onView(withText(R.string.contextmenu_open_in_new_tab))
                .perform(click());
        checkNewTabPopup();
    }

    private void checkNewTabPopup() {
        TestHelper.mDevice.wait(Until.findObject(
                By.res(TestHelper.getAppName(), "snackbar_text")), 5000);

        TestHelper.mDevice.wait(Until.gone(
                By.res(TestHelper.getAppName(), "snackbar_text")), 5000);
    }
}
