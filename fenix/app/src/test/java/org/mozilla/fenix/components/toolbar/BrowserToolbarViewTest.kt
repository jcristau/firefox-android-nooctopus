/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import androidx.coordinatorlayout.widget.CoordinatorLayout
import io.mockk.confirmVerified

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.behavior.BrowserToolbarBehavior
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import mozilla.components.browser.toolbar.behavior.ToolbarPosition as MozacToolbarPosition

@ExperimentalCoroutinesApi
@RunWith(FenixRobolectricTestRunner::class)
class BrowserToolbarViewTest {
    private lateinit var toolbarView: BrowserToolbarView
    private lateinit var toolbar: BrowserToolbar
    private lateinit var behavior: BrowserToolbarBehavior

    @Before
    fun `setup`() {
        toolbar = BrowserToolbar(testContext)
        toolbar.layoutParams = CoordinatorLayout.LayoutParams(100, 100)
        behavior = spyk(BrowserToolbarBehavior(testContext, null, MozacToolbarPosition.BOTTOM))
        (toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior = behavior

        toolbarView = BrowserToolbarView(
            container = CoordinatorLayout(testContext),
            toolbarPosition = ToolbarPosition.BOTTOM,
            interactor = mockk(),
            customTabSession = mockk(relaxed = true),
            lifecycleOwner = mockk()
        )

        toolbarView.view = toolbar
    }

    @Test
    fun `setToolbarBehavior(false) should setDynamicToolbarBehavior if bottom toolbar is dynamic and the tab is not for a PWA or TWA`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns true
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns false

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.setDynamicToolbarBehavior(MozacToolbarPosition.BOTTOM) }
    }

    @Test
    fun `setToolbarBehavior(false) should expandToolbarAndMakeItFixed if bottom toolbar is not set as dynamic`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns false

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(false) should expandToolbarAndMakeItFixed if bottom toolbar is dynamic but the tab is for a PWA or TWA`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns true
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns true

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(true) should setDynamicToolbarBehavior if bottom toolbar is dynamic and the tab is not for a PWA or TWA`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns true
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns false

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.setDynamicToolbarBehavior(MozacToolbarPosition.BOTTOM) }
    }

    @Test
    fun `setToolbarBehavior(true) should expandToolbarAndMakeItFixed if bottom toolbar is not set as dynamic`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns false

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(true) should expandToolbarAndMakeItFixed if bottom toolbar is dynamic but the tab is for a PWA or TWA`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.BOTTOM
        every { testContext.settings().isDynamicToolbarEnabled } returns true
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns true

        toolbarViewSpy.setToolbarBehavior(false)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(true) should expandToolbarAndMakeItFixed for top toolbar if shouldUseFixedTopToolbar`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.TOP
        every { testContext.settings().shouldUseFixedTopToolbar } returns true

        toolbarViewSpy.setToolbarBehavior(true)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(true) should expandToolbarAndMakeItFixed for top toolbar if it is not dynamic`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.TOP
        every { testContext.settings().isDynamicToolbarEnabled } returns false

        toolbarViewSpy.setToolbarBehavior(true)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(true) should expandToolbarAndMakeItFixed for top toolbar if shouldDisableScroll`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.TOP

        toolbarViewSpy.setToolbarBehavior(true)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `setToolbarBehavior(false) should setDynamicToolbarBehavior for top toolbar`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { testContext.settings().toolbarPosition } returns ToolbarPosition.TOP
        every { testContext.settings().shouldUseFixedTopToolbar } returns true
        every { testContext.settings().isDynamicToolbarEnabled } returns true

        toolbarViewSpy.setToolbarBehavior(true)

        verify { toolbarViewSpy.expandToolbarAndMakeItFixed() }
    }

    @Test
    fun `expandToolbarAndMakeItFixed should expand the toolbar and and disable the dynamic behavior`() {
        val toolbarViewSpy = spyk(toolbarView)

        assertNotNull((toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior)

        toolbarViewSpy.expandToolbarAndMakeItFixed()

        verify { toolbarViewSpy.expand() }
        assertNull((toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior)
    }

    @Test
    fun `setDynamicToolbarBehavior should set a BrowserToolbarBehavior for the bottom toolbar`() {
        val toolbarViewSpy = spyk(toolbarView)
        (toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior = null

        toolbarViewSpy.setDynamicToolbarBehavior(MozacToolbarPosition.BOTTOM)

        assertNotNull((toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior)
    }

    @Test
    fun `setDynamicToolbarBehavior should set a BrowserToolbarBehavior for the top toolbar`() {
        val toolbarViewSpy = spyk(toolbarView)
        (toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior = null

        toolbarViewSpy.setDynamicToolbarBehavior(MozacToolbarPosition.TOP)

        assertNotNull((toolbar.layoutParams as CoordinatorLayout.LayoutParams).behavior)
    }

    @Test
    fun `expand should not do anything if isPwaTabOrTwaTab`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns true

        toolbarViewSpy.expand()

        verify { toolbarViewSpy.expand() }
        verify { toolbarViewSpy.isPwaTabOrTwaTab }
        // verify that no other interactions than the expected ones took place
        confirmVerified(toolbarViewSpy)
    }

    @Test
    fun `expand should call forceExpand if not isPwaTabOrTwaTab`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns false

        toolbarViewSpy.expand()

        verify { behavior.forceExpand(toolbar) }
    }

    @Test
    fun `collapse should not do anything if isPwaTabOrTwaTab`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns true

        toolbarViewSpy.collapse()

        verify { toolbarViewSpy.collapse() }
        verify { toolbarViewSpy.isPwaTabOrTwaTab }
        // verify that no other interactions than the expected ones took place
        confirmVerified(toolbarViewSpy)
    }

    @Test
    fun `collapse should call forceExpand if not isPwaTabOrTwaTab`() {
        val toolbarViewSpy = spyk(toolbarView)
        every { toolbarViewSpy.isPwaTabOrTwaTab } returns false

        toolbarViewSpy.collapse()

        verify { behavior.forceCollapse(toolbar) }
    }
}
