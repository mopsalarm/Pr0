package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.view.menu.ActionMenuItem;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import com.flipboard.bottomsheet.BottomSheetLayout;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RequestCodes;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.InfoMessageService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.sync.SyncJob;
import com.pr0gramm.app.ui.back.BackFragmentHelper;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.ui.fragments.BusyDialog;
import com.pr0gramm.app.ui.fragments.DrawerFragment;
import com.pr0gramm.app.ui.fragments.FavoritesFragment;
import com.pr0gramm.app.ui.fragments.FeedFragment;
import com.pr0gramm.app.ui.fragments.ItemWithComment;
import com.pr0gramm.app.ui.fragments.PostPagerNavigation;
import com.pr0gramm.app.ui.intro.IntroActivity;
import com.pr0gramm.app.ui.upload.UploadActivity;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.CustomTabsHelper;
import com.trello.rxlifecycle.android.RxLifecycleAndroid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Actions;
import rx.subjects.BehaviorSubject;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.Noop.noop;
import static rx.android.schedulers.AndroidSchedulers.mainThread;


/**
 * This is the main class of our pr0gramm app.
 */
public class MainActivity extends BaseAppCompatActivity implements
        DrawerFragment.OnFeedFilterSelected,
        FragmentManager.OnBackStackChangedListener,
        ScrollHideToolbarListener.ToolbarActivity,
        MainActionHandler, PermissionHelperActivity,
        AdControl {

    // we use this to propagate a fake-home event to the fragments.
    public static final int ID_FAKE_HOME = android.R.id.list;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BehaviorSubject<Boolean> doNotShowAds = BehaviorSubject.create(false);


    @BindView(R.id.drawer_layout)
    DrawerLayout drawerLayout;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @Nullable
    @BindView(R.id.toolbar_container)
    View toolbarContainer;

    @BindView(R.id.content)
    View contentContainer;

    @BindView(R.id.bottomsheet)
    BottomSheetLayout bottomSheet;

    @Inject
    UserService userService;

    @Inject
    BookmarkService bookmarkService;

    @Inject
    Settings settings;

    @Inject
    SharedPreferences shared;

    @Inject
    SingleShotService singleShotService;

    @Inject
    InfoMessageService infoMessageService;

    @Inject
    AdService adService;

    PermissionHelper permissionHelper = new PermissionHelper(this);

    private ActionBarDrawerToggle drawerToggle;
    private ScrollHideToolbarListener scrollHideToolbarListener;
    private boolean startedWithIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // enable transition on lollipop and above
            supportRequestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        }

        setTheme(theme().getTranslucentStatus());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // use toolbar as action bar
        setSupportActionBar(toolbar);

        // and hide it away on scrolling
        scrollHideToolbarListener = new ScrollHideToolbarListener(
                firstNonNull(toolbarContainer, toolbar));

        // prepare drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        drawerLayout.addDrawerListener(drawerToggle);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        drawerToggle.syncState();

        // listen to fragment changes
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        boolean coldStart = savedInstanceState == null;
        if (coldStart) {
            Intent intent = getIntent();
            boolean startedFromLauncher = intent == null || Intent.ACTION_MAIN.equals(intent.getAction());

            // reset to sfw only.
            if (settings.getFeedStartAtSfw() && startedFromLauncher) {
                logger.info("Force-switch to sfw only.");
                settings.edit()
                        .putBoolean("pref_feed_type_sfw", true)
                        .putBoolean("pref_feed_type_nsfw", false)
                        .putBoolean("pref_feed_type_nsfl", false)
                        .apply();
            }

            createDrawerFragment();

            if (startedFromLauncher) {
                // load feed-fragment into view
                gotoFeedFragment(defaultFeedFilter(), true);

                // only check on normal app start
                checkForInfoMessage();

            } else {
                startedWithIntent = true;
                onNewIntent(intent);
            }
        }

        if (shouldShowOnboardingActivity()) {
            startActivityForResult(new Intent(this, IntroActivity.class), RequestCodes.INSTANCE.getINTRO_ACTIVITY());
            return;
        }

        if (coldStart) {
            boolean updateCheck = true;
            boolean updateCheckDelay = false;

            if (singleShotService.firstTimeInVersion("changelog")) {
                updateCheck = false;

                ChangeLogDialog dialog = new ChangeLogDialog();
                dialog.show(getSupportFragmentManager(), null);

            } else if (shouldShowFeedbackReminder()) {
                //noinspection ResourceType
                Snackbar.make(contentContainer, R.string.feedback_reminder, 10000)
                        .setAction(R.string.okay, noop)
                        .show();

                updateCheckDelay = true;
            } else {
                preparePremiumHint();
            }

            if (updateCheck) {
                Observable.just(null)
                        .delay(updateCheckDelay ? 10 : 0, TimeUnit.SECONDS, mainThread())
                        .compose(RxLifecycleAndroid.bindActivity(lifecycle()))
                        .subscribe(event -> UpdateDialogFragment.Companion.checkForUpdates(this, false));
            }
        }

        // migrate the surface view option.
        if (singleShotService.isFirstTime("migrate.SurfaceView")) {
            settings.edit()
                    .putBoolean("pref_use_texture_view_new", true)
                    .apply();
        }
    }

    private void preparePremiumHint() {
        if (singleShotService.firstTimeToday("hint_ads_pr0mium:2")) {
            Observable<Boolean> showAnyAds =
                    adService.enabledForType(Config.AdType.FEED).take(1);

            showAnyAds.takeFirst(v -> v)
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .onErrorResumeNext(Observable.empty())
                    .filter(v -> !userService.isPremiumUser())
                    .subscribe(adsAreShown -> {
                        Snackbar.make(contentContainer, R.string.hint_dont_like_ads, 20000)
                                .setAction("pr0mium", v -> {
                                    Track.registerLinkClicked();
                                    Uri uri = Uri.parse("https://pr0gramm.com/pr0mium/iap");
                                    new CustomTabsHelper(this).openCustomTab(uri);
                                })
                                .show();
                    });
        }
    }

    @Override
    public void showAds(boolean show) {
        doNotShowAds.onNext(!show);
    }

    private boolean shouldShowOnboardingActivity() {
        return singleShotService.isFirstTime("onboarding-activity:1");
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    private void checkForInfoMessage() {
        infoMessageService.fetch()
                .onErrorResumeNext(Observable.empty())
                .compose(bindToLifecycleAsync())
                .subscribe(this::showInfoMessage);
    }

    private boolean shouldShowFeedbackReminder() {
        // By design it is | and not ||. We want both conditions to
        // be evaluated for the sideeffects
        return settings.getUseBetaChannel()
                && (singleShotService.firstTimeInVersion("hint_feedback_reminder")
                | singleShotService.firstTimeToday("hint_feedback_reminder"));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (!Intent.ACTION_VIEW.equals(intent.getAction()))
            return;

        handleUri(intent.getData());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RequestCodes.INSTANCE.getINTRO_ACTIVITY()) {
            AndroidUtility.recreateActivity(this);
        }
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().removeOnBackStackChangedListener(this);

        try {
            super.onDestroy();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onBackStackChanged() {
        updateToolbarBackButton();
        updateActionbarTitle();

        DrawerFragment drawer = getDrawerFragment();
        if (drawer != null) {
            FeedFilter currentFilter = getCurrentFeedFilter();

            // show the current item in the drawer
            drawer.updateCurrentFilters(currentFilter);
        }

        if (BuildConfig.DEBUG) {
            printFragmentStack();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Settings.VolumeNavigationType navigationType = settings.getVolumeNavigation();
        Fragment fragment = getCurrentFragment();
        if (fragment instanceof PostPagerNavigation) {
            PostPagerNavigation pager = (PostPagerNavigation) fragment;

            // volume keys navigation (only if enabled)
            if (navigationType != Settings.VolumeNavigationType.DISABLED) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (navigationType == Settings.VolumeNavigationType.UP) {
                        pager.moveToNext();
                    } else {
                        pager.moveToPrev();
                    }

                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (navigationType == Settings.VolumeNavigationType.UP) {
                        pager.moveToPrev();
                    } else {
                        pager.moveToNext();
                    }

                    return true;
                }
            }

            // keyboard or d-pad navigation (always possible)
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    pager.moveToNext();
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    pager.moveToPrev();
                    break;
                default:
                    // no-op
                    break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Prints the current fragmetn stack. This should only be invoked in debug builds.
     */
    private void printFragmentStack() {
        List<String> names = new ArrayList<>();
        names.add("root");
        for (int idx = 0; idx < getSupportFragmentManager().getBackStackEntryCount(); idx++) {
            FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(idx);
            names.add(String.valueOf(entry.getName()));
        }

        logger.info("stack: {}", Joiner.on(" -> ").join(names));
    }

    private void updateActionbarTitle() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            FeedFilter filter = getCurrentFeedFilter();
            if (filter == null) {
                bar.setTitle(R.string.pr0gramm);
                bar.setSubtitle(null);
            } else {
                FeedFilterFormatter.FeedTitle feed = FeedFilterFormatter.format(this, filter);
                bar.setTitle(feed.getTitle());
                bar.setSubtitle(feed.getSubtitle());
            }
        }
    }

    /**
     * Returns the current feed filter. Might be null, if no filter could be detected.
     */
    @Nullable
    private FeedFilter getCurrentFeedFilter() {
        // get the filter of the visible fragment.
        FeedFilter currentFilter = null;
        Fragment fragment = getCurrentFragment();
        if (fragment instanceof FilterFragment) {
            currentFilter = ((FilterFragment) fragment).getCurrentFilter();
        }

        return currentFilter;
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content);
    }

    private boolean shouldClearOnIntent() {
        return !(getCurrentFragment() instanceof FavoritesFragment)
                && getSupportFragmentManager().getBackStackEntryCount() == 0;
    }

    private void updateToolbarBackButton() {
        drawerToggle.setDrawerIndicatorEnabled(shouldClearOnIntent());
        drawerToggle.syncState();
    }

    private void createDrawerFragment() {
        DrawerFragment fragment = new DrawerFragment();

        getSupportFragmentManager().beginTransaction()
                .setAllowOptimization(false)
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
                if (!dispatchFakeHomeEvent(item))
                    onBackPressed();

                return true;
            }
        }

        return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    private boolean dispatchFakeHomeEvent(MenuItem item) {
        //noinspection RestrictedApi
        return onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, new ActionMenuItem(
                this, item.getGroupId(), ID_FAKE_HOME, 0, item.getOrder(), item.getTitle()));
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
            return;
        }

        // dispatch to fragments
        if (BackFragmentHelper.dispatchOnBackAction(this)) {
            return;
        }

        // at the end, go back to the "top" page before stopping everything.
        if (getSupportFragmentManager().getBackStackEntryCount() == 0 && !startedWithIntent) {
            FeedFilter filter = getCurrentFeedFilter();
            if (filter != null && !isDefaultFilter(filter)) {
                gotoFeedFragment(defaultFeedFilter(), true);
                return;
            }
        }

        try {
            super.onBackPressed();
        } catch (IllegalStateException ignored) {
            // workaround for:
            // this is sometimes called after onSaveInstanceState
        }
    }

    private boolean isDefaultFilter(FeedFilter filter) {
        return defaultFeedFilter().equals(filter);
    }

    private void showInfoMessage(InfoMessageService.Message message) {
        if (message.endOfLife() >= AndroidUtility.buildVersionCode()) {
            DialogBuilder.Companion.start(this)
                    .contentWithLinks("Support für deine Version ist eingestellt. Um die pr0gramm-App weiter benutzen zu können, lade eine aktuelle Version von https://app.pr0gramm.com herunter.")
                    .positive(R.string.okay, () -> {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://app.pr0gramm.com")));
                        finish();
                    })
                    .show();

            return;
        }

        String text = message.message();
        if (!Strings.isNullOrEmpty(text)) {
            DialogBuilder.Companion.start(this)
                    .contentWithLinks(text)
                    .positive()
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        onBackStackChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // schedule a sync operation every minute
        Observable.interval(0, 1, TimeUnit.MINUTES, mainThread())
                .compose(RxLifecycleAndroid.bindActivity(lifecycle()))
                .subscribe(event -> SyncJob.Companion.syncNow(this));
    }

    @Override
    public void onLogoutClicked() {
        drawerLayout.closeDrawers();
        Track.logout();

        final int logout_successful_hint = R.string.logout_successful_hint;
        userService.logout()
                .toObservable()
                .compose(bindToLifecycleAsync())
                .lift(BusyDialog.busyDialog(this))
                .doOnCompleted(() -> {
                    // show a short information.
                    Snackbar.make(drawerLayout, logout_successful_hint, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.okay, noop)
                            .show();

                    // reset everything!
                    gotoFeedFragment(defaultFeedFilter(), true);
                })
                .subscribe(Actions.empty(), defaultOnError());
    }

    private FeedFilter defaultFeedFilter() {
        FeedType type = settings.getFeedStartAtNew() ? FeedType.NEW : FeedType.PROMOTED;
        return new FeedFilter().withFeedType(type);
    }

    @Override
    public void onFeedFilterSelectedInNavigation(FeedFilter filter) {
        gotoFeedFragment(filter, true);
        drawerLayout.closeDrawers();
    }

    @Override
    public void onOtherNavigationItemClicked() {
        drawerLayout.closeDrawers();
    }

    @Override
    public void onNavigateToFavorites(String username) {
        // move to new fragment
        FavoritesFragment fragment = FavoritesFragment.Companion.newInstance(username);
        moveToFragment(fragment, true);

        drawerLayout.closeDrawers();
    }

    @Override
    public void onUsernameClicked() {
        Optional<String> name = userService.getName();
        if (name.isPresent()) {
            FeedFilter filter = new FeedFilter()
                    .withFeedType(FeedType.NEW)
                    .withUser(name.get());

            gotoFeedFragment(filter, false);
        }

        drawerLayout.closeDrawers();
    }

    @Override
    public void onFeedFilterSelected(FeedFilter filter) {
        onFeedFilterSelected(filter, null);
    }

    @Override
    public void onFeedFilterSelected(FeedFilter filter, @Nullable Bundle searchQueryState) {
        gotoFeedFragment(filter, false, null, searchQueryState);
    }

    @Override
    public void onFeedFilterSelected(FeedFilter filter, @Nullable Bundle searchQueryState,
                                     @Nullable ItemWithComment startAt) {

        gotoFeedFragment(filter, false, startAt, searchQueryState);
    }

    @Override
    public void pinFeedFilter(FeedFilter filter, String title) {
        bookmarkService.create(filter, title);
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void gotoFeedFragment(FeedFilter newFilter, boolean clear) {
        gotoFeedFragment(newFilter, clear, null, null);
    }

    private void gotoFeedFragment(FeedFilter newFilter, boolean clear, @Nullable ItemWithComment start) {
        gotoFeedFragment(newFilter, clear, start, null);
    }

    private void gotoFeedFragment(FeedFilter newFilter, boolean clear,
                                  @Nullable ItemWithComment start,
                                  @Nullable Bundle searchQueryState) {

        moveToFragment(FeedFragment.newInstance(newFilter, start, searchQueryState), clear);
    }

    private void moveToFragment(Fragment fragment, boolean clear) {
        if (isFinishing())
            return;

        if (clear) {
            clearBackStack();
        }

        // and show the fragment
        @SuppressLint("CommitTransaction")
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .setAllowOptimization(false)
                .replace(R.id.content, fragment);

        if (!clear) {
            logger.info("Adding fragment {} to backstrack", fragment.getClass().getName());
            transaction.addToBackStack("Feed" + fragment);
        }

        try {
            transaction.commit();
        } catch (IllegalStateException ignored) {
        }

        // trigger a back-stack changed after adding the fragment.
        handler.post(this::onBackStackChanged);
    }

    private DrawerFragment getDrawerFragment() {
        return (DrawerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.left_drawer);
    }

    private void clearBackStack() {
        try {
            getSupportFragmentManager().popBackStackImmediate(
                    null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception err) {
            AndroidUtility.logToCrashlytics(new RuntimeException(
                    "Ignoring exception from popBackStackImmediate:", err));
        }
    }

    @Override
    public ScrollHideToolbarListener getScrollHideToolbarListener() {
        return scrollHideToolbarListener;
    }

    /**
     * Handles a uri to something on pr0gramm
     *
     * @param uri The uri to handle
     */
    private void handleUri(Uri uri) {
        if (uri.toString().matches(".*/user/[^/]+/resetpass/[^/]+")) {
            Intent intent = new Intent(this, PasswordRecoveryActivity.class);
            intent.putExtra("url", uri.toString());
            startActivity(intent);
            return;
        }

        Optional<FeedFilterWithStart> result = FeedFilterWithStart.Companion.fromUri(uri);
        if (result.isPresent()) {
            boolean clear = shouldClearOnIntent();

            FeedFilter filter = result.get().getFilter();
            ItemWithComment start = result.get().getStart();

            gotoFeedFragment(filter, clear, start);

        } else {
            gotoFeedFragment(defaultFeedFilter(), true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public Observable<Void> requirePermission(String permission) {
        return permissionHelper.requirePermission(permission);
    }

    @Override
    public void showUploadBottomSheet() {
        MenuSheetView menuSheetView = new MenuSheetView(this, R.string.hint_upload, item -> {
            if (bottomSheet != null)
                bottomSheet.dismissSheet();

            if (item.getItemId() == R.id.action_upload_image) {
                UploadActivity.openForType(this, UploadActivity.getMediaTypeImage());
            }

            if (item.getItemId() == R.id.action_upload_video) {
                UploadActivity.openForType(this, UploadActivity.getMediaTypeVideo());
            }

            return true;
        });

        menuSheetView.inflateMenu(R.menu.menu_upload);
        bottomSheet.showWithSheetView(menuSheetView);
    }

    public static void open(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
    }
}
