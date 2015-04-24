package com.pr0gramm.app.ui;

import android.content.Intent;
import android.os.Bundle;

import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;

/**
 * The activity that displays the inbox.
 */
@ContentView(R.layout.activity_inbox)
public class InboxActivity extends RoboActionBarActivity {
    @Inject
    private UserService userService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!userService.isAuthorized()) {
            openMainActivity();
            finish();
            return;
        }

        if (savedInstanceState == null) {

        }
    }

    /**
     * Starts the main activity.
     */
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}
