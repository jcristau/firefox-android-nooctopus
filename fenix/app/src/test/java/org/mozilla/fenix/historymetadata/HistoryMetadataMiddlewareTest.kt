/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.historymetadata

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.storage.HistoryMetadataKey
import mozilla.components.support.test.ext.joinBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class HistoryMetadataMiddlewareTest {

    private lateinit var store: BrowserStore
    private lateinit var middleware: HistoryMetadataMiddleware
    private lateinit var service: HistoryMetadataService

    @Before
    fun setUp() {
        service = mockk(relaxed = true)
        middleware = HistoryMetadataMiddleware(service)
        store = BrowserStore(
            middleware = listOf(middleware) + EngineMiddleware.create(engine = mockk()),
            initialState = BrowserState()
        )
    }

    @Test
    fun `GIVEN normal tab WHEN loading completed THEN meta data is recorded`() {
        val tab = createTab("https://mozilla.org")

        val expectedKey = HistoryMetadataKey(url = tab.content.url)
        every { service.createMetadata(any(), any()) } returns expectedKey

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, false)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify { service.createMetadata(capture(capturedTab), null) }

        assertEquals(tab.id, capturedTab.captured.id)
        assertEquals(expectedKey, store.state.findTab(tab.id)?.historyMetadata)
    }

    @Test
    fun `GIVEN private tab WHEN loading completed THEN no meta data is recorded`() {
        val tab = createTab("https://mozilla.org", private = true)

        val expectedKey = HistoryMetadataKey(url = tab.content.url)
        every { service.createMetadata(any(), any()) } returns expectedKey

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, false)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN normal tab WHEN user navigates and new page starts loading THEN meta data is updated`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = existingKey.url, historyMetadata = existingKey)

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify { service.updateMetadata(existingKey, capture(capturedTab)) }

        assertEquals(tab.id, capturedTab.captured.id)
    }

    @Test
    fun `GIVEN tab without meta data WHEN user navigates and new page starts loading THEN nothing happens`() {
        val tab = createTab(url = "https://mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN tab is not selected WHEN user navigates and new page starts loading THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab, select = true)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
        assertEquals(tab.id, capturedTab.captured.id)

        store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, true)).joinBlocking()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
    }

    @Test
    fun `GIVEN normal media tab WHEN media state is updated THEN meta data is recorded`() {
        val tab = createTab("https://media.mozilla.org")

        val expectedKey = HistoryMetadataKey(url = tab.content.url)
        every { service.createMetadata(any(), any()) } returns expectedKey

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(MediaSessionAction.UpdateMediaMetadataAction(tab.id, mockk())).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify { service.createMetadata(capture(capturedTab), null) }

        assertEquals(tab.id, capturedTab.captured.id)
        assertEquals(expectedKey, store.state.findTab(tab.id)?.historyMetadata)
    }

    @Test
    fun `GIVEN private media tab WHEN media state is updated THEN no meta data is recorded`() {
        val tab = createTab("https://media.mozilla.org", private = true)

        val expectedKey = HistoryMetadataKey(url = tab.content.url)
        every { service.createMetadata(any(), any()) } returns expectedKey

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(MediaSessionAction.UpdateMediaMetadataAction(tab.id, mockk())).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN normal tab is selected WHEN new tab will be added and selected THEN meta data is updated for currently selected tab`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")
        val yetAnotherTab = createTab(url = "https://media.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.AddTabAction(yetAnotherTab, select = true)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
        assertEquals(tab.id, capturedTab.captured.id)
    }

    @Test
    fun `GIVEN private tab is selected WHEN new tab will be added and selected THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey, private = true)
        val otherTab = createTab(url = "https://blog.mozilla.org")
        val yetAnotherTab = createTab(url = "https://media.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.AddTabAction(yetAnotherTab, select = true)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN normal tab is selected WHEN new tab will be selected THEN meta data is updated for currently selected tab`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.SelectTabAction(otherTab.id)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
        assertEquals(tab.id, capturedTab.captured.id)
    }

    @Test
    fun `GIVEN private tab is selected WHEN new tab will be selected THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey, private = true)
        val otherTab = createTab(url = "https://blog.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.SelectTabAction(otherTab.id)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `WHEN normal selected tab is removed THEN meta data is updated`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabAction(tab.id)).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
        assertEquals(tab.id, capturedTab.captured.id)
    }

    @Test
    fun `WHEN private selected tab is removed THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey, private = true)

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabAction(tab.id)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `WHEN non-selected tab is removed THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabAction(otherTab.id)).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN multiple tabs are removed WHEN selected normal tab should also be removed THEN meta data is updated`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")
        val yetAnotherTab = createTab(url = "https://media.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(yetAnotherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabsAction(listOf(tab.id, otherTab.id))).joinBlocking()
        val capturedTab = slot<TabSessionState>()
        verify(exactly = 1) { service.updateMetadata(existingKey, capture(capturedTab)) }
        assertEquals(tab.id, capturedTab.captured.id)
    }

    @Test
    fun `GIVEN multiple tabs are removed WHEN selected private tab should also be removed THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey, private = true)
        val otherTab = createTab(url = "https://blog.mozilla.org")
        val yetAnotherTab = createTab(url = "https://media.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(yetAnotherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabsAction(listOf(tab.id, otherTab.id))).joinBlocking()
        verify { service wasNot Called }
    }

    @Test
    fun `GIVEN multiple tabs are removed WHEN selected tab should not be removed THEN nothing happens`() {
        val existingKey = HistoryMetadataKey(url = "https://mozilla.org")
        val tab = createTab(url = "https://mozilla.org", historyMetadata = existingKey)
        val otherTab = createTab(url = "https://blog.mozilla.org")
        val yetAnotherTab = createTab(url = "https://media.mozilla.org")

        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(yetAnotherTab)).joinBlocking()
        verify { service wasNot Called }

        store.dispatch(TabListAction.RemoveTabsAction(listOf(otherTab.id, yetAnotherTab.id))).joinBlocking()
        verify { service wasNot Called }
    }
}
