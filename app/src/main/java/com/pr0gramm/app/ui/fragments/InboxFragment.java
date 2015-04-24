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
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.MessageAdapter;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(InboxFragment.class);
    private static final String ARG_INBOX_TYPE = "InboxFragment.inboxType";

    @Inject
    private UserService userService;

    @Inject
    private InboxService inboxService;

    @Inject
    private Picasso picasso;

    @InjectView(R.id.messages)
    private RecyclerView messagesView;

    @InjectView(android.R.id.empty)
    private View viewNothingHere;

    private Observable<List<Message>> messages;

    boolean overrideLazyLoading;

    public InboxFragment() {
        setRetainInstance(true);
    }

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

        if (messages != null || !isLazyLoading()) {
            loadInboxContent();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        messagesView = null;
        viewNothingHere = null;
    }

    private boolean isLazyLoading() {
        return !overrideLazyLoading;// && getInboxType() == InboxType.UNREAD;
    }

    private void hideNothingHereIndicator() {
        if (hasView()) {
            viewNothingHere.setVisibility(View.GONE);
        }
    }

    private void showNothingHereIndicator() {
        if (hasView()) {
            viewNothingHere.setVisibility(View.VISIBLE);
            viewNothingHere.setAlpha(0);
            viewNothingHere.animate().alpha(1).start();
        }
    }

    private void loadInboxContent() {
        hideNothingHereIndicator();

        // initialize the message observable
        if (messages == null) {
            logger.info("Query messages of type {}", getInboxType());
            messages = newMessageObservable(inboxService, getInboxType()).cache();
        }

        // only bind if we have a ui.
        if (hasView()) {
            logger.info("Subscribe to the messages of type {}", getInboxType());
            bindFragment(this, messages).subscribe(this::onMessagesLoaded, defaultOnError());
        }
    }

    private boolean hasView() {
        return messagesView != null;
    }

    public void loadIfLazy() {
        if(inboxService == null) {
            overrideLazyLoading = true;

        } else if (messages == null && isLazyLoading()) {
            loadInboxContent();
        }
    }

    private void onMessagesLoaded(List<Message> messages) {
        messagesView.setAdapter(new MessageAdapter(getActivity(), messages));

        if (messages.isEmpty())
            showNothingHereIndicator();
    }

    private InboxType getInboxType() {
        InboxType type = InboxType.ALL;
        Bundle args = getArguments();
        if (args != null) {
            type = InboxType.values()[args.getInt(ARG_INBOX_TYPE, InboxType.ALL.ordinal())];
        }

        return type;
    }

    private static Observable<List<Message>> newMessageObservable(InboxService inboxService, InboxType inboxType) {
        switch (inboxType) {
            case UNREAD:
                return inboxService.getUnreadMessages();

            case PRIVATE:
                return Observable.empty();

            case ALL:
            default:
                return inboxService.getInbox();
        }
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
