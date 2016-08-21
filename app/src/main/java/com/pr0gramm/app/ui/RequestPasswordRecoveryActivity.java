package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.v4.util.PatternsCompat;
import android.widget.Button;
import android.widget.EditText;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

public class RequestPasswordRecoveryActivity extends BaseAppCompatActivity {
    @BindView(R.id.submit)
    Button submit;

    @BindView(R.id.email)
    EditText email;

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
        setContentView(R.layout.activity_request_password_recovery);

        RxTextView.textChanges(email)
                .compose(bindToLifecycle())
                .map(val -> String.valueOf(val).trim())
                .map(val -> PatternsCompat.EMAIL_ADDRESS.matcher(val).matches())
                .subscribe(submit::setEnabled);
    }

    @OnClick(R.id.submit)
    public void submitButtonClicked() {
        String email = String.valueOf(this.email.getText()).trim();
        userService.requestPasswordRecovery(email)
                .compose(bindToLifecycleAsync().forCompletable())
                .subscribe(this::requestCompleted, defaultOnError());
    }

    private void requestCompleted() {
        DialogBuilder.start(this)
                .content(R.string.request_password_recovery_popup_hint)
                .positive(R.string.okay, d -> finish())
                .show();
    }
}
