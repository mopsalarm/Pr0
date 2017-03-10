package com.pr0gramm.app.ui.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.TextView;

import com.f2prateek.dart.InjectExtra;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Floats;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.AdminService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
public class TagsDetailsDialog extends BaseDialogFragment {
    private static final String KEY_FEED_ITEM_ID = "TagsDetailsDialog__feedItem";

    final List<Api.TagDetails.TagInfo> tags = new ArrayList<>();
    final TLongSet selected = new TLongHashSet();

    @Inject
    AdminService adminService;

    @InjectExtra(KEY_FEED_ITEM_ID)
    long itemId;

    @BindView(R.id.tags)
    RecyclerView tagsView;

    @BindView(R.id.busy_indicator)
    View busyView;

    @BindView(R.id.block_user)
    Checkable blockUser;

    @BindView(R.id.block_user_days)
    TextView blockUserDays;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getContext())
                .layout(R.layout.admin_tags_details)
                .negative(R.string.cancel, this::dismiss)
                .positive(R.string.delete, di -> onDeleteClicked())
                .noAutoDismiss()
                .build();
    }

    @Override
    protected void onDialogViewCreated() {
        tagsView.setAdapter(new TagsAdapter());

        tagsView.setLayoutManager(new LinearLayoutManager(
                getDialog().getContext(), LinearLayoutManager.VERTICAL, false));

        adminService.tagsDetails(itemId)
                .compose(bindToLifecycleAsync())
                .subscribe(this::showTagsDetails, defaultOnError());
    }

    private void showTagsDetails(Api.TagDetails tagDetails) {
        Ordering<Api.TagDetails.TagInfo> ordering = Ordering.natural()
                .onResultOf(Api.TagDetails.TagInfo::confidence)
                .reverse();

        // set the new tags and notify the recycler view to redraw itself.
        tags.addAll(ordering.sortedCopy(tagDetails.tags()));
        tagsView.getAdapter().notifyDataSetChanged();

        AndroidUtility.removeView(busyView);
    }

    private void onDeleteClicked() {
        if (selected.isEmpty()) {
            dismiss();
            return;
        }

        Float blockAmount = Floats.tryParse(
                blockUser.isChecked() ? blockUserDays.getText().toString() : "");

        adminService.deleteTags(itemId, selected, blockAmount)
                .compose(bindToLifecycleAsync())
                .lift(BusyDialogFragment.busyDialog(this))
                .subscribe(event -> dismiss(), defaultOnError());
    }

    public static TagsDetailsDialog newInstance(long itemId) {
        Bundle args = new Bundle();
        args.putLong(KEY_FEED_ITEM_ID, itemId);

        TagsDetailsDialog dialog = new TagsDetailsDialog();
        dialog.setArguments(args);
        return dialog;
    }

    private class TagsAdapter extends RecyclerView.Adapter<TagsViewHolder> {
        @Override
        public TagsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new TagsViewHolder(LayoutInflater
                    .from(getDialog().getContext())
                    .inflate(R.layout.tags_details, parent, false));
        }

        @Override
        public void onBindViewHolder(TagsViewHolder view, int position) {
            Api.TagDetails.TagInfo item = tags.get(position);

            view.checkbox.setText(item.tag());
            view.info.setText(String.format("%s, +%d, -%d", item.user(), item.up(), item.down()));

            view.checkbox.setChecked(selected.contains(item.id()));

            // register a listener to check/uncheck this tag.
            view.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selected.add(item.id());
                } else {
                    selected.remove(item.id());
                }
            });
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }

    private class TagsViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView info;

        TagsViewHolder(View view) {
            super(view);
            this.info = ButterKnife.findById(view, R.id.tag_info);
            this.checkbox = ButterKnife.findById(view, R.id.tag_text);
        }
    }
}
