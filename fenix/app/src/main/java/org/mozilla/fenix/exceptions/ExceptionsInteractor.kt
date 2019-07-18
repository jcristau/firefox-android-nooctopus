/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.exceptions

/**
 * Interactor for the exceptions screen
 * Provides implementations for the ExceptionsViewInteractor
 */
class ExceptionsInteractor(
    private val learnMore: () -> Unit,
    private val deleteOne: (ExceptionsItem) -> Unit,
    private val deleteAll: () -> Unit
) : ExceptionsViewInteractor {
    override fun onLearnMore() {
        learnMore.invoke()
    }

    override fun onDeleteAll() {
        deleteAll.invoke()
    }

    override fun onDeleteOne(item: ExceptionsItem) {
        deleteOne.invoke(item)
    }
}
