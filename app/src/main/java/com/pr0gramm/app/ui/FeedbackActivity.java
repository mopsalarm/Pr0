package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.FeedbackService;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 */
public class FeedbackActivity extends RoboActionBarActivity {
    private final ActivityErrorHandler errorHandler = new ActivityErrorHandler(this);

    @Inject
    private FeedbackService feedbackService;

    @InjectView(R.id.submit)
    private Button buttonSubmit;

    @InjectView(R.id.feedback_name)
    private EditText vName;

    @InjectView(R.id.feedback_text)
    private EditText vText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
        }

        buttonSubmit.setOnClickListener(v -> submitClicked());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ErrorDialogFragment.setGlobalErrorDialogHandler(errorHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ErrorDialogFragment.unsetGlobalErrorDialogHandler(errorHandler);
    }

    private void submitClicked() {
        String name = vName.getText().toString().trim();
        String feedback = vText.getText().toString().trim();

        bindActivity(this, feedbackService.post(this, name, feedback))
                .lift(busyDialog(this))
                .subscribe(Actions.empty(), defaultOnError());
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
