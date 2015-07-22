package com.pr0gramm.app.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.OptionMenuHelper;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RxRoboFragment;
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.gparcel.MessageParcelAdapter;
import com.pr0gramm.app.gparcel.core.ParcelAdapter;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.MessageView;
import com.pr0gramm.app.ui.SimpleTextWatcher;

import roboguice.inject.InjectView;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.app.AppObservable.bindActivity;

/**
 * You can write a new message using this fragment. You might even
 * give some context - like a message to answer to.
 */
public class WriteMessageFragment extends RxRoboFragment {
    public static final String ARGUMENT_MESSAGE = "WriteMessageFragment.message";
    public static final String ARGUMENT_USER_ID = "WriteMessageFragment.userId";
    public static final String ARGUMENT_USER_NAME = "WriteMessageFragment.userName";
    public static final String ARGUMENT_ANSWER_COMMENT = "WriteMessageFragment.answerComment";
    public static final String ARGUMENT_COMMENT_ID = "WriteMessageFragment.commentId";
    public static final String ARGUMENT_ITEM_ID = "WriteMessageFragment.itemId";

    @Inject
    private InboxService inboxService;

    @Inject
    private VoteService voteService;

    @InjectView(R.id.message_view)
    private MessageView messageView;

    @InjectView(R.id.new_message_text)
    private TextView messageText;

    @InjectView(R.id.submit)
    private Button buttonSubmit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        getActivity().setTitle(getString(R.string.write_message_title, getReceiverName()));
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_write_message, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateMessageView();

        int primary = getResources().getColor(R.color.primary);
        ViewCompat.setBackgroundTintList(buttonSubmit, ColorStateList.valueOf(primary));
        buttonSubmit.setOnClickListener(v -> sendMessageNow());

        messageText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean empty = s.toString().trim().isEmpty();
                buttonSubmit.setEnabled(!empty);

                getActivity().supportInvalidateOptionsMenu();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_write_message, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.action_send);
        if (item != null)
            item.setEnabled(buttonSubmit != null && buttonSubmit.isEnabled());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return OptionMenuHelper.dispatch(this, item);
    }

    @OptionMenuHelper.OnOptionsItemSelected(R.id.action_send)
    public void sendMessageNow() {
        String message = getMessageText();
        if (message.isEmpty()) {
            DialogBuilder.start(getActivity())
                    .content(R.string.message_must_not_be_empty)
                    .positive(R.string.okay)
                    .show();

            return;
        }

        if(isCommentAnswer()) {
            long parentComment = getParentCommentId();
            long itemId = getItemId();

            bindActivity(getActivity(), voteService.postComment(itemId, parentComment, message))
                    .lift(busyDialog(this))
                    .doOnCompleted(this::dismiss)
                    .subscribe(comments -> {
                        // FIXME Use eventbus or something to inform post fragment
                    }, defaultOnError());

        } else {
            // now send message
            bindActivity(getActivity(), inboxService.send(getReceiverId(), message))
                    .lift(busyDialog(getActivity()))
                    .doOnCompleted(this::dismiss)
                    .subscribe(Actions.empty(), defaultOnError());
        }
    }

    private String getMessageText() {
        return messageText.getText().toString().trim();
    }

    public void dismiss() {
        FragmentActivity activity = getActivity();
        if (activity != null)
            activity.finish();
    }

    private void updateMessageView() {
        // hide view by default and only show, if we found data
        messageView.setVisibility(View.GONE);

        Bundle arguments = getArguments();
        if (arguments == null)
            return;

        Message message = ParcelAdapter.get(MessageParcelAdapter.class, arguments, ARGUMENT_MESSAGE);
        if (message != null) {
            messageView.update(message);
            messageView.setVisibility(View.VISIBLE);
        }
    }

    private String getReceiverName() {
        return getArguments().getString(ARGUMENT_USER_NAME);
    }

    private long getReceiverId() {
        return getArguments().getLong(ARGUMENT_USER_ID);
    }

    private boolean isCommentAnswer() {
        return getArguments().getBoolean(ARGUMENT_ANSWER_COMMENT, false);
    }

    private long getParentCommentId() {
        return getArguments().getLong(ARGUMENT_COMMENT_ID);
    }

    private long getItemId() {
        return getArguments().getLong(ARGUMENT_ITEM_ID);
    }

    public static WriteMessageFragment newInstance() {
        return new WriteMessageFragment();
    }

    public static Bundle newArguments(Message message) {
        Bundle args = newArguments(message.getSenderId(), message.getName());
        args.putParcelable(ARGUMENT_MESSAGE, new MessageParcelAdapter(message));
        return args;
    }

    public static Bundle newArguments(long userId, String userName) {
        Bundle args = new Bundle();
        args.putLong(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_USER_NAME, userName);
        return args;
    }

    public static Bundle newArguments(FeedItem feedItem, Comment comment) {
        Bundle args = newArguments(Message.of(feedItem, comment));
        args.putBoolean(ARGUMENT_ANSWER_COMMENT, true);
        args.putLong(ARGUMENT_COMMENT_ID, comment.getId());
        args.putLong(ARGUMENT_ITEM_ID, feedItem.getId());
        return args;
    }
}
