package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.MultiAutoCompleteTextView;

import com.google.common.base.Splitter;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.TagInputView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import butterknife.ButterKnife;
import roboguice.fragment.RoboDialogFragment;

/**
 */
public class NewTagDialogFragment extends RoboDialogFragment {
    private static final Logger logger = LoggerFactory.getLogger(NewTagDialogFragment.class);

    private MultiAutoCompleteTextView tagInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tags, null);
        tagInput = ButterKnife.findById(view, R.id.tag);

        TagInputView.setup(tagInput);

        return DialogBuilder.start(getActivity())
                .fullWidth()
                .title(R.string.add_new_tag_title)
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
