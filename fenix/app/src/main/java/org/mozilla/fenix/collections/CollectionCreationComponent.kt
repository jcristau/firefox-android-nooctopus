package org.mozilla.fenix.collections

/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Observable
import org.mozilla.fenix.home.sessioncontrol.Tab
import org.mozilla.fenix.home.sessioncontrol.TabCollection
import org.mozilla.fenix.mvi.Action
import org.mozilla.fenix.mvi.ActionBusFactory
import org.mozilla.fenix.mvi.Change
import org.mozilla.fenix.mvi.Reducer
import org.mozilla.fenix.mvi.UIComponent
import org.mozilla.fenix.mvi.UIComponentViewModel
import org.mozilla.fenix.mvi.ViewState

sealed class SaveCollectionStep {
    object SelectTabs : SaveCollectionStep()
    object SelectCollection : SaveCollectionStep()
    object NameCollection : SaveCollectionStep()
}

data class CollectionCreationState(
    val tabs: List<Tab> = listOf(),
    val selectedTabs: Set<Tab> = setOf(),
    val saveCollectionStep: SaveCollectionStep = SaveCollectionStep.SelectTabs
) : ViewState

sealed class CollectionCreationChange : Change {
    data class TabListChange(val tabs: List<Tab>) : CollectionCreationChange()
    object AddAllTabs : CollectionCreationChange()
    data class TabAdded(val tab: Tab) : CollectionCreationChange()
    data class TabRemoved(val tab: Tab) : CollectionCreationChange()
    data class StepChanged(val saveCollectionStep: SaveCollectionStep) : CollectionCreationChange()
}

sealed class CollectionCreationAction : Action {
    object Close : CollectionCreationAction()
    object SelectAllTapped : CollectionCreationAction()
    object AddNewCollection : CollectionCreationAction()
    data class AddTabToSelection(val tab: Tab) : CollectionCreationAction()
    data class RemoveTabFromSelection(val tab: Tab) : CollectionCreationAction()
    data class SaveTabsToCollection(val tabs: List<Tab>) : CollectionCreationAction()
    data class BackPressed(val backPressFrom: SaveCollectionStep) : CollectionCreationAction()
    data class SaveCollectionName(val tabs: List<Tab>, val name: String) :
        CollectionCreationAction()

    data class SelectCollection(val collection: TabCollection) :
        CollectionCreationAction()
}

class CollectionCreationComponent(
    private val container: ViewGroup,
    owner: Fragment,
    bus: ActionBusFactory,
    override var initialState: CollectionCreationState = CollectionCreationState()
) : UIComponent<CollectionCreationState, CollectionCreationAction, CollectionCreationChange>(
    owner,
    bus.getManagedEmitter(CollectionCreationAction::class.java),
    bus.getSafeManagedObservable(CollectionCreationChange::class.java)
) {
    override fun initView() = CollectionCreationUIView(container, actionEmitter, changesObservable)

    override fun render(): Observable<CollectionCreationState> =
        ViewModelProvider(owner, CollectionCreationViewModel.Factory(initialState, changesObservable)).get(
            CollectionCreationViewModel::class.java
        ).render(uiView)

    init {
        render()
    }
}

class CollectionCreationViewModel(
    initialState: CollectionCreationState,
    changesObservable: Observable<CollectionCreationChange>
) :
    UIComponentViewModel<CollectionCreationState, CollectionCreationAction, CollectionCreationChange>(
        initialState,
        changesObservable,
        reducer
    ) {

    class Factory(
        private val initialState: CollectionCreationState,
        private val changesObservable: Observable<CollectionCreationChange>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            CollectionCreationViewModel(initialState, changesObservable) as T
    }

    companion object {
        val reducer: Reducer<CollectionCreationState, CollectionCreationChange> =
            { state, change ->
                when (change) {
                    is CollectionCreationChange.AddAllTabs -> state.copy(selectedTabs = state.tabs.toSet())
                    is CollectionCreationChange.TabListChange -> state.copy(tabs = change.tabs)
                    is CollectionCreationChange.TabAdded -> {
                        val selectedTabs = state.selectedTabs + setOf(change.tab)
                        state.copy(selectedTabs = selectedTabs)
                    }
                    is CollectionCreationChange.TabRemoved -> {
                        val selectedTabs = state.selectedTabs - setOf(change.tab)
                        state.copy(selectedTabs = selectedTabs)
                    }
                    is CollectionCreationChange.StepChanged -> {
                        state.copy(saveCollectionStep = change.saveCollectionStep)
                    }
                }
            }
    }
}
