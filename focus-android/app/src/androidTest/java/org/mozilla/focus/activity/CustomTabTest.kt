/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.mozilla.focus.activity

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.ActivityTestRule
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.focus.activity.robots.browserScreen
import org.mozilla.focus.activity.robots.customTab
import org.mozilla.focus.activity.robots.homeScreen
import org.mozilla.focus.activity.robots.searchScreen
import org.mozilla.focus.helpers.FeatureSettingsHelper
import org.mozilla.focus.helpers.TestHelper.createCustomTabIntent
import org.mozilla.focus.helpers.TestHelper.createMockResponseFromAsset
import org.mozilla.focus.helpers.TestHelper.mDevice
import org.mozilla.focus.helpers.TestHelper.waitingTime
import org.mozilla.focus.testAnnotations.SmokeTest
import java.io.IOException

@RunWith(AndroidJUnit4ClassRunner::class)
class CustomTabTest {
    private lateinit var webServer: MockWebServer
    private val MENU_ITEM_LABEL = "TestItem4223"
    private val ACTION_BUTTON_DESCRIPTION = "TestButton"
    private val TEST_PAGE_HEADER_TEXT = "focus test page"
    private val featureSettingsHelper = FeatureSettingsHelper()

    @get: Rule
    val activityTestRule = ActivityTestRule(
        IntentReceiverActivity::class.java, true, false
    )

    @Before
    fun setUp() {
        featureSettingsHelper.setCfrForTrackingProtectionEnabled(false)
        featureSettingsHelper.setNumberOfTabsOpened(4)
        webServer = MockWebServer()
        webServer.enqueue(createMockResponseFromAsset("plain_test.html"))
        webServer.enqueue(createMockResponseFromAsset("tab1.html"))
        webServer.start()
    }

    @After
    fun tearDown() {
        try {
            webServer.shutdown()
        } catch (e: IOException) {
            throw AssertionError("Could not stop web server", e)
        }
        featureSettingsHelper.resetAllFeatureFlags()
    }

    @Ignore("Crashing, see: https://github.com/mozilla-mobile/focus-android/issues/6437")
    @SmokeTest
    @Test
    fun testCustomTabUI() {
        val customTabPage = webServer.url("plain_test.html").toString()
        val customTabActivity = launchActivity<IntentReceiverActivity>(createCustomTabIntent(customTabPage))

        browserScreen {
            progressBar.waitUntilGone(waitingTime)
            verifyPageContent(TEST_PAGE_HEADER_TEXT)
            verifyPageURL(customTabPage)
        }

        customTab {
            verifyCustomTabActionButton(ACTION_BUTTON_DESCRIPTION)
            verifyShareButtonIsDisplayed()
            openCustomTabMenu()
            verifyTheStandardMenuItems()
            verifyCustomMenuItem(MENU_ITEM_LABEL)
            // Close the menu and close the tab
            mDevice.pressBack()
            closeCustomTab()
            assertEquals(Lifecycle.State.DESTROYED, customTabActivity.state)
        }
    }

    @SmokeTest
    @Test
    @Ignore("Crashing, see: https://github.com/mozilla-mobile/focus-android/issues/5283")
    fun openCustomTabInFocusTest() {
        val browserPage = webServer.url("plain_test.html").toString()
        val customTabPage = webServer.url("tab1.html").toString()

        launchActivity<IntentReceiverActivity>()
        homeScreen {
            skipFirstRun()
        }

        searchScreen {
        }.loadPage(browserPage) {
            verifyPageURL(browserPage)
        }

        launchActivity<IntentReceiverActivity>(createCustomTabIntent(customTabPage))
        customTab {
            progressBar.waitUntilGone(waitingTime)
            verifyPageURL(customTabPage)
            openCustomTabMenu()
        }.clickOpenInFocusButton() {
        }

        browserScreen {
            verifyPageURL(customTabPage)
            mDevice.pressBack()
            verifyPageURL(browserPage)
        }
    }
}
