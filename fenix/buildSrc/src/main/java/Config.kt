import org.gradle.api.Project
import java.lang.Math.pow
import java.lang.RuntimeException

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

object Config {
    // Synchronized build configuration for all modules
    const val compileSdkVersion = 28
    const val minSdkVersion = 21
    const val targetSdkVersion = 28

    @JvmStatic
    private fun generateDebugVersionName(): String {
        val today = Date()
        // Append the year (2 digits) and week in year (2 digits). This will make it easier to distinguish versions and
        // identify ancient versions when debugging issues. However this will still keep the same version number during
        // the week so that we do not end up with a lot of versions in tools like Sentry. As an extra this matches the
        // sections we use in the changelog (weeks).
        return SimpleDateFormat("1.0.yyww", Locale.US).format(today)
    }

    @JvmStatic
    fun releaseVersionName(project: Project): String {
        // This function is called in the configuration phase, before gradle knows which variants we'll use.
        // So, validation that "versionName" has been set happens elsewhere (at time of writing, we staple
        // validation to tasks of type "AppPreBuildTask"
        return if (project.hasProperty("versionName")) project.property("versionName") as String else ""
    }

    @JvmStatic
    fun generateBuildDate(): String {
        val dateTime = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        return "${dateTime.dayOfWeek.toString().toLowerCase().capitalize()} ${dateTime.monthValue}/${dateTime.dayOfMonth} @ ${timeFormatter.format(dateTime)}"
    }

    private val fennecBaseVersionCode by lazy {
        val format = SimpleDateFormat("YYYYMMDDHHMMSS", Locale.US)
        val cutoff = format.parse("20150801000000")
        val build = Date()

        Math.floor((build.time - cutoff.time) / (1000.0 * 60.0 * 60.0)).toInt()
    }

    /**
     * Generates a versionCode that follows the same rules like legacy Fennec builds.
     * Adapted from:
     * https://searchfox.org/mozilla-central/rev/34cb8d0a2a324043bcfc2c56f37b31abe7fb23a8/python/mozbuild/mozbuild/android_version_code.py
     */
    @JvmStatic
    fun generateFennecVersionCode(abi: String): Int {
        // The important consideration is that version codes be monotonically
        // increasing (per Android package name) for all published builds.  The input
        // build IDs are based on timestamps and hence are always monotonically
        // increasing.
        //
        //         The generated v1 version codes look like (in binary):
        //
        // 0111 1000 0010 tttt tttt tttt tttt txpg
        //
        // The 17 bits labelled 't' represent the number of hours since midnight on
        // September 1, 2015.  (2015090100 in YYYYMMMDDHH format.)  This yields a
        // little under 15 years worth of hourly build identifiers, since 2**17 / (366
        //         * 24) =~ 14.92.
        //
        //         The bits labelled 'x', 'p', and 'g' are feature flags.
        //
        // The bit labelled 'x' is 1 if the build is for an x86 or x86-64 architecture,
        // and 0 otherwise, which means the build is for an ARM or ARM64 architecture.
        // (Fennec no longer supports ARMv6, so ARM is equivalent to ARMv7.
        //
        //         ARM64 is also known as AArch64; it is logically ARMv8.)
        //
        // For the same release, x86 and x86_64 builds have higher version codes and
        // take precedence over ARM builds, so that they are preferred over ARM on
        // devices that have ARM emulation.
        //
        // The bit labelled 'p' is 1 if the build is for a 64-bit architecture (x86-64
        //         or ARM64), and 0 otherwise, which means the build is for a 32-bit
        // architecture (x86 or ARM). 64-bit builds have higher version codes so
        // they take precedence over 32-bit builds on devices that support 64-bit.
        //
        //         The bit labelled 'g' is 1 was used for APK splits and is
        //         nowadays always set to 1 until it serves a new purpose.
        //
        // We throw an explanatory exception when we are within one calendar year of
        // running out of build events.  This gives lots of time to update the version
        // scheme.  The responsible individual should then bump the range (to allow
        //         builds to continue) and use the time remaining to update the version scheme
        // via the reserved high order bits.
        //
        //         N.B.: the reserved 0 bit to the left of the highest order 't' bit can,
        //         sometimes, be used to bump the version scheme.  In addition, by reducing the
        // granularity of the build identifiers (for example, moving to identifying
        // builds every 2 or 4 hours), the version scheme may be adjusted further still
        // without losing a (valuable) high order bit.

        val base = fennecBaseVersionCode

        when {
            base < 0 -> throw RuntimeException("Cannot calculate versionCode. Hours underflow.")
            base > pow(2.0, 17.0) -> throw RuntimeException("Cannot calculate versionCode. Hours overflow.")
            base > (pow(2.0, 17.0) - (366 * 24)) ->
                // You have one year to update the version scheme...
                throw RuntimeException("Running out of low order bits calculating versionCode.")
        }

        var version = 0x78200000 // 1111000001000000000000000000000
        // We reserve 1 "middle" high order bit for the future, and 3 low order bits
        // for architecture and APK splits.
        version = version or (base shl  3)


        // 'x' bit is 1 for x86/x86-64 architectures
        if (abi == "x86_64" || abi == "x86") {
            version = version or (1 shl 2)
        }

        // 'p' bit is 1 for 64-bit architectures.
        if (abi == "arm64-v8a" || abi == "x86_64") {
            version = version or (1 shl 1)
        }

        // 'g' bit is currently always 1 (see comment above)
        version = version or (1 shl 0)

        return version
    }
}
