/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package mozilla.components.feature.accounts.push

import mozilla.components.feature.push.AutoPushFeature
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.support.test.any
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FxaPushSupportFeatureTest {

    @Test
    fun `account observer registered`() {
        val accountManager: FxaAccountManager = mock()
        val pushFeature: AutoPushFeature = mock()

        FxaPushSupportFeature(testContext, accountManager, pushFeature)

        verify(accountManager).register(any())

        verify(pushFeature).registerForPushMessages(any(), any(), any(), anyBoolean())
        verify(pushFeature).registerForSubscriptions(any(), any(), anyBoolean())
    }
}