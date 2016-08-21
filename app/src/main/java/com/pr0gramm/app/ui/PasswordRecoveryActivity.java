package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

public class PasswordRecoveryActivity extends BaseAppCompatActivity {
    private String user, token;

    @BindView(R.id.submit)
    Button submit;

    @BindView(R.id.password)
    EditText password;

    @Inject
    UserService userService;

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.theme().basic);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_recovery);

        String url = getIntent().getStringExtra("url");
        Matcher matcher = Pattern.compile("/user/(?<user>[^/]+)/resetpass/(?<token>[^/]+)").matcher(url);
        if (matcher.find()) {
            this.user = matcher.group("user");
            this.token = matcher.group("token");
        } else {
            finish();
        }

        RxTextView.textChanges(password)
                .compose(bindToLifecycle())
                .map(val -> String.valueOf(val).trim())
                .map(val -> val.length() > 6)
                .subscribe(submit::setEnabled);
    }

    @OnClick(R.id.submit)
    public void submitButtonClicked() {
        String password = String.valueOf(this.password.getText()).trim();
        userService.resetPassword(user, token, password)
                .compose(this.<Boolean>bindToLifecycleAsync().forSingle())
                .subscribe(this::requestCompleted, defaultOnError());
    }

    private void requestCompleted(boolean success) {
        Track.passwordChanged();

        DialogBuilder.start(this)
                .content(success ? R.string.password_recovery_success : R.string.password_recovery_error)
                .positive(R.string.okay, d -> finish())
                .show();
    }
}
