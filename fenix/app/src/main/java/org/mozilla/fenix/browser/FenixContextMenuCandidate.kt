/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.content.Context
import android.view.View
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.DefaultSnackbarDelegate
import mozilla.components.feature.tabs.TabsUseCases

class FenixContextMenuCandidate {
    companion object {
        /**
         * Returns the default list of context menu candidates.
         *
         * We are removing open image in new tab temporarily see https://github.com/mozilla-mobile/fenix/issues/2312
         */
        fun defaultCandidates(
            context: Context,
            tabsUseCases: TabsUseCases,
            snackBarParentView: View,
            snackbarDelegate: ContextMenuCandidate.SnackbarDelegate = DefaultSnackbarDelegate()
        ): List<ContextMenuCandidate> = listOf(
            ContextMenuCandidate.createOpenInNewTabCandidate(
                context,
                tabsUseCases,
                snackBarParentView,
                snackbarDelegate
            ),
            ContextMenuCandidate.createOpenInPrivateTabCandidate(
                context,
                tabsUseCases,
                snackBarParentView,
                snackbarDelegate
            ),
            ContextMenuCandidate.createCopyLinkCandidate(context, snackBarParentView, snackbarDelegate),
            ContextMenuCandidate.createShareLinkCandidate(context),
            ContextMenuCandidate.createSaveImageCandidate(context),
            ContextMenuCandidate.createCopyImageLocationCandidate(context, snackBarParentView, snackbarDelegate)
        )
    }
}
