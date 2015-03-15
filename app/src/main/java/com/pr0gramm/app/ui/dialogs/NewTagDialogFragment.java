package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Splitter;
import com.pr0gramm.app.R;

import java.util.List;

import roboguice.fragment.RoboDialogFragment;

/**
 */
public class NewTagDialogFragment extends RoboDialogFragment {
    private EditText tagInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tags, null);
        tagInput = (EditText) view.findViewById(R.id.tag);

        return new MaterialDialog.Builder(getActivity())
                .title(R.string.add_new_tag_title)
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

    private void onOkayClicked(MaterialDialog dialog) {
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
