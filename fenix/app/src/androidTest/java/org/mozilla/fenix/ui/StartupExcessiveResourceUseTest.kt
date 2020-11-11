/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.android.synthetic.main.activity_home.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.perf.RunBlockingCounter
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.perf.ComponentInitCount

// BEFORE INCREASING THESE VALUES, PLEASE CONSULT WITH THE PERF TEAM.
private const val EXPECTED_SUPPRESSION_COUNT = 11
private const val EXPECTED_RUNBLOCKING_COUNT = 2
private const val EXPECTED_COMPONENT_INIT_COUNT = 42
private const val EXPECTED_VIEW_HIERARCHY_DEPTH = 12

private val failureMsgStrictMode = getErrorMessage(
    shortName = "StrictMode suppression",
    implications = "suppressing a StrictMode violation can introduce performance regressions?"
)

private val failureMsgRunBlocking = getErrorMessage(
    shortName = "runBlockingIncrement",
    implications = "using runBlocking may block the main thread and have other negative performance implications?"
)

private val failureMsgComponentInit = getErrorMessage(
    shortName = "Component init",
    implications = "initializing new components on start up may be an indication that we're doing more work than necessary on start up?"
)

private val failureMsgViewHierarchyDepth = getErrorMessage(
    shortName = "view hierarchy depth",
    implications = "having a deep view hierarchy can slow down measure/layout performance?"
) + "Please note that we're not sure if this is a useful metric to assert: with your feedback, " +
    "we'll find out over time if it is or is not."

/**
 * A performance test to limit the number of StrictMode suppressions and number of runBlocking used
 * on startup.
 *
 * This test was written by the perf team.
 *
 * StrictMode detects main thread IO, which is often indicative of a performance issue.
 * It's easy to suppress StrictMode so we wrote a test to ensure we have a discussion
 * if the StrictMode count changes.
 *
 * RunBlocking is mostly used to return values to a thread from a coroutine. However, if that
 * coroutine takes too long, it can lead that thread to block every other operations.
 *
 * The perf team is code owners for this file so they should be notified when the count is modified.
 *
 * IF YOU UPDATE THE TEST NAME, UPDATE CODE OWNERS.
 */
class StartupExcessiveResourceUseTest {
    @get:Rule
    val activityTestRule = HomeActivityTestRule(skipOnboarding = true)

    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun verifyRunBlockingAndStrictModeSuppresionCount() {
        uiDevice.waitForIdle() // wait for async UI to load.

        // This might cause intermittents: at an arbitrary point after start up (such as the visual
        // completeness queue), we might run code on the main thread that suppresses StrictMode,
        // causing this number to fluctuate depending on device speed. We'll deal with it if it occurs.
        val actualSuppresionCount = activityTestRule.activity.components.strictMode.suppressionCount.get().toInt()
        val actualRunBlocking = RunBlockingCounter.count.get()
        val actualComponentInitCount = ComponentInitCount.count.get()
        val actualViewHierarchyDepth = countAndLogViewHierarchyDepth(activityTestRule.activity.rootContainer, 1)

        assertEquals(failureMsgStrictMode, EXPECTED_SUPPRESSION_COUNT, actualSuppresionCount)
        assertEquals(failureMsgRunBlocking, EXPECTED_RUNBLOCKING_COUNT, actualRunBlocking)
        assertEquals(failureMsgComponentInit, EXPECTED_COMPONENT_INIT_COUNT, actualComponentInitCount)
        assertEquals(failureMsgViewHierarchyDepth, EXPECTED_VIEW_HIERARCHY_DEPTH, actualViewHierarchyDepth)
    }
}

private fun countAndLogViewHierarchyDepth(view: View, level: Int): Int {
    // Log for debugging purposes: not sure if this is actually helpful.
    val indent = "| ".repeat(level - 1)
    Log.d("Startup...Test", "${indent}$view")

    return if (view !is ViewGroup) {
        level
    } else {
        val maxDepth = view.children.map { countAndLogViewHierarchyDepth(it, level + 1) }.maxOrNull()
        maxDepth ?: level
    }
}

private fun getErrorMessage(shortName: String, implications: String) = """$shortName count does not match expected count.

    If this PR removed a $shortName call, great! Please decrease the count.

    Did this PR add or call code that increases the $shortName count?
    Did you know that $implications
    Please do your best to implement a solution without adding $shortName calls.
    Please consult the perf team if you have questions or believe that having this call
    is the optimal solution.

"""
