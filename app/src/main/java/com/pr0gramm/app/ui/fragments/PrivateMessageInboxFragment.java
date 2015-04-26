package com.pr0gramm.app.ui.fragments;

import com.pr0gramm.app.api.pr0gramm.response.PrivateMessage;
import com.pr0gramm.app.ui.PrivateMessageAdapter;

import java.util.List;

import rx.Observable;

/**
 */
public class PrivateMessageInboxFragment extends InboxFragment<PrivateMessage> {
    @Override
    protected Observable<List<PrivateMessage>> newMessageObservable() {
        return getInboxService().getPrivateMessages();
    }

    @Override
    protected PrivateMessageAdapter newAdapter(List<PrivateMessage> messages) {
        return new PrivateMessageAdapter(getActivity(), messages, actionListener);
    }
}
