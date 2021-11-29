/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon
import mozilla.components.ui.colors.PhotonColors
import org.mozilla.fenix.components.components

/**
 * Load and display the favicon of a particular website.
 *
 * @param url Website [URL] for which the favicon will be shown.
 * @param size [Dp] height and width of the image to be loaded.
 * @param isPrivate Whether or not a private request (like in private browsing) should be used to
 * download the icon (if needed).
 */
@Composable
fun Favicon(
    url: String,
    size: Dp,
    isPrivate: Boolean = false
) {
    components.core.icons.Loader(
        url = url,
        isPrivate = isPrivate,
        size = size.toIconRequestSize()
    ) {
        Placeholder {
            Box(
                modifier = Modifier.background(
                    color = when (isSystemInDarkTheme()) {
                        true -> PhotonColors.DarkGrey30
                        false -> PhotonColors.LightGrey30
                    }
                )
            )
        }

        WithIcon { icon ->
            Image(
                painter = icon.painter,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(2.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun Dp.toIconRequestSize() = when {
    value <= dimensionResource(IconRequest.Size.DEFAULT.dimen).value -> IconRequest.Size.DEFAULT
    value <= dimensionResource(IconRequest.Size.LAUNCHER.dimen).value -> IconRequest.Size.LAUNCHER
    else -> IconRequest.Size.LAUNCHER_ADAPTIVE
}
