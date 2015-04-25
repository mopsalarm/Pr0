package com.pr0gramm.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.webkit.WebView;

import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import de.cketti.library.changelog.ChangeLog;

/**
 */
public class ChangeLogDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ChangeLog changeLog = new ChangeLog(getActivity());
        String htmlSource = changeLog.getFullLog();

        WebView webView = new WebView(getActivity());
        webView.loadDataWithBaseURL(null, htmlSource, "text/html", "UTF-8", null);

        Settings settings = Settings.of(getActivity());

        return DialogBuilder.start(getActivity())
                .content(webView, false)
                .title(R.string.changelog_title)
                .positive(R.string.okay, () -> {
                    changeLog.skipLogDialog();
                    if (settings.useBetaChannel()) {
                        showFeedbackReminderDialog();
                    }
                })
                .build();
    }

    private void showFeedbackReminderDialog() {
        DialogBuilder.start(getActivity())
                .content(R.string.feedback_reminder)
                .positive(R.string.okay)
                .show();
    }
}
