package com.pr0gramm.app.api.pr0gramm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.*
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLDecoder
import java.util.Arrays.asList
import java.util.concurrent.TimeUnit

typealias OnCookieChangedListener = () -> Unit

/**
 */
class LoginCookieHandler(context: Context, private val preferences: SharedPreferences) : CookieJar {
    private val lock = Any()

    private val uniqueToken: String by lazy {
        val input = ConfigService.makeUniqueIdentifier(context, preferences).hashCode()

        // create a string with 32 bytes
        var result = ""
        result += input.toString(16)
        result += result.hashCode().toString(16)
        result += result.hashCode().toString(16)
        result += result.hashCode().toString(16)

        result.toLowerCase()
    }

    private var httpCookie: okhttp3.Cookie? = null

    var onCookieChanged: OnCookieChangedListener? = null

    /**
     * Gets the value of the login cookie, if any.
     */
    val loginCookieValue: String? get() = httpCookie?.value()

    var cookie: Cookie? = null
        private set

    /**
     * Returns true, if the user has pr0mium status.
     */
    val isPaid: Boolean
        get() = cookie?.paid ?: false

    fun hasCookie(): Boolean {
        return httpCookie != null && cookie?.id != null
    }

    init {
        val restored = preferences.getString(PREF_LOGIN_COOKIE, null)
        if (restored != null && restored != "null") {
            setLoginCookie(restored)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<okhttp3.Cookie> {
        val ppCookie = okhttp3.Cookie.Builder()
                .name("pp").value(uniqueToken)
                .hostOnlyDomain("pr0gramm.com")
                .build()

        if (isNoApiRequest(url))
            return listOf(ppCookie)

        val meCookie = httpCookie
        if (meCookie?.value() == null)
            return listOf(ppCookie)

        return listOf(meCookie, ppCookie)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (isNoApiRequest(url))
            return

        cookies.firstOrNull { isLoginCookie(it) }?.let { setLoginCookie(it) }
    }

    private fun isNoApiRequest(uri: HttpUrl): Boolean {
        return !uri.host().equals("pr0gramm.com", ignoreCase = true) && !uri.host().contains(Debug.mockApiHost)
    }

    private fun isLoginCookie(cookie: okhttp3.Cookie): Boolean {
        return cookie.name() == "me" && !cookie.value().isNullOrEmpty()

    }

    private fun setLoginCookie(value: String) {
        // convert to a http cookie
        setLoginCookie(okhttp3.Cookie.Builder()
                .name("me")
                .value(value)
                .domain("pr0gramm.com")
                .path("/")
                .expiresAt(Instant.now().plus(10 * 365, TimeUnit.DAYS).millis)
                .build())
    }

    private fun setLoginCookie(cookie: okhttp3.Cookie) {
        if (BuildConfig.DEBUG) {
            logger.info("Set login cookie: {}", cookie)
        }

        synchronized(lock) {
            val notChanged = httpCookie != null && cookie.value() == httpCookie!!.value()
            if (notChanged)
                return

            val parsedCookie = parseCookie(cookie)

            val valid = parsedCookie?.id != null && parsedCookie.name != null
            if (valid) {
                this.httpCookie = cookie
                this.cookie = parsedCookie

                // store cookie for next time
                preferences.edit {
                    putString(PREF_LOGIN_COOKIE, cookie.value())
                }

            } else {
                // couldn't parse the cookie or it is not valid
                clearLoginCookie(true)
            }
        }

        onCookieChanged?.invoke()
    }

    fun clearLoginCookie(informListener: Boolean) {
        synchronized(lock) {
            cookie = null
            httpCookie = null
            preferences.edit { remove(PREF_LOGIN_COOKIE) }
        }

        if (informListener) {
            onCookieChanged?.invoke()
        }
    }

    /**
     * Tries to parse the cookie into a [LoginCookieHandler.Cookie] instance.
     */
    private fun parseCookie(cookie: okhttp3.Cookie?): Cookie? {
        if (cookie?.value() == null)
            return null

        try {
            val value = URLDecoder.decode(cookie.value(), "UTF-8")
            return MoshiInstance.adapter<Cookie>().fromJson(value)

        } catch (err: Exception) {
            logger.warn("Could not parse login cookie!", err)

            AndroidUtility.logToCrashlytics(err)
            return null
        }
    }

    /**
     * Gets the nonce. There must be a cookie to perform this action.
     * You will receive a [LoginRequiredException] if there is
     * no cookie to get the nonce from.
     */
    val nonce: Api.Nonce
        get() {
            val cookie = cookie
            if (cookie?.id == null) {
                if (cookie != null)
                    clearLoginCookie(true)

                throw LoginRequiredException()
            }

            return Api.Nonce(cookie.id)
        }

    @JsonClass(generateAdapter = true)
    data class Cookie(val id: String?,
                      @Json(name = "n") val name: String?,
                      @Json(name = "paid") val _paid: Any?,
                      @Json(name = "a") val _admin: Any?) {

        @Transient
        val paid = isTruthValue(_paid)

        @Transient
        val admin = isTruthValue(_admin)
    }

    /**
     */
    class LoginRequiredException internal constructor() : IllegalStateException()

    companion object {
        private val logger = logger("LoginCookieHandler")
        private const val PREF_LOGIN_COOKIE = "LoginCookieHandler.cookieValue"

        private fun isTruthValue(value: Any?): Boolean {
            if (value == null)
                return false

            if (value is Boolean)
                return value

            if (value is Number)
                return value.toInt() != 0

            val parsed: Number? = value.toString().toDoubleOrNull()
            if (parsed != null) {
                return parsed.toInt() != 0
            }

            return asList("true", "1").contains(value.toString().toLowerCase())
        }
    }
}
