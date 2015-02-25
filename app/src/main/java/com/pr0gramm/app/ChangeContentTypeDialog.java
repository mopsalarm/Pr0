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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterables.toArray;
import static java.util.Arrays.asList;

/**
 */
public class ChangeContentTypeDialog extends DialogFragment {
    private static final String ARG_PRE_SELECTED = "preSelected";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //noinspection unchecked
        EnumSet<ContentType> preSelected = (EnumSet<ContentType>)
                getArguments().getSerializable(ARG_PRE_SELECTED);

        List<ContentType> types = asList(ContentType.values());
        List<String> typeStrings = FluentIterable.from(types)
                .transform(ContentType::getTitle)
                .transform(this::getString)
                .toList();

        List<Integer> selected = new ArrayList<>();
        for (int idx = 0; idx < types.size(); idx++) {
            if (preSelected.contains(types.get(idx)))
                selected.add(idx);
        }

        return new MaterialDialog.Builder(getActivity())
                .items(toArray(typeStrings, CharSequence.class))
                .itemsCallbackMultiChoice(toArray(selected, Integer.class), (dialog, indices, items) -> {
                    // convert selected items to enum set and publish
                    publish(EnumSet.copyOf(FluentIterable
                            .of(indices)
                            .transform(types::get)
                            .toList()));
                })
                .positiveText(R.string.okay)
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

    /**
     * Creates a new dialog with the given values pre-selected
     *
     * @param preSelected The pre selected values.
     * @return The new dialog instance.
     */
    public static ChangeContentTypeDialog newInstance(Set<ContentType> preSelected) {
        Bundle arguments = new Bundle();
        arguments.putSerializable(ARG_PRE_SELECTED, EnumSet.copyOf(preSelected));

        ChangeContentTypeDialog dialog = new ChangeContentTypeDialog();
        dialog.setArguments(arguments);
        return dialog;
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
}
