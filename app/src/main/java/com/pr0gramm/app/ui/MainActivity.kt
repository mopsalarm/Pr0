package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.menu.ActionMenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.*
import com.pr0gramm.app.Duration.Companion.seconds
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.model.info.InfoMessage
import com.pr0gramm.app.orm.bookmarkOf
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.back.BackFragmentHelper
import com.pr0gramm.app.ui.base.*
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.fragments.CommentRef
import com.pr0gramm.app.ui.fragments.DrawerFragment
import com.pr0gramm.app.ui.fragments.FavoritesFragment
import com.pr0gramm.app.ui.fragments.FeedFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.flow.*
import kotterknife.bindOptionalView
import kotterknife.bindView
import rx.subjects.BehaviorSubject
import kotlin.properties.Delegates


/**
 * This is the main class of our pr0gramm app.
 */
class MainActivity : BaseAppCompatActivity("MainActivity"),
        DrawerFragment.Callbacks,
        FragmentManager.OnBackStackChangedListener,
        ScrollHideToolbarListener.ToolbarActivity,
        MainActionHandler,
        PermissionHelperActivity,
        RecyclerViewPoolProvider by RecyclerViewPoolMap(),
        AdControl {

    private val handler = Handler(Looper.getMainLooper())
    private val doNotShowAds = BehaviorSubject.create(false)
    private var permissionHelper = PermissionHelperDelegate(this)
    private val settings = Settings.get()

    private val drawerLayout: DrawerLayout by bindView(R.id.drawer_layout)
    private val toolbar: Toolbar by bindView(R.id.toolbar)
    private val contentContainer: View by bindView(R.id.content)

    private val toolbarContainer: View? by bindOptionalView(R.id.toolbar_container)

    private val userService: UserService by instance()
    private val configService: ConfigService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val singleShotService: SingleShotService by instance()
    private val infoMessageService: InfoMessageService by instance()
    private val adService: AdService by instance()

    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var startedWithIntent = false

    override var scrollHideToolbarListener: ScrollHideToolbarListener by Delegates.notNull()

    val adViewAdapter = AdViewAdapter()

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.translucentStatus)
        super.onCreate(savedInstanceState)

        if (settings.secureApp) {
            // hide app from recent apps list
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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

        // listen to fragment changes
        supportFragmentManager.addOnBackStackChangedListener(this)

        handleMarkAsRead(intent)

        if (savedInstanceState == null) {
            val intent: Intent? = intent
            val startedFromLauncher = intent == null || intent.action == Intent.ACTION_MAIN || intent.action == Intent.ACTION_SEARCH

            // reset to sfw only.
            if (settings.feedStartAtSfw && startedFromLauncher) {
                logger.info { "Force-switch to sfw only." }
                settings.edit {
                    putBoolean("pref_feed_type_sfw", true)
                    putBoolean("pref_feed_type_nsfw", false)
                    putBoolean("pref_feed_type_nsfl", false)
                }
            }

            createDrawerFragment()

            if (startedFromLauncher || intent == null) {
                // load feed-fragment into view
                gotoFeedFragment(defaultFeedFilter(), true)

                launchWhenResumed {
                    checkForInfoMessage()
                }

            } else {
                startedWithIntent = true
                onNewIntent(intent)
            }

            launchWhenStarted { onColdStart() }
        }

        // show the intro activity if this is the first time the app started.
        singleShotService.doOnce("onboarding-activity:1") {
            skipInTesting {
                IntroActivity.launch(this)
                return
            }
        }

        // set extra slides to show during some migration
        // val extraSlides = mutableListOf<IntroActivity.Slides>()
        // if (extraSlides.isNotEmpty()) {
        //     IntroActivity.launch(this, extraSlides)
        // }

        // schedule an update in the background
        doInBackground { bookmarkService.update() }

        launchUntilDestroy {
            settings.changes().filter { it === "pref_tag_cloud_view" }.collect {
                invalidateRecyclerViewPool()
            }
        }
    }

    private fun handleMarkAsRead(intent: Intent?) {
        val extras = intent?.extras ?: return
        val itemId = extras.getString(EXTRA_MARK_AS_READ) ?: return
        val timestamp = extras.getParcelable<Instant>(EXTRA_MARK_AS_READ_TIMESTAMP) ?: return

        baseContext.injector.instance<InboxService>().markAsRead(itemId, timestamp)
    }

    private fun shouldShowBuyPremiumHint(): Boolean {
        return !userService.userIsPremium && singleShotService.firstTimeToday("hint_ads_pr0mium:5")
    }

    private fun showBuyPremiumHint() {
        launchWhenStarted {
            val adsEnabledFlow = merge(
                    adService.enabledForType(Config.AdType.FEED).take(1).asFlow(),
                    adService.enabledForType(Config.AdType.FEED_TO_POST_INTERSTITIAL).take(1).asFlow())

            val showAnyAds = adsEnabledFlow
                    .onEach { logger.info { "should show ads: $it" } }
                    .firstOrNull { it } ?: false

            if (!userService.userIsPremium && showAnyAds) {
                Snackbar.make(contentContainer, R.string.hint_dont_like_ads, 10000).apply {
                    configureNewStyle()

                    setAction("pr0mium") {
                        Track.registerLinkClicked()
                        val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
                        BrowserHelper.openCustomTab(this@MainActivity, uri)
                    }

                    show()
                }
            }
        }
    }

    override fun hintBookmarksEditableWithPremium() {
        drawerLayout.closeDrawers()

        Snackbar.make(contentContainer, R.string.hint_edit_bookmarks_premium, 10000).apply {
            configureNewStyle()

            setAction("pr0mium") {
                Track.registerLinkClicked()
                val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
                BrowserHelper.openCustomTab(this@MainActivity, uri)
            }

            show()
        }
    }

    override fun showAds(show: Boolean) {
        doNotShowAds.onNext(!show)
    }

    private fun checkForInfoMessage() {
        launchUntilPause(ignoreErrors = true) {
            showInfoMessage(infoMessageService.fetch())
        }
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

        handleMarkAsRead(intent)
        handleIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RequestCodes.INTRO_ACTIVITY) {
            AndroidUtility.recreateActivity(this)
        }

        if (requestCode == RequestCodes.FEEDBACK && resultCode == Activity.RESULT_OK) {
            Snackbar.make(drawerLayout, R.string.feedback_sent, Snackbar.LENGTH_SHORT)
                    .configureNewStyle()
                    .setAction(R.string.okay, { })
                    .show()
        }
    }

    override fun onDestroy() {
        supportFragmentManager.removeOnBackStackChangedListener(this)

        adViewAdapter.destroy()

        try {
            super.onDestroy()
        } catch (ignored: RuntimeException) {
        }
    }

    override fun onBackStackChanged() {
        updateToolbarBackButton()
        updateActionbarTitle()

        drawerFragment?.updateCurrentFilters(currentFeedFilter)

        logger.debug {
            val stack = (0 until supportFragmentManager.backStackEntryCount).joinToString(" -> ") {
                supportFragmentManager.getBackStackEntryAt(it).name ?: "null"
            }
            "Stack: $stack"
        }
    }

    fun updateActionbarTitle() {
        supportActionBar?.let { bar ->
            val title = (currentFragment as? TitleFragment)?.title

            if (title != null) {
                bar.title = title.title
                bar.subtitle = title.subTitle

            } else {
                bar.setTitle(R.string.pr0gramm)
                bar.subtitle = null
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
        get() = supportFragmentManager.findFragmentById(R.id.content)

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

    @SuppressLint("RestrictedApi")
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

    private fun showInfoMessage(message: InfoMessage) {
        if (message.endOfLife ?: 0 >= AndroidUtility.buildVersionCode()) {
            showDialog(this) {
                contentWithLinks("Support für deine Version ist eingestellt. " +
                        "Um die pr0gramm-App weiter benutzen zu können, lade die " +
                        "aktuelle Version von https://app.pr0gramm.com herunter.")

                positive(R.string.okay) {
                    if (!BuildConfig.DEBUG) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.pr0gramm.com")))
                        finish()
                    }
                }
            }

            return
        }

        message.message?.takeUnless { it.isBlank() }?.let { text ->
            singleShotService.doOnce(message.messageId) {
                showDialog(this) {
                    contentWithLinks(text)
                    positive()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onBackStackChanged()
    }

    override fun onStart() {
        super.onStart()

        launchUntilStop(ignoreErrors = true) {
            runEvery(period = seconds(45)) {
                logger.debug { "Sync from MainActivity" }
                SyncWorker.syncNow(this@MainActivity)
            }
        }
    }

    private suspend fun onColdStart() {
        var updateCheck = true
        var updateCheckDelay = false

        when {
            singleShotService.firstTimeInVersion("changelog") -> {
                updateCheck = false

                val dialog = ChangeLogDialog()
                dialog.show(supportFragmentManager, null)
            }

            shouldShowFeedbackReminder() -> {
                Snackbar.make(contentContainer, R.string.feedback_reminder, 10000)
                        .configureNewStyle()
                        .setAction(R.string.okay) { }
                        .show()

                updateCheckDelay = true
            }

            shouldShowBuyPremiumHint() -> {
                showBuyPremiumHint()
            }

            Build.VERSION.SDK_INT <= configService.config().endOfLifeAndroidVersion && singleShotService.firstTimeToday("endOfLifeAndroidVersionHint") -> {
                Snackbar.make(contentContainer, R.string.old_android_reminder, 10000)
                        .configureNewStyle()
                        .setAction(R.string.okay) { }
                        .show()
            }
        }

        if (updateCheck) {
            launchUntilStop(ignoreErrors = true) {
                if (updateCheckDelay) {
                    delay(seconds(10))
                }

                UpdateDialogFragment.checkForUpdatesInBackground(
                        this@MainActivity, supportFragmentManager)
            }
        }
    }

    override fun onLogoutClicked() {
        drawerLayout.closeDrawers()
        Track.logout()

        launchWhenStarted(busyIndicator = true) {
            userService.logout()

            // show a short information.
            Snackbar.make(contentContainer, R.string.logout_successful_hint, Snackbar.LENGTH_SHORT)
                    .configureNewStyle()
                    .setAction(R.string.okay) { }
                    .show()

            // reset everything!
            gotoFeedFragment(defaultFeedFilter(), true)
        }
    }

    private fun defaultFeedFilter(): FeedFilter {
        if (userService.userIsPremium) {
            // try to parse bookmark filter first
            settings.feedStartWithUri?.let { uri ->
                val parsed = FilterParser.parse(uri)
                if (parsed != null)
                    return parsed.filter
            }
        }

        // fall back to NEW or PROMOTED otherwise.
        val type = if (settings.feedStartAtNew) FeedType.NEW else FeedType.PROMOTED
        return FeedFilter().withFeedType(type)
    }

    override fun onFeedFilterSelectedInNavigation(filter: FeedFilter, startAt: CommentRef?, title: String?) {
        gotoFeedFragment(filter, true, start = startAt, title = title)
        drawerLayout.closeDrawers()
    }

    override fun onOtherNavigationItemClicked() {
        drawerLayout.closeDrawers()
    }

    override fun onNavigateToCollections(username: String) {
        // move to new fragment
        moveToFragment(FavoritesFragment.newInstance(username), clear = true)
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
                                      startAt: CommentRef?, popBackstack: Boolean) {

        if (popBackstack) {
            supportFragmentManager.popBackStackImmediate()
        }

        gotoFeedFragment(filter, false, startAt, queryState)
    }

    override fun bookmarkFilter(filter: FeedFilter, title: String) {
        bookmarkService.save(bookmarkOf(title, filter))
        drawerLayout.openDrawer(GravityCompat.START)

        drawerFragment?.scrollTo(filter)
    }

    private fun gotoFeedFragment(newFilter: FeedFilter, clear: Boolean = false,
                                 start: CommentRef? = null,
                                 queryState: Bundle? = null,
                                 title: String? = null) {

        // show special fragment if we want to see overview of collections of some user.
        newFilter.username?.let { username ->
            if (newFilter.collection == "**ANY") {
                return moveToFragment(FavoritesFragment.newInstance(username), clear = false)
            }
        }

        moveToFragment(FeedFragment.newInstance(newFilter, start, queryState, title), clear)
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
            logger.debug { "Adding fragment ${fragment.javaClass.name} to backstack" }
            transaction.addToBackStack("Feed$fragment")
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
    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        val uriString = uri.toString()
        if (uriString.matches(".*/user/[^/]+/resetpass/[^/]+".toRegex())) {
            val openIntent = Intent(this, PasswordRecoveryActivity::class.java)
            openIntent.putExtra("url", uriString)
            startActivity(openIntent)
            return
        }

        if (uriString.endsWith("/inbox/messages")) {
            startActivity(activityIntent<InboxActivity>(this))
            if (!this.shouldClearOnIntent) {
                return
            }
        }

        "/inbox/messages/([^/]+)$".toRegex().find(uriString)?.let { match ->
            val conversationName = match.groupValues[1]
            ConversationActivity.start(this, conversationName)
            if (!this.shouldClearOnIntent) {
                return
            }
        }

        val notificationTime: Instant? = intent.getParcelableExtra("MainActivity.NOTIFICATION_TIME")

        val result = FilterParser.parse(uri, notificationTime)
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

    override fun requirePermission(permission: String, callback: () -> Unit) {
        return permissionHelper.requirePermission(permission, callback)
    }

    override fun showUploadBottomSheet(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, null)
    }

    companion object {
        const val EXTRA_MARK_AS_READ = "MainActivity.EXTRA_MARK_AS_READ"
        const val EXTRA_MARK_AS_READ_TIMESTAMP = "MainActivity.EXTRA_MARK_AS_READ_TIMESTAMP"

        // we use this to propagate a fake-home event to the fragments.
        const val ID_FAKE_HOME = android.R.id.list

        fun openItemIntent(context: Context, itemId: Long, atComment: Long? = null, feedType: FeedType = FeedType.NEW): Intent {
            val uri = if (atComment == null) {
                UriHelper.of(context).post(feedType, itemId)
            } else {
                UriHelper.of(context).post(feedType, itemId, atComment)
            }

            return Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
        }
    }
}
