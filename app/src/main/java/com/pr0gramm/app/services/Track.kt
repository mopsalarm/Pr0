package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker
import com.google.common.base.Stopwatch
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.config.Config
import java.util.concurrent.TimeUnit

/**
 * Tracking using google analytics. Obviously this is anonymous.
 */
object Track {
    private const val GA_CUSTOM_AUTHORIZED = "&cd1"
    private const val GA_CUSTOM_PREMIUM = "&cd2"
    private const val GA_CUSTOM_ADS = "&cd3"

    private lateinit var ga: Tracker
    private lateinit var settingsTracker: SettingsTrackerService
    private lateinit var config: Config

    fun initialize(context: Context) {
        ga = context.appKodein().instance()
        settingsTracker = context.appKodein().instance()
        config = context.appKodein().instance()
    }

    inline private fun send(b: HitBuilders.EventBuilder.() -> Unit) {
        val event = HitBuilders.EventBuilder().apply { b() }.build()
        ga.send(event)
    }

    fun loginSuccessful() {
        send {
            setCategory("User")
            setAction("Login")
            setLabel("Success")
        }
    }

    fun loginFailed() {
        send {
            setCategory("User")
            setAction("Login")
            setLabel("Success")
        }
    }

    fun logout() {
        send {
            setCategory("User")
            setAction("Logout")
        }
    }

    fun search(query: String) {
        send {
            setCategory("Feed")
            setAction("Search")
            setLabel(query)
        }
    }

    fun writeComment() {
        send {
            setCategory("Content")
            setAction("WriteComment")
        }
    }

    fun writeMessage() {
        send {
            setCategory("Content")
            setAction("WriteMessage")
        }
    }

    fun searchImage() {
        send {
            setCategory("Content")
            setAction("SearchImage")
        }
    }

    fun votePost(vote: Vote) {
        if (config.trackVotes) {
            send {
                setCategory("Content")
                setAction("Vote" + vote.name)
                setLabel("Post")
            }
        }
    }

    fun voteTag(vote: Vote) {
        if (config.trackVotes) {
            send {
                setCategory("Content")
                setAction("Vote" + vote.name)
                setLabel("Tag")
            }
        }
    }

    fun voteComment(vote: Vote) {
        if (config.trackVotes) {
            send {
                setCategory("Content")
                setAction("Vote" + vote.name)
                setLabel("Comment")
            }
        }
    }

    fun gotoFirefoxFocusWebsite() {
        send {
            setCategory("FirefoxFocus")
            setAction("Website")
        }
    }

    fun openBrowser(type: String) {
        send {
            setCategory("Browser")
            setAction("Open")
            setLabel(type)
        }
    }

    fun upload(size: Long) {
        val categoryStart = size / (512 * 1024) * 512

        @SuppressLint("DefaultLocale")
        val sizeCategory = "%d-%d kb".format(categoryStart, categoryStart + 512)

        send {
            setCategory("Content")
            setAction("Upload")
            setLabel(sizeCategory)
        }
    }

    fun statistics() {
        settingsTracker.track()
    }

    fun notificationShown() {
        send {
            setCategory("Notification")
            setAction("Shown")
        }
    }

    fun notificationClosed(method: String) {
        send {
            setCategory("Notification")
            setAction("Closed")
            setLabel(method)
        }
    }

    fun preloadCurrentFeed(size: Int) {
        send {
            setCategory("Feed")
            setAction("Preload")
            setLabel(size.toString())
        }
    }

    fun inviteSent() {
        send {
            setCategory("User")
            setAction("Invited")
        }
    }

    fun registerLinkClicked() {
        send {
            setCategory("Register")
            setAction("Clicked")
        }
    }

    fun advancedSearch(query: String?) {
        send {
            setCategory("Feed")
            setAction("AdvancedSearch")
            setLabel(query)
        }
    }

    fun passwordChanged() {
        send {
            setCategory("User")
            setAction("PasswordChanged")
        }
    }

    fun specialMenuActionClicked(uri: Uri) {
        send {
            setCategory("SpecialMenu")
            setAction("Clicked")
            setLabel(uri.toString())
        }
    }

    fun adClicked(type: Config.AdType) {
        send {
            setCategory("Ads")
            setAction("Clicked")
            setLabel(type.toString())
        }
    }

    fun screen(name: String) {
        ga.setScreenName(name)
        ga.send(HitBuilders.ScreenViewBuilder().build())
    }

    fun updateUserState(loginState: UserService.LoginState) {
        ga.set(GA_CUSTOM_AUTHORIZED, loginState.authorized.toString())
        ga.set(GA_CUSTOM_PREMIUM, loginState.premium.toString())
    }

    fun updateAdType(adType: Config.AdType) {
        ga.set(GA_CUSTOM_ADS, adType.toString())
    }

    fun trackApiCallSpeed(watch: Stopwatch, methodName: String, success: Boolean) {
        ga.send(HitBuilders.TimingBuilder()
                .setCategory("Api")
                .setValue(watch.elapsed(TimeUnit.MILLISECONDS))
                .setVariable(methodName)
                .setLabel(if (success) "success" else "failure")
                .setNonInteraction(true)
                .build())
    }
}
