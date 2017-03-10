package com.pr0gramm.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.services.ContactService;
import com.pr0gramm.app.services.FeedbackService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.OnClick;
import rx.Observable;
import rx.functions.Actions;

import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;

/**
 */
public class ContactActivity extends BaseAppCompatActivity {
    @Inject
    FeedbackService feedbackService;

    @Inject
    UserService userService;

    @Inject
    ContactService contactService;

    @BindView(R.id.submit)
    Button buttonSubmit;

    @BindView(R.id.feedback_name)
    EditText vName;

    @BindView(R.id.feedback_text)
    EditText vText;

    @BindView(R.id.feedback_email)
    EditText vMail;

    @BindView(R.id.feedback_subject)
    EditText vSubject;

    @BindView(R.id.action_feedback_general)
    RadioButton generalFeedbackRadioButton;

    @BindViews({R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_text})
    List<TextView> groupAllTextViews;

    @BindViews({R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_deletion_hint})
    List<View> groupAll;

    @BindViews({R.id.feedback_email, R.id.feedback_subject, R.id.feedback_deletion_hint})
    List<View> groupNormalSupport;

    @BindViews({R.id.feedback_name})
    List<View> groupAppNotLoggedIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feedback);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
        }

        int primary = ContextCompat.getColor(this, ThemeHelper.accentColor());
        ViewCompat.setBackgroundTintList(buttonSubmit, ColorStateList.valueOf(primary));

        // register all the change listeners
        for (TextView textView : groupAllTextViews) {
            textView.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateSubmitButtonActivation();
                }
            });
        }

        Optional<String> name = userService.getName();
        if (name.isPresent()) {
            vName.setText(name.get());
        }

        applyViewVisibility();
    }

    @OnClick({R.id.action_feedback_general, R.id.action_feedback_app})
    void applyViewVisibility() {
        List<View> activeViews;
        if (isNormalSupport()) {
            activeViews = groupNormalSupport;
        } else if (userService.isAuthorized()) {
            activeViews = Collections.emptyList();
        } else {
            activeViews = groupAppNotLoggedIn;
        }

        for (View view : groupAll) {
            view.setVisibility(activeViews.contains(view) ? View.VISIBLE : View.GONE);
        }

        updateSubmitButtonActivation();
    }

    void updateSubmitButtonActivation() {
        boolean enabled = true;
        for (TextView view : groupAllTextViews) {
            if (view.getVisibility() == View.VISIBLE) {
                if (view.getText().toString().trim().isEmpty()) {
                    enabled = false;
                }
            }
        }

        if (vMail.getVisibility() == View.VISIBLE) {
            if (!Patterns.EMAIL_ADDRESS.matcher(vMail.getText()).matches())
                enabled = false;
        }

        buttonSubmit.setEnabled(enabled);
    }

    private boolean isNormalSupport() {
        return generalFeedbackRadioButton.isChecked();
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @OnClick(R.id.submit)
    void submitClicked() {
        String feedback = vText.getText().toString().trim();

        Observable<Nothing> response;
        if (isNormalSupport()) {
            String email = vMail.getText().toString().trim();
            String subject = vSubject.getText().toString().trim();

            feedback += "\n\nGesendet mit der App v" + BuildConfig.VERSION_NAME;

            response = contactService.contactFeedback(email, subject, feedback).toObservable();
        } else {
            String name = userService.getName().or(vName.getText().toString().trim());
            response = feedbackService.post(name, feedback);
        }

        response.compose(bindToLifecycleAsync())
                .lift(busyDialog(this))
                .subscribe(Actions.empty(), defaultOnError(), this::onSubmitSuccess);
    }

    private void onSubmitSuccess() {
        DialogBuilder.start(this)
                .content(R.string.feedback_sent)
                .positive(R.string.okay, di -> finish())
                .onCancel(di -> finish())
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
