/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.library.bookmarks

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import org.junit.Assert.assertSame
import org.junit.Test

class BookmarkStoreTest {

    @Test
    fun `change the tree of bookmarks starting from an empty tree`() = runBlocking {
        val initialState = BookmarkState(null)
        val store = BookmarkStore(initialState)

        assertThat(BookmarkState(null, BookmarkState.Mode.Normal)).isEqualTo(store.state)

        store.dispatch(BookmarkAction.Change(tree)).join()

        assertThat(initialState.copy(tree = tree)).isEqualTo(store.state)
    }

    @Test
    fun `change the tree of bookmarks starting from an existing tree`() = runBlocking {
        val initialState = BookmarkState(tree)
        val store = BookmarkStore(initialState)

        assertThat(BookmarkState(tree, BookmarkState.Mode.Normal)).isEqualTo(store.state)

        store.dispatch(BookmarkAction.Change(newTree)).join()

        assertThat(initialState.copy(tree = newTree)).isEqualTo(store.state)
    }

    @Test
    fun `change the tree of bookmarks to the same value`() = runBlocking {
        val initialState = BookmarkState(tree)
        val store = BookmarkStore(initialState)

        assertThat(BookmarkState(tree, BookmarkState.Mode.Normal)).isEqualTo(store.state)

        store.dispatch(BookmarkAction.Change(tree)).join()

        assertSame(initialState, store.state)
    }

    @Test
    fun `ensure selected items remain selected after a tree change`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(item, subfolder)))
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Change(newTree)).join()

        assertThat(BookmarkState(newTree, BookmarkState.Mode.Selecting(setOf(subfolder)))).isEqualTo(store.state)
    }

    @Test
    fun `select and deselect bookmarks changes the mode`() = runBlocking {
        val initialState = BookmarkState(tree)
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Select(childItem)).join()

        assertThat(BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(childItem)))).isEqualTo(store.state)

        store.dispatch(BookmarkAction.Deselect(childItem)).join()

        assertThat(BookmarkState(tree, BookmarkState.Mode.Normal)).isEqualTo(store.state)
    }

    @Test
    fun `selecting the same item twice does nothing`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(item, subfolder)))
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Select(item)).join()

        assertSame(initialState, store.state)
    }

    @Test
    fun `deselecting an unselected bookmark does nothing`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(childItem)))
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Deselect(item)).join()

        assertSame(initialState, store.state)
    }

    @Test
    fun `deselecting while not in selecting mode does nothing`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Normal)
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Deselect(item)).join()

        assertSame(initialState, store.state)
    }

    @Test
    fun `deselect all bookmarks changes the mode`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(item, childItem)))
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.DeselectAll).join()

        assertThat(initialState.copy(mode = BookmarkState.Mode.Normal)).isEqualTo(store.state)
    }

    @Test
    fun `deselect all bookmarks when none are selected`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Normal)
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.DeselectAll)

        assertSame(initialState, store.state)
    }

    @Test
    fun `deleting bookmarks changes the mode`() = runBlocking {
        val initialState = BookmarkState(tree, BookmarkState.Mode.Selecting(setOf(item, childItem)))
        val store = BookmarkStore(initialState)

        store.dispatch(BookmarkAction.Change(newTree)).join()

        assertThat(initialState.copy(tree = newTree, mode = BookmarkState.Mode.Normal)).isEqualTo(store.state)
    }

    private val item = BookmarkNode(BookmarkNodeType.ITEM, "456", "123", 0, "Mozilla", "http://mozilla.org", null)
    private val separator = BookmarkNode(BookmarkNodeType.SEPARATOR, "789", "123", 1, null, null, null)
    private val subfolder = BookmarkNode(BookmarkNodeType.FOLDER, "987", "123", 0, "Subfolder", null, listOf())
    private val childItem = BookmarkNode(
        BookmarkNodeType.ITEM,
        "987",
        "123",
        2,
        "Firefox",
        "https://www.mozilla.org/en-US/firefox/",
        null
    )
    private val tree = BookmarkNode(
        BookmarkNodeType.FOLDER, "123", null, 0, "Mobile", null, listOf(item, separator, childItem, subfolder)
    )
    private val newTree = BookmarkNode(
        BookmarkNodeType.FOLDER,
        "123",
        null,
        0,
        "Mobile",
        null,
        listOf(separator, subfolder)
    )
}
