package com.pr0gramm.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.parcel.MessageParceler;
import com.pr0gramm.app.parcel.NewCommentParceler;
import com.pr0gramm.app.parcel.core.Parceler;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import butterknife.BindView;
import rx.functions.Actions;

import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;

/**
 */
public class WriteMessageActivity extends BaseAppCompatActivity {
    private static final String ARGUMENT_MESSAGE = "WriteMessageFragment.message";
    private static final String ARGUMENT_RECEIVER_ID = "WriteMessageFragment.userId";
    private static final String ARGUMENT_RECEIVER_NAME = "WriteMessageFragment.userName";
    private static final String ARGUMENT_COMMENT_ID = "WriteMessageFragment.commentId";
    private static final String ARGUMENT_ITEM_ID = "WriteMessageFragment.itemId";

    private static final String RESULT_EXTRA_NEW_COMMENT = "WriteMessageFragment.result.newComment";

    @Inject
    InboxService inboxService;

    @Inject
    UserService userService;

    @Inject
    VoteService voteService;

    @Inject
    MetaService metaService;

    @BindView(R.id.message_view)
    MessageView messageView;

    @BindView(R.id.new_message_text)
    LineMultiAutoCompleteTextView messageText;

    @BindView(R.id.submit)
    Button buttonSubmit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_write_message);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayShowHomeEnabled(true);
            actionbar.setDisplayHomeAsUpEnabled(true);
        }

        // set title
        setTitle(getString(R.string.write_message_title, getReceiverName()));

        // and previous message
        updateMessageView();

        buttonSubmit.setOnClickListener(v -> sendMessageNow());

        messageText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean empty = s.toString().trim().isEmpty();
                buttonSubmit.setEnabled(!empty);

                supportInvalidateOptionsMenu();
            }
        });

        messageText.setTokenizer(new UsernameTokenizer());
        messageText.setAdapter(new UsernameAutoCompleteAdapter(metaService, this,
                android.R.layout.simple_dropdown_item_1line));

        messageText.setAnchorView(findViewById(R.id.auto_complete_popup_anchor));

    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return OptionMenuHelper.dispatch(this, item);
    }

    @OnOptionsItemSelected(android.R.id.home)
    @Override
    public void finish() {
        super.finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_write_message, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.action_send);
        if (item != null)
            item.setEnabled(buttonSubmit != null && buttonSubmit.isEnabled());

        return true;
    }

    @OnOptionsItemSelected(R.id.action_send)
    public void sendMessageNow() {
        String message = getMessageText();
        if (message.isEmpty()) {
            DialogBuilder.start(this)
                    .content(R.string.message_must_not_be_empty)
                    .positive()
                    .show();

            return;
        }

        if (isCommentAnswer()) {
            long parentComment = getParentCommentId();
            long itemId = getItemId();

            voteService.postComment(itemId, parentComment, message)
                    .compose(bindToLifecycleAsync())
                    .lift(busyDialog(this))
                    .doOnCompleted(this::finish)
                    .subscribe(newComments -> {
                        Intent result = new Intent();
                        result.putExtra(RESULT_EXTRA_NEW_COMMENT, new NewCommentParceler(newComments));
                        setResult(Activity.RESULT_OK, result);
                    }, defaultOnError());

            Track.writeComment();

        } else {
            // now send message
            inboxService.send(getReceiverId(), message)
                    .compose(bindToLifecycleAsync())
                    .lift(busyDialog(this))
                    .doOnCompleted(this::finish)
                    .subscribe(Actions.empty(), defaultOnError());

            Track.writeMessage();
        }
    }

    private String getMessageText() {
        return messageText.getText().toString().trim();
    }

    private void updateMessageView() {
        // hide view by default and only show, if we found data
        messageView.setVisibility(View.GONE);

        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        if (extras == null)
            return;

        Message message = Parceler.get(MessageParceler.class, extras, ARGUMENT_MESSAGE);
        if (message != null) {
            messageView.update(message, userService.getName().orNull());
            messageView.setVisibility(View.VISIBLE);
        }
    }

    private String getReceiverName() {
        return getIntent().getStringExtra(ARGUMENT_RECEIVER_NAME);
    }

    private long getReceiverId() {
        return getIntent().getLongExtra(ARGUMENT_RECEIVER_ID, 0);
    }

    private boolean isCommentAnswer() {
        return getIntent().hasExtra(ARGUMENT_COMMENT_ID);
    }

    private long getParentCommentId() {
        return getIntent().getLongExtra(ARGUMENT_COMMENT_ID, 0);
    }

    private long getItemId() {
        return getIntent().getLongExtra(ARGUMENT_ITEM_ID, 0);
    }

    public static Intent intent(Context context, Message message) {
        Intent intent = intent(context, message.getSenderId(), message.getName());
        intent.putExtra(ARGUMENT_MESSAGE, new MessageParceler(message));
        return intent;
    }

    public static Intent intent(Context context, long userId, String userName) {
        Intent intent = new Intent(context, WriteMessageActivity.class);
        intent.putExtra(ARGUMENT_RECEIVER_ID, userId);
        intent.putExtra(ARGUMENT_RECEIVER_NAME, userName);
        return intent;
    }

    public static Intent answerToComment(Context context, FeedItem feedItem, Comment comment) {
        return answerToComment(context, Message.of(feedItem, comment));
    }

    public static Intent answerToComment(Context context, Message message) {
        long itemId = message.getItemId();
        long commentId = message.id();

        Intent intent = intent(context, message);
        intent.putExtra(ARGUMENT_COMMENT_ID, commentId);
        intent.putExtra(ARGUMENT_ITEM_ID, itemId);
        return intent;
    }

    public static NewComment getNewComment(Intent data) {
        return Parceler.get(NewCommentParceler.class,
                data.getExtras(), RESULT_EXTRA_NEW_COMMENT);
    }
}
