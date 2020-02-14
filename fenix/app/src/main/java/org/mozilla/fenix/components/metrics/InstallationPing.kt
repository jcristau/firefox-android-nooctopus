/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.GleanMetrics.Installation
import org.mozilla.fenix.GleanMetrics.Pings
import org.mozilla.fenix.ext.settings

class InstallationPing(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            "${this.javaClass.canonicalName}.prefs", Context.MODE_PRIVATE
        )
    }

    /**
     * Checks whether or not the installation ping was already
     * triggered by the application.
     *
     * Note that this only tells us that Fenix triggered the
     * ping and then delegated the transmission to Glean. We
     * have no way to tell if it was actually sent or not.
     *
     * @return true if it was already triggered, false otherwise.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun wasAlreadyTriggered(): Boolean {
        return prefs.getBoolean("ping_sent", false)
    }

    /**
     * Marks the "installation" ping as triggered by the application.
     * This ensures the ping is not triggered again at the next app
     * start.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun markAsTriggered() {
        prefs.edit().putBoolean("ping_sent", true).apply()
    }

    /**
     * Fills the metrics and triggers the 'installation' ping.
     * This is a separate function to simplify unit-testing.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun triggerPing() {
        if (checkMetricsNotEmpty()) {
            context.settings().also {
                Installation.campaign.set(it.adjustCampaignId)
                Installation.adgroup.set(it.adjustAdGroup)
                Installation.creative.set(it.adjustCreative)
                Installation.network.set(it.adjustNetwork)
                Installation.timestamp.set()
            }

            CoroutineScope(Dispatchers.IO).launch {
                Pings.installation.submit()
                markAsTriggered()
            }
        }
    }

    /**
     * Check that required metrics are not empty before attempting to send ping.
     * */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun checkMetricsNotEmpty(): Boolean = listOf(
        context.settings().adjustAdGroup,
        context.settings().adjustCreative,
        context.settings().adjustNetwork
    ).all { it.isNotEmpty() }

    /**
     * Trigger sending the `installation` ping if it wasn't sent already.
     * Then, mark it so that it doesn't get triggered next time Fenix
     * starts.
     */
    fun checkAndSend() {
        if (wasAlreadyTriggered()) {
            Logger.debug("InstallationPing - already generated")
            return
        }
        triggerPing()
    }
}
