package com.pr0gramm.app.ui;

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

import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.SyncBroadcastReceiver;
import com.pr0gramm.app.UpdateChecker;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Query;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.dialogs.LoginDialogFragment;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.ui.fragments.DrawerFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.PostPagerFragment;

import org.joda.time.Instant;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.errorDialog;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static org.joda.time.Duration.standardHours;
import static rx.android.observables.AndroidObservable.bindActivity;
import static rx.android.observables.AndroidObservable.bindFragment;


/**
 * This is the main class of our pr0gramm app.
 */
@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity implements
        DrawerFragment.OnDrawerActionListener,
        FragmentManager.OnBackStackChangedListener,
        ScrollHideToolbarListener.ToolbarActivity,
        MainActionHandler {

    @InjectView(R.id.drawer_layout)
    private DrawerLayout drawerLayout;

    @InjectView(R.id.toolbar)
    private Toolbar toolbar;

    @Inject
    private UserService userService;

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

        // use toolbar as action bar
        setSupportActionBar(toolbar);

        scrollHideToolbarListener = new ScrollHideToolbarListener(toolbar);

        // prepare drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.setDrawerListener(drawerToggle);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);
        //
        getSupportActionBar().setHomeButtonEnabled(true);
        drawerToggle.syncState();

        // load feed-fragment into view
        if (savedInstanceState == null) {
            createDrawerFragment();
            gotoFeedFragment(FeedType.PROMOTED);
        }

        updateToolbarBackButton();
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        checkForUpdates();

        // we trigger the update here manually now. this will be done using
        // the alarm manager later on.
        Intent intent = new Intent(this, SyncBroadcastReceiver.class);
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().removeOnBackStackChangedListener(this);
        super.onDestroy();
    }

    private void checkForUpdates() {
        if (!settings.updateCheckEnabled())
            return;

        final String key = "MainActivity.lastUpdateCheck";
        Instant last = new Instant(shared.getLong(key, 0));
        if (last.isAfter(Instant.now().minus(standardHours(1))))
            return;

        // update the check-time
        shared.edit().putLong(key, Instant.now().getMillis()).apply();

        // do the check
        bindActivity(this, new UpdateChecker(this).check())
                .onErrorResumeNext(Observable.<UpdateChecker.Update>empty())
                .map(UpdateDialogFragment::newInstance)
                .subscribe(dialog -> dialog.show(getSupportFragmentManager(), null));
    }


    @Override
    public void onBackStackChanged() {
        updateToolbarBackButton();
    }

    private void updateToolbarBackButton() {
        FragmentManager fm = getSupportFragmentManager();
        drawerToggle.setDrawerIndicatorEnabled(fm.getBackStackEntryCount() == 0);
        getSupportActionBar().setDisplayHomeAsUpEnabled(fm.getBackStackEntryCount() > 0);
        drawerToggle.syncState();
    }

    private void createDrawerFragment() {
        DrawerFragment fragment = new DrawerFragment();
        fragment.select(R.id.action_feed_promoted);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.left_drawer, fragment)
                .commit();
    }

    private void gotoFeedFragment(Query query, boolean addToBackStack) {
        FeedFragment fragment = FeedFragment.newInstance(query);

        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
        try {
            tr.replace(R.id.content, fragment);
            if (addToBackStack)
                tr.addToBackStack(null);

        } finally {
            tr.commit();
        }
    }

    private void gotoFeedFragment(FeedType feedType) {
        gotoFeedFragment(new Query().withFeedType(feedType), false);
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

        Observable<UserService.LoginState> state = userService.getLoginStateObservable();
        subscription = bindActivity(this, state).subscribe(this::onLoginStateChanged);
    }

    @Override
    protected void onPause() {
        if (subscription != null)
            subscription.unsubscribe();

        super.onPause();
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        if (state == UserService.LoginState.NOT_AUTHORIZED) {
            // TODO we need to check here, what kind of fragment is visible
            // TODO and then show the promoted feed, if neither new nor promoted
            // TODO is currently visible.
            // gotoFeedFragment(FeedType.PROMOTED);
        }
    }

    @Override
    public void onPostClicked(FeedProxy feed, int idx) {
        Fragment fragment = PostPagerFragment.newInstance(feed, idx);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onLogoutClicked() {
        bindActivity(this, userService.logout())
                .lift(busyDialog(this))
                .lift(errorDialog(this))
                .subscribe();
    }

    @Override
    public void onActionClicked(int action) {
        if (action == R.id.action_feed_new) {
            moveToFeedNew();
            return;
        }

        if (action == R.id.action_feed_promoted) {
            moveToFeedPromoted();
            return;
        }

        if (action == R.id.action_favorites) {
            moveToFeedFavorites();
            return;
        }
    }

    private DrawerFragment getDrawerFragment() {
        return (DrawerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.left_drawer);
    }

    private void moveToFeedPromoted() {
        clearBackStack();
        gotoFeedFragment(FeedType.PROMOTED);
        drawerLayout.closeDrawers();

        getDrawerFragment().select(R.id.action_feed_promoted);
    }

    private void moveToFeedNew() {
        clearBackStack();
        gotoFeedFragment(FeedType.NEW);
        drawerLayout.closeDrawers();

        getDrawerFragment().select(R.id.action_feed_new);
    }

    private void moveToFeedFavorites() {
        LoginDialogFragment.doIfAuthorized(this, () -> {
            String name = userService.getName().orNull();
            if (name == null)
                return;

            clearBackStack();
            gotoFeedFragment(Query.likes(name), false);
            getDrawerFragment().select(R.id.action_favorites);
        });

        drawerLayout.closeDrawers();
    }

    private void clearBackStack() {
        getSupportFragmentManager().popBackStackImmediate(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    /**
     * Called if the user clicked on a task in a post. This should display
     * a new search with the tag as query.
     *
     * @param tag The tag to search for.
     */
    @Override
    public void onTagClicked(Tag tag) {
        Query query = new Query().withTags(tag.getTag());
        gotoFeedFragment(query, true);
    }

    @Override
    public ScrollHideToolbarListener getScrollHideToolbarListener() {
        return scrollHideToolbarListener;
    }
}
