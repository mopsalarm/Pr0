package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.MainActionHandler;
import com.pr0gramm.app.ui.base.BaseDialogFragment;

import javax.inject.Inject;


/**
 */
public class LogoutDialogFragment extends BaseDialogFragment {
    @Inject
    UserService userService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogBuilder.start(getActivity())
                .content(R.string.are_you_sure_to_logout)
                .positive(R.string.logout, this::logout)
                .build();
    }

    private void logout() {
        ((MainActionHandler) getActivity()).onLogoutClicked();
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}
