package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.ignoreAllExceptions
import com.pr0gramm.app.util.recordBreadcrumb
import io.sentry.Sentry
import io.sentry.event.Breadcrumb

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
@SuppressLint("StaticFieldLeak")
object Track : InjectorAware {
    lateinit var context: Context
    override val injector by lazy { context.injector }

    private val settingsTracker by lazy { instance<SettingsTrackerService>() }

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    private inline fun send(eventType: String, bcType: Breadcrumb.Type = Breadcrumb.Type.USER, b: KeyValueAdapter.() -> Unit = {}) {
        ignoreAllExceptions {
            val extras = KeyValueAdapter().apply(b)

            // and also to sentry
            recordBreadcrumb(bcType) {
                setCategory(eventType)
                setData(extras.map)
            }
        }
    }

    fun loginSuccessful() {
        send("login") {
            putBoolean("success", true)
        }
    }

    fun loginFailed() {
        send("login") {
            putBoolean("success", false)
        }
    }

    fun logout() {
        send("logout")
    }

    fun writeComment() {
        send("write_comment")
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

    fun registerFAQClicked() {
        send("aff_faq_clicked")
    }

    fun passwordChanged() {
        send("password_changed")
    }

    fun specialMenuActionClicked(uri: Uri) {
        send("special_menu_item") {
            putString("uri", uri.toString())
        }
    }

    fun updateUserState(state: AuthState) {
        Sentry.getStoredClient()?.let { client ->
            client.context.addTag("pr0.premium", state.premium.toString())
            client.context.addTag("pr0.authorized", state.authorized.toString())
        }
    }

    fun openFeed(filter: FeedFilter) {
        send("view_feed", Breadcrumb.Type.NAVIGATION) {
            filter.tags?.let { putString("tags", it) }
            filter.likes?.let { putString("likes", it) }
            filter.username?.let { putString("username", it) }
        }
    }

    fun viewItem(itemId: Long) {
        send("view_item", Breadcrumb.Type.NAVIGATION) {
            putLong("id", itemId)
        }
    }

    fun inboxActivity() {
        send("inbox", Breadcrumb.Type.NAVIGATION)
    }

    suspend fun statistics() {
        catchAll {
            settingsTracker.track()
        }
    }

    data class AuthState(val authorized: Boolean, val premium: Boolean)


    private class KeyValueAdapter {
        val map = mutableMapOf<String, String>()

        fun putString(key: String, value: String) {
            map[key] = value
        }

        fun putBoolean(key: String, value: Boolean) {
            map[key] = value.toString()
        }

        fun putInt(key: String, value: Int) {
            map[key] = value.toString()
        }

        fun putLong(key: String, value: Long) {
            map[key] = value.toString()
        }
    }
}
