package com.pr0gramm.app.api.pr0gramm

import android.content.SharedPreferences
import com.google.common.base.Strings
import com.google.common.primitives.Doubles
import com.google.gson.*
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Debug
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.edit
import com.pr0gramm.app.util.getIfPrimitive
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.net.URLDecoder
import java.util.Arrays.asList

typealias OnCookieChangedListener = () -> Unit

/**
 */
class LoginCookieHandler(private val preferences: SharedPreferences) : CookieJar {

    private val lock = Any()

    private val gson = GsonBuilder()
            .registerTypeAdapter(Cookie::class.java, CookieDeserializer())
            .create()

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
        if (isNoApiRequest(url))
            return emptyList()

        val theCookie = httpCookie
        if (theCookie == null || theCookie.value() == null)
            return emptyList()

        return listOf(theCookie)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (isNoApiRequest(url))
            return

        cookies.firstOrNull { isLoginCookie(it) }?.let { setLoginCookie(it) }
    }

    private fun isNoApiRequest(uri: HttpUrl): Boolean {
        return !uri.host().equals("pr0gramm.com", ignoreCase = true) && !uri.host().contains(Debug.MOCK_API_HOST)
    }

    private fun isLoginCookie(cookie: okhttp3.Cookie): Boolean {
        return cookie.name() == "me" && !Strings.isNullOrEmpty(cookie.value())

    }

    private fun setLoginCookie(value: String) {
        // convert to a http cookie
        setLoginCookie(okhttp3.Cookie.Builder()
                .name("me")
                .value(value)
                .domain("pr0gramm.com")
                .path("/")
                .expiresAt(DateTime.now().plusYears(10).millis)
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
        if (cookie == null || cookie.value() == null)
            return null

        try {
            val value = URLDecoder.decode(cookie.value(), "UTF-8")
            return gson.fromJson(value, Cookie::class.java)

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
        @Throws(LoginRequiredException::class)
        get() {
            val cookie = cookie
            if (cookie == null || cookie.id == null) {
                if (cookie != null)
                    clearLoginCookie(true)

                throw LoginRequiredException()
            }

            return cookie.let { Api.Nonce(it.id) }
        }

    data class Cookie(val name: String?, val id: String?, val paid: Boolean, val admin: Boolean)

    private inner class CookieDeserializer : JsonDeserializer<Cookie> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Cookie? {
            if (json is JsonObject) {
                return Cookie(
                        id = json.getIfPrimitive("id")?.asString,
                        name = json.getIfPrimitive("n")?.asString,
                        paid = isTruthValue(json.getIfPrimitive("paid")?.asString),
                        admin = isTruthValue(json.getIfPrimitive("a")?.asString))
            } else {
                return null
            }
        }
    }

    /**
     */
    class LoginRequiredException internal constructor() : IllegalStateException()

    companion object {
        private val logger = LoggerFactory.getLogger("LoginCookieHandler")
        private val PREF_LOGIN_COOKIE = "LoginCookieHandler.cookieValue"

        private fun isTruthValue(value: Any?): Boolean {
            if (value == null)
                return false

            if (value is Boolean)
                return value

            if (value is Number)
                return value.toInt() != 0

            val parsed: Number? = Doubles.tryParse(value.toString())
            if (parsed != null) {
                return parsed.toInt() != 0
            }

            return asList("true", "1").contains(value.toString().toLowerCase())
        }
    }
}
