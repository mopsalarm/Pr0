package com.pr0gramm.app.ui.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ListView;

import com.f2prateek.dart.InjectExtra;
import com.google.common.primitives.Floats;
import com.jakewharton.rxbinding.widget.RxAdapterView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.AdminService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.base.BaseDialogFragment;

import javax.inject.Inject;

import butterknife.BindView;
import rx.Observable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
public class ItemAdminDialog extends BaseDialogFragment {
    private static final String KEY_FEED_ITEM = "ItemAdminDialog__feedItem";

    @Inject
    AdminService adminService;

    @InjectExtra(KEY_FEED_ITEM)
    FeedItem item;

    @BindView(R.id.reason)
    ListView reasonListView;

    @BindView(R.id.custom_reason)
    EditText customReasonText;

    @BindView(R.id.block_user)
    Checkable blockUser;

    @BindView(R.id.block_user_days)
    EditText blockUserForDays;

    @BindView(R.id.notify_user)
    Checkable notifyUser;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getContext())
                .layout(R.layout.admin_delete_item)
                .negative(R.string.cancel, this::dismiss)
                .positive(R.string.delete, di -> onDeleteClicked())
                .noAutoDismiss()
                .build();
    }

    @Override
    protected void onDialogViewCreated() {
        reasonListView.setAdapter(new ArrayAdapter<>(getDialog().getContext(),
                android.R.layout.simple_list_item_1, AdminService.REASONS));

        RxAdapterView.itemClicks(reasonListView).subscribe(index -> {
            customReasonText.setText(AdminService.REASONS.get(index));
        });
    }

    private void onDeleteClicked() {
        String reason = customReasonText.getText().toString().trim();
        boolean notifyUser = this.notifyUser.isChecked();
        boolean ban = blockUser.isChecked();

        if (reason.isEmpty()) {
            return;
        }

        Observable<Void> response = deleteItem(reason, notifyUser, ban);
        response.compose(bindToLifecycleAsync())
                .lift(BusyDialogFragment.busyDialog(this))
                .subscribe(event -> dismiss(), defaultOnError());

    }

    private Observable<Void> deleteItem(String reason, boolean notifyUser, boolean ban) {
        if (ban) {
            float banUserDays = firstNonNull(Floats.tryParse(blockUserForDays.getText().toString()), 1.f);
            return adminService.deleteItem(item, reason, notifyUser, banUserDays);
        } else {
            return adminService.deleteItem(item, reason, notifyUser);
        }
    }

    public static ItemAdminDialog newInstance(FeedItem item) {
        Bundle args = new Bundle();
        args.putParcelable(KEY_FEED_ITEM, item);

        ItemAdminDialog dialog = new ItemAdminDialog();
        dialog.setArguments(args);
        return dialog;
    }
}
