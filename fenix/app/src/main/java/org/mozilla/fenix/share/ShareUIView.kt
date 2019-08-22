/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.share

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.component_share.*
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.mvi.UIView

class ShareUIView(
    container: ViewGroup,
    actionEmitter: Observer<ShareAction>,
    changesObservable: Observable<ShareChange>
) : UIView<ShareState, ShareAction, ShareChange>(
    container,
    actionEmitter,
    changesObservable
) {
    override val view: View = LayoutInflater.from(container.context)
        .inflate(R.layout.component_share, container, true)

    init {
        val adapter = AppShareAdapter(view.context, actionEmitter).also {
            it.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    progressBar.visibility = View.GONE
                    appsList.visibility = View.VISIBLE
                }
            })
        }
        appsList.adapter = adapter

        if (view.context.components.backgroundServices.accountManager.accountNeedsReauth()) {
            sendTabGroup.visibility = View.GONE
            accountHeaderText.visibility = View.GONE
        } else {
            devicesList.adapter = AccountDevicesShareAdapter(view.context, actionEmitter)
        }

        container.setOnClickListener { actionEmitter.onNext(ShareAction.Close) }
        closeButton.setOnClickListener { actionEmitter.onNext(ShareAction.Close) }
    }

    override fun updateView() = Consumer<ShareState> {
        ShareState
    }
}
