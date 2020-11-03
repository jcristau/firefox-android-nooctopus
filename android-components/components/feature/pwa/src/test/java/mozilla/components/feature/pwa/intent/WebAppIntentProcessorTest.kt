/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.pwa.intent

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.state.state.ExternalAppType
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.manifest.WebAppManifest
import mozilla.components.feature.intent.ext.getSessionId
import mozilla.components.feature.pwa.ManifestStorage
import mozilla.components.feature.pwa.ext.getWebAppManifest
import mozilla.components.feature.pwa.ext.putUrlOverride
import mozilla.components.feature.pwa.intent.WebAppIntentProcessor.Companion.ACTION_VIEW_PWA
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.test.any
import mozilla.components.support.test.eq
import mozilla.components.support.test.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class WebAppIntentProcessorTest {
    @Test
    fun `process checks if intent action is not valid`() {
        val processor = WebAppIntentProcessor(mock(), mock(), mock())

        assertFalse(processor.process(Intent(ACTION_VIEW)))
        assertFalse(processor.process(Intent(ACTION_VIEW_PWA, null)))
        assertFalse(processor.process(Intent(ACTION_VIEW_PWA, "".toUri())))
    }

    @Test
    fun `process returns false if no manifest is in storage`() = runBlockingTest {
        val storage: ManifestStorage = mock()
        val processor = WebAppIntentProcessor(mock(), mock(), storage)

        `when`(storage.loadManifest("https://mozilla.com")).thenReturn(null)

        assertFalse(processor.process(Intent(ACTION_VIEW_PWA, "https://mozilla.com".toUri())))
    }

    @Test
    fun `process adds session ID and manifest to intent`() = runBlockingTest {
        val storage: ManifestStorage = mock()
        val processor = WebAppIntentProcessor(mock(), mock(), storage)

        val manifest = WebAppManifest(
            name = "Test Manifest",
            startUrl = "https://mozilla.com"
        )
        `when`(storage.loadManifest("https://mozilla.com")).thenReturn(manifest)

        val intent = Intent(ACTION_VIEW_PWA, "https://mozilla.com".toUri())
        assertTrue(processor.process(intent))
        assertNotNull(intent.getSessionId())
        assertEquals(manifest, intent.getWebAppManifest())
    }

    @Test
    fun `process adds custom tab config`() = runBlockingTest {
        val intent = Intent(ACTION_VIEW_PWA, "https://mozilla.com".toUri())

        val storage: ManifestStorage = mock()
        val store = BrowserStore()
        val sessionManager = SessionManager(mock(), store)

        val manifest = WebAppManifest(
            name = "Test Manifest",
            startUrl = "https://mozilla.com"
        )
        `when`(storage.loadManifest("https://mozilla.com")).thenReturn(manifest)

        val processor = WebAppIntentProcessor(sessionManager, mock(), storage)

        assertTrue(processor.process(intent))
        val sessionState = store.state.customTabs.first()
        assertNotNull(sessionState.config)
        assertEquals(ExternalAppType.PROGRESSIVE_WEB_APP, sessionState.config.externalAppType)
    }

    @Test
    fun `url override is applied to session if present`() = runBlockingTest {
        val storage: ManifestStorage = mock()
        val loadUrlUseCase: SessionUseCases.DefaultLoadUrlUseCase = mock()
        val processor = WebAppIntentProcessor(mock(), loadUrlUseCase, storage)
        val urlOverride = "https://mozilla.com/deep/link/index.html"

        val manifest = WebAppManifest(
                name = "Test Manifest",
                startUrl = "https://mozilla.com"
        )

        `when`(storage.loadManifest("https://mozilla.com")).thenReturn(manifest)

        val intent = Intent(ACTION_VIEW_PWA, "https://mozilla.com".toUri())

        intent.putUrlOverride(urlOverride)

        assertTrue(processor.process(intent))
        verify(loadUrlUseCase).invoke(eq(urlOverride), any(), any(), any())
    }
}
