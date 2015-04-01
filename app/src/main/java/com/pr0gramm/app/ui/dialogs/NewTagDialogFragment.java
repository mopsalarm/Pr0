package com.pr0gramm.app.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.pr0gramm.app.Lazy;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

import roboguice.fragment.RoboDialogFragment;

/**
 */
public class NewTagDialogFragment extends RoboDialogFragment {
    private MultiAutoCompleteTextView tagInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);

        // get the auto-suggestion list.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line,
                TAGS.get());

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tags, null);
        tagInput = (MultiAutoCompleteTextView) view.findViewById(R.id.tag);
        tagInput.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        tagInput.setAdapter(adapter);

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

    /**
     * Parses the list of tags.
     */
    @SuppressLint("NewApi")
    private static final Lazy<List<String>> TAGS = Lazy.of(() -> {
        try (InputStream stream = Pr0grammApplication.GLOBAL_CONTEXT.getAssets().open("tags.json")) {
            Type listType = new TypeToken<List<String>>() {
            }.getType();

            try (Reader reader = new InputStreamReader(stream, Charsets.UTF_8)) {
                List<String> tagList = new Gson().fromJson(reader, listType);

                // group by lower-case version of each tag
                ListMultimap<String, String> byLowerCase = FluentIterable
                        .from(tagList)
                        .index(String::toLowerCase);

                // and remove duplicates by getting the first occurrence of each
                // groups values
                return FluentIterable.from(byLowerCase.asMap().entrySet())
                        .transform(e -> Iterables.getFirst(e.getValue(), null))
                        .filter(Predicates.notNull())
                        .toList();
            }

        } catch (Exception error) {
            Log.e("Tags", "Could not load list of tags", error);
            return Collections.emptyList();
        }
    });
}
