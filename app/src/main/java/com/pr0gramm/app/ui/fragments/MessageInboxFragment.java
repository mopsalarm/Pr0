package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.sync.SyncBroadcastReceiver;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.MessageAdapter;

import java.util.List;

import javax.inject.Inject;

/**
 */
public class MessageInboxFragment extends InboxFragment<Message> {
    @Inject
    NotificationService notificationService;

    @Override
    protected LoaderHelper<List<Message>> newLoaderHelper() {
        return LoaderHelper.of(() -> {
            InboxType type = getInboxType();
            if (type == InboxType.UNREAD)
                return getInboxService().getUnreadMessages();

            if (type == InboxType.ALL)
                return getInboxService().getInbox();

            throw new IllegalArgumentException();
        });
    }

    @Override
    protected void displayMessages(RecyclerView recyclerView, List<Message> messages) {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof MessageAdapter) {
            ((MessageAdapter) adapter).setMessages(messages);
        } else {
            recyclerView.setAdapter(newMessageAdapter(messages));
        }

        // after showing messages, we'll just remove unread messages and resync
        if (getActivity() != null) {
            notificationService.cancelForInbox();
            SyncBroadcastReceiver.syncNow(getActivity());
        }
    }

    protected MessageAdapter newMessageAdapter(List<Message> messages) {
        return new MessageAdapter(
                getActivity(), messages, actionListener, R.layout.row_inbox_message);
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

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}
