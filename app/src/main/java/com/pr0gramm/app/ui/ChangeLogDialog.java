package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.pr0gramm.app.util.AndroidUtility;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import proguard.annotation.Keep;
import proguard.annotation.KeepClassMembers;

import static com.pr0gramm.app.services.ThemeHelper.accentColor;


/**
 */
public class ChangeLogDialog extends BaseDialogFragment {
    @Inject
    Settings settings;

    @BindView(R.id.changelog)
    RecyclerView recyclerView;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getContext())
                .layout(R.layout.changelog)
                .positive()
                .build();
    }

    @Override
    protected void onDialogViewCreated() {
        List<ChangeGroup> changes = loadChangelog(getContext());
        recyclerView.setAdapter(new ChangeAdapter(changes));
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    private static class ChangeAdapter extends RecyclerView.Adapter<ChangeViewHolder> {
        private final List<Object> items;

        ChangeAdapter(List<ChangeGroup> changeGroups) {
            ImmutableList.Builder<Object> items = ImmutableList.builder();
            for (int idx = 0; idx < changeGroups.size(); idx++) {
                ChangeGroup group = changeGroups.get(idx);

                boolean current = (idx == 0);
                items.add(Version.of(group.version, current));
                items.addAll(group.changes);
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
                holder.setTextColorId(accentColor(), version.current ? 1 : 0.5f);
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

            if (text.contains("://")) {
                // might contains links that we want to display?
                AndroidUtility.linkify(this.text, builder);
            } else {
                this.text.setText(builder);
            }
        }

        public void setVersion(String version) {
            this.text.setText(version);
        }

        public void setTextColorId(@ColorRes int textColorId, float alpha) {
            int color = ContextCompat.getColor(itemView.getContext(), textColorId);
            this.text.setTextColor(color);
            this.text.setAlpha(alpha);
        }
    }

    @Keep
    @KeepClassMembers
    private static final class Change {
        String type;
        String change;
    }

    @Keep
    @KeepClassMembers
    private static final class ChangeGroup {
        int version;
        List<Change> changes;
    }

    private static final class Version {
        final int number;
        final String formatted;
        final boolean current;

        private Version(int number, String formatted, boolean current) {
            this.number = number;
            this.formatted = formatted;
            this.current = current;
        }

        public static Version of(int number, boolean current) {
            return new Version(number, "Version 1." + number, current);
        }
    }

    @SuppressLint("NewApi")
    private static List<ChangeGroup> loadChangelog(Context context) {
        final int changelog = R.raw.changelog;
        try {
            try (InputStream input = context.getResources().openRawResource(changelog)) {
                InputStreamReader reader = new InputStreamReader(input, Charsets.UTF_8);
                return new Gson().fromJson(reader, LIST_OF_CHANGE_GROUPS.getType());
            }

        } catch (IOException error) {
            AndroidUtility.logToCrashlytics(error);
            return Collections.emptyList();
        }
    }

    private static final TypeToken<List<ChangeGroup>> LIST_OF_CHANGE_GROUPS = new TypeToken<List<ChangeGroup>>() {
    };
}
