package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

import com.google.common.base.Throwables;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.InviteService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.util.Noop.noop;

/**
 */
public class InviteActivity extends BaseAppCompatActivity {
    @Inject
    InviteService inviteService;

    @BindView(R.id.mail)
    EditText mailField;

    @BindViews({R.id.mail, R.id.send_invite})
    List<View> formFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite);
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @OnClick(R.id.send_invite)
    public void onInviteClicked() {
        String email = mailField.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mailField.setError(getString(R.string.error_email));
            return;
        }

        // disable all views
        ButterKnife.apply(formFields, (view, idx) -> view.setEnabled(false));

        inviteService.send(email)
                .compose(bindToLifecycle())
                .doAfterTerminate(() -> ButterKnife.apply(formFields, (view, idx) -> view.setEnabled(true)))
                .subscribe(event -> onInviteSent(), this::onInviteError);
    }

    private void onInviteSent() {
        Snackbar.make(mailField, R.string.invite_hint_success, Snackbar.LENGTH_SHORT)
                .setAction(R.string.okay, noop)
                .show();
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void onInviteError(Throwable error) {
        Throwable cause = Throwables.getRootCause(error);
        if (cause instanceof InviteService.InviteException) {
            InviteService.InviteException inviteError = (InviteService.InviteException) cause;

            if (inviteError.noMoreInvites()) {
                DialogBuilder.start(this)
                        .content(R.string.invite_no_more_invites)
                        .positive()
                        .show();

            } else if (inviteError.emailFormat()) {
                DialogBuilder.start(this)
                        .content(R.string.error_email)
                        .positive()
                        .show();

            } else if (inviteError.emailInUse()) {
                DialogBuilder.start(this)
                        .content(R.string.invite_email_in_use)
                        .positive()
                        .show();
            } else {
                ErrorDialogFragment.defaultOnError().call(error);
            }
        } else {
            ErrorDialogFragment.defaultOnError().call(error);
        }
    }
}
