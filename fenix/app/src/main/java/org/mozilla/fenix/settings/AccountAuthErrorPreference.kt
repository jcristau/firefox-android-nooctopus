/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.mozilla.fenix.R

class AccountAuthErrorPreference : Preference {
    var email: String? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, attributeSetId: Int) : super(context, attrs, attributeSetId)

    init {
        layoutResource = R.layout.account_auth_error_preference
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val emailView = holder.findViewById(R.id.email) as TextView
        emailView.text = email.orEmpty()
        emailView.visibility = when (email.isNullOrEmpty()) {
            true -> View.GONE
            false -> View.VISIBLE
        }
    }
}
