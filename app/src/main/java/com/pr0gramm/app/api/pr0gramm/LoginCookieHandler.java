package com.pr0gramm.app.api.pr0gramm;

import android.content.SharedPreferences;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Debug;
import com.pr0gramm.app.util.AndroidUtility;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import proguard.annotation.Keep;
import proguard.annotation.KeepClassMembers;

import static com.google.common.base.Objects.equal;
import static java.util.Arrays.asList;

/**
 */
@Singleton
public class LoginCookieHandler implements CookieJar {
    private static final Logger logger = LoggerFactory.getLogger("LoginCookieHandler");

    private static final String PREF_LOGIN_COOKIE = "LoginCookieHandler.cookieValue";

    private final Object lock = new Object();
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    private okhttp3.Cookie httpCookie;
    private OnCookieChangedListener onCookieChangedListener;
    private Optional<Cookie> parsedCookie = Optional.absent();

    @Inject
    public LoginCookieHandler(SharedPreferences preferences) {
        this.preferences = preferences;

        String restored = preferences.getString(PREF_LOGIN_COOKIE, null);
        if (restored != null && !"null".equals(restored)) {
            // logger.info("restoring cookie value from prefs: " + restored);
            setLoginCookie(restored);
        }
    }

    @Override
    public List<okhttp3.Cookie> loadForRequest(HttpUrl url) {
        if (isNoApiRequest(url))
            return Collections.emptyList();

        if (httpCookie == null || httpCookie.value() == null)
            return Collections.emptyList();

        return Collections.singletonList(httpCookie);
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<okhttp3.Cookie> cookies) {
        if (isNoApiRequest(url))
            return;

        for (okhttp3.Cookie cookie : cookies) {
            if (isLoginCookie(cookie)) {
                setLoginCookie(cookie);
            }
        }
    }

    private boolean isNoApiRequest(HttpUrl uri) {
        return !uri.host().equalsIgnoreCase("pr0gramm.com") && !uri.host().contains(Debug.MOCK_API_HOST);
    }

    private boolean isLoginCookie(okhttp3.Cookie cookie) {
        return "me".equals(cookie.name()) && !Strings.isNullOrEmpty(cookie.value());

    }

    private void setLoginCookie(String value) {
        // convert to a http cookie
        setLoginCookie(new okhttp3.Cookie.Builder()
                .name("me")
                .value(value)
                .domain("pr0gramm.com")
                .path("/")
                .expiresAt(DateTime.now().plusYears(10).getMillis())
                .build());
    }

    private void setLoginCookie(okhttp3.Cookie cookie) {
        if (BuildConfig.DEBUG) {
            logger.info("Set login cookie: {}", cookie);
        }

        synchronized (lock) {
            boolean notChanged = httpCookie != null && equal(cookie.value(), httpCookie.value());
            if (notChanged)
                return;

            Optional<Cookie> parsedCookie = parseCookie(cookie);
            boolean valid = parsedCookie.transform(c -> c.id != null && c.n != null).or(false);
            if (valid) {
                this.httpCookie = cookie;
                this.parsedCookie = parsedCookie;

                // store cookie for next time
                preferences.edit()
                        .putString(PREF_LOGIN_COOKIE, cookie.value())
                        .apply();

            } else {
                // couldn't parse the cookie or it is not valid
                clearLoginCookie(true);
            }
        }

        if (onCookieChangedListener != null)
            onCookieChangedListener.onCookieChanged();
    }

    public void clearLoginCookie(boolean informListener) {
        synchronized (lock) {
            httpCookie = null;
            parsedCookie = Optional.absent();
            preferences.edit().remove(PREF_LOGIN_COOKIE).apply();
        }

        if (informListener && onCookieChangedListener != null)
            onCookieChangedListener.onCookieChanged();
    }

    /**
     * Tries to parse the cookie into a {@link LoginCookieHandler.Cookie} instance.
     */
    private Optional<Cookie> parseCookie(okhttp3.Cookie cookie) {
        if (cookie == null || cookie.value() == null)
            return Optional.absent();

        try {
            String value = AndroidUtility.urlDecode(cookie.value(), Charsets.UTF_8);
            return Optional.of(gson.fromJson(value, Cookie.class));
        } catch (Exception err) {
            logger.warn("Could not parse login cookie!", err);

            AndroidUtility.logToCrashlytics(err);
            return Optional.absent();
        }
    }

    /**
     * Gets the value of the login cookie, if any.
     */
    public Optional<String> getLoginCookie() {
        return Optional.fromNullable(httpCookie != null ? httpCookie.value() : null);
    }

    public void setOnCookieChangedListener(OnCookieChangedListener onCookieChangedListener) {
        this.onCookieChangedListener = onCookieChangedListener;
    }

    public Optional<Cookie> getCookie() {
        return parsedCookie;
    }

    /**
     * Gets the nonce. There must be a cookie to perform this action.
     * You will receive a {@link LoginRequiredException} if there is
     * no cookie to get the nonce from.
     */
    public Api.Nonce getNonce() throws LoginRequiredException {
        Optional<Cookie> cookie = getCookie();
        if (!cookie.transform(c -> c.id != null).or(false)) {
            if (cookie.isPresent())
                clearLoginCookie(true);

            throw new LoginRequiredException();
        }

        return cookie.transform(c -> new Api.Nonce(c.id)).get();
    }

    /**
     * Returns true, if the user has pr0mium status.
     */
    public boolean isPaid() {
        Object result = getCookie().transform(cookie -> cookie.paid).or(false);
        if (result instanceof Boolean)
            return (boolean) result;

        if (result instanceof Number)
            return ((Number) result).intValue() != 0;

        return asList("true", "1").contains(result.toString().toLowerCase());
    }

    public boolean hasCookie() {
        return httpCookie != null && parsedCookie.transform(c -> c.id != null).or(false);
    }

    @Keep
    @KeepClassMembers
    public static class Cookie {
        public String n;
        public String id;
        public Object paid;

        @SerializedName("a")
        public Object admin;
    }

    public interface OnCookieChangedListener {
        /**
         * Called if the cookie has changed.
         */
        void onCookieChanged();
    }

    /**
     */
    public static class LoginRequiredException extends IllegalStateException {
        private LoginRequiredException() {
        }
    }
}
