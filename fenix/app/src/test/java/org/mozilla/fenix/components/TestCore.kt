/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.pwa.WebAppShortcutManager

class TestCore(context: Context) : Core(context) {

    override val engine = mockk<Engine>(relaxed = true) {
        every { this@mockk getProperty "settings" } returns mockk<Settings>()
    }
    override val sessionManager = SessionManager(engine)
    override val store = mockk<BrowserStore>()
    override val client = mockk<Client>()
    override val webAppShortcutManager = mockk<WebAppShortcutManager>()
}
