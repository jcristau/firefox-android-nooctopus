/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.creditcards

import mozilla.components.concept.storage.CreditCard
import org.mozilla.fenix.settings.creditcards.CreditCardEditorFragment.Companion.NUMBER_OF_YEARS_TO_SHOW
import java.util.Calendar

/**
 * The state for the [CreditCardEditorFragment].
 *
 * @property billingName The credit card billing name to display.
 * @property cardNumber The credit card number to display.
 * @property expiryMonth The selected credit card expiry month.
 * @property expiryYears The range of expiry years to display.
 * @property isEditing Whether or not the credit is being edited.
 */
data class CreditCardEditorState(
    val billingName: String = "",
    val cardNumber: String = "",
    val expiryMonth: Int = 1,
    val expiryYears: Pair<Int, Int>,
    val isEditing: Boolean = false
)

/**
 * Returns a [CreditCardEditorState] from the given [CreditCard].
 */
fun CreditCard.toCreditCardEditorState(): CreditCardEditorState {
    val startYear = expiryYear.toInt()
    val endYear = startYear + NUMBER_OF_YEARS_TO_SHOW

    return CreditCardEditorState(
        billingName = billingName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth.toInt(),
        expiryYears = Pair(startYear, endYear),
        isEditing = true
    )
}

/**
 * Returns the initial credit editor state if no credit card is provided.
 *
 * @return an empty [CreditCardEditorState] with a range of expiry years based on the latest
 * 10 years.
 */
fun getInitialCreditCardEditorState(): CreditCardEditorState {
    val calendar = Calendar.getInstance()
    val startYear = calendar.get(Calendar.YEAR)
    val endYear = startYear + NUMBER_OF_YEARS_TO_SHOW

    return CreditCardEditorState(
        expiryYears = Pair(startYear, endYear)
    )
}
