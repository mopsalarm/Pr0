package com.pr0gramm.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewPropertyAnimator;

import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Query;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;


/**
 * This is the main class of our pr0gramm app.
 */
@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity implements
        DrawerFragment.OnDrawerActionListener {

    @InjectView(R.id.drawer_layout)
    private DrawerLayout drawerLayout;

    @InjectView(R.id.toolbar)
    private Toolbar toolbar;

    @Inject
    private LoginService loginService;

    private ActionBarDrawerToggle drawerToggle;

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
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbarBackButton);
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

    private void gotoFeedFragment(FeedType feedType) {
        gotoFeedFragment(new Query().withFeedType(feedType));
    }

    private void gotoFeedFragment(Query query) {
        FeedFragment fragment = FeedFragment.newInstance(query);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
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

        if (drawerToggle.onOptionsItemSelected(item))
            return true;

        if (item.getItemId() == R.id.action_login) {
            LoginDialogFragment dialog = new LoginDialogFragment();
            dialog.show(getSupportFragmentManager(), null);
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
            loginService.logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem logout = menu.findItem(R.id.action_logout);
        if (logout != null)
            logout.setVisible(loginService.isAuthorized());

        MenuItem login = menu.findItem(R.id.action_login);
        if (login != null)
            login.setVisible(!loginService.isAuthorized());

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
            clearBackStack();
            gotoFeedFragment(FeedType.NEW);
            drawerLayout.closeDrawers();
            return;
        }

        if (action == R.id.action_feed_promoted) {
            clearBackStack();
            gotoFeedFragment(FeedType.PROMOTED);
            drawerLayout.closeDrawers();
            return;
        }

        if (action == R.id.action_favorites) {
            LoginDialogFragment.doIfAuthorized(this, () -> {
                clearBackStack();
                gotoFeedFragment(Query.likes("Mopsalarm"));
                drawerLayout.closeDrawers();
                return;
            });

            return;
        }
    }

    private void clearBackStack() {
        getSupportFragmentManager().popBackStackImmediate(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
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
