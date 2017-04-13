package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.base.Throwables;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.InviteService;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
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

    @BindView(R.id.invites)
    RecyclerView invites;

    @BindView(R.id.remaining)
    TextView remainingInvites;

    @BindView(R.id.invites_empty)
    View invitesEmptyHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().getBasic());

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_invite);
        disableInputViews();

        invites.setLayoutManager(new LinearLayoutManager(this));

        requeryInvites();
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
        disableInputViews();

        inviteService.send(email)
                .compose(bindToLifecycleAsync().forCompletable())
                .doAfterTerminate(this::requeryInvites)
                .subscribe(this::onInviteSent, this::onInviteError);

        Track.inviteSent();
    }

    private void requeryInvites() {
        inviteService.invites()
                .compose(bindToLifecycleAsync())
                .subscribe(this::handleInvites, defaultOnError());
    }

    private void handleInvites(InviteService.Invites invites) {
        this.invites.setAdapter(new InviteAdapter(invites.getInvited()));
        this.invitesEmptyHint.setVisibility(invites.getInvited().size() > 0 ? View.GONE : View.VISIBLE);

        String text = getString(R.string.invite_remaining, invites.getInviteCount());
        remainingInvites.setText(text);

        if (invites.getInviteCount() > 0) {
            enableInputViews();
        }
    }

    private void enableInputViews() {
        ButterKnife.apply(formFields, (view, idx) -> {
            view.setVisibility(View.VISIBLE);
            view.setEnabled(true);
        });
    }

    private void disableInputViews() {
        ButterKnife.apply(formFields, (view, idx) -> view.setEnabled(false));
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
                defaultOnError().call(error);
            }
        } else {
            defaultOnError().call(error);
        }
    }
}
