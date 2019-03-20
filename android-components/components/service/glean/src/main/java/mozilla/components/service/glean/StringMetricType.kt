/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean

import android.support.annotation.VisibleForTesting
import mozilla.components.service.glean.storages.StringsStorageEngine
import mozilla.components.support.base.log.logger.Logger

/**
 * This implements the developer facing API for recording string metrics.
 *
 * Instances of this class type are automatically generated by the parsers at build time,
 * allowing developers to record values that were previously registered in the metrics.yaml file.
 *
 * The string API only exposes the [set] method, which takes care of validating the input
 * data and making sure that limits are enforced.
 */
data class StringMetricType(
    override val disabled: Boolean,
    override val category: String,
    override val lifetime: Lifetime,
    override val name: String,
    override val sendInPings: List<String>
) : CommonMetricData {

    override val defaultStorageDestinations: List<String> = listOf("metrics")

    private val logger = Logger("glean/StringMetricType")

    /**
     * Set a string value.
     *
     * @param value This is a user defined string value. If the length of the string exceeds
     *              the maximum length, it will be truncated.
     */
    fun set(value: String) {
        if (!shouldRecord(logger)) {
            return
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            // Delegate storing the string to the storage engine.
            StringsStorageEngine.record(
                this@StringMetricType,
                value = value
            )
        }
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only. This function will
     * attempt to await the last task (if any) writing to the the metric's storage engine before
     * returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testHasValue(pingName: String = getStorageNames().first()): Boolean {
        Dispatchers.API.assertInTestingMode()

        return StringsStorageEngine.getSnapshot(pingName, false)?.get(identifier) != null
    }

    /**
     * Returns the stored value for testing purposes only. This function will attempt to await the
     * last task (if any) writing to the the metric's storage engine before returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testGetValue(pingName: String = getStorageNames().first()): String {
        Dispatchers.API.assertInTestingMode()

        return StringsStorageEngine.getSnapshot(pingName, false)!![identifier]!!
    }
}
