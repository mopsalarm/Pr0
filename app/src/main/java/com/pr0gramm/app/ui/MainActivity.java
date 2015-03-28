package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.google.common.base.Throwables;
import com.pr0gramm.app.ErrorFormatting;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.SyncBroadcastReceiver;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.ui.fragments.DrawerFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.PostPagerFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import de.cketti.library.changelog.ChangeLog;
import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.functions.Actions;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindActivity;


/**
 * This is the main class of our pr0gramm app.
 */
public class MainActivity extends RoboActionBarActivity implements
        DrawerFragment.OnFeedFilterSelected,
        FragmentManager.OnBackStackChangedListener,
        ScrollHideToolbarListener.ToolbarActivity,
        MainActionHandler, ErrorDialogFragment.OnErrorDialogHandler {

    @InjectView(R.id.drawer_layout)
    private DrawerLayout drawerLayout;

    @InjectView(R.id.toolbar)
    private Toolbar toolbar;

    @Nullable
    @InjectView(R.id.toolbar_container)
    private View toolbarContainer;

    @Inject
    private UserService userService;

    @Inject
    private BookmarkService bookmarkService;

    @Inject
    private Settings settings;

    @Inject
    private SharedPreferences shared;

    private Subscription subscription;
    private ActionBarDrawerToggle drawerToggle;
    private ScrollHideToolbarListener scrollHideToolbarListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (settings.useHardwareAcceleration()) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        setContentView(R.layout.activity_main);

        // use toolbar as action bar
        setSupportActionBar(toolbar);

        // and hide it away on scrolling
        scrollHideToolbarListener = new ScrollHideToolbarListener(
                firstNonNull(toolbarContainer, toolbar));

        // prepare drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.setDrawerListener(drawerToggle);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);

        getSupportActionBar().setHomeButtonEnabled(true);
        drawerToggle.syncState();

        // listen to fragment changes
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // load feed-fragment into view
        if (savedInstanceState == null) {
            createDrawerFragment();
            gotoFeedFragment(new FeedFilter(), true);
        }

        // we trigger the update here manually now. this will be done using
        // the alarm manager later on.
        Intent intent = new Intent(this, SyncBroadcastReceiver.class);
        sendBroadcast(intent);

        ChangeLog changelog = new ChangeLog(this);
        if (changelog.isFirstRun()) {
            ChangeLogDialog dialog = new ChangeLogDialog();
            dialog.show(getSupportFragmentManager(), null);

        } else {
            // start the update check.
            UpdateDialogFragment.checkForUpdates(this, false);
        }
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().removeOnBackStackChangedListener(this);
        super.onDestroy();
    }

    @Override
    public void onBackStackChanged() {
        updateToolbarBackButton();

        DrawerFragment drawer = getDrawerFragment();
        if (drawer != null) {
            // get the filter of the visible fragment.
            FeedFilter currentFilter = null;
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content);
            if (fragment != null) {
                if (fragment instanceof FeedFragment) {
                    currentFilter = ((FeedFragment) fragment).getCurrentFilter();
                }

                if (fragment instanceof PostPagerFragment) {
                    currentFilter = ((PostPagerFragment) fragment).getCurrentFilter();
                }
            }

            // show the current item in the drawer
            drawer.updateCurrentFilters(currentFilter);
        }
    }

    private void updateToolbarBackButton() {
        FragmentManager fm = getSupportFragmentManager();
        drawerToggle.setDrawerIndicatorEnabled(fm.getBackStackEntryCount() == 0);
        getSupportActionBar().setDisplayHomeAsUpEnabled(fm.getBackStackEntryCount() > 0);
        drawerToggle.syncState();
    }

    private void createDrawerFragment() {
        DrawerFragment fragment = new DrawerFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.left_drawer, fragment)
                .commit();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!drawerToggle.isDrawerIndicatorEnabled()) {
            if (item.getItemId() == android.R.id.home) {
                getSupportFragmentManager().popBackStack();
                return true;
            }
        }

        return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);

    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawers();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ErrorDialogFragment.setGlobalErrorDialogHandler(this);

        Observable<UserService.LoginState> state = userService.getLoginStateObservable();
        subscription = bindActivity(this, state).subscribe(this::onLoginStateChanged, Actions.empty());

        onBackStackChanged();
    }

    @Override
    protected void onPause() {
        ErrorDialogFragment.unsetGlobalErrorDialogHandler(this);

        if (subscription != null)
            subscription.unsubscribe();

        super.onPause();
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        if (state == UserService.LoginState.NOT_AUTHORIZED) {
            // go back to the "top"-fragment
            // gotoFeedFragment(new FeedFilter());
        }
    }

    @Override
    public void onPostClicked(FeedProxy feed, int idx) {
        if (idx < 0 || idx >= feed.getItemCount())
            return;

        Fragment fragment = PostPagerFragment.newInstance(feed, idx);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }

    @Override
    public void onLogoutClicked() {
        bindActivity(this, userService.logout())
                .lift(busyDialog(this))
                .subscribe(Actions.empty(), defaultOnError());
    }

    @Override
    public void onFeedFilterSelectedInNavigation(FeedFilter filter) {
        gotoFeedFragment(filter, true);
        drawerLayout.closeDrawers();
    }

    @Override
    public void onFeedFilterSelected(FeedFilter filter) {
        gotoFeedFragment(filter, false);
    }

    @Override
    public void pinFeedFilter(FeedFilter filter, String title) {
        bookmarkService.create(filter, title).subscribe(Actions.empty(), defaultOnError());
        drawerLayout.openDrawer(Gravity.START);
    }

    private void gotoFeedFragment(FeedFilter newFilter, boolean clear) {
        if (clear)
            clearBackStack();

        Fragment fragment = FeedFragment.newInstance(newFilter);

        // and show the fragment
        @SuppressLint("CommitTransaction")
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, fragment);

        if (!clear)
            transaction.addToBackStack(null);

        transaction.commit();
    }

    private DrawerFragment getDrawerFragment() {
        return (DrawerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.left_drawer);
    }

    private void clearBackStack() {
        getSupportFragmentManager().popBackStackImmediate(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public ScrollHideToolbarListener getScrollHideToolbarListener() {
        return scrollHideToolbarListener;
    }

    @Override
    public void showErrorDialog(Throwable error, ErrorFormatting.Formatter<?> formatter) {
        String message = formatter.handles(error)
                ? formatter.getMessage(this, error)
                : Throwables.getStackTraceAsString(error);

        ErrorDialogFragment.showErrorString(getSupportFragmentManager(), message);
    }
}
