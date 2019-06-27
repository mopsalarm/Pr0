package com.pr0gramm.app.api.pr0gramm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Debug
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.adapter
import com.pr0gramm.app.model.user.LoginCookie
import com.pr0gramm.app.services.config.ConfigService
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okio.ByteString.Companion.encode
import rx.subjects.BehaviorSubject
import java.net.URLDecoder
import java.util.*

/**
 */
class LoginCookieJar(context: Context, private val preferences: SharedPreferences) : CookieJar {
    private val logger = Logger("LoginCookieJar")
    private val keyLoginCookie = "LoginCookieHandler.cookieValue"

    private object Lock

    private val uniqueToken: String by lazy {
        val input = ConfigService.makeUniqueIdentifier(context, preferences)
        input.encode(Charsets.UTF_8).md5().hex().toLowerCase(Locale.ROOT)
    }

    private var httpCookie: okhttp3.Cookie? = null

    /**
     * The parsed http cookie
     */
    val parsedCookie: LoginCookie?
        get() = observeCookie.value

    /**
     * Observe the existing cookie. Get a null value if the cookie
     * was removed and changed to null.
     */
    val observeCookie: BehaviorSubject<LoginCookie?> = BehaviorSubject.create()

    fun hasCookie(): Boolean {
        return httpCookie != null && parsedCookie?.id != null
    }

    init {
        val restored = preferences.getString(keyLoginCookie, null)
        if (restored != null && restored != "null") {
            restoreLoginCookie(restored)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<okhttp3.Cookie> {
        if (isNoApiRequest(url))
            return listOf()

        val cookies = mutableListOf<okhttp3.Cookie>()

        // always add ppCookie if available
        cookies += newCookie("pp", uniqueToken)

        // and add meCookie if available
        httpCookie?.let { meCookie ->
            cookies += meCookie
        }

        logger.debug { "Using cookies for request to $url: ${cookies.joinToString { it.name }}" }

        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (isNoApiRequest(url))
            return

        if (httpCookie != null) {
            // get login cookie candidate.
            val loginCookie = cookies.firstOrNull { it.name == "me" } ?: return
            updateLoginCookie(loginCookie)
        }
    }

    private fun isNoApiRequest(uri: HttpUrl): Boolean {
        val isApiRequest = uri.host.equals("pr0gramm.com", ignoreCase = true)
                || uri.host.contains(Debug.mockApiHost)

        return !isApiRequest
    }

    private fun restoreLoginCookie(value: String) {
        logger.debug { "Restoring login cookie from value: $value" }
        updateLoginCookie(newCookie("me", value))
    }

    fun updateLoginCookie(cookie: okhttp3.Cookie): Boolean {
        logger.debug { "Want to update login cookie: ${cookie.value}" }

        synchronized(Lock) {
            val previousCookie = httpCookie

            // do nothing if the cookie value has not changed.
            val notChanged = previousCookie?.value == cookie.value
            if (notChanged)
                return true

            val parsedCookie = parseCookie(cookie)

            if (parsedCookie != null) {
                logger.debug { "Updating stored cookie: $parsedCookie" }

                // store cookie for next time
                preferences.edit {
                    putString(keyLoginCookie, cookie.value)
                }

                this.httpCookie = cookie
                this.observeCookie.onNext(parsedCookie)
                return true

            } else {
                logger.warn { "Could not parse login cookie" }

                // cookie is invalid, remove all existing cookies
                clearLoginCookie()
            }
        }

        return false
    }

    fun clearLoginCookie() {
        logger.debug { "Clearing current login cookie: $parsedCookie" }

        synchronized(Lock) {
            // remove cookie from storage
            preferences.edit { remove(keyLoginCookie) }

            // and publish cookie to listeners
            httpCookie = null
            observeCookie.onNext(null)
        }
    }

    /**
     * Tries to parse the cookie into a [LoginCookieJar.LoginCookie] instance.
     */
    private fun parseCookie(cookie: okhttp3.Cookie): LoginCookie? {
        return try {
            val value = URLDecoder.decode(cookie.value, "UTF-8")
            MoshiInstance.adapter<LoginCookie>().fromJson(value)
        } catch (err: Exception) {
            null
        }
    }

    /**
     * Gets the nonce. There must be a valid cookie to perform this action.
     * You will receive a [LoginRequiredException] if there is
     * no cookie to get the nonce from.
     */
    fun requireNonce(): Api.Nonce {
        val id = parsedCookie?.id ?: throw LoginRequiredException()
        return Api.Nonce(id)
    }

    /**
     * An exception indicating that a login is required.
     */
    class LoginRequiredException : IllegalStateException()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun newCookie(name: String, value: String, domain: String = "pr0gramm.com"): okhttp3.Cookie {
    return okhttp3.Cookie.Builder().name(name).value(value).domain(domain).build()
}
