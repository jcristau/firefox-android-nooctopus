/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.readerview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.feature.readerview.view.ReaderViewControlsView
import mozilla.components.support.test.any
import mozilla.components.support.test.argumentCaptor
import mozilla.components.support.test.eq
import mozilla.components.support.test.mock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import java.lang.RuntimeException

@RunWith(RobolectricTestRunner::class)
class ReaderViewFeatureTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        ReaderViewFeature.installedWebExt = null
    }

    @Test
    fun `start installs webextension`() {
        val engine = mock(Engine::class.java)
        val sessionManager = mock(SessionManager::class.java)

        val readerViewFeature = ReaderViewFeature(context, engine, sessionManager, mock())
        assertNull(ReaderViewFeature.installedWebExt)
        readerViewFeature.start()

        val onSuccess = argumentCaptor<((WebExtension) -> Unit)>()
        val onError = argumentCaptor<((String, Throwable) -> Unit)>()
        verify(engine, times(1)).installWebExtension(
                eq(ReaderViewFeature.READER_VIEW_EXTENSION_ID),
                eq(ReaderViewFeature.READER_VIEW_EXTENSION_URL),
                eq(true),
                onSuccess.capture(),
                onError.capture()
        )

        onSuccess.value.invoke(mock(WebExtension::class.java))
        assertNotNull(ReaderViewFeature.installedWebExt)

        readerViewFeature.start()
        verify(engine, times(1)).installWebExtension(
                eq(ReaderViewFeature.READER_VIEW_EXTENSION_ID),
                eq(ReaderViewFeature.READER_VIEW_EXTENSION_URL),
                eq(true),
                onSuccess.capture(),
                onError.capture()
        )

        onError.value.invoke(ReaderViewFeature.READER_VIEW_EXTENSION_ID, RuntimeException())
    }

    @Test
    fun `start registers observer for selected session`() {
        val engine = mock(Engine::class.java)
        val sessionManager: SessionManager = mock()
        val view: ReaderViewControlsView = mock()

        val readerViewFeature = spy(ReaderViewFeature(context, engine, sessionManager, view))
        readerViewFeature.start()

        verify(readerViewFeature).observeSelected()
    }

    @Test
    fun `start registers content message handler for selected session`() {
        val engine = mock(Engine::class.java)
        val sessionManager: SessionManager = mock()
        val view: ReaderViewControlsView = mock()
        val session: Session = mock()
        val engineSession: EngineSession = mock()
        val ext: WebExtension = mock()
        val messageHandler = argumentCaptor<MessageHandler>()
        val message: Any = mock()

        ReaderViewFeature.installedWebExt = ext

        `when`(sessionManager.getOrCreateEngineSession(session)).thenReturn(engineSession)
        `when`(sessionManager.selectedSession).thenReturn(session)
        val readerViewFeature = spy(ReaderViewFeature(context, engine, sessionManager, view))

        readerViewFeature.start()
        verify(ext).registerContentMessageHandler(eq(engineSession), eq(ReaderViewFeature.READER_VIEW_EXTENSION_ID), messageHandler.capture())

        val port: Port = mock()
        `when`(port.engineSession).thenReturn(engineSession)

        messageHandler.value.onPortConnected(port)
        assertTrue(ReaderViewFeature.ports.containsValue(port))

        messageHandler.value.onPortMessage(message, port)
        verify(port).postMessage(any())

        messageHandler.value.onPortDisconnected(port)
        assertFalse(ReaderViewFeature.ports.containsValue(port))
    }

    @Test
    fun `stop also stops interactor`() {
        val engine = mock(Engine::class.java)
        val sessionManager: SessionManager = mock()
        val view: ReaderViewControlsView = mock()

        val readerViewFeature = spy(ReaderViewFeature(context, engine, sessionManager, view))
        readerViewFeature.stop()

        assertNull(view.listener)
    }

    @Test
    fun `showControls invokes the presenter`() {
        val view: ReaderViewControlsView = mock()
        val feature = spy(ReaderViewFeature(context, mock(), mock(), view))

        feature.showControls()

        verify(view).setColorScheme(any())
        verify(view).setFont(any())
        verify(view).setFontSize(anyInt())
        verify(view).showControls()
    }

    @Test
    fun `hideControls invokes the presenter`() {
        val view: ReaderViewControlsView = mock()
        val feature = spy(ReaderViewFeature(context, mock(), mock(), view))

        feature.hideControls()

        verify(view).hideControls()
    }
}
