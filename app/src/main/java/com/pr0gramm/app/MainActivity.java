package com.pr0gramm.app;

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
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewPropertyAnimator;

import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Query;

import org.joda.time.Instant;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;

import static com.pr0gramm.app.ErrorDialogFragment.errorDialog;
import static org.joda.time.Duration.standardHours;
import static rx.android.observables.AndroidObservable.bindActivity;


/**
 * This is the main class of our pr0gramm app.
 */
@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity implements
        DrawerFragment.OnDrawerActionListener, FragmentManager.OnBackStackChangedListener {

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

    private ActionBarDrawerToggle drawerToggle;
    private Subscription subscription;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // use toolbar as action bar
        setSupportActionBar(toolbar);

        // prepare drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.setDrawerListener(drawerToggle);

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
                .lift(errorDialog(this))
                .map(UpdateChecker.UpdateDialogFragment::newInstance)
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!drawerToggle.isDrawerIndicatorEnabled()) {
            if (item.getItemId() == android.R.id.home) {
                getSupportFragmentManager().popBackStack();
                return true;
            }
        }

        if (drawerToggle.onOptionsItemSelected(item))
            return true;

        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (item.getItemId() == R.id.action_login) {
            LoginDialogFragment dialog = new LoginDialogFragment();
            dialog.show(getSupportFragmentManager(), null);
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
            userService.logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem logout = menu.findItem(R.id.action_logout);
        if (logout != null)
            logout.setVisible(userService.isAuthorized());

        MenuItem login = menu.findItem(R.id.action_login);
        if (login != null)
            login.setVisible(!userService.isAuthorized());

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START | Gravity.LEFT)) {
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

    public void onPostClicked(FeedProxy feed, int idx) {
        Fragment fragment = PostPagerFragment.newInstance(feed, idx);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
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
    public void onTagClicked(Post.Tag tag) {
        Query query = new Query().withTags(tag.getTag());
        gotoFeedFragment(query, true);
    }

    public class ScrollHideToolbarListener {
        private int toolbarMarginOffset = 0;
        private ViewPropertyAnimator animation;

        private ScrollHideToolbarListener() {
        }

        private void applyToolbarPosition(boolean animated) {
            int y = -toolbarMarginOffset;
            if (animated) {
                animation = toolbar.animate().translationY(y).setDuration(250);
                animation.start();
            } else {
                if (animation != null) {
                    animation.cancel();
                    animation = null;
                }

                Log.i("MainActivity", "Toolbar is " + toolbar);
                toolbar.setTranslationY(y);
            }
        }

        public void onScrolled(int dy) {
            int abHeight = AndroidUtility.getActionBarSize(MainActivity.this);

            toolbarMarginOffset += dy;
            if (toolbarMarginOffset > abHeight)
                toolbarMarginOffset = abHeight;

            if (toolbarMarginOffset < 0)
                toolbarMarginOffset = 0;

            applyToolbarPosition(false);
        }

        public void reset() {
            if (toolbarMarginOffset != 0) {
                toolbarMarginOffset = 0;
                applyToolbarPosition(true);
            }
        }
    }

    public final ScrollHideToolbarListener onScrollHideToolbarListener = new ScrollHideToolbarListener();
}
