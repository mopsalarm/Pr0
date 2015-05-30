package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.services.VoteService;

import java.util.List;

import roboguice.fragment.RoboDialogFragment;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 */
public class ReplyCommentDialogFragment extends RoboDialogFragment {

    public static String ARGUMENT_PARENT_COMMENT_NAME = "parentCommentName";
    public static String ARGUMENT_PARENT_COMMENT_ID = "parentCommentId";
    public static String ARGUMENT_ITEM_ID = "itemId";

    private EditText commentInput;

    @Inject
    private VoteService voteService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_comment, null);
        commentInput = (EditText) view.findViewById(R.id.comment);

        // restore message text on rotation
        if (savedInstanceState != null) {
            String text = savedInstanceState.getString("commentText", "");
            commentInput.setText(text);
        }

        return DialogBuilder.start(getActivity())
                .fullWidth()
                .title(getString(R.string.add_reply_comment_title,
                                getArguments().getString(ARGUMENT_PARENT_COMMENT_NAME)))
                .content(view, true)
                .negative(R.string.cancel)
                .positive(R.string.dialog_action_add, this::onOkayClicked)
                .build();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("commentText", commentInput.getText().toString());
    }

    private void onOkayClicked() {
        String text = commentInput.getText().toString().trim();

        // do nothing if the user had not typed a comment
        if (text.isEmpty())
            return;

        // inform parent
        long parentComment = getArguments().getLong(ARGUMENT_PARENT_COMMENT_ID);
        long itemId = getArguments().getLong(ARGUMENT_ITEM_ID);

        Fragment parentFragment = getParentFragment();

        bindActivity(getActivity(), voteService.postComment(itemId, parentComment, text))
                .lift(busyDialog(this))
                .subscribe(comments -> {
                    if (parentFragment instanceof OnNewCommentsListener) {
                        ((OnNewCommentsListener) parentFragment).onNewComments(comments);
                    }

                }, defaultOnError());
    }

    public static ReplyCommentDialogFragment newInstance(long itemId, Optional<Post.Comment> parent) {
        long parentId = parent.transform(Post.Comment::getId).or(0L);
        String name = parent.transform(Post.Comment::getName).or("");
        parent.get().getName();
        return newInstance(itemId, parentId, name);
    }

    public static ReplyCommentDialogFragment newInstance(long itemId, long parentId, String name) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARGUMENT_PARENT_COMMENT_ID, parentId);
        arguments.putLong(ARGUMENT_ITEM_ID, itemId);
        arguments.putString(ARGUMENT_PARENT_COMMENT_NAME, name);

        ReplyCommentDialogFragment dialog = new ReplyCommentDialogFragment();
        dialog.setArguments(arguments);
        return dialog;
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    public interface OnNewCommentsListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        void onNewComments(List<Post.Comment> comments);
    }
}
