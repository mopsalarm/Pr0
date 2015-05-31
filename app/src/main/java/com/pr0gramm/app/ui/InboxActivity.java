package com.pr0gramm.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.OnErrorDialogHandler;
import com.pr0gramm.app.ui.fragments.InboxFragment;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;
import com.pr0gramm.app.ui.fragments.WrittenCommentFragment;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;

/**
 * The activity that displays the inbox.
 */
public class InboxActivity extends RoboActionBarActivity implements ViewPager.OnPageChangeListener {
    public static final String EXTRA_INBOX_TYPE = "InboxActivity.inboxType";

    private final OnErrorDialogHandler errorHandler = new ActivityErrorHandler(this);

    @Inject
    private UserService userService;

    @InjectView(R.id.pager)
    private ViewPager viewPager;

    @InjectView(R.id.tabs)
    private TabLayout tabLayout;

    private TabsAdapter tabsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!userService.isAuthorized()) {
            openMainActivity();
            finish();
            return;
        }

        setContentView(R.layout.activity_inbox);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        tabsAdapter = new TabsAdapter(this);
        tabsAdapter.addTab(R.string.inbox_type_unread, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.UNREAD));

        tabsAdapter.addTab(R.string.inbox_type_all, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.ALL));

        tabsAdapter.addTab(R.string.inbox_type_private, PrivateMessageInboxFragment.class, null);

        tabsAdapter.addTab(R.string.inbox_type_comments, WrittenCommentFragment.class, null);

        viewPager.setAdapter(tabsAdapter);
        viewPager.addOnPageChangeListener(this);

        tabLayout.setupWithViewPager(viewPager);

        // this is to animate the little line below the tabs
        // viewPager.addOnPageChangeListener(new PageChangeListener());

        // restore previously selected tab
        if (savedInstanceState != null) {
            viewPager.setCurrentItem(savedInstanceState.getInt("tab"));
        } else {
            handleNewIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNewIntent(intent);
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

    private void handleNewIntent(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            showInboxType(InboxType.values()[extras.getInt(EXTRA_INBOX_TYPE, 0)]);
        }
    }

    private void showInboxType(InboxType type) {
        if (type == InboxType.UNREAD)
            viewPager.setCurrentItem(0);

        else if (type == InboxType.ALL)
            viewPager.setCurrentItem(1);

        else if (type == InboxType.PRIVATE)
            viewPager.setCurrentItem(2);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (viewPager != null)
            onTabChanged();
    }

    private void onTabChanged() {
        int index = viewPager.getCurrentItem();
        if (index >= 0 && index < tabsAdapter.getCount()) {
            setTitle(tabsAdapter.getPageTitle(index));
        }

        InboxFragment fragment = tabsAdapter.getTabFragment(index)
                .transform(f -> (InboxFragment) f)
                .orNull();

        if (fragment != null) {
            // now perform the load on the inbox
            fragment.loadIfLazy();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (viewPager != null) {
            outState.putInt("tab", viewPager.getCurrentItem());
        }
    }

    /**
     * Starts the main activity.
     */
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        onTabChanged();
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

//    private class PageChangeListener implements ViewPager.OnPageChangeListener {
//        private int scrollingState = ViewPager.SCROLL_STATE_IDLE;
//
//        @Override
//        public void onPageSelected(int position) {
//            if (scrollingState == ViewPager.SCROLL_STATE_IDLE) {
//                updateIndicatorPosition(position, 0);
//            }
//
//            // tabWidget.setCurrentTab(position);
//            tabHost.setCurrentTab(position);
//        }
//
//        @Override
//        public void onPageScrollStateChanged(int state) {
//            scrollingState = state;
//        }
//
//        @Override
//        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
//            updateIndicatorPosition(position, positionOffset);
//        }
//
//        private void updateIndicatorPosition(int position, float positionOffset) {
//            View tabView = tabWidget.getChildTabViewAt(position);
//            int indicatorWidth = tabView.getWidth();
//            int indicatorLeft = (int) ((position + positionOffset) * indicatorWidth);
//
//            final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) indicator.getLayoutParams();
//            layoutParams.width = indicatorWidth;
//            layoutParams.setMargins(indicatorLeft, 0, 0, 0);
//            indicator.setLayoutParams(layoutParams);
//        }
//    }
}
