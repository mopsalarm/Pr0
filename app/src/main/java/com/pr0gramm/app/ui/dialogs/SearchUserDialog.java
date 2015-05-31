package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.fragments.BusyDialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.fragment.RoboDialogFragment;

import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class SearchUserDialog extends RoboDialogFragment {
    private static final Logger logger = LoggerFactory.getLogger(SearchUserDialog.class);

    private TextInputLayout inputView;

    @Inject
    private UserService userService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(),
                R.style.Theme_AppCompat_Light_Dialog);

        inputView = (TextInputLayout) LayoutInflater
                .from(context)
                .inflate(R.layout.search_user_dialog, null);

        return DialogBuilder.start(context)
                .content(inputView)
                .fullWidth()
                .positive(R.string.action_search, di -> onSearchClicked())
                .negative(R.string.cancel, Dialog::dismiss)
                .noAutoDismiss()
                .build();
    }

    private void onSearchClicked() {
        String username = inputView.getEditText().getText().toString().trim();

        bindFragment(this, userService.info(username))
                .lift(BusyDialogFragment.busyDialog(this))
                .subscribe(this::onSearchSuccess, this::onSearchFailure);
    }

    private void onSearchSuccess(Info info) {
        logger.info("Found user info: {} {}", info.getUser().getId(), info.getUser().getName());

        Fragment parent = getParentFragment();
        if (parent instanceof Listener) {
            ((Listener) parent).onUserInfo(info);
        }

        dismissAllowingStateLoss();
    }

    private void onSearchFailure(Throwable throwable) {
        inputView.setError(getString(R.string.user_not_found));
    }

    public interface Listener {
        void onUserInfo(Info info);
    }
}
