package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.f2prateek.dart.InjectExtra;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Update;
import com.pr0gramm.app.services.UpdateChecker;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.DialogDismissListener;

/**
 * This activity is just there to host the update dialog fragment.
 */
public class UpdateActivity extends BaseAppCompatActivity implements DialogDismissListener {
    public static final String EXTRA_UPDATE = "UpdateActivity__EXTRA_UPDATE";

    @InjectExtra(EXTRA_UPDATE)
    Update update;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.theme().basic);
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            UpdateChecker.download(this, update);
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
