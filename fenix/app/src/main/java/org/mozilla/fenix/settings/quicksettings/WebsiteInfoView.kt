/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.quicksettings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.quicksettings_website_info.*
import mozilla.components.support.ktx.android.content.getDrawableWithTint
import org.mozilla.fenix.R

/**
 * MVI View that knows to display a whether the current website uses a secure connection or not.
 *
 * Currently it does not support any user interaction.
 *
 * @param container [ViewGroup] in which this View will inflate itself.
 */
class WebsiteInfoView(
    container: ViewGroup
) : LayoutContainer {

    override val containerView: View = LayoutInflater.from(container.context)
        .inflate(R.layout.quicksettings_website_info, container, true)

    /**
     * Allows changing what this View displays.
     *
     * @param state [WebsiteInfoState] to be rendered.
     */
    fun update(state: WebsiteInfoState) {
        bindUrl(state.websiteUrl)
        bindTitle(state.websiteTitle)
        bindSecurityInfo(state.websiteSecurityUiValues)
        bindCertificateName(state.certificateName)
    }

    private fun bindUrl(websiteUrl: String) {
        url.text = websiteUrl
    }

    private fun bindTitle(websiteTitle: String) {
        title.text = websiteTitle
    }

    private fun bindCertificateName(cert: String) {
        val certificateLabel = containerView.context.getString(R.string.certificate_info_verified_by, cert)
        certificateInfo.text = certificateLabel
        certificateInfo.isVisible = cert.isNotEmpty()
    }

    private fun bindSecurityInfo(uiValues: WebsiteSecurityUiValues) {
        val tint = getColor(containerView.context, uiValues.iconTintRes)
        securityInfo.setText(uiValues.securityInfoRes)
        securityInfoIcon.setImageDrawable(
            containerView.context.getDrawableWithTint(uiValues.iconRes, tint)
        )
    }
}
