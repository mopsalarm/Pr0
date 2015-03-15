package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.common.base.Strings;
import com.pr0gramm.app.R;
import com.pr0gramm.app.SyncBroadcastReceiver;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.services.UserService;

import net.danlew.android.joda.DateUtils;

import org.joda.time.DateTimeZone;
import org.joda.time.Weeks;

import javax.inject.Inject;

import retrofit.RetrofitError;
import roboguice.RoboGuice;
import roboguice.fragment.RoboDialogFragment;
import rx.Observable;
import rx.Subscriber;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.errorDialog;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.showErrorString;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class LoginDialogFragment extends RoboDialogFragment {
    private static final String PREF_USERNAME = "LoginDialogFragment.username";

    @Inject
    private SharedPreferences prefs;

    @Inject
    private UserService userService;

    private Runnable doOnLogin;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = getActivity();
        context = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light);

        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.login, null);

        // reset last username in the dialog.
        String defaultUsername = prefs.getString(PREF_USERNAME, "");
        if (!Strings.isNullOrEmpty(defaultUsername)) {
            EditText usernameView = (EditText) layout.findViewById(R.id.username);
            usernameView.setText(defaultUsername);
        }

        return new MaterialDialog.Builder(context)
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
                .lift(loginErrorIntercept())
                .lift(errorDialog(this))
                .subscribe(this::onLoginResponse);
    }

    private Observable.Operator<Login, Login> loginErrorIntercept() {
        return subscriber -> new Subscriber<Login>() {
            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }

            @Override
            public void onError(Throwable err_) {
                if (err_ instanceof RetrofitError) {
                    RetrofitError err = (RetrofitError) err_;
                    if (err.getResponse() != null && err.getResponse().getStatus() == 403) {
                        try {
                            subscriber.onNext(new Login(false));
                            subscriber.onCompleted();

                        } catch (Throwable forward) {
                            subscriber.onError(forward);
                        }

                        return;
                    }
                }
                subscriber.onError(err_);
            }

            @Override
            public void onNext(Login value) {
                subscriber.onNext(value);
            }
        };
    }

    private void onLoginResponse(Login response) {
        if (response.isSuccess()) {
            SyncBroadcastReceiver.scheduleNextSync(getActivity());

            if (doOnLogin != null)
                doOnLogin.run();

            dismiss();

        } else {
            Login.BanInfo ban = response.getBan();
            if (ban != null && ban.isBanned()) {
                CharSequence date = DateUtils.getRelativeDateTimeString(getActivity(),
                        ban.getTill().toDateTime(DateTimeZone.getDefault()),
                        Weeks.ONE,
                        DateUtils.FORMAT_SHOW_DATE);

                String reason = ban.getReason();
                showErrorString(getFragmentManager(), getString(R.string.banned, date, reason));
                dismiss();

            } else {
                String msg = getString(R.string.login_not_successful);
                showErrorString(getFragmentManager(), msg);
                dismiss();
            }
        }
    }

    private static boolean doIfAuthorized(Context context, FragmentManager fm, Runnable runnable,
                                          Runnable retry) {

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
            dialog.doOnLogin = retry;
            dialog.show(fm, null);

            return false;
        }
    }

    public static boolean doIfAuthorized(Fragment fragment, Runnable runnable) {
        return doIfAuthorized(fragment, runnable, null);
    }

    public static boolean doIfAuthorized(Fragment fragment, Runnable runnable, Runnable retry) {
        return doIfAuthorized(fragment.getActivity(), fragment.getChildFragmentManager(), runnable, retry);
    }

    public static boolean doIfAuthorized(FragmentActivity fragment, Runnable runnable) {
        return doIfAuthorized(fragment, runnable, null);
    }

    public static boolean doIfAuthorized(FragmentActivity fragment, Runnable runnable, Runnable retry) {
        return doIfAuthorized(fragment, fragment.getSupportFragmentManager(), runnable, retry);
    }
}
