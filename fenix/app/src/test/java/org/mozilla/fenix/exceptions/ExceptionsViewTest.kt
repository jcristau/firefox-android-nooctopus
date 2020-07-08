/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.exceptions

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.android.synthetic.main.component_exceptions.*
import mozilla.components.concept.engine.content.blocking.TrackingProtectionException
import mozilla.components.support.test.robolectric.testContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner

@RunWith(FenixRobolectricTestRunner::class)
class ExceptionsViewTest {

    private lateinit var container: ViewGroup
    private lateinit var interactor: ExceptionsInteractor
    private lateinit var exceptionsView: ExceptionsView

    @Before
    fun setup() {
        mockkConstructor(ExceptionsAdapter::class)
        container = FrameLayout(testContext)
        interactor = mockk()

        exceptionsView = ExceptionsView(container, interactor)
        every { anyConstructed<ExceptionsAdapter>().updateData(any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkConstructor(ExceptionsAdapter::class)
    }

    @Test
    fun `binds exception text`() {
        assertTrue(exceptionsView.exceptions_learn_more.movementMethod is LinkMovementMethod)
        assertTrue(exceptionsView.exceptions_learn_more.text is Spannable)
        assertEquals("Learn more", exceptionsView.exceptions_learn_more.text.toString())

        every { interactor.onLearnMore() } just Runs
        exceptionsView.exceptions_learn_more.performClick()
        verify { interactor.onLearnMore() }
    }

    @Test
    fun `binds empty list to adapter`() {
        exceptionsView.update(ExceptionsFragmentState(emptyList()))

        assertTrue(exceptionsView.exceptions_empty_view.isVisible)
        assertFalse(exceptionsView.exceptions_list.isVisible)
        verify { anyConstructed<ExceptionsAdapter>().updateData(emptyList()) }
    }

    @Test
    fun `binds list with items to adapter`() {
        val items = listOf<TrackingProtectionException>(mockk(), mockk())
        exceptionsView.update(ExceptionsFragmentState(items))

        assertFalse(exceptionsView.exceptions_empty_view.isVisible)
        assertTrue(exceptionsView.exceptions_list.isVisible)
        verify { anyConstructed<ExceptionsAdapter>().updateData(items) }
    }
}
