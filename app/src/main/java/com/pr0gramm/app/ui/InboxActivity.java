package com.pr0gramm.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.fragments.MessageInboxFragment;
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment;
import com.pr0gramm.app.ui.fragments.WrittenCommentFragment;

import javax.inject.Inject;

import butterknife.BindView;


/**
 * The activity that displays the inbox.
 */
public class InboxActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {
    public static final String EXTRA_INBOX_TYPE = "InboxActivity.inboxType";
    public static final String EXTRA_FROM_NOTIFICATION = "InboxActivity.fromNotification";
    public static final String EXTRA_MESSAGE_TIMESTAMP = "InboxActivity.maxMessageId";

    @Inject
    UserService userService;

    @Inject
    NotificationService notificationService;

    @Inject
    InboxService inboxService;

    @BindView(R.id.pager)
    ViewPager viewPager;

    @BindView(R.id.tabs)
    TabLayout tabLayout;

    private TabsAdapter tabsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.INSTANCE.getTheme().getNoActionBar());
        super.onCreate(savedInstanceState);

        if (!userService.isAuthorized()) {
            MainActivity.open(this);
            finish();
            return;
        }

        setContentView(R.layout.activity_inbox);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        tabsAdapter = new TabsAdapter(this, this.getSupportFragmentManager());
        tabsAdapter.addTab(R.string.inbox_type_unread, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.UNREAD));

        tabsAdapter.addTab(R.string.inbox_type_all, MessageInboxFragment.class,
                MessageInboxFragment.buildArguments(InboxType.ALL));

        tabsAdapter.addTab(R.string.inbox_type_private, PrivateMessageInboxFragment.class, null);

        tabsAdapter.addTab(R.string.inbox_type_comments, WrittenCommentFragment.class, null);

        viewPager.setAdapter(tabsAdapter);
        viewPager.addOnPageChangeListener(this);

        tabLayout.setupWithViewPager(viewPager);

        // restore previously selected tab
        if (savedInstanceState != null) {
            viewPager.setCurrentItem(savedInstanceState.getInt("tab"));
        } else {
            handleNewIntent(getIntent());
        }

        // track if we've clicked the notification!
        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.notificationClosed("clicked");
        }

        inboxService.markAsRead(getIntent().getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0));
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item) || OptionMenuHelper.INSTANCE.dispatch(this, item);
    }

    @OnOptionsItemSelected(android.R.id.home)
    @Override
    public void finish() {
        super.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // remove pending notifications.
        notificationService.cancelForInbox();
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
        if (type != null && type.ordinal() < tabsAdapter.getCount()) {
            viewPager.setCurrentItem(type.ordinal());
        }
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
            trackScreen(index);
        }
    }

    /**
     * Might not be the most beautiful code, but works for now.
     */
    private void trackScreen(int index) {
        if (index == 0)
            Track.screen("InboxUnread");

        if (index == 1)
            Track.screen("InboxOverview");

        if (index == 2)
            Track.screen("InboxPrivate");

        if (index == 3)
            Track.screen("InboxComments");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (viewPager != null) {
            outState.putInt("tab", viewPager.getCurrentItem());
        }
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
}
