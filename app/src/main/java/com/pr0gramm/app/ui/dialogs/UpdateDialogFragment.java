package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.pr0gramm.app.R;
import com.pr0gramm.app.UpdateChecker;

/**
 */
public class UpdateDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        UpdateChecker.Update update = getArguments().getParcelable("update");
        return new MaterialDialog.Builder(getActivity())
                .content(getString(R.string.new_update_available, update.getChangelog()))
                .positiveText(R.string.download)
                .negativeText(R.string.ignore)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        download(update);
                    }
                })
                .build();
    }

    private void download(UpdateChecker.Update update) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(update.getApk()));
        startActivity(intent);
    }

    public static UpdateDialogFragment newInstance(UpdateChecker.Update update) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("update", update);

        UpdateDialogFragment dialog = new UpdateDialogFragment();
        dialog.setArguments(bundle);
        return dialog;
    }
}
