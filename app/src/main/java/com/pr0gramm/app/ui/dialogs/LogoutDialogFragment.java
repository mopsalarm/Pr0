package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.MainActionHandler;

import roboguice.fragment.RoboDialogFragment;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.errorDialog;

/**
 */
public class LogoutDialogFragment extends RoboDialogFragment {
    @Inject
    private UserService userService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialDialog.Builder(getActivity())
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ((MainActionHandler) getActivity()).onLogoutClicked();
                    }
                })
                .content(R.string.are_you_sure_to_logout)
                .negativeText(R.string.cancel)
                .positiveText(R.string.logout)
                .build();
    }
}
