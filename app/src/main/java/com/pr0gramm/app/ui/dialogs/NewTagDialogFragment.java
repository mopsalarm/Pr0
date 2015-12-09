package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.MultiAutoCompleteTextView;

import com.google.common.base.Splitter;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.TagInputView;
import com.pr0gramm.app.ui.base.BaseDialogFragment;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 */
public class NewTagDialogFragment extends BaseDialogFragment {
    @Bind(R.id.tag)
    MultiAutoCompleteTextView tagInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_tags, null);
        ButterKnife.bind(this, view);

        // add tag-completion
        TagInputView.setup(tagInput);

        return DialogBuilder.start(getActivity())
                .content(view, true)
                .negative(R.string.cancel)
                .positive(R.string.dialog_action_add, this::onOkayClicked)
                .build();
    }

    private void onOkayClicked() {
        String text = tagInput.getText().toString();

        // split text into tags.
        Splitter splitter = Splitter.on(",").omitEmptyStrings().trimResults();
        List<String> tags = splitter.splitToList(text);

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return;

        // inform parent
        ((OnAddNewTagsListener) getParentFragment()).onAddNewTags(tags);
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    public interface OnAddNewTagsListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        void onAddNewTags(List<String> tags);
    }
}
