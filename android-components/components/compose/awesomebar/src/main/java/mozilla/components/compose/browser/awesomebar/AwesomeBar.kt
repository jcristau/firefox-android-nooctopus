/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.awesomebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mozilla.components.concept.awesomebar.AwesomeBar

/**
 * An awesome bar displaying suggestions from the list of provided [AwesomeBar.SuggestionProvider]s.
 */
@Composable
fun AwesomeBar(
    text: String,
    providers: List<AwesomeBar.SuggestionProvider>
) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .height(IntrinsicSize.Max)
            .background(Color.White)
    ) {
        val suggestions = remember { mutableStateOf(emptyList<AwesomeBar.Suggestion>()) }

        LaunchedEffect(text) {
            suggestions.value = providers.flatMap { provider -> provider.onInputChanged(text) }
        }

        Suggestions(suggestions.value)
    }
}

@Composable
private fun Suggestions(suggestions: List<AwesomeBar.Suggestion>) {
    suggestions.forEach { suggestion ->
        Text(
            text = suggestion.title ?: "",
            modifier = Modifier.padding(8.dp)
                .clickable { suggestion.onSuggestionClicked?.invoke() }
        )
    }
}
