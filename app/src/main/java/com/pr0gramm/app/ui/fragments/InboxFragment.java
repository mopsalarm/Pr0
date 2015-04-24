package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.MessageAdapter;
import com.squareup.picasso.Picasso;

import java.util.List;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class InboxFragment extends RoboFragment {
    private static final String ARG_INBOX_TYPE = "InboxFragment.inboxType";

    @Inject
    private UserService userService;

    @Inject
    private InboxService inboxService;

    @Inject
    private Picasso picasso;

    @InjectView(R.id.messages)
    private RecyclerView messagesView;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        messagesView.setItemAnimator(null);
        messagesView.setLayoutManager(new LinearLayoutManager(getActivity()));

        if (!isLazyLoading()) {
            reloadInboxContent();
        }
    }

    public void reloadInboxContent() {
        Observable<List<Message>> messages = newMessageObservable();
        bindFragment(this, messages).subscribe(this::onMessagesLoaded, defaultOnError());
    }

    private boolean isLazyLoading() {
        return getInboxType() == InboxType.UNREAD;
    }

    private Observable<List<Message>> newMessageObservable() {
        switch (getInboxType()) {
            case UNREAD:
                return inboxService.getUnreadMessages();

            case PRIVATE:
                return Observable.empty();

            case ALL:
            default:
                return inboxService.getInbox();
        }
    }

    private void onMessagesLoaded(List<Message> messages) {
        messagesView.setAdapter(new MessageAdapter(getActivity(), messages));
    }

    private InboxType getInboxType() {
        InboxType type = InboxType.ALL;
        Bundle args = getArguments();
        if (args != null) {
            type = InboxType.values()[args.getInt(ARG_INBOX_TYPE, InboxType.ALL.ordinal())];
        }

        return type;
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
