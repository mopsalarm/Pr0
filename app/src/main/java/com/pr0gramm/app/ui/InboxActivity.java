package com.pr0gramm.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;

import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.fragments.InboxFragment;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;

/**
 * The activity that displays the inbox.
 */
public class InboxActivity extends RoboActionBarActivity {
    public static final String EXTRA_INBOX_TYPE = "InboxActivity.inboxType";

    @Inject
    private UserService userService;

    @InjectView(android.R.id.tabhost)
    private TabHost tabHost;

    @InjectView(R.id.pager)
    private ViewPager viewPager;

    @InjectView(android.R.id.tabs)
    private TabWidget tabWidget;

    @InjectView(R.id.tab_indicator)
    private View indicator;

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

        // put the actionbar down
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setElevation(0);

        // elevate the tab host, so that it cast a shadow.
        float elevation = 4 * getResources().getDisplayMetrics().density;
        ViewCompat.setElevation(tabHost, elevation);

        tabHost.setup();

        tabsAdapter = new TabsAdapter(this, tabHost, tabWidget, viewPager);
        tabsAdapter.addTab(tabHost.newTabSpec("Inbox.unread"), R.string.inbox_type_unread, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.UNREAD));

        tabsAdapter.addTab(tabHost.newTabSpec("Inbox.all"), R.string.inbox_type_all, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.ALL));

        tabsAdapter.addTab(tabHost.newTabSpec("Inbox.private"), R.string.inbox_type_private,
                PrivateMessageInboxFragment.class, null);

        tabsAdapter.setOnTabChangedListener(tabId -> onTabChanged());

        // this is to animate the little line below the tabs
        viewPager.setOnPageChangeListener(new PageChangeListener());

        // restore previously selected tab
        if (savedInstanceState != null) {
            tabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
        } else {
            handleNewIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNewIntent(intent);
    }

    private void handleNewIntent(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            showInboxType(InboxType.values()[extras.getInt(EXTRA_INBOX_TYPE, 0)]);
        }
    }

    private void showInboxType(InboxType type) {
        if (type == InboxType.UNREAD)
            tabHost.setCurrentTab(0);

        else if (type == InboxType.ALL)
            tabHost.setCurrentTab(1);

        else if (type == InboxType.PRIVATE)
            tabHost.setCurrentTab(2);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (tabHost != null)
            onTabChanged();
    }

    private void onTabChanged() {
        int index = tabHost.getCurrentTab();
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

        if (tabHost != null) {
            outState.putString("tab", tabHost.getCurrentTabTag());
        }
    }

    /**
     * Starts the main activity.
     */
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private class PageChangeListener implements ViewPager.OnPageChangeListener {
        private int scrollingState = ViewPager.SCROLL_STATE_IDLE;

        @Override
        public void onPageSelected(int position) {
            if (scrollingState == ViewPager.SCROLL_STATE_IDLE) {
                updateIndicatorPosition(position, 0);
            }

            // tabWidget.setCurrentTab(position);
            tabHost.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            scrollingState = state;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            updateIndicatorPosition(position, positionOffset);
        }

        private void updateIndicatorPosition(int position, float positionOffset) {
            View tabView = tabWidget.getChildTabViewAt(position);
            int indicatorWidth = tabView.getWidth();
            int indicatorLeft = (int) ((position + positionOffset) * indicatorWidth);

            final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            layoutParams.width = indicatorWidth;
            layoutParams.setMargins(indicatorLeft, 0, 0, 0);
            indicator.setLayoutParams(layoutParams);
        }
    }
}
