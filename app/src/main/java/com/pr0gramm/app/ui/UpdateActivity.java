package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.f2prateek.dart.InjectExtra;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.services.Update;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.DialogDismissListener;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

/**
 * This activity is just there to host the update dialog fragment.
 */
public class UpdateActivity extends BaseAppCompatActivity implements DialogDismissListener {
    public static final String EXTRA_UPDATE = "UpdateActivity.EXTRA_UPDATE";

    @InjectExtra(EXTRA_UPDATE)
    Update update;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            UpdateDialogFragment dialog = UpdateDialogFragment.newInstance(update);
            dialog.show(getSupportFragmentManager(), null);
        }
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        // nothing to do here
    }


    @Override
    public void onDialogDismissed(DialogFragment dialog) {
        finish();
    }
}
