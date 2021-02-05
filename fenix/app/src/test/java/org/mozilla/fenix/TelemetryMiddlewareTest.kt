/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import mozilla.components.browser.session.engine.EngineMiddleware
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.LoadRequestState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.service.glean.testing.GleanTestRule
import mozilla.components.support.base.android.Clock
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.test.rule.MainCoroutineRule
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import org.mozilla.fenix.search.telemetry.ads.AdsTelemetry
import org.mozilla.fenix.utils.Settings
import org.mozilla.fenix.GleanMetrics.Engine as EngineMetrics

@RunWith(FenixRobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class TelemetryMiddlewareTest {

    private lateinit var store: BrowserStore
    private lateinit var settings: Settings
    private lateinit var telemetryMiddleware: TelemetryMiddleware
    private lateinit var metrics: MetricController
    private lateinit var adsTelemetry: AdsTelemetry
    private val testDispatcher = TestCoroutineDispatcher()

    @get:Rule
    val coroutinesTestRule = MainCoroutineRule(testDispatcher)

    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())

    private val clock = FakeClock()

    @Before
    fun setUp() {
        Clock.delegate = clock

        settings = Settings(testContext)
        metrics = mockk(relaxed = true)
        adsTelemetry = mockk()
        telemetryMiddleware = TelemetryMiddleware(
            settings,
            adsTelemetry,
            metrics
        )
        store = BrowserStore(
            middleware = listOf(telemetryMiddleware) + EngineMiddleware.create(engine = mockk(), sessionLookup = { null }),
            initialState = BrowserState()
        )
    }

    @After
    fun tearDown() {
        Clock.reset()
    }

    @Test
    fun `WHEN a tab is added THEN the open tab count is updated`() {
        assertEquals(0, settings.openTabsCount)

        store.dispatch(TabListAction.AddTabAction(createTab("https://mozilla.org"))).joinBlocking()
        assertEquals(1, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }
    }

    @Test
    fun `WHEN a private tab is added THEN the open tab count is not updated`() {
        assertEquals(0, settings.openTabsCount)

        store.dispatch(TabListAction.AddTabAction(createTab("https://mozilla.org", private = true))).joinBlocking()
        assertEquals(0, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveNoOpenTabs) }
    }

    @Test
    fun `WHEN multiple tabs are added THEN the open tab count is updated`() {
        assertEquals(0, settings.openTabsCount)
        store.dispatch(
            TabListAction.AddMultipleTabsAction(listOf(
                createTab("https://mozilla.org"),
                createTab("https://firefox.com"))
            )
        ).joinBlocking()

        assertEquals(2, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }
    }

    @Test
    fun `WHEN a tab is removed THEN the open tab count is updated`() {
        store.dispatch(
            TabListAction.AddMultipleTabsAction(listOf(
                createTab(id = "1", url = "https://mozilla.org"),
                createTab(id = "2", url = "https://firefox.com"))
            )
        ).joinBlocking()
        assertEquals(2, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }

        store.dispatch(TabListAction.RemoveTabAction("1")).joinBlocking()
        assertEquals(1, settings.openTabsCount)
        verify(exactly = 2) { metrics.track(Event.HaveOpenTabs) }
    }

    @Test
    fun `WHEN all tabs are removed THEN the open tab count is updated`() {
        store.dispatch(
            TabListAction.AddMultipleTabsAction(listOf(
                createTab("https://mozilla.org"),
                createTab("https://firefox.com"))
            )
        ).joinBlocking()
        assertEquals(2, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }

        store.dispatch(TabListAction.RemoveAllTabsAction).joinBlocking()
        assertEquals(0, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveNoOpenTabs) }
    }

    @Test
    fun `WHEN all normal tabs are removed THEN the open tab count is updated`() {
        store.dispatch(
            TabListAction.AddMultipleTabsAction(listOf(
                createTab("https://mozilla.org"),
                createTab("https://firefox.com"),
                createTab("https://getpocket.com", private = true))
            )
        ).joinBlocking()
        assertEquals(2, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }

        store.dispatch(TabListAction.RemoveAllNormalTabsAction).joinBlocking()
        assertEquals(0, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveNoOpenTabs) }
    }

    @Test
    fun `WHEN tabs are restored THEN the open tab count is updated`() {
        assertEquals(0, settings.openTabsCount)
        val tabsToRestore = listOf(
            createTab("https://mozilla.org"),
            createTab("https://firefox.com")
        )

        store.dispatch(TabListAction.RestoreAction(tabsToRestore)).joinBlocking()
        assertEquals(2, settings.openTabsCount)
        verify(exactly = 1) { metrics.track(Event.HaveOpenTabs) }
    }

    @Test
    fun `GIVEN a page is loading WHEN loading is complete THEN we record a UriOpened event`() {
        val tab = createTab(id = "1", url = "https://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify(exactly = 0) { metrics.track(Event.UriOpened) }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, false)).joinBlocking()
        verify(exactly = 1) { metrics.track(Event.UriOpened) }
    }

    @Test
    fun `GIVEN a private page is loading WHEN loading is complete THEN we never record a UriOpened event`() {
        val tab = createTab(id = "1", url = "https://mozilla.org", private = true)
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify(exactly = 0) { metrics.track(Event.UriOpened) }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, false)).joinBlocking()
        verify(exactly = 0) { metrics.track(Event.UriOpened) }
    }

    @Test
    fun `GIVEN a load request WHEN no redirect chain is available THEN a new chain will be created`() {
        val tab = createTab(id = "1", url = "http://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadRequestAction(
            tab.id, LoadRequestState(tab.content.url, true, true))
        ).joinBlocking()

        assertNull(telemetryMiddleware.redirectChains[tab.id])

        store.dispatch(ContentAction.UpdateLoadRequestAction(
            tab.id, LoadRequestState("https://mozilla.org", true, true))
        ).joinBlocking()

        assertNotNull(telemetryMiddleware.redirectChains[tab.id])
        assertEquals(tab.content.url, telemetryMiddleware.redirectChains[tab.id]!!.root)
    }

    @Test
    fun `GIVEN a load request WHEN a redirect chain is available THEN url is added to chain`() {
        val tab = createTab(id = "1", url = "http://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadRequestAction(
            tab.id, LoadRequestState("https://mozilla.org", true, true))
        ).joinBlocking()

        assertNotNull(telemetryMiddleware.redirectChains[tab.id])
        assertEquals(tab.content.url, telemetryMiddleware.redirectChains[tab.id]!!.root)
        assertEquals("https://mozilla.org", telemetryMiddleware.redirectChains[tab.id]!!.chain.first())
    }

    @Test
    fun `GIVEN a location update WHEN no redirect chain is available THEN no ads telemetry is recorded`() {
        val tab = createTab(id = "1", url = "http://mozilla.org")
        store.dispatch(ContentAction.UpdateUrlAction(tab.id, "http://mozilla.org")).joinBlocking()
        verify(exactly = 0) { adsTelemetry.trackAdClickedMetric(any(), any()) }
    }

    @Test
    fun `GIVEN a location update WHEN a redirect chain is available THEN ads telemetry is recorded`() {
        val tab = createTab(id = "1", url = "http://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadRequestAction(
            tab.id, LoadRequestState("https://mozilla.org", true, true))
        ).joinBlocking()

        store.dispatch(ContentAction.UpdateUrlAction(tab.id, "https://mozilla.org")).joinBlocking()
        verify(exactly = 1) { adsTelemetry.trackAdClickedMetric(tab.content.url, listOf("https://mozilla.org")) }
    }

    @Test
    fun `GIVEN a location update WHEN ads telemetry is recorded THEN redirect chain is reset`() {
        val tab = createTab(id = "1", url = "http://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadRequestAction(
            tab.id, LoadRequestState("https://mozilla.org", true, true))
        ).joinBlocking()

        assertNotNull(telemetryMiddleware.redirectChains[tab.id])

        store.dispatch(ContentAction.UpdateUrlAction(tab.id, "https://mozilla.org")).joinBlocking()
        assertNull(telemetryMiddleware.redirectChains[tab.id])
    }

    @Test
    fun `WHEN a download is added THEN the downloads count is updated`() {
        store.dispatch(DownloadAction.AddDownloadAction(mock())).joinBlocking()

        verify { metrics.track(Event.DownloadAdded) }
    }

    @Test
    fun `WHEN foreground tab getting killed THEN middleware counts it`() {
        store.dispatch(TabListAction.RestoreAction(
            listOf(
                createTab("https://www.mozilla.org", id = "foreground"),
                createTab("https://getpocket.com", id = "background_pocket"),
                createTab("https://theverge.com", id = "background_verge")
            ),
            selectedTabId = "foreground"
        )).joinBlocking()

        Assert.assertFalse(EngineMetrics.tabKills["foreground"].testHasValue())
        Assert.assertFalse(EngineMetrics.tabKills["background"].testHasValue())

        store.dispatch(
            EngineAction.KillEngineSessionAction("foreground")
        ).joinBlocking()

        Assert.assertTrue(EngineMetrics.tabKills["foreground"].testHasValue())
    }

    @Test
    fun `WHEN background tabs getting killed THEN middleware counts it`() {
        store.dispatch(TabListAction.RestoreAction(
            listOf(
                createTab("https://www.mozilla.org", id = "foreground"),
                createTab("https://getpocket.com", id = "background_pocket"),
                createTab("https://theverge.com", id = "background_verge")
            ),
            selectedTabId = "foreground"
        )).joinBlocking()

        Assert.assertFalse(EngineMetrics.tabKills["foreground"].testHasValue())
        Assert.assertFalse(EngineMetrics.tabKills["background"].testHasValue())

        store.dispatch(
            EngineAction.KillEngineSessionAction("background_pocket")
        ).joinBlocking()

        Assert.assertFalse(EngineMetrics.tabKills["foreground"].testHasValue())
        Assert.assertTrue(EngineMetrics.tabKills["background"].testHasValue())
        assertEquals(1, EngineMetrics.tabKills["background"].testGetValue())

        store.dispatch(
            EngineAction.KillEngineSessionAction("background_verge")
        ).joinBlocking()

        Assert.assertFalse(EngineMetrics.tabKills["foreground"].testHasValue())
        Assert.assertTrue(EngineMetrics.tabKills["background"].testHasValue())
        assertEquals(2, EngineMetrics.tabKills["background"].testGetValue())
    }

    @Test
    fun `WHEN foreground tab gets killed THEN middleware records foreground age`() {
        store.dispatch(TabListAction.RestoreAction(
            listOf(
                createTab("https://www.mozilla.org", id = "foreground"),
                createTab("https://getpocket.com", id = "background_pocket"),
                createTab("https://theverge.com", id = "background_verge")
            ),
            selectedTabId = "foreground"
        )).joinBlocking()

        clock.elapsedTime = 100

        store.dispatch(EngineAction.LinkEngineSessionAction(
            sessionId = "foreground",
            engineSession = mock()
        )).joinBlocking()

        Assert.assertFalse(EngineMetrics.killForegroundAge.testHasValue())
        Assert.assertFalse(EngineMetrics.killBackgroundAge.testHasValue())

        clock.elapsedTime = 500

        store.dispatch(
            EngineAction.KillEngineSessionAction("foreground")
        ).joinBlocking()

        Assert.assertTrue(EngineMetrics.killForegroundAge.testHasValue())
        Assert.assertFalse(EngineMetrics.killBackgroundAge.testHasValue())
        assertEquals(400, EngineMetrics.killForegroundAge.testGetValue())
    }

    @Test
    fun `WHEN background tab gets killed THEN middleware records background age`() {
        store.dispatch(TabListAction.RestoreAction(
            listOf(
                createTab("https://www.mozilla.org", id = "foreground"),
                createTab("https://getpocket.com", id = "background_pocket"),
                createTab("https://theverge.com", id = "background_verge")
            ),
            selectedTabId = "foreground"
        )).joinBlocking()

        clock.elapsedTime = 100

        store.dispatch(EngineAction.LinkEngineSessionAction(
            sessionId = "background_pocket",
            engineSession = mock()
        )).joinBlocking()

        clock.elapsedTime = 700

        Assert.assertFalse(EngineMetrics.killForegroundAge.testHasValue())
        Assert.assertFalse(EngineMetrics.killBackgroundAge.testHasValue())

        store.dispatch(
            EngineAction.KillEngineSessionAction("background_pocket")
        ).joinBlocking()

        Assert.assertTrue(EngineMetrics.killBackgroundAge.testHasValue())
        Assert.assertFalse(EngineMetrics.killForegroundAge.testHasValue())
        assertEquals(600, EngineMetrics.killBackgroundAge.testGetValue())
    }
}

private class FakeClock : Clock.Delegate {
    var elapsedTime: Long = 0
    override fun elapsedRealtime(): Long = elapsedTime
}
