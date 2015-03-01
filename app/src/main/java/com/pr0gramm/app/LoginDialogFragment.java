package com.pr0gramm.app;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.common.base.Strings;
import com.pr0gramm.app.api.LoginResponse;

import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.fragment.RoboDialogFragment;
import rx.functions.Action1;

import static com.pr0gramm.app.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class LoginDialogFragment extends RoboDialogFragment {
    private static final String PREF_USERNAME = "LoginDialogFragment.username";

    @Inject
    private SharedPreferences prefs;

    @Inject
    private UserService userService;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View layout = inflater.inflate(R.layout.login, null);

        // reset last username in the dialog.
        String defaultUsername = prefs.getString(PREF_USERNAME, "");
        if (!Strings.isNullOrEmpty(defaultUsername)) {
            EditText usernameView = (EditText) layout.findViewById(R.id.username);
            usernameView.setText(defaultUsername);
        }

        return new MaterialDialog.Builder(getActivity())
                .title(R.string.login)
                .customView(layout, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        onLoginClicked(dialog);
                    }
                })
                .positiveText(R.string.login)
                .autoDismiss(false)
                .theme(Theme.LIGHT)
                .build();
    }

    private void onLoginClicked(MaterialDialog dialog) {
        View view = dialog.getCustomView();

        TextView usernameView = (TextView) view.findViewById(R.id.username);
        TextView passwordView = (TextView) view.findViewById(R.id.password);

        String username = usernameView.getText().toString();
        String password = passwordView.getText().toString();

        if (username.isEmpty()) {
            usernameView.setError(getString(R.string.must_not_be_empty));
            return;
        }

        if (password.isEmpty()) {
            passwordView.setError(getString(R.string.must_not_be_empty));
            return;
        }

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply();

        bindFragment(this, userService.login(username, password))
                .lift(busyDialog(this))
                .subscribe(
                        response -> onLoginSuccess(response, usernameView::setError),
                        this::onLoginError);
    }

    private void onLoginSuccess(LoginResponse response, Action1<String> showError) {
        if (response.isSuccess()) {
            dismiss();

        } else {
            String msg = getString(R.string.login_not_successful);
            showError.call(msg);
        }
    }

    private void onLoginError(Throwable throwable) {
        Log.e("LoginDialog", "Login error", throwable);
    }

    private static boolean doIfAuthorized(Context context, FragmentManager fm, Runnable runnable) {
        UserService userService = RoboGuice
                .getInjector(context)
                .getInstance(UserService.class);

        Log.i("LoginDialog", "Using login service " + userService);

        if (userService.isAuthorized()) {
            Log.i("LoginDialog", "is authorized");
            runnable.run();
            return true;

        } else {
            Log.i("LoginDialog", "not authorized, showing login dialog");

            LoginDialogFragment dialog = new LoginDialogFragment();
            dialog.show(fm, null);

            return false;
        }
    }

    public static boolean doIfAuthorized(Fragment fragment, Runnable runnable) {
        return doIfAuthorized(fragment.getActivity(), fragment.getChildFragmentManager(), runnable);
    }

    public static boolean doIfAuthorized(FragmentActivity fragment, Runnable runnable) {
        return doIfAuthorized(fragment, fragment.getSupportFragmentManager(), runnable);
    }
}
