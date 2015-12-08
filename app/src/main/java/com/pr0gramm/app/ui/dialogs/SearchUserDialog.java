package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.AutoCompleteTextView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.pr0gramm.app.ui.fragments.BusyDialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 */
public class SearchUserDialog extends BaseDialogFragment {
    private static final Logger logger = LoggerFactory.getLogger("SearchUserDialog");

    private TextInputLayout inputView;

    @Inject
    UserService userService;

    @Inject
    MetaService metaService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(),
                R.style.Theme_AppCompat_Light_Dialog);

        inputView = (TextInputLayout) LayoutInflater
                .from(context)
                .inflate(R.layout.search_user_dialog, null);

        AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView) inputView.getEditText();
        if (autoCompleteTextView != null) {
            autoCompleteTextView.setAdapter(new UsernameAutoCompleteAdapter(metaService, context,
                    "", android.R.layout.simple_dropdown_item_1line));
        }


        return DialogBuilder.start(context)
                .content(inputView)
                .positive(R.string.action_search_simple, di -> onSearchClicked())
                .negative(Dialog::dismiss)
                .noAutoDismiss()
                .build();
    }

    private void onSearchClicked() {
        String username = inputView.getEditText().getText().toString().trim();

        userService.info(username)
                .compose(bindToLifecycle())
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

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    public interface Listener {
        void onUserInfo(Info info);
    }
}
