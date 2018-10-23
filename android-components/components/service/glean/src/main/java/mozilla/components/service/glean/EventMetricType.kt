/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean

import mozilla.components.service.glean.storages.EventsStorageEngine
import mozilla.components.support.base.log.logger.Logger

// Maximum length of any string value in the extra dictionary, in UTF8 byte sequence length.
internal const val MAX_LENGTH_EXTRA_KEY_VALUE = 80
// Maximum length of any passed value string, in UTF8 byte sequence length.
internal const val MAX_LENGTH_VALUE = 80

/**
 * This implements the developer facing API for recording events.
 *
 * Instances of this class type are automatically generated by the parsers at built time,
 * allowing developers to record events that were previously registered in the metrics.yaml file.
 *
 * The Events API only exposes the [record] method, which takes care of validating the input
 * data and making sure that limits are enforced.
 */
data class EventMetricType(
    override val applicationProperty: Boolean = false,
    override val disabled: Boolean = false,
    override val category: String,
    override val name: String,
    override val sendInPings: List<String> = listOf("default"),
    override val userProperty: Boolean = false,
    val objects: List<String>,
    val allowedExtraKeys: List<String>? = null
) : CommonMetricData {
    private val logger = Logger("glean/EventMetricType")

    /**
     * Record an event by using the information provided by the instance of this class.
     *
     * @param objectId the object the event occurred on, e.g. 'reload_button'. The maximum
     *                 length of this string is defined by [MAX_LENGTH_OBJECT_ID]
     * @param value optional. This is a user defined value, providing context for the event. The
     *              maximum length of this string is defined by [MAX_LENGTH_VALUE]
     * @param extra optional. This is map, both keys and values need to be strings, keys are
     *              identifiers. This is used for events where additional richer context is needed.
     *              The maximum length for values is defined by [MAX_LENGTH_EXTRA_KEY_VALUE]
     */
    @Suppress("ReturnCount", "ComplexMethod")
    public fun record(objectId: String, value: String? = null, extra: Map<String, String>? = null) {
        // TODO report errors through other special metrics handled by the SDK. See bug 1499761.

        // Silently drop recording for disabled events.
        if (disabled) {
            return
        }

        // TODO implement "user" metric lifetime. See bug 1499756.
        // Metrics can be recorded with application or user lifetime. For now,
        // we only support "application": metrics live as long as the application lives.
        if (!applicationProperty) {
            logger.error("The metric lifetime must be explicitly set.")
            return
        }

        // We don't need to check that the objectId is short, since that
        // has already been determined at build time for each of the valid objectId values.
        if (!objects.contains(objectId)) {
            logger.warn("objectId '$objectId' is not valid on the $category.$name metric")
            return
        }

        val truncatedValue = value?.let {
            if (it.length > MAX_LENGTH_VALUE) {
                logger.warn("Value parameter exceeds maximum string length, truncating.")
                return@let it.substring(0, MAX_LENGTH_VALUE)
            }
            it
        }

        // Check if the provided extra keys are allowed and have sane values.
        val truncatedExtraKeys = extra?.toMutableMap()?.let { eventKeys ->
            if (allowedExtraKeys == null) {
                logger.error("Cannot use extra keys are no extra keys are defined.")
                return
            }

            for ((key, extraValue) in eventKeys) {
                if (!allowedExtraKeys.contains(key)) {
                    logger.error("$key extra key is not allowed for $category.$name.")
                    return
                }

                if (extraValue.length > MAX_LENGTH_EXTRA_KEY_VALUE) {
                    logger.warn("$extraValue for $key is too long for $category.$name, truncating.")
                    eventKeys[key] = extraValue.substring(0, MAX_LENGTH_EXTRA_KEY_VALUE)
                }
            }
            eventKeys
        }

        // Delegate storing the event to the storage engine.
        EventsStorageEngine.record(
            stores = sendInPings,
            category = category,
            name = name,
            objectId = objectId,
            value = truncatedValue,
            extra = truncatedExtraKeys
        )
    }
}
