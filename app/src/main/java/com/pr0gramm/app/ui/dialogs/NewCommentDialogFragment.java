package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Post;

/**
 */
public class NewCommentDialogFragment extends DialogFragment {
    private EditText commentInput;

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

        return new MaterialDialog.Builder(getActivity())
                .title(R.string.add_new_comment_title)
                .customView(view, true)
                .negativeText(R.string.cancel)
                .positiveText(R.string.action_add_tag)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        onOkayClicked(dialog);
                    }
                })
                .build();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("commentText", commentInput.getText().toString());
    }

    private void onOkayClicked(MaterialDialog dialog) {
        String text = commentInput.getText().toString().trim();

        // do nothing if the user had not typed a comment
        if (text.isEmpty())
            return;

        // inform parent
        long parentComment = getArguments().getLong("parentCommentId");
        ((OnAddNewCommentListener) getParentFragment()).onAddNewCommment(parentComment, text);
    }

    public static NewCommentDialogFragment newInstance(Optional<Post.Comment> parent) {
        long parentId = parent.transform(Post.Comment::getId).or(0L);
        Bundle arguments = new Bundle();
        arguments.putLong("parentCommentId", parentId);

        NewCommentDialogFragment dialog = new NewCommentDialogFragment();
        dialog.setArguments(arguments);
        return dialog;
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    public interface OnAddNewCommentListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        void onAddNewCommment(long parentId, String comment);
    }
}
