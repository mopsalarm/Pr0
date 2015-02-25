package com.pr0gramm.app;


import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import java.util.EnumSet;
import java.util.List;

/**
 */
public class ChangeContentTypeDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // convert combinations into strings
        CharSequence[] items = FluentIterable.from(COMBINATIONS)
                .transform(item -> ContentType.toString(getActivity(), item))
                .<CharSequence>transform(str -> str)
                .toArray(CharSequence.class);

        // and show dialog
        return new MaterialDialog.Builder(getActivity())
                .items(items)
                .itemsCallback((dialog, view, idx, str) -> publish(COMBINATIONS.get(idx)))
                .negativeText(R.string.cancel)
                .build();
    }

    private void publish(EnumSet<ContentType> contentTypes) {
        Log.i("Types", ContentType.toString(getActivity(), contentTypes));

        // inform activity about changes
        FragmentActivity activity = getActivity();
        if (activity instanceof ContentTypeChangeListener)
            ((ContentTypeChangeListener) activity).onContentTypeChanged(contentTypes);

        // and parent fragment, if any.
        Fragment parent = getParentFragment();
        if (parent instanceof ContentTypeChangeListener)
            ((ContentTypeChangeListener) parent).onContentTypeChanged(contentTypes);
    }

    public interface ContentTypeChangeListener {
        /**
         * Called if the user selected a new set of content types.
         * The new set may not be different to the one given to the
         * {@link com.pr0gramm.app.ChangeContentTypeDialog} in the first place.
         *
         * @param contentTypes The updated content types.
         */
        void onContentTypeChanged(EnumSet<ContentType> contentTypes);
    }

    private static final List<EnumSet<ContentType>> COMBINATIONS = ImmutableList.of(
            EnumSet.of(ContentType.SFW),
            EnumSet.of(ContentType.NSFW),
            EnumSet.of(ContentType.NSFL),
            EnumSet.of(ContentType.SFW, ContentType.NSFW),
            EnumSet.of(ContentType.SFW, ContentType.NSFL),
            EnumSet.of(ContentType.NSFW, ContentType.NSFL),
            EnumSet.of(ContentType.SFW, ContentType.NSFW, ContentType.NSFL));
}
