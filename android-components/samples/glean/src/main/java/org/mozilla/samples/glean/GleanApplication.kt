/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.glean

import android.app.Application
import android.content.Intent
import mozilla.components.service.experiments.Configuration
import mozilla.components.service.glean.Glean
import mozilla.components.service.experiments.Experiments
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import org.mozilla.samples.glean.GleanMetrics.Basic
import org.mozilla.samples.glean.GleanMetrics.Test
import org.mozilla.samples.glean.GleanMetrics.Custom
import org.mozilla.samples.glean.GleanMetrics.Pings

class GleanApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // We want the log messages of all builds to go to Android logcat
        Log.addSink(AndroidLogSink())

        // Register the sample application's custom pings.
        Glean.registerPings(Pings)

        // Initialize the Glean library. Ideally, this is the first thing that
        // must be done right after enabling logging.
        Glean.initialize(applicationContext, uploadEnabled = true)

        // Initialize the Experiments library and pass in the callback that will generate a
        // broadcast Intent to signal the application that experiments have been updated.
        Experiments.initialize(applicationContext) {
            val intent = Intent()
            intent.action = "org.mozilla.samples.glean.experiments.updated"
            sendBroadcast(intent)
        }

        Test.timespan.start()

        Custom.counter.add()

        // Set a sample value for a metric.
        Basic.os.set("Android")
    }
}
