/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.telemetry;

import android.support.annotation.NonNull;

import org.mozilla.focus.architecture.NonNullObserver;
import org.mozilla.focus.session.Session;
import org.mozilla.focus.session.Source;

import java.util.List;

public class TelemetrySessionObserver extends NonNullObserver<List<Session>> {
    @Override
    protected void onValueChanged(@NonNull List<Session> sessions) {
        for (final Session session : sessions) {
            if (!session.isRecorded()) {
                addTelemetryEvent(session);

                session.markAsRecorded();
            }
        }
    }

    private void addTelemetryEvent(final Session session) {
        final Source source = session.getSource();

        switch (source) {
            case VIEW:
                TelemetryWrapper.browseIntentEvent();
                break;

            case SHARE:
                TelemetryWrapper.shareIntentEvent(session.isSearch());
                break;

            case TEXT_SELECTION:
                TelemetryWrapper.textSelectionIntentEvent();
                break;

            case HOME_SCREEN:
                TelemetryWrapper.openHomescreenShortcutEvent();
                break;

            case USER_ENTERED:
                // We report this event from the UI components - The event depends on how the user
                // interacted with the UI.
                break;

            case CUSTOM_TAB:
                TelemetryWrapper.customTabsIntentEvent(session.getCustomTabConfig().getOptionsList());
                break;

            default:
                throw new IllegalStateException("Unknown session source: " + source);
        }
    }
}
