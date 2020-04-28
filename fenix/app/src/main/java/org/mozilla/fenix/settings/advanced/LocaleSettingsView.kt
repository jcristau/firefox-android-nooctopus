/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.advanced

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.component_locale_settings.view.*
import org.mozilla.fenix.R
import java.util.Locale

interface LocaleSettingsViewInteractor {

    fun onLocaleSelected(locale: Locale)

    fun onDefaultLocaleSelected()

    fun onSearchQueryTyped(query: String)
}

class LocaleSettingsView(
    container: ViewGroup,
    val interactor: LocaleSettingsViewInteractor
) {

    val view: View = LayoutInflater.from(container.context)
        .inflate(R.layout.component_locale_settings, container, true)

    private val localeAdapter: LocaleAdapter

    init {
        view.locale_list.apply {
            localeAdapter = LocaleAdapter(interactor)
            adapter = localeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    fun update(state: LocaleSettingsState) {
        localeAdapter.updateData(state.searchedLocaleList, state.selectedLocale)
    }

    fun onResume() {
        view.requestFocus()
    }
}
