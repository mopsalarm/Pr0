package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.MainActionHandler;

import roboguice.fragment.RoboDialogFragment;

/**
 */
public class LogoutDialogFragment extends RoboDialogFragment {
    @Inject
    private UserService userService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getActivity())
                .content(R.string.are_you_sure_to_logout)
                .negative(R.string.cancel)
                .positive(R.string.logout, this::logout)
                .build();
    }

    private void logout() {
        ((MainActionHandler) getActivity()).onLogoutClicked();
    }
}
