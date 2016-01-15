package com.pr0gramm.app.ui.fragments;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ListView;

import com.f2prateek.dart.InjectExtra;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxAdapterView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.AdminService;
import com.pr0gramm.app.ui.base.BaseDialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.pr0gramm.app.services.ThemeHelper.popupContext;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
public class ItemAdminDialog extends BaseDialogFragment {
    private static final Logger logger = LoggerFactory.getLogger("ItemAdminDialog");

    private static final String KEY_FEED_ITEM = "ItemAdminDialog.feedItem";
    private static final String[] REASONS = Iterables.toArray(AdminService.REASONS, String.class);

    @Inject
    AdminService adminService;

    @InjectExtra(KEY_FEED_ITEM)
    FeedItem item;

    @Bind(R.id.reason)
    ListView reasonListView;

    @Bind(R.id.custom_reason)
    EditText customReasonText;

    @Bind(R.id.block_user)
    Checkable blockUser;

    @Bind(R.id.block_user_days)
    EditText blockUserForDays;

    @Bind(R.id.notify_user)
    Checkable notifyUser;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(popupContext(getContext()))
                .setView(R.layout.admin_delete_item)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        ButterKnife.bind(this, getDialog());


        Button positiveButton = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE);
        RxView.clicks(positiveButton).subscribe(event -> {
            onDeleteClicked();
        });

        reasonListView.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, AdminService.REASONS));

        RxAdapterView.itemClicks(reasonListView).subscribe(index -> {
            customReasonText.setText(AdminService.REASONS.get(index));
        });
    }

    private void onDeleteClicked() {
        String reason = customReasonText.getText().toString();
        boolean notifyUser = this.notifyUser.isChecked();
        boolean ban = blockUser.isChecked();

        Observable<Void> response = deleteItem(reason, notifyUser, ban);
        response.compose(bindToLifecycle())
                .lift(BusyDialogFragment.busyDialog(this))
                .subscribe(event -> dismiss(), defaultOnError());

    }

    private Observable<Void> deleteItem(String reason, boolean notifyUser, boolean ban) {
        if (ban) {
            int banUserDays = firstNonNull(Ints.tryParse(blockUserForDays.getText().toString()), 1);
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
