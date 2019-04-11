/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean.private

import android.support.annotation.VisibleForTesting
import mozilla.components.service.glean.Dispatchers
import mozilla.components.service.glean.storages.TimespansStorageEngine
import mozilla.components.service.glean.timing.TimingManager
import mozilla.components.support.base.log.logger.Logger

/**
 * This implements the developer facing API for recording timespans.
 *
 * Instances of this class type are automatically generated by the parsers at build time,
 * allowing developers to record values that were previously registered in the metrics.yaml file.
 *
 * The timespans API exposes the [start], [stopAndSum] and [cancel] methods.
 */
data class TimespanMetricType(
    override val disabled: Boolean,
    override val category: String,
    override val lifetime: Lifetime,
    override val name: String,
    override val sendInPings: List<String>,
    val timeUnit: TimeUnit
) : CommonMetricData {

    override val defaultStorageDestinations: List<String> = listOf("metrics")

    private val logger = Logger("glean/TimespanMetricType")

    /**
     * Start tracking time for the provided metric and associated object. This
     * records an error if it’s already tracking time (i.e. start was already
     * called with no corresponding [stopAndSum]): in that case the original
     * start time will be preserved.
     *
     * @param anyObject The object to associate with this timing.  This allows
     * for concurrent timing of events associated with different objects to the
     * same timespan metric.
     */
    fun start(anyObject: Any) {
        if (!shouldRecord(logger)) {
            return
        }

        TimingManager.start(this, anyObject)
    }

    /**
     * Stop tracking time for the provided metric and associated object. Add the
     * elapsed time to the time currently stored in the metric. This will record
     * an error if no [start] was called.
     *
     * @param anyObject The object to associate with this timing.  This allows
     * for concurrent timing of events associated with different objects to the
     * same timespan metric.
     */
    fun stopAndSum(anyObject: Any) {
        if (!shouldRecord(logger)) {
            return
        }

        TimingManager.stop(this, anyObject)?.let { elapsedNanos ->
            @Suppress("EXPERIMENTAL_API_USAGE")
            Dispatchers.API.launch {
                TimespansStorageEngine.sum(this@TimespanMetricType, timeUnit, elapsedNanos)
            }
        }
    }

    /**
     * Abort a previous [start] call. No error is recorded if no [start] was called.
     *
     * @param anyObject The object to associate with this timing.  This allows
     * for concurrent timing of events associated with different objects to the
     * same timespan metric.
     */
    fun cancel(anyObject: Any) {
        if (!shouldRecord(logger)) {
            return
        }

        TimingManager.cancel(this, anyObject)
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testHasValue(pingName: String = getStorageNames().first()): Boolean {
        return TimespansStorageEngine.getSnapshot(pingName, false)?.get(identifier) != null
    }

    /**
     * Returns the stored value for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testGetValue(pingName: String = getStorageNames().first()): Long {
        return TimespansStorageEngine.getSnapshot(pingName, false)!![identifier]!!
    }
}
