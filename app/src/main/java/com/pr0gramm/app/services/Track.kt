package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Event
import com.google.firebase.analytics.FirebaseAnalytics.Param
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import rx.Observable

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
@SuppressLint("StaticFieldLeak")
object Track : InjectorAware {
    private const val GA_CUSTOM_AUTHORIZED = "authorized"
    private const val GA_CUSTOM_PREMIUM = "premium"
    private const val GA_CUSTOM_ADS = "ads"

    lateinit var context: Context
    override val injector by lazy { context.injector }

    private val fa by lazy { instance<FirebaseAnalytics>() }
    private val settingsTracker by lazy { instance<SettingsTrackerService>() }
    private val config by lazy { instance<ConfigService>() }

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    private inline fun send(eventType: String, b: Bundle.() -> Unit = {}) {
        catchAll {
            fa.logEvent(eventType, Bundle().apply(b))
        }
    }

    fun loginSuccessful() {
        send(Event.LOGIN) {
            putBoolean(Param.SUCCESS, true)
        }
    }

    fun loginFailed() {
        send(Event.LOGIN) {
            putBoolean(Param.SUCCESS, false)
        }
    }

    fun logout() {
        send("logout")
    }

    fun search(query: String) {
        send(Event.SEARCH) {
            putString(Param.SEARCH_TERM, query)
        }
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
        if (config.config().trackVotes) {
            send("vote") {
                putString("vote_type", vote.name)
                putString("content_type", "post")
            }
        }
    }

    fun voteTag(vote: Vote) {
        if (config.config().trackVotes) {
            send("vote") {
                putString("vote_type", vote.name)
                putString("content_type", "tag")
            }
        }
    }

    fun voteComment(vote: Vote) {
        if (config.config().trackVotes) {
            send("vote") {
                putString("vote_type", vote.name)
                putString("content_type", "comment")
            }
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

    fun screen(activity: Activity?, name: String) {
        activity?.let {
            fa.setCurrentScreen(it, name, name)
        }
    }

    fun updateUserState(loginState: Observable<UserService.LoginState>) {
        loginState.distinctUntilChanged { st -> Pair(st.authorized, st.premium) }.subscribe { state ->
            fa.setUserProperty(GA_CUSTOM_AUTHORIZED, state.authorized.toString())
            fa.setUserProperty(GA_CUSTOM_PREMIUM, state.premium.toString())
        }
    }

    fun updateAdType(adType: Config.AdType) {
        fa.setUserProperty(GA_CUSTOM_ADS, adType.toString())
    }

    fun trackSyncCall(millis: Long, success: Boolean) {
        send("sync_api") {
            putBoolean("success", success)
            putLong("duration", millis)
        }
    }

    suspend fun statistics() {
        catchAll {
            settingsTracker.track()
        }
    }
}
