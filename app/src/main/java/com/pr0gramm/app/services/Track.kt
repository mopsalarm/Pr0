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
            putBoolean("success", true)
        }

        Stats().increment("login.succeeded")
    }

    fun loginFailed(type: String) {
        send("login") {
            putBoolean("success", false)
            putString("type", type)
        }

        Stats().increment("login.failed", "reason:$type")
    }

    fun logout() {
        send("logout")
    }

    fun writeComment(root: Boolean) {
        send("write_comment") {
            putBoolean("root", root)
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
            putString("vote_type", vote.name)
            putString("content_type", "post")
        }
    }

    fun voteTag(vote: Vote) {
        send("vote") {
            putString("vote_type", vote.name)
            putString("content_type", "tag")
        }
    }

    fun voteComment(vote: Vote) {
        send("vote") {
            putString("vote_type", vote.name)
            putString("content_type", "comment")
        }
    }

    fun gotoFirefoxFocusWebsite() {
        send("goto_firefox_website")
    }

    fun openBrowser(type: String) {
        send("open_browser") {
            putString("type", type)
        }
    }

    fun upload(size: Long) {
        val categoryStart = size / (512 * 1024) * 512

        @SuppressLint("DefaultLocale")
        val sizeCategory = "%d-%d kb".format(categoryStart, categoryStart + 512)

        send("upload") {
            putLong("size", size)
            putString("size_category", sizeCategory)
        }
    }

    fun inboxNotificationShown() {
        send("inbox_notification_shown")
    }

    fun inboxNotificationClosed(method: String) {
        send("inbox_notification_close") {
            putString("method", method)
        }
    }

    fun preloadCurrentFeed(size: Int) {
        send("preload_feed") {
            putInt("item_count", size)
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
            putString("uri", uri.toString())
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
            filter.tags?.let { putBoolean("tags", true) }
            filter.collection?.let { putBoolean("collection", true) }
            filter.username?.let { putBoolean("username", true) }
        }
    }

    fun viewItem(itemId: Long) {
        send("view_item") {
            putLong("id", itemId)
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
            putLong("id", itemId)
        }
    }

    fun adEvent(type: String) {
        send("ad_$type")
    }

    data class AuthState(val authorized: Boolean, val premium: Boolean)
}
