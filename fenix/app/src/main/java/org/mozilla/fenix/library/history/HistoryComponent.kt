/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.fenix.library.history

import android.view.ViewGroup
import org.mozilla.fenix.mvi.Action
import org.mozilla.fenix.mvi.ActionBusFactory
import org.mozilla.fenix.mvi.Change
import org.mozilla.fenix.mvi.UIComponent
import org.mozilla.fenix.mvi.ViewState

data class HistoryItem(val url: String)

class HistoryComponent(
    private val container: ViewGroup,
    bus: ActionBusFactory,
    override var initialState: HistoryState = HistoryState(emptyList())
) :
    UIComponent<HistoryState, HistoryAction, HistoryChange>(
        bus.getManagedEmitter(HistoryAction::class.java),
        bus.getSafeManagedObservable(HistoryChange::class.java)
    ) {

    override val reducer: (HistoryState, HistoryChange) -> HistoryState = { state, change ->
        when (change) {
            is HistoryChange.Change -> state.copy(items = change.list)
        }
    }

    override fun initView() = HistoryUIView(container, actionEmitter, changesObservable)

    init {
        render(reducer)
    }
}

data class HistoryState(val items: List<HistoryItem>) : ViewState

sealed class HistoryAction : Action {
    object Select : HistoryAction()
}

sealed class HistoryChange : Change {
    data class Change(val list: List<HistoryItem>) : HistoryChange()
}
