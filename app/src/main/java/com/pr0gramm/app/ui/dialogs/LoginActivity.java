package com.pr0gramm.app.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RequestCodes;
import com.pr0gramm.app.api.pr0gramm.response.ImmutableLogin;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.sync.SyncBroadcastReceiver;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.util.AndroidUtility;

import net.danlew.android.joda.DateUtils;

import org.joda.time.DateTimeZone;
import org.joda.time.Weeks;

import javax.inject.Inject;

import butterknife.Bind;
import retrofit.HttpException;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.showErrorString;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static com.pr0gramm.app.util.AndroidUtility.toObservable;

/**
 */
public class LoginActivity extends BaseAppCompatActivity {
    private static final String PREF_USERNAME = "LoginDialogFragment.username";

    @Inject
    SharedPreferences prefs;

    @Inject
    UserService userService;

    @Bind(R.id.username)
    EditText usernameView;

    @Bind(R.id.password)
    EditText passwordView;

    @Bind(R.id.login)
    Button submitView;

    private Subscription subscription;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // restore last username
        String defaultUsername = prefs.getString(PREF_USERNAME, "");
        if (!Strings.isNullOrEmpty(defaultUsername)) {
            usernameView.setText(defaultUsername);
        }

        submitView.setOnClickListener(v -> onLoginClicked());

        updateActivityBackground();
    }

    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    private void updateActivityBackground() {
        int fallbackColor = getResources().getColor(R.color.primary_dark);
        Drawable background = new WrapCrashingDrawable(fallbackColor,
                ResourcesCompat.getDrawable(getResources(), R.drawable.login_background, getTheme()));

        AndroidUtility.setViewBackground(findViewById(R.id.content), background);
    }

    private void enableView(boolean enable) {
        for (View view : ImmutableList.<View>of(usernameView, passwordView, submitView)) {
            view.setEnabled(enable);
        }
    }

    private void onLoginClicked() {
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

        enableView(false);

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply();

        subscription = userService.login(username, password)
                .compose(bindToLifecycle())
                .lift(busyDialog(this, getString(R.string.login_please_wait), UserService.LoginProgress::getProgress))
                .flatMap(progress -> toObservable(progress.getLogin()))
                .lift(new LoginErrorInterceptor())
                .doOnError(err -> enableView(true))
                .subscribe(this::onLoginResponse, defaultOnError());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (subscription != null)
            subscription.unsubscribe();
    }

    private static class LoginErrorInterceptor implements Observable.Operator<Login, Login> {
        @Override
        public Subscriber<? super Login> call(Subscriber<? super Login> subscriber) {
            return new Subscriber<Login>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable err_) {
                    if (err_ instanceof HttpException) {
                        HttpException err = (HttpException) err_;
                        if (err.code() == 403) {
                            try {
                                subscriber.onNext(ImmutableLogin.builder().success(false).build());
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
    }

    private void onLoginResponse(Login response) {
        if (response.isSuccess()) {
            SyncBroadcastReceiver.scheduleNextSync(this);

            // signal success
            setResult(RESULT_OK);
            finish();

            Track.loginSuccessful();

        } else {
            Login.BanInfo ban = response.getBanInfo();
            if (ban != null && ban.isBanned()) {
                CharSequence date = DateUtils.getRelativeDateTimeString(this,
                        ban.getTill().toDateTime(DateTimeZone.getDefault()),
                        Weeks.ONE,
                        DateUtils.FORMAT_SHOW_DATE);

                String reason = ban.getReason();
                showErrorString(getSupportFragmentManager(), getString(R.string.banned, date, reason));

            } else {
                String msg = getString(R.string.login_not_successful);
                showErrorString(getSupportFragmentManager(), msg);
                enableView(true);

                Track.loginFailed();
            }
        }
    }

    public static abstract class DoIfAuthorizedHelper {
        @Nullable
        private Runnable retry;

        private DoIfAuthorizedHelper() {
        }

        public void onActivityResult(int requestCode, int resultCode) {
            if (requestCode == RequestCodes.AUTHORIZED_HELPER) {
                if (resultCode == RESULT_OK && retry != null) {
                    retry.run();
                }

                retry = null;
            }
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method
         * will show a login screen.
         */
        public boolean run(Runnable runnable) {
            return run(runnable, null);
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method shows
         * the login screen. After a successful login, the given 'retry' runnable will be called.
         */
        public boolean run(Runnable runnable, @Nullable Runnable retry) {
            Context context = getContext();
            if (context == null)
                return false;

            UserService userService = Dagger.appComponent(context).userService();
            if (userService.isAuthorized()) {
                runnable.run();
                return true;

            } else {
                this.retry = retry;

                Intent intent = new Intent(context, LoginActivity.class);
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER);
                return false;
            }
        }

        protected abstract Context getContext();

        protected abstract void startActivityForResult(Intent intent, int requestCode);
    }

    public static DoIfAuthorizedHelper helper(Activity activity) {
        return new DoIfAuthorizedHelper() {
            @Override
            protected Context getContext() {
                return activity;
            }

            @Override
            protected void startActivityForResult(Intent intent, int requestCode) {
                activity.startActivityForResult(intent, requestCode);
            }
        };
    }

    public static DoIfAuthorizedHelper helper(Fragment fragment) {
        return new DoIfAuthorizedHelper() {
            @Override
            protected Context getContext() {
                return fragment.getActivity();
            }

            @Override
            protected void startActivityForResult(Intent intent, int requestCode) {
                fragment.startActivityForResult(intent, requestCode);
            }
        };
    }

    private static class WrapCrashingDrawable extends LevelListDrawable {
        @ColorInt
        private final int fallbackColor;

        public WrapCrashingDrawable(@ColorInt int fallbackColor, Drawable drawable) {
            this.fallbackColor = fallbackColor;
            addLevel(0, 2, drawable);
            setLevel(1);
        }

        @Override
        public void draw(Canvas canvas) {
            try {
                super.draw(canvas);
            } catch (Exception ignored) {
                canvas.drawColor(fallbackColor);
            }
        }
    }
}
