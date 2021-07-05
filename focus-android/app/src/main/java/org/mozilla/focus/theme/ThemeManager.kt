/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.theme

import android.content.Context
import android.util.TypedValue

object ThemeManager {
    fun resolveAttribute(attribute: Int, context: Context): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attribute, typedValue, true)

        return typedValue.resourceId
    }
}
