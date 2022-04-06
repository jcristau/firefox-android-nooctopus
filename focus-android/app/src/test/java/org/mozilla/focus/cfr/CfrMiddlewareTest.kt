/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.cfr

import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SecurityInfoState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.libstate.ext.waitUntilIdle
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations
import org.mozilla.focus.Components
import org.mozilla.focus.state.AppState
import org.mozilla.focus.state.AppStore
import org.mozilla.focus.state.Screen
import org.mozilla.focus.utils.Features
import org.mozilla.focus.utils.Settings
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CfrMiddlewareTest {
    private lateinit var cfrMiddleware: CfrMiddleware
    private lateinit var browserStore: BrowserStore
    private lateinit var appStore: AppStore
    private lateinit var settings: Settings

    @Mock
    private lateinit var components: Components

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        cfrMiddleware = CfrMiddleware(components)
        browserStore = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(cfrMiddleware)
        )
        appStore = AppStore(
            AppState(
                screen = Screen.Home,
                showTrackingProtectionCfr = false,
            )
        )
        settings = Settings(testContext)

        doReturn(appStore).`when`(components).appStore
        doReturn(settings).`when`(components).settings
    }

    @Test
    fun `GIVEN erase cfr is enabled and tracking protection cfr is not displayed WHEN AddTabAction is intercepted THEN the numberOfTabsOpened is increased`() {
        if (Features.IS_ERASE_CFR_ENABLED) {
            browserStore.dispatch(TabListAction.AddTabAction(createTab())).joinBlocking()

            assertEquals(1, components.settings.numberOfTabsOpened)
        }
    }

    @Test
    fun `GIVEN erase cfr is enabled and tracking protection cfr is not displayed WHEN AddTabAction is intercepted for the third time THEN showEraseTabsCfr is changed to true`() {
        if (Features.IS_ERASE_CFR_ENABLED) {
            browserStore.dispatch(TabListAction.AddTabAction(createTab(tabId = 1))).joinBlocking()
            browserStore.dispatch(TabListAction.AddTabAction(createTab(tabId = 2))).joinBlocking()
            browserStore.dispatch(TabListAction.AddTabAction(createTab(tabId = 3))).joinBlocking()
            appStore.waitUntilIdle()

            assertTrue(appStore.state.showEraseTabsCfr)
        }
    }

    @Test
    fun `GIVEN shouldShowCfrForTrackingProtection is true WHEN UpdateSecurityInfoAction is intercepted THEN showTrackingProtectionCfr is changed to true`() {
        if (Features.IS_TRACKING_PROTECTION_CFR_ENABLED) {
            val updateSecurityInfoAction = ContentAction.UpdateSecurityInfoAction(
                "1",
                SecurityInfoState(
                    secure = true,
                    host = "test.org",
                    issuer = "Test"
                )
            )
            browserStore.dispatch(updateSecurityInfoAction).joinBlocking()
            appStore.waitUntilIdle()

            assertTrue(appStore.state.showTrackingProtectionCfr)
        }
    }

    @Test
    fun `GIVEN insecure tab WHEN UpdateSecurityInfoAction is intercepted THEN showTrackingProtectionCfr is not changed to true`() {
        if (Features.IS_TRACKING_PROTECTION_CFR_ENABLED) {
            val insecureTab = createTab(isSecure = false)
            val updateSecurityInfoAction = ContentAction.UpdateSecurityInfoAction(
                "1",
                SecurityInfoState(
                    secure = false,
                    host = "test.org",
                    issuer = "Test"
                )
            )

            browserStore.dispatch(TabListAction.AddTabAction(insecureTab)).joinBlocking()
            browserStore.dispatch(updateSecurityInfoAction).joinBlocking()
            appStore.waitUntilIdle()

            assertFalse(appStore.state.showTrackingProtectionCfr)
        }
    }

    @Test
    fun `GIVEN mozilla tab WHEN UpdateSecurityInfoAction is intercepted THEN showTrackingProtectionCfr is not changed to true`() {
        if (Features.IS_TRACKING_PROTECTION_CFR_ENABLED) {
            val mozillaTab = createTab(url = "https://www.mozilla.org")
            val updateSecurityInfoAction = ContentAction.UpdateSecurityInfoAction(
                "1",
                SecurityInfoState(
                    secure = true,
                    host = "test.org",
                    issuer = "Test"
                )
            )
            browserStore.dispatch(TabListAction.AddTabAction(mozillaTab)).joinBlocking()
            browserStore.dispatch(updateSecurityInfoAction).joinBlocking()
            appStore.waitUntilIdle()

            assertFalse(appStore.state.showTrackingProtectionCfr)
        }
    }

    private fun createTab(
        tabUrl: String = "https://www.test.org",
        tabId: Int = 1,
        isSecure: Boolean = true
    ): TabSessionState {
        val tab = createTab(tabUrl, id = tabId.toString())
        return tab.copy(
            content = tab.content.copy(securityInfo = SecurityInfoState(secure = isSecure))
        )
    }
}
