package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.MessageAdapter;

import java.util.List;

import rx.Observable;

/**
 */
public class MessageInboxFragment extends InboxFragment<Message> {
    @Override
    protected Observable<List<Message>> newMessageObservable() {
        InboxType type = getInboxType();
        if (type == InboxType.UNREAD)
            return getInboxService().getUnreadMessages();

        if (type == InboxType.ALL)
            return getInboxService().getInbox();

        throw new IllegalArgumentException();
    }

    @Override
    protected MessageAdapter newAdapter(List<Message> messages) {
        return new MessageAdapter(getActivity(), messages, actionListener, R.layout.inbox_message);
    }

    /**
     * Builds arguments to create a fragment for the given type.
     *
     * @param inboxType The inbox type to create the fragment for.
     */
    public static Bundle buildArguments(InboxType inboxType) {
        Bundle args = new Bundle();
        args.putInt(ARG_INBOX_TYPE, inboxType.ordinal());
        return args;
    }
}
