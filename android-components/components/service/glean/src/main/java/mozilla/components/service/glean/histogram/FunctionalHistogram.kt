/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean.histogram

import androidx.annotation.VisibleForTesting
import mozilla.components.support.ktx.android.org.json.tryGetLong
import org.json.JSONObject
import java.lang.Math.pow
import kotlin.math.log

/**
 * This class represents a histogram where the bucketing is performed by a
 * function, rather than pre-computed buckets. It is meant to help serialize
 * and deserialize data to the correct format for transport and storage, as well
 * as performing the calculations to determine the correct bucket for each sample.
 *
 * The bucket index of a given sample is determined with the following function:
 *
 *     i = ⌊n log₂(𝑥)⌋
 *
 * In other words, there are n buckets for each power of 2 magnitude.
 *
 * The value of 8 for n was determined experimentally based on existing data to have sufficient
 * resolution.
 *
 * @param values a map containing the minimum bucket value mapped to the accumulated count
 * @param sum the accumulated sum of all the samples in the histogram
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
data class FunctionalHistogram(
    // map from bucket limits to accumulated values
    val values: MutableMap<Long, Long> = mutableMapOf(),
    var sum: Long = 0
) {
    companion object {
        // The base of the logarithm used to determine bucketing
        internal const val LOG_BASE = 2.0

        // The buckets per each order of magnitude of the logarithm.
        internal const val BUCKETS_PER_MAGNITUDE = 8.0

        // The combined log base and buckets per magnitude.
        internal val EXPONENT = pow(LOG_BASE, 1.0 / BUCKETS_PER_MAGNITUDE)

        /**
         * Maps a sample to a "bucket index" that it belongs in.
         * A "bucket index" is the consecutive integer index of each bucket, useful as a
         * mathematical concept, even though the internal representation is stored and
         * sent using the minimum value in each bucket.
         */
        internal fun sampleToBucketIndex(sample: Long): Long {
            return log(sample.toDouble() + 1, EXPONENT).toLong()
        }

        /**
         * Determines the minimum value of a bucket, given a bucket index.
         */
        internal fun bucketIndexToBucketMinimum(bucketIndex: Long): Long {
            return pow(EXPONENT, bucketIndex.toDouble()).toLong()
        }

        /**
         * Maps a sample to the minimum value of the bucket it belongs in.
         */
        internal fun sampleToBucketMinimum(sample: Long): Long {
            return if (sample == 0L) {
                0L
            } else {
                bucketIndexToBucketMinimum(sampleToBucketIndex(sample))
            }
        }

        /**
         * Factory function that takes stringified JSON and converts it back into a
         * [FunctionalHistogram].
         *
         * @param json Stringified JSON value representing a [FunctionalHistogram] object
         * @return A [FunctionalHistogram] or null if unable to rebuild from the string.
         */
        @Suppress("ReturnCount", "ComplexMethod", "NestedBlockDepth")
        internal fun fromJsonString(json: String): FunctionalHistogram? {
            val jsonObject: JSONObject
            try {
                jsonObject = JSONObject(json)
            } catch (e: org.json.JSONException) {
                return null
            }

            // Attempt to parse the values map, if it fails then something is wrong and we need to
            // return null.
            val values = try {
                val mapData = jsonObject.getJSONObject("values")
                val valueMap: MutableMap<Long, Long> = mutableMapOf()
                mapData.keys().forEach { key ->
                    mapData.tryGetLong(key)?.let {
                        valueMap[key.toLong()] = it
                    }
                }
                valueMap
            } catch (e: org.json.JSONException) {
                // This should only occur if there isn't a key/value pair stored for "values"
                return null
            }
            val sum = jsonObject.tryGetLong("sum") ?: return null

            return FunctionalHistogram(
                values = values,
                sum = sum
            )
        }
    }

    // This is a calculated read-only property that returns the total count of accumulated values
    val count: Long
        get() = values.map { it.value }.sum()

    /**
     * Accumulates a sample to the correct bucket.
     * If a value doesn't exist for this bucket yet, one is created.
     *
     * @param sample Long value representing the sample that is being accumulated
     */
    internal fun accumulate(sample: Long) {
        var bucketMinimum = sampleToBucketMinimum(sample)
        values[bucketMinimum] = (values[bucketMinimum] ?: 0) + 1
        sum += sample
    }

    /**
     * Helper function to build the [FunctionalHistogram] into a JSONObject for serialization
     * purposes.
     */
    internal fun toJsonObject(): JSONObject {
        return JSONObject(mapOf(
            "values" to values.mapKeys { "${it.key}" },
            "sum" to sum
        ))
    }

    /**
     * Helper function to build the [FunctionalHistogram] into a JSONObject for sending in the
     * ping payload.
     */
    internal fun toJsonPayloadObject(): JSONObject {
        val completeValues = if (values.size != 0) {
            // A bucket range is defined by its own key, and the key of the next
            // highest bucket. This explicitly adds any empty buckets (even if they have values
            // of 0) between the lowest and highest bucket so that the backend knows the
            // bucket ranges even without needing to know that function that was used to
            // create the buckets.
            val minBucket = sampleToBucketIndex(values.keys.min()!!)
            val maxBucket = sampleToBucketIndex(values.keys.max()!!) + 1

            var completeValues: MutableMap<String, Long> = mutableMapOf()

            for (i in minBucket..maxBucket) {
                val bucketMinimum = bucketIndexToBucketMinimum(i)
                val bucketSum = values.get(bucketMinimum)?.let { it } ?: 0
                completeValues[bucketMinimum.toString()] = bucketSum
            }

            completeValues
        } else {
            values
        }

        return JSONObject(mapOf(
            "values" to completeValues,
            "sum" to sum
        ))
    }
}
