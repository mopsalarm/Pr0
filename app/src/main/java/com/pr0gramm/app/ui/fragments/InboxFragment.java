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

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class InboxFragment extends RoboFragment {
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

        bindFragment(this, inboxService.getInbox())
                .subscribe(this::onMessagesLoaded, defaultOnError());
    }

    private void onMessagesLoaded(List<Message> messages) {
        messagesView.setAdapter(new MessageAdapter(getActivity(), messages));
    }
}
