package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pr0gramm.app.Stats
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.ignoreAllExceptions

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
@SuppressLint("StaticFieldLeak")
object Track : InjectorAware {
    private lateinit var context: Context
    override val injector by lazy { context.injector }

    private val settingsTracker by lazy { instance<SettingsTrackerService>() }
    private var installerTracked = false

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    private inline fun send(eventType: String, b: Bundle.() -> Unit = {}) {
        ignoreAllExceptions {
            val extras = Bundle().apply(b)
            FirebaseAnalytics.getInstance(context).logEvent(eventType, extras)
        }
    }

    fun loginStarted() {
        send("loginStarted")
        Stats().increment("login.started")
    }

    fun loginSuccessful() {
        send("login") {
            putBoolean(FirebaseAnalytics.Param.SUCCESS, true)
        }

        Stats().increment("login.succeeded")
    }

    fun loginFailed(type: String) {
        send("login") {
            putBoolean(FirebaseAnalytics.Param.SUCCESS, false)
            putString(FirebaseAnalytics.Param.VALUE, type)
        }

        Stats().increment("login.failed", "reason:$type")
    }

    fun logout() {
        send("logout")
    }

    fun writeComment(root: Boolean) {
        send("write_comment") {
            putBoolean(FirebaseAnalytics.Param.VALUE, root)
        }
    }

    fun writeMessage() {
        send("write_message")
    }

    fun searchImage() {
        send("search_image")
    }

    fun votePost(vote: Vote) {
        send("vote") {
            putString(FirebaseAnalytics.Param.VALUE, vote.name)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, "post")
        }
    }

    fun voteTag(vote: Vote) {
        send("vote") {
            putString(FirebaseAnalytics.Param.VALUE, vote.name)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, "tag")
        }
    }

    fun voteComment(vote: Vote) {
        send("vote") {
            putString(FirebaseAnalytics.Param.VALUE, vote.name)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, "comment")
        }
    }

    fun gotoFirefoxFocusWebsite() {
        send("goto_firefox_website")
    }

    fun openBrowser(type: String) {
        send("open_browser") {
            putString(FirebaseAnalytics.Param.VALUE, type)
        }
    }

    fun upload(size: Long) {
        val categoryStart = size / (512 * 1024) * 512

        @SuppressLint("DefaultLocale")
        val sizeCategory = "%d-%d kb".format(categoryStart, categoryStart + 512)

        send("upload") {
            putLong(FirebaseAnalytics.Param.VALUE, size)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, sizeCategory)
        }
    }

    fun inboxNotificationShown() {
        send("inbox_notification_shown")
    }

    fun inboxNotificationClosed(method: String) {
        send("inbox_notification_close") {
            putString(FirebaseAnalytics.Param.METHOD, method)
        }
    }

    fun preloadCurrentFeed(size: Int) {
        send("preload_feed") {
            putInt(FirebaseAnalytics.Param.VALUE, size)
        }
    }

    fun inviteSent() {
        send("invite_sent")
    }

    fun registerLinkClicked() {
        send("aff_register_clicked")
    }

    fun faqClicked() {
        send("aff_faq_clicked")
    }

    fun privacyClicked() {
        send("aff_privacy_clicked")
    }

    fun imprintClicked() {
        send("aff_imprint_clicked")
    }

    fun passwordChanged() {
        send("password_changed")
    }

    fun specialMenuActionClicked(uri: Uri) {
        send("special_menu_item") {
            putString(FirebaseAnalytics.Param.VALUE, uri.toString())
        }
    }

    fun updateUserState(state: AuthState) = ignoreAllExceptions {
        val fa = FirebaseAnalytics.getInstance(context)
        fa.setUserProperty("pr0_premium", state.premium.toString())
        fa.setUserProperty("pr0_authorized", state.authorized.toString())

        val fc = FirebaseCrashlytics.getInstance()
        fc.setCustomKey("pr0_premium", state.premium)
        fc.setCustomKey("pr0_authorized", state.authorized)
    }

    fun openFeed(filter: FeedFilter) {
        send("view_feed") {
            filter.tags?.let { putBoolean(FirebaseAnalytics.Param.TERM, true) }
            filter.collection?.let { putBoolean(FirebaseAnalytics.Param.GROUP_ID, true) }
            filter.username?.let { putBoolean(FirebaseAnalytics.Param.AFFILIATION, true) }
        }
    }

    fun viewItem(itemId: Long) {
        send("view_item") {
            putLong(FirebaseAnalytics.Param.ITEM_ID, itemId)
        }
    }

    fun inboxActivity() {
        send("inbox")
    }

    suspend fun statistics() {
        catchAll {
            settingsTracker.track()
        }
    }

    fun openZoomView(itemId: Long) {
        send("zoom_view") {
            putLong(FirebaseAnalytics.Param.ITEM_ID, itemId)
        }
    }

    fun adEvent(type: String) {
        send("ad_$type")
    }

    fun installer(name: String?) {
        if (!installerTracked) {
            installerTracked = true

            send("installer") {
                putString(FirebaseAnalytics.Param.VALUE, name.toString())
            }
        }
    }

    data class AuthState(val authorized: Boolean, val premium: Boolean)
}
