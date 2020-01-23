/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import org.mozilla.fenix.utils.StartupTaskManager

/**
 * Component group for all functionality related to performance.
 */
class PerformanceComponent {
    val visualCompletenessTaskManager by lazy { StartupTaskManager() }
}
