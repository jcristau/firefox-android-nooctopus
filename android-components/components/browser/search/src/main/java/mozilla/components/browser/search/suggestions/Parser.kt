/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.search.suggestions

import mozilla.components.browser.search.SearchEngine
import org.json.JSONArray
import org.json.JSONObject

/*
 * Convenience method to convert a JSONArray into a sequence.
 */
private fun JSONArray.asSequence(): Sequence<Any> {
    return object : Sequence<Any> {

        override fun iterator() = object : Iterator<Any> {
            val it = (0 until this@asSequence.length()).iterator()

            override fun next(): Any {
                val i = it.next()
                return this@asSequence.get(i)
            }

            override fun hasNext() = it.hasNext()
        }
    }
}

/**
 * The Parser is a function that takes a JSON Response and maps
 * it to a Suggestion list.
 */
typealias JSONResponse = String
typealias ResponseParser = (JSONResponse) -> List<String>

/**
 * Builds a Parser that pulls suggestions out of a given index
 */
private fun buildJSONArrayParser(resultsIndex: Int): ResponseParser {
    return { input ->
        JSONArray(input)
                .getJSONArray(resultsIndex)
                .asSequence()
                .map { it as? String }
                .filterNotNull()
                .toList()
    }
}

/**
 * Builds a Parser that pulls suggestions out of a JSON object with the given key
 */
private fun buildJSONObjectParser(resultsKey: String): ResponseParser {
    return { input ->
        JSONObject(input)
                .getJSONArray(resultsKey)
                .asSequence()
                .map { it as? String }
                .filterNotNull()
                .toList()
    }
}

/**
 * Builds a custom parser for Qwant
 */
private fun buildQwantParser(): ResponseParser {
    return { input ->
        JSONObject(input)
                .getJSONObject("data")
                .getJSONArray("items")
                .asSequence()
                .map { it as? JSONObject }
                .map { it?.getString("value") }
                .filterNotNull()
                .toList()
    }
}

/**
 * The available Parsers
 */
val defaultResponseParser = buildJSONArrayParser(1)
val azerdictResponseParser = buildJSONObjectParser("suggestions")
val daumResponseParser = buildJSONObjectParser("items")
val qwantResponseParser = buildQwantParser()

/**
 * Selects a Parser based on a SearchEngine
 */
fun selectResponseParser(searchEngine: SearchEngine): ResponseParser = when (searchEngine.name) {
    "Azerdict" -> azerdictResponseParser
    "다음지도" -> daumResponseParser
    "Qwant" -> qwantResponseParser
    else -> defaultResponseParser
}
