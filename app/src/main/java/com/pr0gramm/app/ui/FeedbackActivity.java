package com.pr0gramm.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.FeedbackService;
import com.pr0gramm.app.services.UserService;

import javax.inject.Inject;

import butterknife.Bind;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;

/**
 */
public class FeedbackActivity extends RxRoboAppCompatActivity {
    @Inject
    FeedbackService feedbackService;

    @Inject
    UserService userService;

    @Bind(R.id.submit)
    Button buttonSubmit;

    @Bind(R.id.feedback_name)
    EditText vName;

    @Bind(R.id.feedback_text)
    EditText vText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
        }

        int primary = getResources().getColor(R.color.primary);
        ViewCompat.setBackgroundTintList(buttonSubmit, ColorStateList.valueOf(primary));
        buttonSubmit.setOnClickListener(v -> submitClicked());

        vText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean empty = s.toString().trim().isEmpty();
                buttonSubmit.setEnabled(!empty);
            }
        });

        Optional<String> name = userService.getName();
        if (name.isPresent()) {
            vName.setText(name.get());
        }
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    private void submitClicked() {
        String name = vName.getText().toString().trim();
        String feedback = vText.getText().toString().trim();

        feedbackService.post(name, feedback)
                .lift(busyDialog(this))
                .compose(bindToLifecycle())
                .doOnCompleted(this::onSubmitSuccess)
                .subscribe(Actions.empty(), defaultOnError());
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
