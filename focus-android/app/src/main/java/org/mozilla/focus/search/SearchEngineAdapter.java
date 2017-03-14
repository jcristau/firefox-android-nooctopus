/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.mozilla.focus.R;

import java.util.List;

/**
 * Adapter for displaying a list of search engines.
 */
public class SearchEngineAdapter extends BaseAdapter {
    private List<SearchEngine> searchEngines;

    public SearchEngineAdapter() {
        searchEngines = SearchEngineManager.getInstance().getSearchEngines();
    }

    @Override
    public int getCount() {
        return searchEngines.size();
    }

    @Override
    public SearchEngine getItem(int position) {
        return searchEngines.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SearchEngine searchEngine = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_search_engine, parent, false);
        }

        final TextView titleView = (TextView) convertView.findViewById(R.id.title);
        titleView.setText(searchEngine.getName());

        final ImageView iconView = (ImageView) convertView.findViewById(R.id.icon);
        iconView.setImageBitmap(searchEngine.getIcon());

        return convertView;
    }
}
