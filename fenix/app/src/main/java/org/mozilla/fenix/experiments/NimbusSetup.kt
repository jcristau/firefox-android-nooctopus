/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.experiments

import android.content.Context
import android.net.Uri
import android.os.StrictMode
import io.sentry.Sentry
import mozilla.components.service.nimbus.NimbusApi
import mozilla.components.service.nimbus.Nimbus
import mozilla.components.service.nimbus.NimbusDisabled
import mozilla.components.service.nimbus.NimbusServerSettings
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.components.isSentryEnabled
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings

@Suppress("TooGenericExceptionCaught")
fun createNimbus(context: Context, url: String?): NimbusApi =
    try {
        // Eventually we'll want to use `NimbusDisabled` when we have no NIMBUS_ENDPOINT.
        // but we keep this here to not mix feature flags and how we configure Nimbus.
        val serverSettings = if (!url.isNullOrBlank()) {
            NimbusServerSettings(url = Uri.parse(url))
        } else {
            null
        }

        // Global opt out state is stored in Nimbus, and shouldn't be toggled to `true`
        // from the app unless the user does so from a UI control.
        // However, the user may have opt-ed out of mako experiments already, so
        // we should respect that setting here.
        val enabled =
            context.components.strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
                context.settings().isExperimentationEnabled
            }

        Nimbus(context, serverSettings).apply {
            // This performs the minimal amount of work required to load branch and enrolment data
            // into memory. If `getExperimentBranch` is called from another thread between here
            // and the next nimbus disk write (setting `globalUserParticipation` or
            // `applyPendingExperiments()`) then this has you covered.
            // This call does its work on the db thread.
            initialize()

            if (!enabled) {
                // This opts out of nimbus experiments. It involves writing to disk, so does its
                // work on the db thread.
                globalUserParticipation = enabled
            }

            // We may have downloaded experiments on a previous run, so let's start using them
            // now. We didn't do this earlier, so as to make getExperimentBranch and friends returns
            // the same thing throughout the session. This call does its work on the db thread.
            applyPendingExperiments()

            // Now fetch the experiments from the server. These will be available for feature
            // configuration on the next run of the app. This call launches on the fetch thread.
            fetchExperiments()
        }
    } catch (e: Throwable) {
        // Something went wrong. We'd like not to, but stability of the app is more important than
        // failing fast here.
        if (isSentryEnabled()) {
            Sentry.capture(e)
        } else {
            Logger.error("Failed to initialize Nimbus", e)
        }
        NimbusDisabled()
    }
