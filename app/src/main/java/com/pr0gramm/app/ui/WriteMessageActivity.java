package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import com.pr0gramm.app.OptionMenuHelper;
import com.pr0gramm.app.OptionMenuHelper.OnOptionsItemSelected;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RxRoboAppCompatActivity;
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.ui.fragments.WriteMessageFragment;

import roboguice.inject.ContentView;

/**
 */
@ContentView(R.layout.activity_fragment)
public class WriteMessageActivity extends RxRoboAppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            WriteMessageFragment fragment = WriteMessageFragment.newInstance();
            fragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.content, fragment, null)
                    .commit();
        }

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayShowHomeEnabled(true);
            actionbar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item) || OptionMenuHelper.dispatch(this, item);
    }

    @OnOptionsItemSelected(android.R.id.home)
    @Override
    public void finish() {
        super.finish();
    }

    public static Intent intent(Context context, Message message) {
        Intent intent = new Intent(context, WriteMessageActivity.class);
        intent.putExtras(WriteMessageFragment.newArguments(message));
        return intent;
    }

    public static Intent intent(Context context, long userId, String name) {
        Intent intent = new Intent(context, WriteMessageActivity.class);
        intent.putExtras(WriteMessageFragment.newArguments(userId, name));
        return intent;
    }

    public static Intent answerComment(Context context, FeedItem feedItem, Comment comment) {
        Intent intent = new Intent(context, WriteMessageActivity.class);
        intent.putExtras(WriteMessageFragment.newArguments(feedItem, comment));
        return intent;
    }
}
