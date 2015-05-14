package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

/**
 */
public class ChangeLogDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Settings settings = Settings.of(getActivity());

        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), DialogBuilder.theme());

        List<Change> changes = changelog(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        RecyclerView recycler = (RecyclerView) inflater.inflate(R.layout.changelog, null);
        recycler.setAdapter(new ChangeAdapter(changes));
        recycler.setLayoutManager(new LinearLayoutManager(context));

        return DialogBuilder.start(context)
                .content(recycler, false)
                .positive(R.string.okay, () -> {
                    if (settings.useBetaChannel()) {
                        showFeedbackReminderDialog();
                    }
                })
                .build();
    }

    private void showFeedbackReminderDialog() {
        DialogBuilder.start(getActivity())
                .content(R.string.feedback_reminder)
                .positive(R.string.okay)
                .show();
    }

    private static class ChangeAdapter extends RecyclerView.Adapter<ChangeViewHolder> {
        private final List<Object> items;
        private final int currentVersion = Pr0grammApplication.getPackageInfo().versionCode;

        ChangeAdapter(List<Change> changes) {
            int lastVersion = -1;

            ImmutableList.Builder<Object> items = ImmutableList.builder();
            for (Change change : changes) {
                if (change.version != lastVersion) {
                    items.add(Version.of(change.version));
                    lastVersion = change.version;
                }

                items.add(change);
            }

            this.items = items.build();
        }

        @Override
        public ChangeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            View view;
            switch (viewType) {
                case VIEW_TYPE_VERSION:
                    view = inflater.inflate(R.layout.changelog_version, parent, false);
                    break;

                case VIEW_TYPE_CHANGE:
                    view = inflater.inflate(R.layout.changelog_change, parent, false);
                    break;

                default:
                    throw new IllegalArgumentException("invalid view type: " + viewType);
            }

            return new ChangeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ChangeViewHolder holder, int position) {
            Object item = items.get(position);

            if (item instanceof Change) {
                Change change = (Change) item;
                holder.setText(change.type, change.change);
            }

            if (item instanceof Version) {
                Version version = (Version) item;
                holder.setVersion(version.formatted);
                holder.setTextColorId(version.number == currentVersion
                        ? R.color.primary : R.color.primary_dark_material_light);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof Version ? VIEW_TYPE_VERSION : VIEW_TYPE_CHANGE;
        }

        private static final int VIEW_TYPE_VERSION = 0;
        private static final int VIEW_TYPE_CHANGE = 1;
    }

    private static class ChangeViewHolder extends RecyclerView.ViewHolder {
        private final TextView text;

        ChangeViewHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }

        public void setText(String type, String text) {
            StyleSpan bold = new StyleSpan(Typeface.BOLD);

            final SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(type);
            builder.append(' ');
            builder.append(text);

            builder.setSpan(bold, 0, type.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            this.text.setText(builder);
        }

        public void setVersion(String version) {
            this.text.setText(version);
        }

        public void setTextColorId(@ColorRes int textColorId) {
            int color = itemView.getContext().getResources().getColor(textColorId);
            this.text.setTextColor(color);
        }
    }

    private static final class Change {
        int version;
        String type;
        String change;
    }

    private static final class Version {
        final int number;
        final String formatted;

        public Version(int number, String formatted) {
            this.number = number;
            this.formatted = formatted;
        }

        public static Version of(int number) {
            return new Version(number, "Version 1." + number);
        }
    }

    @SuppressLint("NewApi")
    public static List<Change> changelog(Context context) {
        try {
            try (InputStream input = context.getResources().openRawResource(R.raw.changelog)) {
                InputStreamReader reader = new InputStreamReader(input, Charsets.UTF_8);
                return new Gson().fromJson(reader, listOfChangeTypeToken.getType());
            }

        } catch (IOException error) {
            AndroidUtility.logToCrashlytics(error);
            return Collections.emptyList();
        }
    }

    private static final TypeToken<List<Change>> listOfChangeTypeToken = new TypeToken<List<Change>>() {
    };
}
