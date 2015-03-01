package com.pr0gramm.app;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.LoginResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 */
@Singleton
public class UserService {
    private final Api api;
    private final LoginCookieHandler cookieHandler;
    private final Gson gson;

    private final BehaviorSubject<LoginState> loginStateObservable
            = BehaviorSubject.create(LoginState.NOT_AUTHORIZED);

    @Inject
    public UserService(Api api, LoginCookieHandler cookieHandler, Gson gson) {
        this.api = api;
        this.gson = gson;

        this.cookieHandler = cookieHandler;
        this.cookieHandler.setOnCookieChangedListener(this::onCookieChanged);
    }

    private void onCookieChanged() {
        Optional<String> cookie = cookieHandler.getLoginCookie();
        LoginState state = cookie.isPresent() ? LoginState.AUTHORIZED : LoginState.NOT_AUTHORIZED;
        loginStateObservable.onNext(state);
    }

    Observable<LoginResponse> login(String username, String password) {
        return api.login(username, password);
    }

    public boolean isAuthorized() {
        return cookieHandler.getLoginCookie().isPresent();
    }

    public void logout() {
        cookieHandler.clearLoginCookie();
    }

    public Observable<LoginState> getLoginStateObservable() {
        return loginStateObservable.asObservable();
    }

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.
     *
     * @return The name of the currently signed in user.
     */
    public Optional<String> getName() {
        return cookieHandler.getLoginCookie().transform(value -> {
            value = AndroidUtility.urlDecode(value, Charsets.UTF_8);
            Cookie cookie = gson.fromJson(value, Cookie.class);
            return cookie.n;
        });
    }

    private static class Cookie {
        public String n;
    }

    public enum LoginState {
        AUTHORIZED, NOT_AUTHORIZED;
    }
}
