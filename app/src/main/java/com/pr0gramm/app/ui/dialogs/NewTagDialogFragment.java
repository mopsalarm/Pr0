package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Splitter;
import com.pr0gramm.app.R;

import java.util.List;

import roboguice.fragment.RoboDialogFragment;

/**
 */
public class NewTagDialogFragment extends RoboDialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        return new MaterialDialog.Builder(getActivity())
                .customView(R.layout.md_input_dialog, true)
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
        EditText view = (EditText) dialog.getCustomView().findViewById(android.R.id.edit);
        String text = view.getText().toString();

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
