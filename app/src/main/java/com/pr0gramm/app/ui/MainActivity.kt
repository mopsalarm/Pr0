package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.view.menu.ActionMenuItem
import android.support.v7.widget.Toolbar
import android.view.*
import com.flipboard.bottomsheet.BottomSheetLayout
import com.github.salomonbrys.kodein.instance
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.cast.SessionManagerListenerAdapter
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.sync.SyncJob
import com.pr0gramm.app.ui.back.BackFragmentHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.fragments.*
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.ui.upload.UploadActivity
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.decoupleSubscribe
import com.pr0gramm.app.util.onErrorResumeEmpty
import kotterknife.bindOptionalView
import kotterknife.bindView
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.android.schedulers.AndroidSchedulers.mainThread
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


/**
 * This is the main class of our pr0gramm app.
 */
class MainActivity : BaseAppCompatActivity("MainActivity"),
        DrawerFragment.OnFeedFilterSelected,
        FragmentManager.OnBackStackChangedListener,
        ScrollHideToolbarListener.ToolbarActivity,
        MainActionHandler,
        PermissionHelperActivity,
        AdControl {

    private val handler = Handler(Looper.getMainLooper())
    private val doNotShowAds = BehaviorSubject.create(false)
    private var permissionHelper = PermissionHelper(this)
    private val settings = Settings.get()

    private val drawerLayout: DrawerLayout by bindView(R.id.drawer_layout)
    private val toolbar: Toolbar by bindView(R.id.toolbar)
    private val contentContainer: View by bindView(R.id.content)
    private val bottomSheet: BottomSheetLayout by bindView(R.id.bottomsheet)

    private val toolbarContainer: View? by bindOptionalView(R.id.toolbar_container)

    private val userService: UserService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val singleShotService: SingleShotService by instance()
    private val infoMessageService: InfoMessageService by instance()
    private val adService: AdService by instance()

    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var startedWithIntent = false

    override var scrollHideToolbarListener: ScrollHideToolbarListener by Delegates.notNull()

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.translucentStatus)
        super.onCreate(savedInstanceState)

        if (settings.secureApp) {
            // hide app from recent apps list
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.activity_main)

        // use toolbar as action bar
        setSupportActionBar(toolbar)

        // and hide it away on scrolling
        scrollHideToolbarListener = ScrollHideToolbarListener(toolbarContainer ?: toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // prepare drawer layout
        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name)
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // listen to fragment changes
        supportFragmentManager.addOnBackStackChangedListener(this)

        val coldStart = savedInstanceState == null
        if (coldStart) {
            val intent: Intent? = intent
            val startedFromLauncher = intent == null || intent.action == Intent.ACTION_MAIN

            // reset to sfw only.
            if (settings.feedStartAtSfw && startedFromLauncher) {
                logger.info("Force-switch to sfw only.")
                settings.edit {
                    putBoolean("pref_feed_type_sfw", true)
                    putBoolean("pref_feed_type_nsfw", false)
                    putBoolean("pref_feed_type_nsfl", false)
                }
            }

            createDrawerFragment()

            if (startedFromLauncher) {
                // load feed-fragment into view
                gotoFeedFragment(defaultFeedFilter(), true)

                // only check on normal app start
                checkForInfoMessage()

            } else {
                startedWithIntent = true
                onNewIntent(intent!!)
            }
        }

        if (singleShotService.isFirstTime("onboarding-activity:1")) {
            startActivityForResult(Intent(this, IntroActivity::class.java), RequestCodes.INTRO_ACTIVITY)
            return
        }

        if (coldStart) {
            var updateCheck = true
            var updateCheckDelay = false

            if (singleShotService.firstTimeInVersion("changelog")) {
                updateCheck = false

                val dialog = ChangeLogDialog()
                dialog.show(supportFragmentManager, null)

            } else if (shouldShowFeedbackReminder()) {

                Snackbar.make(contentContainer, R.string.feedback_reminder, 10000)
                        .setAction(R.string.okay, { })
                        .show()

                updateCheckDelay = true
            } else {
                preparePremiumHint()
            }

            if (updateCheck) {
                Observable.just<Any>(null)
                        .delay((if (updateCheckDelay) 10 else 0).toLong(), TimeUnit.SECONDS, mainThread())
                        .compose(bindToLifecycle())
                        .subscribe { UpdateDialogFragment.checkForUpdates(this, false) }
            }
        }
    }

    private fun preparePremiumHint() {
        if (singleShotService.firstTimeToday("hint_ads_pr0mium:2")) {
            val showAnyAds = adService.enabledForType(Config.AdType.FEED).take(1)

            showAnyAds.takeFirst { v -> v }
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .onErrorResumeEmpty()
                    .filter { !userService.isPremiumUser }
                    .subscribe { adsAreShown ->
                        Snackbar.make(contentContainer, R.string.hint_dont_like_ads, 20000)
                                .setAction("pr0mium") {
                                    Track.registerLinkClicked()
                                    val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap")
                                    BrowserHelper.openCustomTab(this, uri)
                                }
                                .show()
                    }
        }
    }

    override fun showAds(show: Boolean) {
        doNotShowAds.onNext(!show)
    }

    private fun checkForInfoMessage() {
        infoMessageService.fetch()
                .onErrorResumeEmpty()
                .compose(bindToLifecycleAsync())
                .subscribe { showInfoMessage(it) }
    }

    private fun shouldShowFeedbackReminder(): Boolean {
        val firstTimeToday = singleShotService.firstTimeToday("hint_feedback_reminder")
        val firstInVersion = singleShotService.firstTimeInVersion("hint_feedback_reminder")
        return settings.useBetaChannel && (firstInVersion || firstTimeToday)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (Intent.ACTION_VIEW != intent.action)
            return

        handleUri(intent.data)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RequestCodes.INTRO_ACTIVITY) {
            AndroidUtility.recreateActivity(this)
        }
    }

    override fun onDestroy() {
        supportFragmentManager.removeOnBackStackChangedListener(this)

        try {
            super.onDestroy()
        } catch (ignored: RuntimeException) {
        }
    }

    override fun onBackStackChanged() {
        updateToolbarBackButton()
        updateActionbarTitle()

        drawerFragment?.updateCurrentFilters(currentFeedFilter)

        if (BuildConfig.DEBUG) {
            logger.info("Stack: {}", (0 until supportFragmentManager.backStackEntryCount)
                    .map { supportFragmentManager.getBackStackEntryAt(it).name }
                    .joinToString(" -> "))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val navigationType = settings.volumeNavigation

        val fragment = currentFragment
        if (fragment is PostPagerNavigation) {
            // volume keys navigation (only if enabled)
            if (navigationType !== Settings.VolumeNavigationType.DISABLED) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (navigationType === Settings.VolumeNavigationType.UP) {
                        fragment.moveToNext()
                    } else {
                        fragment.moveToPrev()
                    }

                    return true
                }

                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (navigationType === Settings.VolumeNavigationType.UP) {
                        fragment.moveToPrev()
                    } else {
                        fragment.moveToNext()
                    }

                    return true
                }
            }

            // keyboard or d-pad navigation (always possible)
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> fragment.moveToNext()
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> fragment.moveToPrev()
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun updateActionbarTitle() {
        supportActionBar?.let { bar ->
            val filter = currentFeedFilter
            if (filter == null) {
                bar.setTitle(R.string.pr0gramm)
                bar.subtitle = null
            } else {
                val feed = FeedFilterFormatter.format(this, filter)
                bar.title = feed.title
                bar.subtitle = feed.subtitle
            }
        }
    }

    private val drawerFragment: DrawerFragment?
        get() = supportFragmentManager.findFragmentById(R.id.left_drawer) as? DrawerFragment

    /**
     * Returns the current feed filter. Might be null, if no filter could be detected.
     */
    private val currentFeedFilter: FeedFilter?
        get() = (currentFragment as? FilterFragment)?.currentFilter

    private val currentFragment: Fragment?
        get() = supportFragmentManager?.findFragmentById(R.id.content)

    private val shouldClearOnIntent: Boolean
        get() = currentFragment !is FavoritesFragment && supportFragmentManager.backStackEntryCount == 0

    private fun updateToolbarBackButton() {
        drawerToggle.isDrawerIndicatorEnabled = shouldClearOnIntent
        drawerToggle.syncState()
    }

    private fun createDrawerFragment() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.left_drawer, DrawerFragment())
                .commit()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!drawerToggle.isDrawerIndicatorEnabled) {
            if (item.itemId == android.R.id.home) {
                if (!dispatchFakeHomeEvent(item))
                    onBackPressed()

                return true
            }
        }

        return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.media_route_menu_item)?.let { item ->
            logger.info("Found cast button from fragment menu.")

            if (settings.allowCasting) {
                // show the cast button if available
                CastButtonFactory.setUpMediaRouteButton(applicationContext, menu,
                        R.id.media_route_menu_item)
            } else {
                item.setVisible(false)
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    private fun dispatchFakeHomeEvent(item: MenuItem): Boolean {
        return onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, ActionMenuItem(
                this, item.groupId, ID_FAKE_HOME, 0, item.order, item.title))
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers()
            return
        }

        // dispatch to fragments
        if (BackFragmentHelper.dispatchOnBackAction(this)) {
            return
        }

        // at the end, go back to the "top" page before stopping everything.
        if (supportFragmentManager.backStackEntryCount == 0 && !startedWithIntent) {
            val filter = currentFeedFilter
            if (filter != null && !isDefaultFilter(filter)) {
                gotoFeedFragment(defaultFeedFilter(), true)
                return
            }
        }

        try {
            super.onBackPressed()
        } catch (err: IllegalStateException) {
            // workaround for:
            // this is sometimes called after onSaveInstanceState
            logger.warn("Error in onBackPressed:", err)
        }
    }

    private fun isDefaultFilter(filter: FeedFilter): Boolean {
        return defaultFeedFilter() == filter
    }

    private fun showInfoMessage(message: InfoMessageService.Message) {
        if (message.endOfLife >= AndroidUtility.buildVersionCode()) {
            showDialog(this) {
                contentWithLinks("Support für deine Version ist eingestellt. " +
                        "Um die pr0gramm-App weiter benutzen zu können, lade eine " +
                        "aktuelle Version von https://app.pr0gramm.com herunter.")

                positive(R.string.okay) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.pr0gramm.com")))
                    finish()
                }
            }

            return
        }

        val text = message.message?.takeIf(String::isNotBlank)
        if (text != null) {
            showDialog(this) {
                contentWithLinks(text)
                positive()
            }
        }
    }

    val castSessionListener = object : SessionManagerListenerAdapter<Session>() {
        override fun onSessionStarted(session: Session, sessionId: String) {
            invalidateOptionsMenu()
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            invalidateOptionsMenu()
        }
    }

    override fun onResume() {
        CastContext.getSharedInstance(this)
                .sessionManager
                .addSessionManagerListener(castSessionListener)

        super.onResume()
        onBackStackChanged()
    }

    override fun onPause() {
        super.onPause()

        CastContext.getSharedInstance(this)
                .sessionManager
                .removeSessionManagerListener(castSessionListener)
    }

    override fun onStart() {
        super.onStart()

        // schedule a sync operation every minute
        Observable.interval(0, 1, TimeUnit.MINUTES, mainThread())
                .compose(bindToLifecycle())
                .subscribe { SyncJob.syncNow(this) }
    }

    override fun onLogoutClicked() {
        drawerLayout.closeDrawers()
        Track.logout()

        val logout_successful_hint = R.string.logout_successful_hint
        userService.logout()
                .decoupleSubscribe()
                .compose(bindToLifecycleAsync<Any>())
                .lift(BusyDialog.busyDialog<Any>(this))
                .doOnCompleted {
                    // show a short information.
                    Snackbar.make(drawerLayout, logout_successful_hint, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.okay, { })
                            .show()

                    // reset everything!
                    gotoFeedFragment(defaultFeedFilter(), true)
                }
                .subscribeWithErrorHandling()
    }

    private fun defaultFeedFilter(): FeedFilter {
        val type = if (settings.feedStartAtNew) FeedType.NEW else FeedType.PROMOTED
        return FeedFilter().withFeedType(type)
    }

    override fun onFeedFilterSelectedInNavigation(filter: FeedFilter) {
        gotoFeedFragment(filter, true)
        drawerLayout.closeDrawers()
    }

    override fun onOtherNavigationItemClicked() {
        drawerLayout.closeDrawers()
    }

    override fun onNavigateToFavorites(username: String) {
        // move to new fragment
        val fragment = FavoritesFragment.newInstance(username)
        moveToFragment(fragment, true)

        drawerLayout.closeDrawers()
    }

    override fun onUsernameClicked() {
        val name = userService.name
        if (name != null) {
            val filter = FeedFilter().withFeedType(FeedType.NEW).withUser(name)
            gotoFeedFragment(filter, false)
        }

        drawerLayout.closeDrawers()
    }

    override fun onFeedFilterSelected(filter: FeedFilter) {
        gotoFeedFragment(filter)
    }

    override fun onFeedFilterSelected(filter: FeedFilter, searchQueryState: Bundle?) {
        gotoFeedFragment(filter, queryState = searchQueryState)
    }

    override fun onFeedFilterSelected(filter: FeedFilter, queryState: Bundle?,
                                      startAt: ItemWithComment?, popBackstack: Boolean) {

        if (popBackstack) {
            supportFragmentManager.popBackStackImmediate()
        }

        gotoFeedFragment(filter, false, startAt, queryState)
    }

    override fun pinFeedFilter(filter: FeedFilter, title: String) {
        bookmarkService.create(filter, title)
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun gotoFeedFragment(newFilter: FeedFilter, clear: Boolean = false,
                                 start: ItemWithComment? = null,
                                 queryState: Bundle? = null) {

        moveToFragment(FeedFragment.newInstance(newFilter, start, queryState), clear)
    }

    private fun moveToFragment(fragment: Fragment, clear: Boolean) {
        if (isFinishing)
            return

        if (clear) {
            clearBackStack()
        }

        // and show the fragment
        val transaction = supportFragmentManager
                .beginTransaction()
                .replace(R.id.content, fragment)

        if (!clear) {
            logger.info("Adding fragment {} to backstrack", fragment.javaClass.name)
            transaction.addToBackStack("Feed" + fragment)
        }

        try {
            transaction.commit()
        } catch (err: IllegalStateException) {
            logger.warn("Error in commit: ", err)
        }

        // trigger a back-stack changed after adding the fragment.
        handler.post { onBackStackChanged() }
    }

    private fun clearBackStack() {
        try {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } catch (err: Exception) {
            AndroidUtility.logToCrashlytics(RuntimeException(
                    "Ignoring exception from popBackStackImmediate:", err))
        }
    }

    /**
     * Handles a uri to something on pr0gramm

     * @param uri The uri to handle
     */
    private fun handleUri(uri: Uri) {
        if (uri.toString().matches(".*/user/[^/]+/resetpass/[^/]+".toRegex())) {
            val intent = Intent(this, PasswordRecoveryActivity::class.java)
            intent.putExtra("url", uri.toString())
            startActivity(intent)
            return
        }

        val result = FeedFilterWithStart.fromUri(uri)
        if (result != null) {
            val filter = result.filter
            val start = result.start

            gotoFeedFragment(filter, shouldClearOnIntent, start)

        } else {
            gotoFeedFragment(defaultFeedFilter(), true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {

        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun requirePermission(permission: String): Observable<Void> {
        return permissionHelper.requirePermission(permission)
    }

    override fun showUploadBottomSheet() {
        val menuSheetView = MenuSheetView(this, R.string.hint_upload) { item ->
            bottomSheet.dismissSheet()

            if (item.itemId == R.id.action_upload_image) {
                UploadActivity.openForType(this, UploadActivity.MEDIA_TYPE_IMAGE)
            }

            if (item.itemId == R.id.action_upload_video) {
                UploadActivity.openForType(this, UploadActivity.MEDIA_TYPE_VIDEO)
            }

            true
        }

        menuSheetView.inflateMenu(R.menu.menu_upload)
        bottomSheet.showWithSheetView(menuSheetView)
    }

    companion object {
        // we use this to propagate a fake-home event to the fragments.
        const val ID_FAKE_HOME = android.R.id.list

        fun open(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }
}
