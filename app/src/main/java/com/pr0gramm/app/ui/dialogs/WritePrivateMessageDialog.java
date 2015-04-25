package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.InboxService;

import javax.inject.Inject;

import roboguice.fragment.RoboDialogFragment;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 */
public class WritePrivateMessageDialog extends RoboDialogFragment {
    private EditText messageText;

    @Inject
    private InboxService inboxService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(getActivity(), R.style.Theme_AppCompat_Light);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_write_private_message, null);
        messageText = (EditText) view.findViewById(R.id.text);

        // restore message text on rotation
        if (savedInstanceState != null) {
            String text = savedInstanceState.getString("messageText", "");
            messageText.setText(text);
        }

        // include the username into the summary
        TextView summary = (TextView) view.findViewById(R.id.summary);
        summary.setText(getString(R.string.write_private_message_summary, getReceiverName()));

        return new MaterialDialog.Builder(getActivity())
                .customView(view, true)
                .negativeText(R.string.cancel)
                .positiveText(R.string.action_send)
                .autoDismiss(false)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        onOkayClicked(dialog);
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dismiss();
                    }
                })
                .build();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("messageText", messageText.getText().toString());
    }

    private void onOkayClicked(MaterialDialog dialog) {
        String message = messageText.getText().toString().trim();
        if (message.isEmpty())
            return;

        // now send message
        bindActivity(getActivity(), inboxService.send(getReceiverId(), message))
                .lift(busyDialog(getActivity()))
                .doOnCompleted(this::dismiss)
                .subscribe(Actions.empty(), defaultOnError());
    }

    private String getReceiverName() {
        return getArguments().getString("receiver");
    }

    private int getReceiverId() {
        return getArguments().getInt("receiverId");
    }

    public static DialogFragment newInstance(int receiverId, String name) {
        Bundle arguments = new Bundle();
        arguments.putString("receiver", name);
        arguments.putInt("receiverId", receiverId);

        WritePrivateMessageDialog dialog = new WritePrivateMessageDialog();
        dialog.setArguments(arguments);
        return dialog;
    }
}
