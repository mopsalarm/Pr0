package com.pr0gramm.app.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.MessageActionListener;
import com.pr0gramm.app.ui.WriteMessageActivity;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.squareup.picasso.Picasso;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static org.joda.time.Duration.standardMinutes;

/**
 */
public abstract class InboxFragment<T> extends BaseFragment {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected static final String ARG_INBOX_TYPE = "InboxFragment.inboxType";

    @Inject
    InboxService inboxService;

    @Inject
    Picasso picasso;

    @Bind(R.id.messages)
    RecyclerView messagesView;

    @Bind(android.R.id.empty)
    View viewNothingHere;

    @Bind(R.id.busy_indicator)
    View viewBusyIndicator;

    @Bind(R.id.refresh)
    SwipeRefreshLayout swipeRefreshLayout;

    private LoaderHelper<List<T>> loader;
    private Instant loadStartedTimestamp;

    public InboxFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.loader = newLoaderHelper();
        this.loader.reload();
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

        swipeRefreshLayout.setOnRefreshListener(this::reloadInboxContent);
        swipeRefreshLayout.setColorSchemeResources(R.color.orange_primary);

        showBusyIndicator();

        // load the messages
        loadStartedTimestamp = Instant.now();
        loader.load(this::onMessagesLoaded, defaultOnError(), this::hideBusyIndicator);
    }

    @Override
    public void onResume() {
        super.onResume();

        // reload if re-started after one minute
        if (loadStartedTimestamp.plus(standardMinutes(1)).isBeforeNow()) {
            loadStartedTimestamp = Instant.now();
            reloadInboxContent();
        }
    }

    @Override
    public void onDestroyView() {
        loader.detach();
        super.onDestroyView();
    }

    private void reloadInboxContent() {
        loader.reload();
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

    private void showBusyIndicator() {
        if (hasView() && viewBusyIndicator != null) {
            viewBusyIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void hideBusyIndicator() {
        if (hasView()) {
            if (viewBusyIndicator != null) {
                viewBusyIndicator.setVisibility(View.GONE);
                ViewParent parent = viewBusyIndicator.getParent();
                ((ViewGroup) parent).removeView(viewBusyIndicator);

                viewBusyIndicator = null;
            }

            swipeRefreshLayout.setRefreshing(false);
        }
    }

    protected abstract LoaderHelper<List<T>> newLoaderHelper();

    private boolean hasView() {
        return messagesView != null;
    }

    private void onMessagesLoaded(List<T> messages) {
        hideBusyIndicator();
        hideNothingHereIndicator();

        // replace previous adapter with new values
        displayMessages(messagesView, messages);

        if (messages.isEmpty())
            showNothingHereIndicator();
    }

    protected abstract void displayMessages(RecyclerView recyclerView, List<T> messages);

    protected InboxType getInboxType() {
        InboxType type = InboxType.ALL;
        Bundle args = getArguments();
        if (args != null) {
            type = InboxType.values()[args.getInt(ARG_INBOX_TYPE, InboxType.ALL.ordinal())];
        }

        return type;
    }

    protected InboxService getInboxService() {
        return inboxService;
    }

    protected final MessageActionListener actionListener = new MessageActionListener() {
        @Override
        public void onAnswerToPrivateMessage(Message message) {
            startActivity(WriteMessageActivity.intent(getActivity(), message));
        }

        @Override
        public void onAnswerToCommentClicked(Message message) {
            startActivity(WriteMessageActivity.answerToComment(getActivity(), message));
        }

        @Override
        public void onNewPrivateMessage(long userId, String name) {
            startActivity(WriteMessageActivity.intent(getActivity(), userId, name));
        }

        @Override
        public void onCommentClicked(long itemId, long commentId) {
            open(UriHelper.of(getActivity()).post(FeedType.NEW, itemId, commentId));
        }

        private void open(Uri uri) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri, getActivity(), MainActivity.class);
            startActivity(intent);
        }

        @Override
        public void onUserClicked(int userId, String username) {
            open(UriHelper.of(getActivity()).uploads(username));
        }
    };
}
