package com.pr0gramm.app;

import android.content.SharedPreferences;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.pr0gramm.app.api.pr0gramm.Api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.google.common.base.Objects.equal;
import static java.util.Arrays.asList;

/**
 */
@Singleton
public class LoginCookieHandler extends CookieHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoginCookieHandler.class);

    private static final String PREF_LOGIN_COOKIE = "LoginCookieHandler.cookieValue";

    private final Object lock = new Object();
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    private HttpCookie httpCookie;
    private OnCookieChangedListener onCookieChangedListener;
    private Optional<Cookie> parsedCookie = Optional.absent();

    @Inject
    public LoginCookieHandler(SharedPreferences preferences) {
        this.preferences = preferences;

        String restored = preferences.getString(PREF_LOGIN_COOKIE, null);
        if (restored != null && !"null".equals(restored)) {
            logger.info("restoring cookie value from prefs: " + restored);
            setLoginCookie(restored);
        }
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
        if (!isApiRequest(uri))
            return requestHeaders;

        if (httpCookie == null || httpCookie.getValue() == null)
            return Collections.emptyMap();

        // logger.debug("LoginCookieHandler", "Add login cookie to request: " + httpCookie.getValue());
        return cookiesToHeaders(Collections.singletonList(httpCookie));
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        if (!isApiRequest(uri)) return;

        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (!"Set-Cookie".equalsIgnoreCase(entry.getKey()))
                continue;

            List<String> values = entry.getValue();
            for (String value : values) {
                List<HttpCookie> cookies;
                try {
                    cookies = HttpCookie.parse(value);
                } catch (IllegalArgumentException err) {
                    logger.warn("LoginCookieHandler", "invalid cookie format", err);
                    continue;
                }

                for (HttpCookie cookie : cookies)
                    handleCookie(cookie);
            }
        }
    }

    private boolean isApiRequest(URI uri) {
        return uri.getHost().equalsIgnoreCase("pr0gramm.com") || uri.getHost().contains("mockable.io");
    }

    private void handleCookie(HttpCookie cookie) {
        if (isLoginCookie(cookie)) {
            // logger.debug("LoginCookieHandler", "Got login cookie: " + cookie.getValue());
            setLoginCookie(cookie);
        }
    }

    private boolean isLoginCookie(HttpCookie cookie) {
        if (!"me".equals(cookie.getName()))
            return false;

        String value = String.valueOf(cookie.getValue());
        return !"null".equals(value);
    }

    public void setLoginCookie(String value) {
        logger.info("Set login cookie called: " + value);

        // convert to a http cookie
        HttpCookie cookie = new HttpCookie("me", value);
        cookie.setVersion(0);
        setLoginCookie(cookie);
    }

    private void setLoginCookie(HttpCookie cookie) {
        synchronized (lock) {
            boolean notChanged = httpCookie != null && equal(cookie.getValue(), httpCookie.getValue());
            if (notChanged)
                return;

            Optional<Cookie> parsedCookie = parseCookie(cookie);
            boolean valid = parsedCookie.transform(c -> c.id != null && c.n != null).or(false);
            if (valid) {
                this.httpCookie = cookie;
                this.parsedCookie = parsedCookie;

                // store cookie for next time
                preferences.edit()
                        .putString(PREF_LOGIN_COOKIE, cookie.getValue())
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
     * Tries to parse the cookie into a {@link com.pr0gramm.app.LoginCookieHandler.Cookie} instance.
     */
    private Optional<Cookie> parseCookie(HttpCookie cookie) {
        if (cookie == null || cookie.getValue() == null)
            return Optional.absent();

        try {
            String value = AndroidUtility.urlDecode(cookie.getValue(), Charsets.UTF_8);
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
        return Optional.fromNullable(httpCookie != null ? httpCookie.getValue() : null);
    }

    public OnCookieChangedListener getOnCookieChangedListener() {
        return onCookieChangedListener;
    }

    public void setOnCookieChangedListener(OnCookieChangedListener onCookieChangedListener) {
        this.onCookieChangedListener = onCookieChangedListener;
    }

    public Optional<Cookie> getCookie() {
        return parsedCookie;
    }

    /**
     * Gets the nonce. There must be a cookie to perform this action.
     * You will receive a {@link com.pr0gramm.app.LoginRequiredException} if there is
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

    public static class Cookie {
        public String n;
        public String id;
        public Object paid;
    }

    /**
     * Got this one from {@link java.net.CookieManager}
     *
     * @param cookies The cookies to encode.
     * @return A map that can be returned from
     * {@link java.net.CookieHandler#get(java.net.URI, java.util.Map)}.
     */
    private static Map<String, List<String>> cookiesToHeaders(List<HttpCookie> cookies) {
        if (cookies.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder result = new StringBuilder();

        // If all cookies are version 1, add a version 1 header. No header for version 0 cookies.
        int minVersion = 1;
        for (HttpCookie cookie : cookies) {
            minVersion = Math.min(minVersion, cookie.getVersion());
        }
        if (minVersion == 1) {
            result.append("$Version=\"1\"; ");
        }

        result.append(cookies.get(0).toString());
        for (int i = 1; i < cookies.size(); i++) {
            result.append("; ").append(cookies.get(i).toString());
        }

        return Collections.singletonMap("Cookie", Collections.singletonList(result.toString()));
    }

    public interface OnCookieChangedListener {
        /**
         * Called if the cookie has changed.
         */
        void onCookieChanged();
    }
}
